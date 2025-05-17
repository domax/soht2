/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Data
@Configuration
@ConfigurationProperties("connector")
public class ConnectorConfig {

  private DataSize readBufferSize = DataSize.ofKilobytes(16);
  private Duration socketTimeout = Duration.ofMillis(100);
}
