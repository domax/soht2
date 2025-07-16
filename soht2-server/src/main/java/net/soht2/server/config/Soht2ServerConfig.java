/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Configuration properties for the SOHT2 server.
 *
 * <p>This class holds the properties required to configure the SOHT2 server, including the read
 * buffer size and socket timeout.
 */
@Data
@Configuration
@ConfigurationProperties("soht2.server")
public class Soht2ServerConfig {

  /** The size of the read buffer for incoming data. */
  private DataSize readBufferSize = DataSize.ofKilobytes(16);

  /** The timeout for server socket operations. */
  private Duration socketTimeout = Duration.ofMillis(100);
}
