/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static net.soht2.common.compress.Compressor.compressorCache;
import static net.soht2.common.dto.Soht2Protocol.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_ENCODING;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.UUID;
import lombok.val;
import net.soht2.client.config.Soht2ClientConfig;
import net.soht2.client.config.Soht2ClientProperties;
import net.soht2.client.test.UTHelper;
import net.soht2.common.dto.Soht2Connection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

@RestClientTest(Soht2Client.class)
@Import(Soht2ClientConfig.class)
@ActiveProfiles("test")
class Soht2ClientTest {

  private static final String CLIENT_HEADER = "X-SOHT2-Client";
  private static final String CLIENT_VALUE = "soht2-client";

  @Autowired MockRestServiceServer server;
  @Autowired Soht2ClientProperties soht2ClientProperties;
  @Autowired Soht2Client soht2Client;
  @Autowired JsonMapper jsonMapper;

  @Test
  void open_OK() {
    val connection =
        Soht2Connection.builder()
            .clientHost("localhost")
            .targetHost("targethost")
            .targetPort(12345)
            .build();

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "?host=localhost&port=8080"))
        .andExpect(method(POST))
        .andExpect(header(CLIENT_HEADER, CLIENT_VALUE))
        .andExpect(header(AUTHORIZATION, "Basic c3lzdGVtOnRlc3QxMjM0"))
        .andRespond(
            withSuccess(jsonMapper.writeValueAsString(connection), MediaType.APPLICATION_JSON));

    val opened = soht2Client.open("localhost", 8080).get();
    assertThat(opened.connection()).isEqualTo(connection);
    assertThat(opened.duplex()).isFalse();
  }

  @Test
  void open_duplexAdvertised() {
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
            withSuccess(jsonMapper.writeValueAsString(connection), MediaType.APPLICATION_JSON)
                .header(HEADER_TRANSPORT, TRANSPORT_DUPLEX));

    val opened = soht2Client.open("localhost", 8080).get();
    assertThat(opened.connection()).isEqualTo(connection);
    assertThat(opened.duplex()).isTrue();
  }

  @Test
  void upload_OK() {
    val connectionId = UUID.randomUUID();
    val bytesIn = UTHelper.createBinData(1024);

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "/" + connectionId + "/upload"))
        .andExpect(method(POST))
        .andExpect(header(CLIENT_HEADER, CLIENT_VALUE))
        .andExpect(header(AUTHORIZATION, "Basic c3lzdGVtOnRlc3QxMjM0"))
        .andExpect(header(HEADER_SEQ, "7"))
        .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(content().bytes(bytesIn))
        .andRespond(withNoContent());

    assertThatNoException().isThrownBy(() -> soht2Client.upload(connectionId, 7, bytesIn).get());
  }

  @Test
  void upload_failure() {
    val connectionId = UUID.randomUUID();

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "/" + connectionId + "/upload"))
        .andRespond(withStatus(HttpStatus.CONFLICT));

    assertThat(soht2Client.upload(connectionId, 1, new byte[] {1}).isFailure()).isTrue();
  }

  @Test
  void download_data() {
    val connectionId = UUID.randomUUID();
    val bytesOut = UTHelper.createBinData(4096);

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "/" + connectionId + "/download"))
        .andExpect(method(GET))
        .andExpect(header(CLIENT_HEADER, CLIENT_VALUE))
        .andExpect(header(AUTHORIZATION, "Basic c3lzdGVtOnRlc3QxMjM0"))
        .andExpect(header(HEADER_SEQ, "3"))
        .andRespond(withSuccess(bytesOut, MediaType.APPLICATION_OCTET_STREAM));

    val download = soht2Client.download(connectionId, 3).get();
    assertThat(download.status()).isEqualTo(Soht2Client.DownloadStatus.DATA);
    assertThat(download.data()).isEqualTo(bytesOut);
  }

  @Test
  void download_compressedData() {
    val connectionId = UUID.randomUUID();
    val bytesOut = UTHelper.createBinData(4096);
    val gzip = compressorCache.apply("gzip");

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "/" + connectionId + "/download"))
        .andRespond(
            withSuccess(gzip.compress(bytesOut), MediaType.APPLICATION_OCTET_STREAM)
                .header(CONTENT_ENCODING, gzip.getContentEncoding()));

    assertThat(soht2Client.download(connectionId, 1).get().data()).isEqualTo(bytesOut);
  }

  @Test
  void download_empty() {
    val connectionId = UUID.randomUUID();

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "/" + connectionId + "/download"))
        .andRespond(withNoContent());

    assertThat(soht2Client.download(connectionId, 1).get()).isEqualTo(Soht2Client.Download.EMPTY);
  }

  @Test
  void download_endOfStream() {
    val connectionId = UUID.randomUUID();

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "/" + connectionId + "/download"))
        .andRespond(withStatus(HttpStatus.GONE).header(HEADER_EOS, EOS_VALUE));

    assertThat(soht2Client.download(connectionId, 1).get()).isEqualTo(Soht2Client.Download.EOS);
  }

  @Test
  void download_gone() {
    val connectionId = UUID.randomUUID();

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "/" + connectionId + "/download"))
        .andRespond(withStatus(HttpStatus.GONE));

    assertThat(soht2Client.download(connectionId, 1).get()).isEqualTo(Soht2Client.Download.GONE);
  }

  @Test
  void download_failure() {
    val connectionId = UUID.randomUUID();

    server
        .expect(requestTo(soht2ClientProperties.getUrl() + "/" + connectionId + "/download"))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThat(soht2Client.download(connectionId, 1).getCause())
        .isInstanceOf(HttpClientErrorException.Forbidden.class);
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
        .andExpect(header(CLIENT_HEADER, CLIENT_VALUE))
        .andExpect(header(AUTHORIZATION, "Basic c3lzdGVtOnRlc3QxMjM0"))
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
        .andExpect(header(CLIENT_HEADER, CLIENT_VALUE))
        .andExpect(header(AUTHORIZATION, "Basic c3lzdGVtOnRlc3QxMjM0"))
        .andRespond(withSuccess());

    assertThatNoException().isThrownBy(() -> soht2Client.close(connectionId).get());
  }
}
