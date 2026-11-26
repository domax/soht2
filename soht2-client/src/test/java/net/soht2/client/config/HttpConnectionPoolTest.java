/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.config;

import static net.soht2.client.config.HttpClientTestSupport.connectionConfig;
import static net.soht2.client.config.HttpClientTestSupport.contextRunner;
import static net.soht2.client.config.HttpClientTestSupport.pool;
import static net.soht2.client.config.HttpClientTestSupport.properties;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import lombok.val;
import net.soht2.client.config.HttpClientTestSupport.Mode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

class HttpConnectionPoolTest {

  @SuppressWarnings("resource")
  @ParameterizedTest
  @EnumSource(Mode.class)
  void requestFactory_appliesMaxHttpConnections(Mode mode) {
    contextRunner(properties(mode, 7))
        .run(context -> {
          val factory = context.getBean(ClientHttpRequestFactory.class);
          assertThat(factory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
          val cm = pool((HttpComponentsClientHttpRequestFactory) factory);
          assertThat(cm.getMaxTotal()).isEqualTo(7);
          assertThat(cm.getDefaultMaxPerRoute()).isEqualTo(7);
        });
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void requestFactory_validatesStaleConnections(Mode mode) {
    contextRunner(properties(mode, 3))
        .run(context -> {
          val factory =
              (HttpComponentsClientHttpRequestFactory)
                  context.getBean(ClientHttpRequestFactory.class);
          assertThat(connectionConfig(factory).getValidateAfterInactivity().toMilliseconds())
              .isEqualTo(
                  TimeUnit.SECONDS.toMillis(HttpClientConfig.VALIDATE_AFTER_INACTIVITY_SECONDS));
        });
  }
}
