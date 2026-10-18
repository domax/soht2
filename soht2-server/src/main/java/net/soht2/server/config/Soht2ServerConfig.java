/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;

/**
 * Configuration properties for the SOHT2 server.
 *
 * <p>This class holds the properties required to configure the SOHT2 server, including the read
 * buffer size and socket timeout.
 */
@Slf4j
@Data
@Configuration
@EnableScheduling
@ConfigurationProperties("soht2.server")
@PropertySource(value = "classpath:version.properties", ignoreResourceNotFound = true)
public class Soht2ServerConfig implements InitializingBean {

  /** The path to the database file. */
  private File databasePath = new File("./soht2");

  /** The default username with administrative permissions for the SOHT2 server. */
  private String adminUsername = "admin";

  /** The default password for the admin user. Randomly generated if not specified. */
  private String defaultAdminPassword;

  /** The size of the read buffer for incoming data. */
  private DataSize readBufferSize = DataSize.ofMegabytes(1);

  /** The timeout for server socket read operations. */
  private Duration socketReadTimeout = Duration.ofMillis(100);

  /** The time-to-live (TTL) for user cache entries. */
  private Duration userCacheTtl = Duration.ofMinutes(10);

  /** Properties for managing abandoned connections. */
  private AbandonedConnectionsProperties abandonedConnections =
      new AbandonedConnectionsProperties();

  /** A public URL of the OpenAPI server */
  private URI openApiServerUrl;

  /** Whether to enable gathering the connection history. */
  private boolean enableHistory = true;

  @Override
  public void afterPropertiesSet() {
    log.debug("afterPropertiesSet: {}", this);
    Assert.notNull(databasePath, "Database path must not be empty");
    Assert.hasText(adminUsername, "Admin username must not be empty");
    Assert.hasText(defaultAdminPassword, "Default admin password must not be empty");
  }

  /**
   * Builds the OpenAPI definition, using the project version baked into {@code version.properties}
   * at build time.
   */
  @Bean
  public OpenAPI soht2OpenApi(
      @Value("${version:unknown}") String version,
      @Value("${soht2.server.open-api-server-url:}") String openApiServerUrl) {
    return new OpenAPI()
        .info(new Info().title("SOHT2 Server API").version(version))
        .servers(List.of(new Server().url(openApiServerUrl).description("SOHT2 OpenAPI Server")));
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
