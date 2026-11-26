/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import static io.vavr.API.unchecked;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static net.soht2.server.test.UTHelper.createBinData;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.vavr.control.Try;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.val;
import net.soht2.common.compress.Compressor;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.config.Soht2ServerConfig;
import net.soht2.server.entity.UserEntity;
import net.soht2.server.test.EchoClient;
import net.soht2.server.test.EchoServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

@SuppressWarnings("java:S2925")
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "system", password = "test", authorities = UserEntity.ROLE_ADMIN)
class Soht2ServiceTest {

  static final int PORT_NUMBER = 12345;

  @Autowired Soht2Service soht2Service;
  @Autowired Soht2ServerConfig soht2ServerConfig;

  @MockitoBean Soht2UserService soht2UserService;

  int socketTimeout;
  int bufferSize;
  Authentication authentication;
  Duration downloadHoldTimeout;
  Duration abandonedTimeout;

  @BeforeEach
  void beforeEach() {
    socketTimeout = (int) soht2ServerConfig.getSocketReadTimeout().toMillis();
    bufferSize = (int) soht2ServerConfig.getReadBufferSize().toBytes();
    authentication = SecurityContextHolder.getContext().getAuthentication();
    downloadHoldTimeout = soht2ServerConfig.getDownloadHoldTimeout();
    abandonedTimeout = soht2ServerConfig.getAbandonedConnections().getTimeout();

    doReturn(Optional.of(UserEntity.builder().name("system").role(UserEntity.ROLE_ADMIN).build()))
        .when(soht2UserService)
        .getCachedUserEntity(anyString());
  }

  @AfterEach
  void afterEach() {
    soht2ServerConfig.setDownloadHoldTimeout(downloadHoldTimeout);
    soht2ServerConfig.getAbandonedConnections().setTimeout(abandonedTimeout);
  }

  @Test
  void testEcho() {
    val inputList = List.of(createBinData(bufferSize), createBinData(bufferSize));
    val resultSize = inputList.stream().mapToInt(v -> v.length).sum();

    val expected = new ByteArrayOutputStream(resultSize);
    inputList.forEach(v -> Try.run(() -> expected.write(v)).get());

    val actual = new ByteArrayOutputStream(resultSize);
    try (val server =
            EchoServer.builder()
                .portNumber(PORT_NUMBER)
                .socketTimeout(socketTimeout)
                .bufferSize(bufferSize)
                .build();
        val client = new EchoClient(server)) {
      inputList.stream()
          // give it at least 0.5 seconds to make sure the server is processed a whole buffer
          .peek(v -> Try.run(() -> Thread.sleep(500)).get())
          .peek(v -> assertThat(server.isRunning()).isTrue())
          .map(
              unchecked(bytes ->
                  // wrap to thread to prove a client.shout() has access to the socket
                  supplyAsync(() -> client.shout(bytes)).get()))
          .forEach(data -> Try.run(() -> actual.write(data)).get());
      assertThat(actual.toByteArray()).isEqualTo(expected.toByteArray());
    }
  }

  @Test
  void testExchange() {
    try (val server =
            EchoServer.builder()
                .portNumber(PORT_NUMBER)
                .socketTimeout(socketTimeout)
                .bufferSize(bufferSize)
                .build();
        val client =
            soht2Service.open(
                Soht2Connection.builder()
                    .user(Soht2User.builder().username("system").build())
                    .clientHost("localhost")
                    .targetHost("localhost")
                    .targetPort(PORT_NUMBER)
                    .build(),
                authentication)) {
      assertThat(client.isOpened()).isTrue();
      val connectionId = client.soht2().id();
      assertThat(soht2Service.list(authentication))
          .hasSize(1)
          .first()
          .extracting(Soht2Connection::id)
          .isEqualTo(connectionId);

      val inputList =
          List.of(createBinData(bufferSize), createBinData(bufferSize * 2 / 3), new byte[0]);
      val resultSize = inputList.stream().mapToInt(v -> v.length).sum();

      val expected = new ByteArrayOutputStream(resultSize);
      inputList.forEach(v -> Try.run(() -> expected.write(v)).get());

      val actual = new ByteArrayOutputStream(resultSize);
      inputList.stream()
          .peek(v -> Try.run(() -> Thread.sleep(100)).get())
          .peek(v -> assertThat(server.isRunning()).isTrue())
          .map(data -> soht2Service.exchange(connectionId, data, null).get())
          .forEach(data -> Try.run(() -> actual.write(data)).get());

      assertThat(actual.toByteArray()).isEqualTo(expected.toByteArray());
    }

    assertThat(soht2Service.list(authentication)).isEmpty();
  }

