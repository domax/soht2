/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.config;

import java.util.Set;
import java.util.function.Consumer;
import lombok.val;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds the client HTTP request factory the same way the application context does. */
final class HttpClientTestSupport {

  static final String FACTORY = "spring.http.clients.imperative.factory=http-components";

  private HttpClientTestSupport() {}

  /** Client modes that each customize the HttpClient 5 transport differently. */
  enum Mode {
    DIRECT(p -> {}),
    PROXY(p -> {
      p.getProxy().setHost("proxy.example");
      p.getProxy().setPort(3128);
    }),
    SSL_DISABLED(p -> p.setDisableSslVerification(true));

    final Consumer<Soht2ClientProperties> customizer;

    Mode(Consumer<Soht2ClientProperties> customizer) {
      this.customizer = customizer;
    }
  }

  static Soht2ClientProperties properties(Mode mode, int maxHttpConnections) {
    val host = new Soht2ClientProperties.HostProperties();
    host.setLocalPort(22222);
    host.setRemoteHost("localhost");
    host.setRemotePort(22);
    val props = new Soht2ClientProperties();
    props.setUsername("user");
    props.setPassword("secret");
    props.setConnections(Set.of(host));
    props.setMaxHttpConnections(maxHttpConnections);
    mode.customizer.accept(props);
    return props;
  }

  static ApplicationContextRunner contextRunner(Soht2ClientProperties properties) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class))
        .withUserConfiguration(HttpClientConfig.class)
        .withBean(Soht2ClientProperties.class, () -> properties)
        .withPropertyValues(FACTORY);
  }

  static Object httpClient(HttpComponentsClientHttpRequestFactory factory) {
    return factory.getHttpClient();
  }

  static Object field(Object target, String name) {
    return ReflectionTestUtils.getField(target, name);
  }

  static PoolingHttpClientConnectionManager pool(HttpComponentsClientHttpRequestFactory factory) {
    return (PoolingHttpClientConnectionManager) field(httpClient(factory), "connManager");
  }

  @SuppressWarnings("unchecked")
  static ConnectionConfig connectionConfig(HttpComponentsClientHttpRequestFactory factory) {
    val resolver =
        (Resolver<HttpRoute, ConnectionConfig>) field(pool(factory), "connectionConfigResolver");
    return resolver.resolve(new HttpRoute(new HttpHost("server.example", 8080)));
  }
}
