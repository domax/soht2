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

  /**
   * Defines the type of compression applied to requests.
   *
   * <p>This enum specifies the different types of compression that can be applied to requests sent
   * to the SOHT2 server.
   */
  public enum CompressionType {
    /** No compression applied to requests. */
    NONE,
    /** GZIP compression applied to requests. */
    GZIP,
    /** DEFLATE compression applied to requests. */
    DEFLATE
  }

  /**
   * Properties for request compression.
   *
   * <p>This class holds the properties related to request compression, including what is a
   * compression type, and the minimum request size for compression to be applied.
   */
  @Data
  public static class CompressionProperties {

    /**
     * Whether request compression is enabled or, if enabled, which encoding is used.
     *
     * <p>In the case of DEFLATE, only client requests are compressed, while server responses are
     * not. In the case of GZIP, both client requests and server responses are compressed.
     */
    private CompressionType type = CompressionType.NONE;

    /** The threshold size in bytes above which compression is applied. */
    private DataSize minRequestSize = DataSize.ofKilobytes(2);
  }

  /**
   * Properties for polling configuration.
   *
   * <p>This class holds the properties related to polling the server, including initial delay,
   * maximum delay, and factor for exponential backoff.
   */
  @Data
  public static class PollProperties {
    /** The strategy for polling the server for updates. */
    private PollStrategyType strategy = PollStrategyType.EXPONENT;

    /** The initial delay before the first poll request. Used in all supported strategies. */
    private Duration initialDelay = Duration.ofSeconds(1);

    /** The maximum delay between poll requests. Used in LINEAR and EXPONENT strategies. */
    private Duration maxDelay = Duration.ofSeconds(30);

    /** The factor by which the delay increases for EXPONENT strategy. */
    private int factor = 5;
  }

  @Data
  public static class ProxyProperties {
    /** The host name or IP address of the proxy server. If omitted, then proxy won't be used. */
    private String host;

    /** The port number of the proxy server. */
    private int port = 3128;

    /**
     * The optional username for authentication with the proxy server. If omitted, then a proxy
     * without authentication will be used.
     */
    private String username;

    /**
     * The optional password for authentication with the proxy server. We don't recommend setting
     * this property in your configuration file, use environment variable
     * SOHT2_CLIENT_PROXY_PASSWORD instead.
     */
    private String password;

    /** The domain for NTLM authentication with the proxy server. */
    private String domain;
  }

  /** The URL of the SOHT2 server API. */
  private URI url = URI.create("http://localhost:8080/api/connection");

  /** The optional username for basic authentication with the SOHT2 server. */
  private String username;

  /** The optional password for basic authentication with the SOHT2 server. */
  private String password;

  /** The timeout for client socket read operations. */
  private Duration socketReadTimeout = Duration.ofMillis(100);

  /** The size of the read buffer for incoming data. */
  private DataSize readBufferSize = DataSize.ofKilobytes(64);

  /** The list of host properties for connections to be established. */
  private Set<HostProperties> connections = new HashSet<>();

  /** Properties for compression of requests. */
  private CompressionProperties compression = new CompressionProperties();

  /** Properties for polling configuration. */
  private PollProperties poll = new PollProperties();

  /** Properties for proxy configuration. */
  private ProxyProperties proxy = new ProxyProperties();

  /**
   * Whether to disable SSL verification.
   *
   * <p>This property is used to disable SSL certificate verification for HTTPS connections. It is
   * useful for development and testing purposes but should not be used in production environments.
   */
  private boolean disableSslVerification = false;
}
