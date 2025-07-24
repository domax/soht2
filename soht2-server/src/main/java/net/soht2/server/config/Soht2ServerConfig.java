/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.config;

import io.vavr.control.Try;
import java.io.File;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;
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
public class Soht2ServerConfig implements InitializingBean {

  /** The properties for the SOHT2 database. */
  private DatabaseProperties database = new DatabaseProperties();

  /** The size of the read buffer for incoming data. */
  private DataSize readBufferSize = DataSize.ofKilobytes(64);

  /** The timeout for server socket read operations. */
  private Duration socketReadTimeout = Duration.ofMillis(100);

  /** The time-to-live (TTL) for user cache entries. */
  private Duration userCacheTtl = Duration.ofMinutes(10);

  /** Properties for managing abandoned connections. */
  private AbandonedConnectionsProperties abandonedConnections =
      new AbandonedConnectionsProperties();

  @Override
  public void afterPropertiesSet() {
    if (!StringUtils.hasLength(database.getDefaultAdminPassword())) {
      database.setDefaultAdminPassword(
          Try.of(() -> new byte[10])
              .andThenTry(b -> new SecureRandom().nextBytes(b))
              .mapTry(HexFormat.of().withLowerCase()::formatHex)
              .get());
      log.warn("Generated default admin password: {}", database.getDefaultAdminPassword());
    }
  }

  /**
   * Properties for the SOHT2 database.
   *
   * <p>This class holds the properties related to the SOHT2 database, including the path to the
   * database file and default administrative credentials.
   */
  @Data
  public static class DatabaseProperties {

    /** The path to the database file. */
    private File path = new File("./soht2");

    /** The default username with administrative permissions for the SOHT2 server. */
    private String adminUsername = "admin";

    /** The default password for the admin user. Randomly generated if not specified. */
    private String defaultAdminPassword;
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
