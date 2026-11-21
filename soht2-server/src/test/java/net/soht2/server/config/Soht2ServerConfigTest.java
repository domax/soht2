/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.Map;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class Soht2ServerConfigTest {

  private static Soht2ServerConfig validConfig() {
    val config = new Soht2ServerConfig();
    config.setDefaultAdminPassword("test");
    return config;
  }

  @Test
  void downloadHoldTimeout_default() {
    assertThat(new Soht2ServerConfig().getDownloadHoldTimeout()).isEqualTo(Duration.ofSeconds(25));
  }

  @Test
  void downloadHoldTimeout_binding() {
    val source =
        new MapConfigurationPropertySource(Map.of("soht2.server.download-hold-timeout", "PT5S"));
    val config = new Binder(source).bind("soht2.server", Soht2ServerConfig.class).get();
    assertThat(config.getDownloadHoldTimeout()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void afterPropertiesSet_positive_downloadHoldTimeout() {
    val config = validConfig();
    config.setDownloadHoldTimeout(Duration.ofMillis(1));
    assertThatNoException().isThrownBy(config::afterPropertiesSet);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "-PT1S"})
  void afterPropertiesSet_nonPositive_downloadHoldTimeout(String value) {
    val config = validConfig();
    config.setDownloadHoldTimeout(Duration.parse(value));
    assertThatIllegalArgumentException()
        .isThrownBy(config::afterPropertiesSet)
        .withMessageContaining("soht2.server.download-hold-timeout");
  }
}
