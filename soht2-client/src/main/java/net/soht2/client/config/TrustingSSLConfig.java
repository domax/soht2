/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

@Slf4j
@Configuration
@ConditionalOnProperty("soht2.client.disable-ssl-verification")
public class TrustingSSLConfig {

  @Bean
  HttpClientConnectionManager trustingClientConnectionManager() {
    return Try.of(() -> SSLContexts.custom().loadTrustMaterial(null, (c, a) -> true).build())
        .mapTry(ssl -> new DefaultClientTlsStrategy(ssl, NoopHostnameVerifier.INSTANCE))
        .mapTry(
            cts ->
                PoolingHttpClientConnectionManagerBuilder.create()
                    .setTlsSocketStrategy(cts)
                    .build())
        .onSuccess(cm -> log.info("trustingClientConnectionManager: {}", cm))
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
