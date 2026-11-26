/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.config;

import static net.soht2.client.config.HttpClientTestSupport.contextRunner;
import static net.soht2.client.config.HttpClientTestSupport.properties;
import static net.soht2.common.compress.Compressor.compressorCache;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.ACCEPT_ENCODING;
import static org.springframework.http.HttpHeaders.CONTENT_ENCODING;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.val;
import net.soht2.client.config.HttpClientTestSupport.Mode;
import net.soht2.client.service.Soht2Client;
import net.soht2.client.test.UTHelper;
import net.soht2.common.compress.CompressionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Runs duplex downloads over the real HttpClient 5 transport to check that the transport neither
 * advertises nor decodes HTTP compression on its own: SOHT2 negotiates and decodes it itself.
 */
class HttpCompressionTest {

  private static final byte[] DATA = UTHelper.createBinData(4096);

  private final AtomicReference<String> acceptEncoding = new AtomicReference<>();
  private final AtomicReference<String> responseEncoding = new AtomicReference<>();
  private HttpServer httpServer;

  @BeforeEach
  void startServer() throws Exception {
    httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    httpServer.createContext(
        "/api/connection",
        exchange -> {
          acceptEncoding.set(exchange.getRequestHeaders().getFirst(ACCEPT_ENCODING));
          val encoding = responseEncoding.get();
          val body = encoding == null ? DATA : compressorCache.apply(encoding).compress(DATA);
          if (encoding != null) exchange.getResponseHeaders().set(CONTENT_ENCODING, encoding);
          exchange.sendResponseHeaders(200, body.length);
          try (val out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    httpServer.start();
  }

  @AfterEach
  void stopServer() {
    httpServer.stop(0);
  }

  private Soht2ClientProperties clientProperties(CompressionType compression) {
    val props = properties(Mode.DIRECT, 5);
    props.setUrl(
        URI.create("http://127.0.0.1:" + httpServer.getAddress().getPort() + "/api/connection"));
    props.getCompression().setType(compression);
    return props;
  }

  @SuppressWarnings("SameParameterValue")
  private void download(Soht2ClientProperties props, byte[] expected) {
    contextRunner(props)
        .run(context -> {
          val restClient =
              new Soht2ClientConfig()
                  .restClient(
                      props, RestClient.builder(), context.getBean(ClientHttpRequestFactory.class));
          val download = new Soht2Client(restClient, props).download(UUID.randomUUID(), 1).get();
          assertThat(download.status()).isEqualTo(Soht2Client.DownloadStatus.DATA);
          assertThat(download.data()).isEqualTo(expected);
        });
  }

  @Test
  void download_noCompression_advertisesIdentityOnly() {
    download(clientProperties(CompressionType.NONE), DATA);
    assertThat(acceptEncoding.get()).isEqualTo("identity");
  }

  @Test
  void download_gzip_decodesBodyExactlyOnce() {
    responseEncoding.set("gzip");
    download(clientProperties(CompressionType.GZIP), DATA);
    assertThat(acceptEncoding.get()).isEqualTo("gzip");
  }

  @Test
  void transport_keepsContentEncodedBody() {
    responseEncoding.set("gzip");
    val props = clientProperties(CompressionType.GZIP);
    contextRunner(props)
        .run(context -> {
          val response =
              RestClient.builder()
                  .requestFactory(context.getBean(ClientHttpRequestFactory.class))
                  .build()
                  .get()
                  .uri(props.getUrl())
                  .header(ACCEPT_ENCODING, "gzip")
                  .retrieve()
                  .toEntity(byte[].class);
          assertThat(response.getHeaders().getFirst(CONTENT_ENCODING)).isEqualTo("gzip");
          assertThat(compressorCache.apply("gzip").decompress(response.getBody())).isEqualTo(DATA);
        });
  }
}
