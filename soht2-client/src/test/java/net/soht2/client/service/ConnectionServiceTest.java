/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.vavr.control.Try;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.client.config.Soht2ClientConfig;
import net.soht2.client.config.Soht2ClientProperties;
import net.soht2.client.test.EchoClient;
import net.soht2.client.test.UTHelper;
import net.soht2.common.dto.Soht2Connection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

@Slf4j
@SpringBootTest(classes = ConnectionService.class)
@Import(Soht2ClientConfig.class)
@ActiveProfiles("test")
class ConnectionServiceTest {

  @Autowired ConnectionService connectionService;
  @Autowired Soht2ClientProperties soht2ClientProperties;

  @MockitoBean Soht2Client soht2Client;
  @MockitoBean RestClient restClient;

  @Test
  void startConnections_OK() {
    val bufferSize = (int) soht2ClientProperties.getReadBufferSize().toBytes();
    val socketTimeout = (int) soht2ClientProperties.getSocketReadTimeout().toMillis();

    val bytesIn = UTHelper.createBinData(bufferSize * 2 / 3);
    val bytesEmpty = new byte[0];
    val bytesOut1 = UTHelper.createBinData(bufferSize);
    val bytesOut2 = UTHelper.createBinData(bufferSize / 3);

    val host =
        soht2ClientProperties.getConnections().stream().findAny().orElseThrow(AssertionError::new);
    val soht2 =
        Soht2Connection.builder()
            .targetHost(host.getRemoteHost())
            .targetPort(host.getRemotePort())
            .build();

    doReturn(Try.success(soht2)).when(soht2Client).open(anyString(), anyInt());
    doReturn(
            Try.success(bytesIn),
            Try.success(bytesOut1),
            Try.success(bytesOut2),
            Try.success(bytesEmpty),
            Try.<byte[]>success(null),
            Try.<byte[]>failure(new RuntimeException("Test error")))
        .when(soht2Client)
        .exchange(any(UUID.class), any());
    doReturn(Try.success((Void) null)).when(soht2Client).close(any(UUID.class));

    connectionService.startConnections();
    //    await().until(() -> !connectionService.getConnectionIds(soht2.id()).isEmpty());
    try (val echoClient =
        EchoClient.builder()
            .portNumber(host.getLocalPort())
            .socketTimeout(socketTimeout)
            .bufferSize(bufferSize)
            .build()) {
      echoClient.shout(bytesIn);
      await().until(() -> connectionService.isServerOpen(soht2.id()));
    }

    verify(soht2Client).open(host.getRemoteHost(), host.getRemotePort());
    verify(soht2Client).exchange(soht2.id(), bytesIn);
    verify(soht2Client, atLeast(5)).exchange(soht2.id(), bytesEmpty);
    verify(soht2Client).close(soht2.id());
  }
}
