/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.config;

import static net.soht2.client.config.HttpClientTestSupport.connectionConfig;
import static net.soht2.client.config.HttpClientTestSupport.contextRunner;
import static net.soht2.client.config.HttpClientTestSupport.properties;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.val;
import net.soht2.client.config.HttpClientTestSupport.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * The default HTTP client must not time out a duplex download before the server answers it after
 * {@code soht2.server.download-hold-timeout} (25 s by default).
 */
class HttpReadTimeoutTest {

  private static final String READ_TIMEOUT = "spring.http.clients.read-timeout";
  private static final Duration MIN_READ_TIMEOUT = Duration.ofSeconds(60);

  private static PropertySource<?> applicationYaml() throws Exception {
    return new YamlPropertySourceLoader()
        .load("application.yaml", new ClassPathResource("application.yaml"))
        .getFirst();
  }

  @Test
  void defaultReadTimeout_isAtLeast60s() throws Exception {
    val value = applicationYaml().getProperty(READ_TIMEOUT);
    assertThat(value).isNotNull();
    assertThat(Duration.parse(value.toString())).isGreaterThanOrEqualTo(MIN_READ_TIMEOUT);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void requestFactory_appliesReadTimeout(Mode mode) throws Exception {
    val value = String.valueOf(applicationYaml().getProperty(READ_TIMEOUT));
    contextRunner(properties(mode, 5))
        .withPropertyValues(READ_TIMEOUT + "=" + value)
        .run(context -> {
          val factory =
              (HttpComponentsClientHttpRequestFactory)
                  context.getBean(ClientHttpRequestFactory.class);
          assertThat(connectionConfig(factory).getSocketTimeout().toMilliseconds())
              .isEqualTo(Duration.parse(value).toMillis());
        });
  }

  @Test
  void applicationYaml_selectsHttpComponentsFactory() throws Exception {
    assertThat(applicationYaml().getProperty("spring.http.clients.imperative.factory"))
        .hasToString("http-components");
  }
}
