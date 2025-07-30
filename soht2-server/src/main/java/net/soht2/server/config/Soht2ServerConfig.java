/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import java.io.File;
import java.net.URI;
import java.time.Duration;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;

/**
 * Configuration properties for the SOHT2 server.
 *
 * <p>This class holds the properties required to configure the SOHT2 server, including the read
 * buffer size and socket timeout.
 */
// <editor-fold desc="OpenAPI Annotations">
@OpenAPIDefinition(
    info = @Info(title = "SOHT2 Server API", version = "0.0.4"),
    servers = {
      @Server(url = "${soht2.server.open-api-server-url}", description = "SOHT2 OpenAPI Server")
    })
// </editor-fold>
@Slf4j
@Data
@Configuration
@EnableScheduling
@ConfigurationProperties("soht2.server")
public class Soht2ServerConfig implements InitializingBean {

  /** The path to the database file. */
  private File databasePath = new File("./soht2");

  /** The default username with administrative permissions for the SOHT2 server. */
  private String adminUsername = "admin";

  /** The default password for the admin user. Randomly generated if not specified. */
  private String defaultAdminPassword;

  /** The size of the read buffer for incoming data. */
  private DataSize readBufferSize = DataSize.ofKilobytes(64);

  /** The timeout for server socket read operations. */
  private Duration socketReadTimeout = Duration.ofMillis(100);

  /** The time-to-live (TTL) for user cache entries. */
  private Duration userCacheTtl = Duration.ofMinutes(10);

  /** Properties for managing abandoned connections. */
  private AbandonedConnectionsProperties abandonedConnections =
      new AbandonedConnectionsProperties();

  /** A public URL of the OpenAPI server */
  private URI openApiServerUrl;

  @Override
  public void afterPropertiesSet() {
    Assert.notNull(databasePath, "Database path must not be empty");
    Assert.hasText(adminUsername, "Admin username must not be empty");
    Assert.hasText(defaultAdminPassword, "Default admin password must not be empty");
    log.debug("afterPropertiesSet: {}", this);
  }

  /**
   * Properties for managing abandoned connections.
   *
   * <p>This class holds the properties related to abandoned connections, including the timeout
   * after which they will be forcibly closed and the interval at which the server checks for
   * abandoned connections.
   */
  @Data
  public static class AbandonedConnectionsProperties {

    /** The timeout for abandoned connections, after which they will be forcibly closed. */
    private Duration timeout = Duration.ofMinutes(1);

    /** The interval at which the server checks for abandoned connections. */
    private Duration checkInterval = Duration.ofSeconds(5);
  }
}
