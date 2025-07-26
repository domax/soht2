/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import io.vavr.control.Try;
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
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

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
    log.debug("restClient: clientHttpRequestFactory={}", clientHttpRequestFactory);
    return ofNullable(clientHttpRequestFactory)
        .map(restClientBuilder::requestFactory)
        .orElse(restClientBuilder)
        .baseUrl(properties.getUrl().toString())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE, APPLICATION_OCTET_STREAM_VALUE)
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
    val poll = properties.getPoll();
    return switch (poll.getStrategy()) {
      case CONSTANT -> ConstantPollStrategy.builder().delay(poll.getInitialDelay()).build();
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

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty("soht2.client.disable-ssl-verification")
  static class AcceptingSSLConfig {

    @Bean
    HttpClientConnectionManager httpClientConnectionManager() {
      return Try.of(() -> SSLContexts.custom().loadTrustMaterial(null, (c, a) -> true).build())
          .mapTry(ssl -> new DefaultClientTlsStrategy(ssl, NoopHostnameVerifier.INSTANCE))
          .mapTry(
              cts ->
                  PoolingHttpClientConnectionManagerBuilder.create()
                      .setTlsSocketStrategy(cts)
                      .build())
          .onSuccess(cm -> log.debug("httpClientConnectionManager: {}", cm))
          .get();
    }

    @Bean
    @ConditionalOnMissingBean
    ClientHttpRequestFactory clientHttpRequestFactory(
        HttpClientConnectionManager httpClientConnectionManager) {
      return Try.of(
              () -> HttpClients.custom().setConnectionManager(httpClientConnectionManager).build())
          .mapTry(HttpComponentsClientHttpRequestFactory::new)
          .onSuccess(factory -> log.debug("clientHttpRequestFactory: {}", factory))
          .get();
    }
  }
}
