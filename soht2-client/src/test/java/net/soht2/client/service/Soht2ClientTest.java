/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.val;
import net.soht2.client.config.Soht2ClientConfig;
import net.soht2.client.config.Soht2ClientProperties;
import net.soht2.client.test.UTHelper;
import net.soht2.common.dto.Soht2Connection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(Soht2Client.class)
@Import(Soht2ClientConfig.class)
@ActiveProfiles("test")
class Soht2ClientTest {

  @Autowired MockRestServiceServer server;
  @Autowired Soht2ClientProperties soht2ClientProperties;
  @Autowired Soht2Client soht2Client;
  @Autowired ObjectMapper objectMapper;

  @Test
  void open_OK() throws Exception {
    val connection =
        Soht2Connection.builder()
            .clientHost("localhost")
            .targetHost("targethost")
            .targetPort(12345)
            .build();

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "?host=localhost&port=8080"))
        .andExpect(method(POST))
        .andRespond(
            withSuccess(objectMapper.writeValueAsString(connection), MediaType.APPLICATION_JSON));

    assertThat(soht2Client.open("localhost", 8080).get()).isEqualTo(connection);
  }

  @Test
  void exchange_OK() {
    val connectionId = UUID.randomUUID();
    val bufferSize = (int) soht2ClientProperties.getReadBufferSize().toBytes();
    val bytesIn = UTHelper.createBinData(bufferSize * 2 / 3);
    val bytesOut = UTHelper.createBinData(bufferSize * 3 / 2);

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "/" + connectionId))
        .andExpect(method(POST))
        .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(content().bytes(bytesIn))
        .andRespond(withSuccess(bytesOut, MediaType.APPLICATION_OCTET_STREAM));

    assertThat(soht2Client.exchange(connectionId, bytesIn).get()).isEqualTo(bytesOut);
  }

  @Test
  void close_OK() {
    val connectionId = UUID.randomUUID();

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "/" + connectionId))
        .andExpect(method(DELETE))
        .andRespond(withSuccess());

    assertThat(soht2Client.close(connectionId).get()).isNull();
  }
}
