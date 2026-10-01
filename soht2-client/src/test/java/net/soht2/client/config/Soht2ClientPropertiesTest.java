/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import lombok.val;
import net.soht2.client.config.Soht2ClientProperties.HostProperties;
import org.junit.jupiter.api.Test;

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
  void afterPropertiesSet_NEGATIVE_IDLE_TIMEOUT() {
    val props = validProperties();
    props.setDisconnectOnIdleTimeout(Duration.ofSeconds(-1));
    assertThrows(IllegalArgumentException.class, props::afterPropertiesSet);
  }

  @Test
  void afterPropertiesSet_ZERO_IDLE_TIMEOUT() {
    val props = validProperties();
    assertDoesNotThrow(props::afterPropertiesSet);
  }
}
