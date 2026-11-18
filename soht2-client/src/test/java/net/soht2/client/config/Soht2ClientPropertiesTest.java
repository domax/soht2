/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import lombok.val;
import net.soht2.client.config.Soht2ClientProperties.HostProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class Soht2ClientPropertiesTest {

  private Soht2ClientProperties validProperties() {
    val props = new Soht2ClientProperties();
    props.setUrl(URI.create("http://localhost:8080/api/connection"));
    props.setUsername("user");
    props.setPassword("pass");
    val host = new HostProperties();
    host.setLocalPort(12345);
    host.setRemoteHost("remotehost");
    host.setRemotePort(8080);
    props.getConnections().add(host);
    return props;
  }

  @Test
  void afterPropertiesSet_negative_idle_timeout() {
    val props = validProperties();
    props.setDisconnectOnIdleTimeout(Duration.ofSeconds(-1));
    assertThrows(IllegalArgumentException.class, props::afterPropertiesSet);
  }

  @Test
  void afterPropertiesSet_zero_idle_timeout() {
    val props = validProperties();
    assertDoesNotThrow(props::afterPropertiesSet);
  }

  @Test
  void maxHttpConnections_default() {
    assertThat(new Soht2ClientProperties().getMaxHttpConnections()).isEqualTo(100);
  }

  @Test
  void maxHttpConnections_binding() {
    val source =
        new MapConfigurationPropertySource(Map.of("soht2.client.max-http-connections", "4"));
    val props = new Binder(source).bind("soht2.client", Soht2ClientProperties.class).get();
    assertThat(props.getMaxHttpConnections()).isEqualTo(4);
  }

  @Test
  void afterPropertiesSet_zero_max_http_connections() {
    val props = validProperties();
    props.setMaxHttpConnections(0);
    assertThrows(IllegalArgumentException.class, props::afterPropertiesSet);
  }
}
