/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import net.soht2.client.service.HCClientHttpRequestFactory;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Configuration class for creating HTTP client components with SSL verification disabled. This
 * setup is intended for scenarios where SSL verification needs to be bypassed, such as testing
 * environments or when interacting with servers using self-signed certificates.
 *
 * <p>The configuration applies only if the {@code soht2.client.disable-ssl-verification} property
 * is enabled.
 *
 * <h3>Components provided by this configuration:</h3>
 *
 * <ol>
 *   <li>A custom {@link HttpClientConnectionManager} that trusts all SSL certificates and disables
 *       hostname verification.
 *   <li>A {@link ClientHttpRequestFactory} bean that uses the custom connection manager for HTTP
 *       requests.
 * </ol>
 *
 * <h3>Key features include:</h3>
 *
 * <ul>
 *   <li>Trusting all SSL certificates using the {@link TrustAllStrategy}.
 *   <li>Disabling hostname verification with the {@link NoopHostnameVerifier}.
 *   <li>Support for HTTP clients with advanced configuration through {@link
 *       HCClientHttpRequestFactory}.
 * </ul>
 *
 * <h3>Dependency Injection:</h3>
 *
 * <ul>
 *   <li>The {@link ClientHttpRequestFactory} bean is conditionally registered, ensuring no
 *       conflicts if a bean of the same type already exists.
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty("soht2.client.disable-ssl-verification")
public class TrustingSSLConfig {

  @Bean
  HttpClientConnectionManager trustingClientConnectionManager() {
    return Try.of(SSLContextBuilder::create)
        .mapTry(b -> b.loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
        .mapTry(ctx -> new SSLConnectionSocketFactory(ctx, NoopHostnameVerifier.INSTANCE))
        .mapTry(
            sslSocketFactory ->
                RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslSocketFactory)
                    .build())
        .mapTry(PoolingHttpClientConnectionManager::new)
        .onSuccess(cm -> log.info("trustingClientConnectionManager: {}", cm))
        .get();
  }

  @Bean
  @ConditionalOnMissingBean
  ClientHttpRequestFactory clientHttpRequestFactory(
      HttpClientConnectionManager httpClientConnectionManager) {
    return Try.of(
            () -> HttpClients.custom().setConnectionManager(httpClientConnectionManager).build())
        .mapTry(HCClientHttpRequestFactory::new)
        .onSuccess(factory -> log.debug("clientHttpRequestFactory: {}", factory))
        .get();
  }
}
