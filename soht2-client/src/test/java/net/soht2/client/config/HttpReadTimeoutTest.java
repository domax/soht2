/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

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

  @Test
  void defaultRequestFactory_appliesReadTimeout() throws Exception {
    val value = String.valueOf(applicationYaml().getProperty(READ_TIMEOUT));
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class))
        .withPropertyValues(READ_TIMEOUT + "=" + value)
        .run(context -> {
          val factory = context.getBean(ClientHttpRequestFactory.class);
          assertThat(ReflectionTestUtils.getField(factory, "readTimeout"))
              .isEqualTo(Duration.parse(value));
        });
  }
}
