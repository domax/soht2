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
import org.springframework.util.unit.DataSize;

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

  @Test
  void targetConnectTimeout_default() {
    assertThat(new Soht2ServerConfig().getTargetConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void targetConnectTimeout_binding() {
    val source =
        new MapConfigurationPropertySource(Map.of("soht2.server.target-connect-timeout", "PT2S"));
    val config = new Binder(source).bind("soht2.server", Soht2ServerConfig.class).get();
    assertThat(config.getTargetConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "-PT1S"})
  void afterPropertiesSet_nonPositive_targetConnectTimeout(String value) {
    val config = validConfig();
    config.setTargetConnectTimeout(Duration.parse(value));
    assertThatIllegalArgumentException()
        .isThrownBy(config::afterPropertiesSet)
        .withMessageContaining("soht2.server.target-connect-timeout");
  }

  @Test
  void maxRequestSize_default() {
    assertThat(new Soht2ServerConfig().getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(4));
  }

  @Test
  void maxRequestSize_binding() {
    val source =
        new MapConfigurationPropertySource(Map.of("soht2.server.max-request-size", "512KB"));
    val config = new Binder(source).bind("soht2.server", Soht2ServerConfig.class).get();
    assertThat(config.getMaxRequestSize()).isEqualTo(DataSize.ofKilobytes(512));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0B", "-1B"})
  void afterPropertiesSet_nonPositive_maxRequestSize(String value) {
    val config = validConfig();
    config.setMaxRequestSize(DataSize.parse(value));
    assertThatIllegalArgumentException()
        .isThrownBy(config::afterPropertiesSet)
        .withMessageContaining("soht2.server.max-request-size");
  }

  @Test
  void cors_default_empty() {
    assertThat(new Soht2ServerConfig().getCors().getAllowedOrigins()).isEmpty();
  }

  @Test
  void cors_binding() {
    val source =
        new MapConfigurationPropertySource(
            Map.of("soht2.server.cors.allowed-origins", "http://localhost:5173"));
    val config = new Binder(source).bind("soht2.server", Soht2ServerConfig.class).get();
    assertThat(config.getCors().getAllowedOrigins()).containsExactly("http://localhost:5173");
  }
}
