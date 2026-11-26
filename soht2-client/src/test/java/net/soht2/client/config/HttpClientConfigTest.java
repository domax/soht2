/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.config;

import static net.soht2.client.config.HttpClientTestSupport.contextRunner;
import static net.soht2.client.config.HttpClientTestSupport.field;
import static net.soht2.client.config.HttpClientTestSupport.httpClient;
import static net.soht2.client.config.HttpClientTestSupport.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import lombok.val;
import net.soht2.client.config.HttpClientTestSupport.Mode;
import net.soht2.client.config.Soht2ClientProperties.ProxyAuth;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.NTLMSchemeFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class HttpClientConfigTest {

  private static final String STORE_PASSWORD = "changeit";
  private static final HttpHost PROXY = new HttpHost("proxy.example", 3128);

  @TempDir static Path tempDir;
  private static HttpsServer httpsServer;

  @BeforeAll
  static void startHttpsServer() throws Exception {
    val keyStorePath = tempDir.resolve("self-signed.p12");
    val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    val process =
        new ProcessBuilder(
                keytool,
                "-genkeypair",
                "-alias",
                "stub",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "1",
                "-dname",
                "CN=self-signed.invalid",
                "-storetype",
                "PKCS12",
                "-keystore",
                keyStorePath.toString(),
                "-storepass",
                STORE_PASSWORD)
            .redirectErrorStream(true)
            .start();
    assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue())
        .as(new String(process.getInputStream().readAllBytes()))
        .isZero();

    val keyStore = KeyStore.getInstance("PKCS12");
    try (val in = new FileInputStream(keyStorePath.toFile())) {
      keyStore.load(in, STORE_PASSWORD.toCharArray());
    }
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keyStore, STORE_PASSWORD.toCharArray());
    val sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), null, null);

    httpsServer = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
    httpsServer.createContext(
        "/",
        exchange -> {
          val body = "ok".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (val out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    httpsServer.start();
  }

  @AfterAll
  static void stopHttpsServer() {
    if (httpsServer != null) httpsServer.stop(0);
  }

  private static Soht2ClientProperties proxyProperties(ProxyAuth auth) {
    val props = properties(Mode.PROXY, 5);
    props.getProxy().setUsername("proxy-user");
    props.getProxy().setPassword("proxy-secret");
    props.getProxy().setDomain("CORP");
    props.getProxy().setAuth(auth);
    return props;
  }

  private static String httpsUrl() {
    return "https://127.0.0.1:" + httpsServer.getAddress().getPort() + "/";
  }

  @Test
  @SuppressWarnings({"unchecked", "deprecation"})
  void proxyMode_registersBasicAndNtlmAuthSchemes() {
    contextRunner(proxyProperties(ProxyAuth.BASIC))
        .run(context -> {
          val client =
              httpClient(
                  (HttpComponentsClientHttpRequestFactory)
                      context.getBean(ClientHttpRequestFactory.class));
          assertThat(field(field(client, "routePlanner"), "proxy")).isEqualTo(PROXY);
          val registry = (Lookup<AuthSchemeFactory>) field(client, "authSchemeRegistry");
          assertThat(registry.lookup(StandardAuthScheme.BASIC))
              .isInstanceOf(BasicSchemeFactory.class);
          assertThat(registry.lookup(StandardAuthScheme.NTLM))
              .isInstanceOf(NTLMSchemeFactory.class);
        });
  }

  @Test
  void proxyMode_basicAuth_usesUsernamePasswordCredentials() {
    contextRunner(proxyProperties(ProxyAuth.BASIC))
        .run(context -> {
          val credentials = proxyCredentials(context.getBean(ClientHttpRequestFactory.class));
          assertThat(credentials).isInstanceOf(UsernamePasswordCredentials.class);
          val basic = (UsernamePasswordCredentials) credentials;
          assertThat(basic.getUserName()).isEqualTo("proxy-user");
          assertThat(basic.getUserPassword()).isEqualTo("proxy-secret".toCharArray());
        });
  }

  @Test
  @SuppressWarnings("deprecation")
  void proxyMode_ntlmAuth_usesNtCredentials() {
    contextRunner(proxyProperties(ProxyAuth.NTLM))
        .run(context -> {
          val credentials = proxyCredentials(context.getBean(ClientHttpRequestFactory.class));
          assertThat(credentials).isInstanceOf(NTCredentials.class);
          val ntlm = (NTCredentials) credentials;
          assertThat(ntlm.getUserName()).isEqualTo("proxy-user");
          assertThat(ntlm.getDomain()).isEqualTo("CORP");
          assertThat(ntlm.getPassword()).isEqualTo("proxy-secret".toCharArray());
        });
  }

  @Test
  void sslDisabled_trustsSelfSignedCertificate() {
    contextRunner(properties(Mode.SSL_DISABLED, 5))
        .run(context -> {
          val restClient =
              RestClient.builder()
                  .requestFactory(context.getBean(ClientHttpRequestFactory.class))
                  .build();
          assertThat(restClient.get().uri(httpsUrl()).retrieve().body(String.class))
              .isEqualTo("ok");
        });
  }

  @Test
  void sslEnabled_rejectsSelfSignedCertificate() {
    contextRunner(properties(Mode.DIRECT, 5))
        .run(context -> {
          val restClient =
              RestClient.builder()
                  .requestFactory(context.getBean(ClientHttpRequestFactory.class))
                  .build();
          assertThatThrownBy(() -> restClient.get().uri(httpsUrl()).retrieve().body(String.class))
              .isInstanceOf(ResourceAccessException.class)
              .hasCauseInstanceOf(SSLHandshakeException.class);
        });
  }

  private static Object proxyCredentials(ClientHttpRequestFactory factory) {
    val client = httpClient((HttpComponentsClientHttpRequestFactory) factory);
    val provider = (CredentialsProvider) field(client, "credentialsProvider");
    return provider.getCredentials(new AuthScope(PROXY), null);
  }
}
