/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.vavr.control.Try;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.val;
import net.soht2.client.config.Soht2ClientConfig;
import net.soht2.client.config.Soht2ClientProperties;
import net.soht2.client.test.EchoClient;
import net.soht2.client.test.UTHelper;
import net.soht2.common.dto.Soht2Connection;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    doReturn(Try.success(soht2)).when(soht2Client).open(anyString(), anyInt());
    doReturn(Try.success((Void) null)).when(soht2Client).close(any(UUID.class));
  }

  @AfterEach
  void afterEach() {
    soht2ClientProperties.setDisconnectOnIdleTimeout(Duration.ZERO);
    connectionService.stopConnections();
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
    doReturn(Try.failure(new IllegalStateException("open failed")), Try.success(soht2))
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
    doReturn(Try.success(soht2Blocked), Try.success(soht2Active))
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

  private void startAndAwait() {
    val socketStarted = new AtomicBoolean();
    connectionService.startConnections(h -> socketStarted.set(true));
    await().until(socketStarted::get);
  }
}