  @Test
  void testExchange_returnsIndependentArrays() {
    try (val ignored =
            EchoServer.builder()
                .portNumber(PORT_NUMBER)
                .socketTimeout(socketTimeout)
                .bufferSize(bufferSize)
                .build();
        val client =
            soht2Service.open(
                Soht2Connection.builder()
                    .user(Soht2User.builder().username("system").build())
                    .clientHost("localhost")
                    .targetHost("localhost")
                    .targetPort(PORT_NUMBER)
                    .build(),
                authentication)) {
      val connectionId = client.soht2().id();
      val first = createBinData(bufferSize);
      val second = createBinData(bufferSize);

      val firstChunks = exchangeFully(connectionId, first);
      val secondChunks = exchangeFully(connectionId, second);

      // the first response must not be overwritten by the next read on the same connection
      assertThat(concat(firstChunks)).isEqualTo(first);
      assertThat(concat(secondChunks)).isEqualTo(second);
      assertThat(firstChunks).allMatch(c -> c.length <= bufferSize);
      firstChunks.forEach(a -> secondChunks.forEach(b -> assertThat(a).isNotSameAs(b)));
    }
  }

  @Test
  void testDuplex_roundTrip() {
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val id = sc.soht2().id();
      val data = createBinData(bufferSize * 2 + 100);
      assertThat(soht2Service.upload(id, 1, data, null).isSuccess()).isTrue();
      assertThat(downloadFully(id, 1, data.length)).isEqualTo(data);
      assertThat(sc.bytesWritten()).isEqualTo(data.length);
      assertThat(sc.bytesRead()).isEqualTo(data.length);
    }
  }

  @Test
  void testDownload_emptyAfterHoldTimeout() {
    soht2ServerConfig.setDownloadHoldTimeout(Duration.ofMillis(500));
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val start = System.nanoTime();
      val result = soht2Service.download(sc.soht2().id(), 1).get();
      val elapsed = Duration.ofNanos(System.nanoTime() - start);
      assertThat(result.isEmpty()).isTrue();
      assertThat(elapsed).isBetween(Duration.ofMillis(450), Duration.ofSeconds(3));
      assertThat(sc.isOpened()).isTrue();
    }
  }

  @Test
  void testUpload_whileDownloadIsHeld() throws Exception {
    soht2ServerConfig.setDownloadHoldTimeout(Duration.ofSeconds(10));
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val id = sc.soht2().id();
      val held = CompletableFuture.supplyAsync(() -> soht2Service.download(id, 1).get());
      Thread.sleep(300);
      assertThat(held).isNotDone();

      val data = "ping".getBytes(StandardCharsets.UTF_8);
      val start = System.nanoTime();
      assertThat(soht2Service.upload(id, 1, data, null).isSuccess()).isTrue();
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));

      // the held download is answered as soon as the echoed data arrives, not after the hold time
      assertThat(held.get(2, TimeUnit.SECONDS).data()).isEqualTo(data);
    }
  }

  @Test
  void testUpload_repeatedSeqIsNotWrittenTwice() {
    soht2ServerConfig.setDownloadHoldTimeout(Duration.ofMillis(500));
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val id = sc.soht2().id();
      val data = "hello".getBytes(StandardCharsets.UTF_8);
      assertThat(soht2Service.upload(id, 1, data, null).isSuccess()).isTrue();
      assertThat(soht2Service.upload(id, 1, data, null).isSuccess()).isTrue();
      assertThat(sc.bytesWritten()).isEqualTo(data.length);

      assertThat(soht2Service.download(id, 1).get().data()).isEqualTo(data);
      assertThat(soht2Service.download(id, 2).get().isEmpty()).isTrue();
    }
  }

  @Test
  void testDownload_repeatedSeqReplaysSameBytes() {
    soht2ServerConfig.setDownloadHoldTimeout(Duration.ofMillis(500));
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val id = sc.soht2().id();
      val data = "hello".getBytes(StandardCharsets.UTF_8);
      soht2Service.upload(id, 1, data, null).get();

      val first = soht2Service.download(id, 1).get();
      val retry = soht2Service.download(id, 1).get();
      assertThat(first.data()).isEqualTo(data);
      assertThat(retry.data()).isEqualTo(data);
      assertThat(sc.bytesRead()).isEqualTo(data.length);
      assertThat(soht2Service.download(id, 2).get().isEmpty()).isTrue();
    }
  }

  @Test
  void testDownload_repeatedEmptySeqStaysEmpty() throws Exception {
    soht2ServerConfig.setDownloadHoldTimeout(Duration.ofMillis(300));
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val id = sc.soht2().id();
      assertThat(soht2Service.download(id, 1).get().isEmpty()).isTrue();

      val data = "late".getBytes(StandardCharsets.UTF_8);
      soht2Service.upload(id, 1, data, null).get();
      Thread.sleep(200);

      // a retry of the empty download replays the empty result and never reads the target
      assertThat(soht2Service.download(id, 1).get().isEmpty()).isTrue();
      assertThat(sc.bytesRead()).isZero();
      // the new data is returned only under the next sequence number
      assertThat(soht2Service.download(id, 2).get().data()).isEqualTo(data);
    }
  }

  @Test
  void testUpload_compressionBombIsRejectedAndRetryable() {
    soht2ServerConfig.setDownloadHoldTimeout(Duration.ofMillis(500));
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val id = sc.soht2().id();
      val gzip = Compressor.compressorCache.apply("gzip");
      val maxBytes = (int) soht2ServerConfig.getMaxRequestSize().toBytes();
      val bomb = gzip.compress(new byte[maxBytes + 1]);

      val rejected = soht2Service.upload(id, 1, bomb, "gzip");
      assertThat(rejected.isFailure()).isTrue();
      assertThat(rejected.getCause())
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
      assertThat(sc.isOpened()).isTrue();
      assertThat(sc.bytesWritten()).isZero();
      assertThat(sc.lastUploadSeq().get()).isZero();

      // a retry with the same sequence number and a valid body is written and acknowledged
      val data = "retry".getBytes(StandardCharsets.UTF_8);
      assertThat(soht2Service.upload(id, 1, gzip.compress(data), "gzip").isSuccess()).isTrue();
      assertThat(sc.bytesWritten()).isEqualTo(data.length);
      assertThat(soht2Service.download(id, 1).get().data()).isEqualTo(data);
    }
  }

  @Test
  void testExchange_compressionBombIsRejected() {
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val gzip = Compressor.compressorCache.apply("gzip");
      val maxBytes = (int) soht2ServerConfig.getMaxRequestSize().toBytes();
      val bomb = gzip.compress(new byte[maxBytes + 1]);

      val rejected = soht2Service.exchange(sc.soht2().id(), bomb, "gzip");
      assertThat(rejected.isFailure()).isTrue();
      assertThat(rejected.getCause())
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
      assertThat(sc.isOpened()).isTrue();
      assertThat(sc.bytesWritten()).isZero();
    }
  }

  @Test
  void testUpload_outOfOrderSeqConflictsAndCloses() {
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val result = soht2Service.upload(sc.soht2().id(), 3, new byte[] {1}, null);
      assertThat(result.isFailure()).isTrue();
      assertThat(result.getCause())
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
      assertThat(sc.isOpened()).isFalse();
      assertThat(sc.bytesWritten()).isZero();
    }
    assertThat(soht2Service.list(authentication)).isEmpty();
  }

  @Test
  void testDownload_outOfOrderSeqConflictsAndCloses() {
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val result = soht2Service.download(sc.soht2().id(), 2);
      assertThat(result.getCause())
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
      assertThat(sc.isOpened()).isFalse();
    }
  }

  @Test
  void testDownload_targetCloseYieldsEosAfterFinalData() throws Exception {
    val finalData = "bye".getBytes(StandardCharsets.UTF_8);
    try (val serverSocket = new ServerSocket(PORT_NUMBER)) {
      val target =
          CompletableFuture.runAsync(() ->
              Try.run(
                      () -> {
                        try (val socket = serverSocket.accept()) {
                          socket.getOutputStream().write(finalData);
                          socket.getOutputStream().flush();
                        }
                      })
                  .get());
      try (val sc = openConnection()) {
        val id = sc.soht2().id();
        target.get(2, TimeUnit.SECONDS);

        assertThat(downloadFully(id, 1, finalData.length)).isEqualTo(finalData);
        val eos = soht2Service.download(id, sc.lastDownloadSeq().get() + 1).get();
        assertThat(eos.eos()).isTrue();
        assertThat(sc.isOpened()).isFalse();

        val retry = soht2Service.download(id, sc.lastDownloadSeq().get());
        assertThat(retry.getCause())
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.GONE));
      }
    }
    assertThat(soht2Service.list(authentication)).isEmpty();
  }

  @Test
  void testDownload_closedWhileHeldIsGone() throws Exception {
    soht2ServerConfig.setDownloadHoldTimeout(Duration.ofSeconds(10));
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val id = sc.soht2().id();
      val held = CompletableFuture.supplyAsync(() -> soht2Service.download(id, 1));
      Thread.sleep(300);
      soht2Service.close(id);

      val result = held.get(2, TimeUnit.SECONDS);
      assertThat(result.getCause())
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.GONE));
    }
  }

  @Test
  void testExchange_afterHeldDownloadUsesShortReadTimeout() {
    soht2ServerConfig.setDownloadHoldTimeout(Duration.ofMillis(800));
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val id = sc.soht2().id();
      assertThat(soht2Service.download(id, 1).get().isEmpty()).isTrue();

      // the hold left a long socket timeout; a simple exchange must restore socket-read-timeout
      val start = System.nanoTime();
      assertThat(soht2Service.exchange(id, null, null).get()).isEmpty();
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(600));
    }
  }

  @Test
  void testCloseAbandoned_keepsConnectionWithHeldDownload() throws Exception {
    soht2ServerConfig.setDownloadHoldTimeout(Duration.ofSeconds(3));
    soht2ServerConfig.getAbandonedConnections().setTimeout(Duration.ofMillis(200));
    try (val ignored = echoServer();
        val sc = openConnection()) {
      val id = sc.soht2().id();
      val held = CompletableFuture.supplyAsync(() -> soht2Service.download(id, 1));
      Thread.sleep(600);
      assertThat(sc.downloadInFlight().get()).isTrue();
      assertThat(sc.activityAge()).isGreaterThanOrEqualTo(Duration.ofMillis(200));

      soht2Service.closeAbandonedConnections();
      assertThat(sc.isOpened()).isTrue();

      val data = "still here".getBytes(StandardCharsets.UTF_8);
      assertThat(soht2Service.upload(id, 1, data, null).isSuccess()).isTrue();
      assertThat(held.get(2, TimeUnit.SECONDS).get().data()).isEqualTo(data);
      assertThat(sc.downloadInFlight().get()).isFalse();
    }
  }

  @Test
  void testCloseAbandoned_closesIdleConnection() throws Exception {
    soht2ServerConfig.getAbandonedConnections().setTimeout(Duration.ofMillis(200));
    try (val ignored = echoServer();
        val sc = openConnection()) {
      Thread.sleep(400);
      soht2Service.closeAbandonedConnections();
      assertThat(sc.isOpened()).isFalse();
    }
  }

  @Test
  void testDownload_coalescesBurstIntoOnePayload() throws Exception {
    val readBufferSize = soht2ServerConfig.getReadBufferSize();
    soht2ServerConfig.setReadBufferSize(DataSize.ofKilobytes(64));
    val chunks = List.of(createBinData(10240), createBinData(10240), createBinData(10240));
    val expected = new ByteArrayOutputStream();
    for (val c : chunks) expected.write(c);
    try (val serverSocket = new ServerSocket(PORT_NUMBER);
        val sc = openConnection()) {
      try (val target = serverSocket.accept()) {
        for (val c : chunks) {
          target.getOutputStream().write(c);
          target.getOutputStream().flush();
        }
        Thread.sleep(200);
        val result = soht2Service.download(sc.soht2().id(), 1).get();
        assertThat(result.data()).isEqualTo(expected.toByteArray());
      }
    } finally {
      soht2ServerConfig.setReadBufferSize(readBufferSize);
    }
  }

  @Test
  void testDownload_payloadNeverExceedsReadBufferSize() throws Exception {
    val data = createBinData(bufferSize * 3 + 123);
    try (val serverSocket = new ServerSocket(PORT_NUMBER);
        val sc = openConnection()) {
      try (val target = serverSocket.accept()) {
        target.getOutputStream().write(data);
        target.getOutputStream().flush();
        Thread.sleep(200);
        val id = sc.soht2().id();
        val received = new ByteArrayOutputStream();
        for (long seq = 1; received.size() < data.length; seq++) {
          val payload = soht2Service.download(id, seq).get().data();
          assertThat(payload.length).isLessThanOrEqualTo(bufferSize);
          received.write(payload);
        }
        assertThat(received.toByteArray()).isEqualTo(data);
      }
    }
  }

  @Test
  void testOpen_setsTcpNoDelay() {
    try (val ignored = echoServer();
        val sc = openConnection()) {
      assertThat(sc.isTcpNoDelay()).isTrue();
    }
  }

  @Test
  void testOpen_unreachableTargetFailsWithinConnectTimeout() {
    val connectTimeout = soht2ServerConfig.getTargetConnectTimeout();
    soht2ServerConfig.setTargetConnectTimeout(Duration.ofSeconds(1));
    try {
      val start = System.nanoTime();
      // a non-routable address: connection attempts are silently dropped (or rejected at once)
      val result = Try.of(() -> openConnection("10.255.255.1", PORT_NUMBER));
      val elapsed = Duration.ofNanos(System.nanoTime() - start);
      assertThat(result.isFailure()).isTrue();
      assertThat(result.getCause()).isInstanceOf(IOException.class);
      assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
      assertThat(soht2Service.list(authentication)).isEmpty();
    } finally {
      soht2ServerConfig.setTargetConnectTimeout(connectTimeout);
    }
  }

  EchoServer echoServer() {
    return EchoServer.builder()
        .portNumber(PORT_NUMBER)
        .socketTimeout(socketTimeout)
        .bufferSize(bufferSize)
        .build();
  }

  ServerConnection openConnection() {
    return openConnection("localhost", PORT_NUMBER);
  }

  @SuppressWarnings("SameParameterValue")
  ServerConnection openConnection(String targetHost, int targetPort) {
    return soht2Service.open(
        Soht2Connection.builder()
            .user(Soht2User.builder().username("system").build())
            .clientHost("localhost")
            .targetHost(targetHost)
            .targetPort(targetPort)
            .build(),
        authentication);
  }

  /** Downloads with consecutive sequence numbers starting at {@code seq} until enough bytes. */
  @SuppressWarnings("SameParameterValue")
  byte[] downloadFully(UUID id, long seq, int expectedLength) {
    val out = new ByteArrayOutputStream(expectedLength);
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    var next = seq;
    while (out.size() < expectedLength && System.nanoTime() < deadline) {
      val result = soht2Service.download(id, next++).get();
      assertThat(result.eos()).isFalse();
      out.writeBytes(result.data());
    }
    return out.toByteArray();
  }

  List<byte[]> exchangeFully(UUID id, byte[] data) {
    val chunks = new ArrayList<byte[]>();
    var received = 0;
    var payload = data;
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (received < data.length && System.nanoTime() < deadline) {
      val chunk = soht2Service.exchange(id, payload, null).get();
      payload = null;
      if (chunk.length > 0) {
        chunks.add(chunk);
        received += chunk.length;
      }
    }
    return chunks;
  }

  static byte[] concat(List<byte[]> chunks) {
    val out = new ByteArrayOutputStream();
    chunks.forEach(out::writeBytes);
    return out.toByteArray();
  }
}
