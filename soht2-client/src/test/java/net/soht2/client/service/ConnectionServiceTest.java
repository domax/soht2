/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.vavr.control.Try;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.val;
import net.soht2.client.config.Soht2ClientConfig;
import net.soht2.client.config.Soht2ClientProperties;
import net.soht2.client.service.Soht2Client.Download;
import net.soht2.client.service.Soht2Client.OpenResult;
import net.soht2.client.test.EchoClient;
import net.soht2.client.test.UTHelper;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2Protocol.TransportType;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

@SpringBootTest(classes = ConnectionService.class)
@Import(Soht2ClientConfig.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConnectionServiceTest {

  @Autowired ConnectionService connectionService;
  @Autowired Soht2ClientProperties soht2ClientProperties;

  @MockitoBean Soht2Client soht2Client;
  @MockitoBean RestClient restClient;

  final byte[] bytesEmpty = new byte[0];

  Soht2ClientProperties.HostProperties host;
  Soht2Connection soht2;

  @BeforeEach
  void beforeEach() {
    host =
        soht2ClientProperties.getConnections().stream().findAny().orElseThrow(AssertionError::new);
    soht2ClientProperties.setConnections(Set.of(host));
    soht2 =
        Soht2Connection.builder()
            .targetHost(host.getRemoteHost())
            .targetPort(host.getRemotePort())
            .build();

    doReturn(Try.success(new OpenResult(soht2, false)))
        .when(soht2Client)
        .open(anyString(), anyInt());
    doReturn(Try.success((Void) null)).when(soht2Client).close(any(UUID.class));
    doReturn(Try.success((Void) null)).when(soht2Client).upload(any(UUID.class), anyLong(), any());
  }

  @AfterEach
  void afterEach() {
    soht2ClientProperties.setDisconnectOnIdleTimeout(Duration.ZERO);
    soht2ClientProperties.setTransport(TransportType.DUPLEX);
    connectionService.stopConnections();
    host.setLocalHost("127.0.0.1");
  }

  @Test
  @Order(1)
  void startConnections_OK() {
    soht2ClientProperties.setDisconnectOnIdleTimeout(Duration.ofSeconds(1));

    val bufferSize = (int) soht2ClientProperties.getReadBufferSize().toBytes();
    val socketTimeout = (int) soht2ClientProperties.getSocketReadTimeout().toMillis();

    val bytesIn = UTHelper.createBinData(bufferSize * 2 / 3);
    val bytesOut1 = UTHelper.createBinData(bufferSize);
    val bytesOut2 = UTHelper.createBinData(bufferSize / 3);

    doReturn(
            Try.success(bytesIn),
            Try.success(bytesOut1),
            Try.success(bytesOut2),
            Try.success(bytesEmpty))
        .when(soht2Client)
        .exchange(any(UUID.class), any());

    val socketStarted = new AtomicBoolean();
    connectionService.startConnections(h -> socketStarted.set(true));
    await().until(socketStarted::get);
    try (val echoClient =
        EchoClient.builder()
            .portNumber(host.getLocalPort())
            .socketTimeout(socketTimeout)
            .bufferSize(bufferSize)
            .build()) {
      echoClient.shout(bytesIn);
      await()
          .pollDelay(Duration.ofSeconds(1))
          .until(() -> connectionService.isSessionClosed(soht2.id()));
    }

    verify(soht2Client).open(host.getRemoteHost(), host.getRemotePort());
    verify(soht2Client).exchange(soht2.id(), bytesIn);
    verify(soht2Client, atLeast(5)).exchange(soht2.id(), bytesEmpty);
    verify(soht2Client).close(soht2.id());
  }

  @Test
  @Order(2)
  void startConnections_openFailureClosesSocketAndKeepsAccepting() throws Exception {
    doReturn(
            Try.failure(new IllegalStateException("open failed")),
            Try.success(new OpenResult(soht2, false)))
        .when(soht2Client)
        .open(anyString(), anyInt());
    doReturn(Try.success(bytesEmpty)).when(soht2Client).exchange(any(UUID.class), any());

    startAndAwait();

    try (val socket = new Socket("localhost", host.getLocalPort())) {
      socket.setSoTimeout(5000);
      assertThat(socket.getInputStream().read()).isEqualTo(-1);
    }

    try (val ignored = new Socket("localhost", host.getLocalPort())) {
      await().until(() -> !connectionService.isSessionClosed(soht2.id()));
    }
    verify(soht2Client, times(2)).open(host.getRemoteHost(), host.getRemotePort());
  }

  @Test
  @Order(3)
  void stopConnections_releasesPortForRestart() throws Exception {
    startAndAwait();
    connectionService.stopConnections();
    new ServerSocket(host.getLocalPort()).close();

    startAndAwait();
    try (val ignored = new Socket("localhost", host.getLocalPort())) {
      await().until(() -> !connectionService.isSessionClosed(soht2.id()));
    }
  }

  @Test
  @Order(4)
  void startConnections_sessionsOnSamePortAreIndependent() {
    val bufferSize = (int) soht2ClientProperties.getReadBufferSize().toBytes();
    val soht2Blocked =
        Soht2Connection.builder()
            .targetHost(host.getRemoteHost())
            .targetPort(host.getRemotePort())
            .build();
    val soht2Active =
        Soht2Connection.builder()
            .targetHost(host.getRemoteHost())
            .targetPort(host.getRemotePort())
            .build();
    doReturn(
            Try.success(new OpenResult(soht2Blocked, false)),
            Try.success(new OpenResult(soht2Active, false)))
        .when(soht2Client)
        .open(anyString(), anyInt());

    val release = new CountDownLatch(1);
    doAnswer(
            inv -> {
              if (soht2Blocked.id().equals(inv.getArgument(0))) {
                await()
                    .with()
                    .timeout(11, TimeUnit.SECONDS)
                    .until(() -> release.await(10, TimeUnit.SECONDS));
                return Try.success(bytesEmpty);
              }
              return Try.success(inv.<byte[]>getArgument(1));
            })
        .when(soht2Client)
        .exchange(any(UUID.class), any());

    startAndAwait();
    val bytesIn = UTHelper.createBinData(bufferSize / 2);
    try (val ignored =
        EchoClient.builder()
            .portNumber(host.getLocalPort())
            .socketTimeout(5000)
            .bufferSize(bufferSize)
            .build()) {
      await().until(() -> !connectionService.isSessionClosed(soht2Blocked.id()));
      verify(soht2Client, timeout(5000)).exchange(eq(soht2Blocked.id()), any());

      try (val activeClient =
          EchoClient.builder()
              .portNumber(host.getLocalPort())
              .socketTimeout(5000)
              .bufferSize(bufferSize)
              .build()) {
        val started = System.nanoTime();
        assertThat(activeClient.shout(bytesIn)).isEqualTo(bytesIn);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
      }
      assertThat(release.getCount()).isEqualTo(1);
    } finally {
      release.countDown();
    }
  }

  @Test
  @Order(5)
  void duplex_uploadProceedsWhileDownloadIsBlocked() throws Exception {
    openDuplex();
    val release = new CountDownLatch(1);
    doAnswer(
            inv -> {
              await()
                  .with()
                  .timeout(11, TimeUnit.SECONDS)
                  .until(() -> release.await(10, TimeUnit.SECONDS));
              return Try.success(Download.EMPTY);
            })
        .when(soht2Client)
        .download(any(UUID.class), anyLong());

    startAndAwait();
    val bytesIn1 = UTHelper.createBinData(100);
    val bytesIn2 = UTHelper.createBinData(200);
    try (val socket = new Socket("localhost", host.getLocalPort())) {
      verify(soht2Client, timeout(5000)).download(soht2.id(), 1);
      socket.getOutputStream().write(bytesIn1);
      socket.getOutputStream().flush();
      verify(soht2Client, timeout(2000)).upload(soht2.id(), 1, bytesIn1);
      socket.getOutputStream().write(bytesIn2);
      socket.getOutputStream().flush();
      verify(soht2Client, timeout(2000)).upload(soht2.id(), 2, bytesIn2);
      assertThat(release.getCount()).isEqualTo(1);
    } finally {
      release.countDown();
    }
    verify(soht2Client, never()).exchange(any(UUID.class), any());
  }

  @SuppressWarnings("java:S2925")
  @Test
  @Order(6)
  void duplex_serverDataIsWrittenWithoutPollDelay() throws Exception {
    openDuplex();
    val bytesOut = UTHelper.createBinData(1000);
    val dataSent = new AtomicBoolean();
    doAnswer(
            inv -> {
              if (dataSent.compareAndSet(false, true)) {
                Thread.sleep(300);
                return Try.success(Download.of(bytesOut));
              }
              Thread.sleep(100);
              return Try.success(Download.EMPTY);
            })
        .when(soht2Client)
        .download(any(UUID.class), anyLong());

    startAndAwait();
    try (val socket = new Socket("localhost", host.getLocalPort())) {
      socket.setSoTimeout(5000);
      val started = System.nanoTime();
      assertThat(socket.getInputStream().readNBytes(bytesOut.length)).isEqualTo(bytesOut);
      assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
      verify(soht2Client, timeout(2000)).download(soht2.id(), 2);
    }
  }

  @Test
  @Order(7)
  void duplex_endOfStreamClosesLocalSocketWithoutDelete() throws Exception {
    openDuplex();
    val bytesOut = UTHelper.createBinData(100);
    doReturn(Try.success(Download.of(bytesOut)), Try.success(Download.EOS))
        .when(soht2Client)
        .download(any(UUID.class), anyLong());

    startAndAwait();
    try (val socket = new Socket("localhost", host.getLocalPort())) {
      socket.setSoTimeout(5000);
      assertThat(socket.getInputStream().readNBytes(bytesOut.length)).isEqualTo(bytesOut);
      assertThat(socket.getInputStream().read()).isEqualTo(-1);
    }
    await().until(() -> connectionService.isSessionClosed(soht2.id()));
    verify(soht2Client, never()).close(soht2.id());
  }

  @Test
  @Order(8)
  void duplex_localEndOfStreamClosesServerConnection() throws Exception {
    openDuplex();
    stubEmptyDownloads();

    startAndAwait();
    try (val ignored = new Socket("localhost", host.getLocalPort())) {
      await().until(() -> !connectionService.isSessionClosed(soht2.id()));
    }
    await().until(() -> connectionService.isSessionClosed(soht2.id()));
    verify(soht2Client, timeout(2000)).close(soht2.id());
  }

  @Test
  @Order(9)
  void duplex_failedUploadClosesSession() throws Exception {
    openDuplex();
    stubEmptyDownloads();
    doReturn(Try.failure(new IllegalStateException("upload failed")))
        .when(soht2Client)
        .upload(any(UUID.class), anyLong(), any());

    startAndAwait();
    try (val socket = new Socket("localhost", host.getLocalPort())) {
      socket.setSoTimeout(5000);
      socket.getOutputStream().write(UTHelper.createBinData(10));
      socket.getOutputStream().flush();
      assertThat(socket.getInputStream().read()).isEqualTo(-1);
    }
    assertThat(connectionService.isSessionClosed(soht2.id())).isTrue();
    verify(soht2Client, timeout(2000)).close(soht2.id());
  }

  @SuppressWarnings({"java:S2925", "BusyWait"})
  @Test
  @Order(10)
  void duplex_idleTimeoutFiresOnlyWhenBothLanesAreIdle() throws Exception {
    openDuplex();
    soht2ClientProperties.setDisconnectOnIdleTimeout(Duration.ofSeconds(1));
    val downloadActiveUntil = System.nanoTime() + Duration.ofMillis(1500).toNanos();
    doAnswer(
            inv -> {
              Thread.sleep(200);
              return Try.success(
                  System.nanoTime() < downloadActiveUntil
                      ? Download.of(new byte[] {1})
                      : Download.EMPTY);
            })
        .when(soht2Client)
        .download(any(UUID.class), anyLong());

    startAndAwait();
    try (val socket = new Socket("localhost", host.getLocalPort())) {
      await().until(() -> !connectionService.isSessionClosed(soht2.id()));
      // Download lane carries data for 1.5 s, upload lane carries data until 2.5 s.
      val uploadActiveUntil = System.nanoTime() + Duration.ofMillis(2500).toNanos();
      while (System.nanoTime() < uploadActiveUntil) {
        assertThat(connectionService.isSessionClosed(soht2.id())).isFalse();
        socket.getOutputStream().write(1);
        socket.getOutputStream().flush();
        Thread.sleep(200);
      }
      val idleSince = System.nanoTime();
      await()
          .atMost(Duration.ofSeconds(3))
          .until(() -> connectionService.isSessionClosed(soht2.id()));
      assertThat(Duration.ofNanos(System.nanoTime() - idleSince))
          .isGreaterThanOrEqualTo(Duration.ofMillis(700));
    }
    verify(soht2Client, timeout(2000)).close(soht2.id());
  }

  @Test
  @Order(11)
  void transport_simpleSettingUsesExchange() throws Exception {
    openDuplex();
    soht2ClientProperties.setTransport(TransportType.SIMPLE);
    doReturn(Try.success(bytesEmpty)).when(soht2Client).exchange(any(UUID.class), any());

    startAndAwait();
    try (val ignored = new Socket("localhost", host.getLocalPort())) {
      verify(soht2Client, timeout(5000).atLeastOnce()).exchange(eq(soht2.id()), any());
    }
    verify(soht2Client, never()).download(any(UUID.class), anyLong());
    verify(soht2Client, never()).upload(any(UUID.class), anyLong(), any());
  }

  @Test
  @Order(12)
  void transport_missingOpenHeaderFallsBackToExchange() throws Exception {
    doReturn(Try.success(bytesEmpty)).when(soht2Client).exchange(any(UUID.class), any());

    startAndAwait();
    try (val ignored = new Socket("localhost", host.getLocalPort())) {
      verify(soht2Client, timeout(5000).atLeastOnce()).exchange(eq(soht2.id()), any());
    }
    verify(soht2Client, never()).download(any(UUID.class), anyLong());
    verify(soht2Client, never()).upload(any(UUID.class), anyLong(), any());
  }

  @SuppressWarnings("resource")
  @Test
  @Order(13)
  void serve_acceptedSocketHasTcpNoDelay() throws Exception {
    openDuplex();
    stubEmptyDownloads();

    startAndAwait();
    try (val ignored = new Socket("127.0.0.1", host.getLocalPort())) {
      await().until(() -> !connectionService.isSessionClosed(soht2.id()));
      assertThat(sessionSocket().getTcpNoDelay()).isTrue();
      // duplex upload wakes up once a second at most (idle timeout disabled)
      assertThat(sessionSocket().getSoTimeout()).isEqualTo(1000);
    }
  }

  @SuppressWarnings("resource")
  @Test
  @Order(14)
  void duplex_idleSessionIsClosedAfterIdleTimeout() throws Exception {
    openDuplex();
    stubEmptyDownloads();
    soht2ClientProperties.setDisconnectOnIdleTimeout(Duration.ofMillis(500));

    startAndAwait();
    try (val ignored = new Socket("127.0.0.1", host.getLocalPort())) {
      await().until(() -> !connectionService.isSessionClosed(soht2.id()));
      assertThat(sessionSocket().getSoTimeout()).isEqualTo(500);
      await()
          .atMost(Duration.ofSeconds(3))
          .until(() -> connectionService.isSessionClosed(soht2.id()));
    }
    verify(soht2Client, timeout(2000)).close(soht2.id());
  }

  @Test
  @Order(15)
  void duplex_localDataIsUploadedPromptly() throws Exception {
    openDuplex();
    stubEmptyDownloads();

    startAndAwait();
    val bytesIn = UTHelper.createBinData(100);
    try (val socket = new Socket("127.0.0.1", host.getLocalPort())) {
      await().until(() -> !connectionService.isSessionClosed(soht2.id()));
      val started = System.nanoTime();
      socket.getOutputStream().write(bytesIn);
      socket.getOutputStream().flush();
      verify(soht2Client, timeout(1000)).upload(soht2.id(), 1, bytesIn);
      assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(500));
    }
  }

  @Test
  @Order(16)
  void duplexWakeMillis_isBoundedByIdleTimeout() {
    soht2ClientProperties.setDisconnectOnIdleTimeout(Duration.ZERO);
    assertThat(connectionService.duplexWakeMillis()).isEqualTo(1000);
    soht2ClientProperties.setDisconnectOnIdleTimeout(Duration.ofMinutes(5));
    assertThat(connectionService.duplexWakeMillis()).isEqualTo(1000);
    soht2ClientProperties.setDisconnectOnIdleTimeout(Duration.ofMillis(250));
    assertThat(connectionService.duplexWakeMillis()).isEqualTo(250);
  }

  @SuppressWarnings("unchecked")
  private Socket sessionSocket() {
    val sessions =
        (Map<UUID, ConnectionService.SessionState>)
            ReflectionTestUtils.getField(connectionService, "sessions");
    assertThat(sessions).isNotNull().containsKey(soht2.id());
    return sessions.get(soht2.id()).socket;
  }

  private void openDuplex() {
    doReturn(Try.success(new OpenResult(soht2, true)))
        .when(soht2Client)
        .open(anyString(), anyInt());
  }

  @SuppressWarnings("java:S2925")
  private void stubEmptyDownloads() {
    doAnswer(
            inv -> {
              Thread.sleep(100);
              return Try.success(Download.EMPTY);
            })
        .when(soht2Client)
        .download(any(UUID.class), anyLong());
  }

  private void startAndAwait() {
    val socketStarted = new AtomicBoolean();
    connectionService.startConnections(h -> socketStarted.set(true));
    await().until(socketStarted::get);
  }

  @Test
  @Order(20)
  void connect_defaultBindsLoopbackOnly() throws Exception {
    startAndAwait();
    assertThat(boundAddress().isLoopbackAddress()).isTrue();

    val external = nonLoopbackAddress();
    assumeTrue(external.isPresent(), "no non-loopback IPv4 address available");
    try (val socket = new Socket()) {
      val address = new InetSocketAddress(external.get(), host.getLocalPort());
      // Refused (or dropped, on some VPN/firewall setups): either way, not accepted.
      assertThatThrownBy(() -> socket.connect(address, 2000))
          .isInstanceOfAny(ConnectException.class, SocketTimeoutException.class);
    }
  }

  @Test
  @Order(21)
  void connect_wildcardBindAcceptsOtherHostsAndWarns() throws Exception {
    val logger = (Logger) LoggerFactory.getLogger(ConnectionService.class);
    val appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    host.setLocalHost("0.0.0.0");
    try {
      startAndAwait();
    } finally {
      logger.detachAppender(appender);
    }
    assertThat(boundAddress().isAnyLocalAddress()).isTrue();
    val warning = "connect: port " + host.getLocalPort() + " accepts connections from other hosts";
    assertThat(appender.list)
        .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().startsWith(warning));

    val external = nonLoopbackAddress();
    assumeTrue(external.isPresent(), "no non-loopback IPv4 address available");
    try (val ignored = new Socket(external.get(), host.getLocalPort())) {
      await().until(() -> !connectionService.isSessionClosed(soht2.id()));
    }
  }

  @SuppressWarnings({"unchecked", "resource"})
  private InetAddress boundAddress() {
    val sockets =
        (Set<ServerSocket>) ReflectionTestUtils.getField(connectionService, "serverSockets");
    assertThat(sockets).isNotNull().hasSize(1);
    return sockets.iterator().next().getInetAddress();
  }

  private static Optional<InetAddress> nonLoopbackAddress() throws Exception {
    return NetworkInterface.networkInterfaces()
        .filter(ni -> Try.of(() -> ni.isUp() && !ni.isLoopback()).getOrElse(false))
        .flatMap(NetworkInterface::inetAddresses)
        .filter(a -> a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress())
        .findFirst();
  }
}
