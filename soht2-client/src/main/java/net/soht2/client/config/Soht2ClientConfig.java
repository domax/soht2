/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Data;
import net.soht2.client.service.ConstantPollStrategy;
import net.soht2.client.service.ExponentPollStrategy;
import net.soht2.client.service.LinearPollStrategy;
import net.soht2.client.service.PollStrategy;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Data
@Configuration
@EnableConfigurationProperties(Soht2ClientProperties.class)
public class Soht2ClientConfig {

  @Bean
  RestClient restClient(Soht2ClientProperties properties, RestClient.Builder builder) {
    // val httpClient = HttpClient.create().compress(true);
    return builder
        // .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl(properties.getUrl().toString())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE, APPLICATION_OCTET_STREAM_VALUE)
        // .defaultHeader(ACCEPT_ENCODING, "gzip") // TODO: add GZIP support
        .defaultRequest(
            rq ->
                Optional.of(
                        Stream.of(properties.getUsername(), properties.getPassword())
                            .filter(StringUtils::hasLength)
                            .toList())
                    .filter(l -> l.size() == 2)
                    .map(l -> Strings.join(l, ':'))
                    .map(v -> Base64.getEncoder().encodeToString(v.getBytes()))
                    .ifPresent(v -> rq.header(AUTHORIZATION, "Basic " + v)))
        .build();
  }

  @Bean
  PollStrategy pollStrategy(Soht2ClientProperties properties) {
    return switch (properties.getPollStrategy()) {
      case CONSTANT -> new ConstantPollStrategy();
      case LINEAR -> new LinearPollStrategy();
      case EXPONENT -> new ExponentPollStrategy();
    };
  }
}
