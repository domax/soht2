/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import io.vavr.control.Try;
import java.net.InetAddress;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.soht2.client.service.HCClientHttpRequestFactory;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Configuration class for setting up HTTP proxy-related components in the application. This class
 * conditionally sets up beans necessary for enabling HTTP communication through a proxy server,
 * with support for both NTLM and Basic authentication mechanisms. Components such as proxy host,
 * credentials, and client request configuration are conditionally instantiated based on application
 * properties.
 *
 * <h3>Annotations:</h3>
 *
 * <ul>
 *   <li>{@link Configuration}: Marks this class as a configuration class for Spring's IoC
 *       container.
 *   <li>{@link ConditionalOnProperty}: Controls bean creation based on the presence of specific
 *       properties.
 *   <li>{@link ConditionalOnBean}: Controls bean creation based on the presence of other beans in
 *       the context.
 *   <li>{@link ConditionalOnMissingBean}: Prevents duplicate bean definitions when a specific bean
 *       is already defined.
 *   <li>{@link Slf4j}: Enables logging for debugging and informational purposes.
 * </ul>
 *
 * <h3>Beans Provided:</h3>
 *
 * <ol>
 *   <li>{@code httpHostProxy}: Provides an {@link HttpHost} representing the proxy server. The
 *       properties for the proxy host (e.g., host and port) are obtained from the application's
 *       configuration.
 *   <li>{@code ntlmProxyCredentials}: Provides NTLM credentials ({@link NTCredentials}) for proxy
 *       authentication if NTLM authentication is required and the necessary properties (username,
 *       password, domain) are set.
 *   <li>{@code basicProxyCredentials}: Provides Basic authentication credentials ({@link
 *       UsernamePasswordCredentials}) for the proxy. This is created if NTLM credentials are not
 *       defined, but the username and password are set.
 *   <li>{@code credentialsProvider}: Provides a {@link CredentialsProvider} to manage
 *       authentication credentials for the proxy. This is dependent on the presence of both the
 *       {@link HttpHost} and {@link Credentials} beans.
 *   <li>{@code requestConfig}: Provides a custom {@link RequestConfig} that configures the HTTP
 *       client's request preferences, including proxy server settings and supported authentication
 *       schemes.
 *   <li>{@code clientHttpRequestFactory}: Provides a {@link ClientHttpRequestFactory} for creating
 *       HTTP requests with the configured proxy, request settings, credentials provider, and
 *       connection manager.
 * </ol>
 *
 * <h3>Dependencies:</h3>
 *
 * <ul>
 *   <li>{@link Soht2ClientProperties}: Supplies the proxy-related properties such as host, port,
 *       username, password, and domain.
 *   <li>{@link HttpClientConnectionManager}: Configurable connection manager for HTTP clients.
 *   <li>{@link AuthScope}, {@link AuthSchemes}, {@link BasicCredentialsProvider}: Used for managing
 *       authentication details.
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty("soht2.client.proxy.host")
public class ProxyConfig {

  @Bean
  HttpHost httpHostProxy(Soht2ClientProperties properties) {
    return Try.of(properties::getProxy)
        .mapTry(p -> new HttpHost(p.getHost(), p.getPort()))
        .onSuccess(proxyHost -> log.info("httpHostProxy: proxyHost={}", proxyHost))
        .get();
  }

  @ConditionalOnProperty("soht2.client.proxy.username")
  @ConditionalOnProperty(value = "soht2.client.proxy.auth", havingValue = "NTLM")
  @Bean
  Credentials ntlmProxyCredentials(Soht2ClientProperties properties) {
    return Try.of(properties::getProxy)
        .mapTry(
            p ->
                new NTCredentials(
                    p.getUsername(),
                    p.getPassword(),
                    InetAddress.getLocalHost().getHostName(),
                    p.getDomain()))
        .onSuccess(credentials -> log.info("ntlmProxyCredentials: {}", credentials))
        .get();
  }

  @ConditionalOnProperty("soht2.client.proxy.username")
  @ConditionalOnMissingBean(value = Credentials.class, name = "ntlmProxyCredentials")
  @Bean
  Credentials basicProxyCredentials(Soht2ClientProperties properties) {
    return Try.of(properties::getProxy)
        .mapTry(p -> new UsernamePasswordCredentials(p.getUsername(), p.getPassword()))
        .onSuccess(credentials -> log.info("basicProxyCredentials: {}", credentials))
        .get();
  }

  @ConditionalOnBean(value = Credentials.class)
  @Bean
  CredentialsProvider credentialsProvider(HttpHost httpHostProxy, Credentials credentials) {
    return Try.of(BasicCredentialsProvider::new)
        .andThenTry(provider -> provider.setCredentials(new AuthScope(httpHostProxy), credentials))
        .onSuccess(provider -> log.info("credentialsProvider: {}", provider))
        .get();
  }

  @Bean
  @ConditionalOnBean(value = CredentialsProvider.class)
  RequestConfig requestConfig(HttpHost httpHostProxy) {
    return RequestConfig.custom()
        .setProxy(httpHostProxy)
        .setProxyPreferredAuthSchemes(List.of(AuthSchemes.BASIC, AuthSchemes.NTLM))
        .build();
  }

  @Bean
  ClientHttpRequestFactory clientHttpRequestFactory(
      HttpHost httpHostProxy,
      @Autowired(required = false) RequestConfig requestConfig,
      @Autowired(required = false) CredentialsProvider credentialsProvider,
      @Autowired(required = false) HttpClientConnectionManager httpClientConnectionManager) {
    log.info(
        "clientHttpRequestFactory: "
            + "httpHostProxy={}, "
            + "requestConfig={}, "
            + "credentialsProvider={}, "
            + "httpClientConnectionManager={}",
        httpHostProxy,
        requestConfig,
        credentialsProvider,
        httpClientConnectionManager);
    return Try.of(
            () ->
                HttpClients.custom()
                    .setProxy(httpHostProxy)
                    .setConnectionManager(httpClientConnectionManager)
                    .setDefaultCredentialsProvider(credentialsProvider)
                    .setDefaultRequestConfig(requestConfig)
                    .build())
        .mapTry(HCClientHttpRequestFactory::new)
        .onSuccess(factory -> log.info("clientHttpRequestFactory: {}", factory))
        .get();
  }
}
