/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Configuration properties for the SOHT2 client.
 *
 * <p>This class holds the properties required to connect to a SOHT2 server, including the server
 * URL, authentication credentials, socket timeout, read buffer size, polling strategy, and host
 * properties for connections.
 */
@Data
@ConfigurationProperties("soht2.client")
public class Soht2ClientProperties {

  /**
   * Properties for each host connection.
   *
   * <p>This class holds the properties for a single host connection, including the local port,
   * remote host, and remote port.
   */
  @Data
  public static class HostProperties {
    /** The host name or IP address of the local machine. */
    private int localPort;

    /** The host name or IP address of the remote machine. */
    private String remoteHost;

    /** The port number on the remote machine to connect to. */
    private int remotePort;
  }

  /** Defines the strategy for polling the server for updates. */
  public enum PollStrategyType {
    /** Polling strategy that uses a constant delay between requests. */
    CONSTANT,
    /** Polling strategy that increases the delay linearly with each request. */
    LINEAR,
    /** Polling strategy that increases the delay exponentially with each request. */
    EXPONENT
  }

  /** The URL of the SOHT2 server API. */
  private URI url = URI.create("http://localhost:8080/api/connection");

  /** The optional username for basic authentication with the SOHT2 server. */
  private String username;

  /** The optional password for basic authentication with the SOHT2 server. */
  private String password;

  /** The timeout for client socket accept connections. */
  private Duration socketAcceptTimeout = Duration.ofSeconds(10);

  /** The timeout for client socket read operations. */
  private Duration socketReadTimeout = Duration.ofMillis(100);

  /** The size of the read buffer for incoming data. */
  private DataSize readBufferSize = DataSize.ofKilobytes(16);

  /** The strategy for polling the server for updates. */
  private PollStrategyType pollStrategy = PollStrategyType.EXPONENT;

  /** The list of host properties for connections to be established. */
  private Set<HostProperties> connections = new HashSet<>();
}
