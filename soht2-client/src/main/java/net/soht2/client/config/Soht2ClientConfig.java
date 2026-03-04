/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.client.service.ConstantPollStrategy;
import net.soht2.client.service.ExponentPollStrategy;
import net.soht2.client.service.LinearPollStrategy;
import net.soht2.client.service.PollStrategy;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Configuration class for the SOHT2 client.
 *
 * <p>This class is responsible for configuring and instantiating beans required for the SOHT2
 * client. It includes the setup for the REST client and defines the polling strategy based on
 * application properties. The configuration depends on {@link Soht2ClientProperties} for
 * client-specific settings.
 */
@Slf4j
@Data
@Configuration
@EnableConfigurationProperties(Soht2ClientProperties.class)
public class Soht2ClientConfig {

  @Bean
  RestClient restClient(
      Soht2ClientProperties properties,
      RestClient.Builder restClientBuilder,
      @Autowired(required = false) ClientHttpRequestFactory clientHttpRequestFactory) {
    ofNullable(clientHttpRequestFactory).ifPresent(restClientBuilder::requestFactory);
    restClientBuilder
        .baseUrl(properties.getUrl().toString())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE, APPLICATION_OCTET_STREAM_VALUE);
    Optional.of(
            Stream.of(properties.getUsername(), properties.getPassword())
                .filter(StringUtils::hasLength)
                .toList())
        .filter(creds -> creds.size() == 2)
        .map(creds -> Strings.join(creds, ':'))
        .map(creds -> Base64.getEncoder().encodeToString(creds.getBytes()))
        .ifPresent(creds -> restClientBuilder.defaultHeader(AUTHORIZATION, "Basic " + creds));

    return restClientBuilder.build();
  }

  @Bean
  PollStrategy pollStrategy(Soht2ClientProperties properties) {
    val poll = properties.getPoll();
    return switch (poll.getStrategy()) {
      case CONSTANT -> ConstantPollStrategy.builder().delay(poll.getMaxDelay()).build();
      case LINEAR ->
          LinearPollStrategy.builder()
              .initialDelay(poll.getInitialDelay())
              .maxDelay(poll.getMaxDelay())
              .build();
      case EXPONENT ->
          ExponentPollStrategy.builder()
              .initialDelay(poll.getInitialDelay())
              .maxDelay(poll.getMaxDelay())
              .factor(poll.getFactor())
              .build();
    };
  }
}
