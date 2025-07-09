package net.soht2.client.config;

import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.Data;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Data
@Configuration
@ConfigurationProperties("connector")
public class ConnectionConfig {

  @Data
  public static class HostProperties {
    private int localPort;
    private String remoteHost;
    private int remotePort;
  }

  /** Client can use a persistent exchange connection, or can issue multiple read commands. */
  public enum ConnectionMode {
    /**
     * A single connection is opened and used for all read operations.<br>
     * This is the default.
     */
    STATEFUL,
    /**
     * A new connection is opened for each read operation.<br>
     * This is less efficient but works with more proxy servers.
     */
    STATELESS
  }

  private Duration socketTimeout = Duration.ofMillis(1000);
  private URI url = URI.create("http://localhost:8080/api/connection");
  private ConnectionMode mode = ConnectionMode.STATEFUL;
  private String username;
  private String password;
  private DataSize readBufferSize = DataSize.ofKilobytes(16);
  private Set<HostProperties> connections = new HashSet<>();

  @Bean
  WebClient webClient() {
    val httpClient = HttpClient.create().compress(true);
    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl(url.toString())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE, APPLICATION_OCTET_STREAM_VALUE)
        .defaultHeader(ACCEPT_ENCODING, "gzip")
        .defaultRequest(
            rq ->
                Optional.of(Stream.of(username, password).filter(StringUtils::hasLength).toList())
                    .filter(l -> l.size() == 2)
                    .map(l -> Strings.join(l, ':'))
                    .map(v -> Base64.getEncoder().encodeToString(v.getBytes()))
                    .ifPresent(v -> rq.header(AUTHORIZATION, "Basic " + v)))
        .build();
  }
}
