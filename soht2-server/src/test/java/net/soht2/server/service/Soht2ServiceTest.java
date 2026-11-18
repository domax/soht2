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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.val;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.config.Soht2ServerConfig;
import net.soht2.server.entity.UserEntity;
import net.soht2.server.test.EchoClient;
import net.soht2.server.test.EchoServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

  @BeforeEach
  void beforeEach() {
    socketTimeout = (int) soht2ServerConfig.getSocketReadTimeout().toMillis();
    bufferSize = (int) soht2ServerConfig.getReadBufferSize().toBytes();
    authentication = SecurityContextHolder.getContext().getAuthentication();

    doReturn(Optional.of(UserEntity.builder().name("system").role(UserEntity.ROLE_ADMIN).build()))
        .when(soht2UserService)
        .getCachedUserEntity(anyString());
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
