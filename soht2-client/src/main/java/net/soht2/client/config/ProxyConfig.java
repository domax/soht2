/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import java.security.Principal;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.*;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

@Slf4j
@Configuration
@ConditionalOnProperty("soht2.client.proxy.host")
public class ProxyConfig {

  @ConditionalOnProperty("soht2.client.proxy.username")
  @ConditionalOnProperty("soht2.client.proxy.domain")
  @Bean
  Principal ntlmPrincipal(Soht2ClientProperties properties) {
    return Try.of(properties::getProxy)
        .mapTry(p -> new NTUserPrincipal(p.getDomain(), p.getUsername()))
        .get();
  }

  @ConditionalOnProperty("soht2.client.proxy.username")
  @ConditionalOnMissingBean(value = Principal.class, name = "ntlmPrincipal")
  @Bean
  Principal basicPrincipal(Soht2ClientProperties properties) {
    return new BasicUserPrincipal(properties.getProxy().getUsername());
  }

  @ConditionalOnBean(value = Principal.class)
  @ConditionalOnProperty("soht2.client.proxy.password")
  @Bean
  Credentials proxyCredentials(Soht2ClientProperties properties, Principal proxyPrincipal) {
    return Try.of(properties::getProxy)
        .mapTry(p -> new UsernamePasswordCredentials(proxyPrincipal, p.getPassword().toCharArray()))
        .onSuccess(credentials -> log.info("proxyCredentials: {}", credentials))
        .get();
  }

  @ConditionalOnBean(value = Credentials.class)
  @Bean
  CredentialsProvider credentialsProvider(
      Soht2ClientProperties properties, Credentials credentials) {
    return Try.of(properties::getProxy)
        .mapTry(p -> new AuthScope(p.getHost(), p.getPort()))
        .mapTry(scope -> Tuple.of(scope, new BasicCredentialsProvider()))
        .andThenTry(t -> t._2.setCredentials(t._1, credentials))
        .mapTry(Tuple2::_2)
        .onSuccess(provider -> log.info("credentialsProvider: {}", provider))
        .get();
  }

  @Bean
  ClientHttpRequestFactory clientHttpRequestFactory(
      Soht2ClientProperties properties,
      @Autowired(required = false) CredentialsProvider credentialsProvider,
      @Autowired(required = false) HttpClientConnectionManager httpClientConnectionManager) {
    return Try.of(properties::getProxy)
        .mapTry(p -> new HttpHost(p.getHost(), p.getPort()))
        .onSuccess(proxyHost -> log.info("clientHttpRequestFactory: proxyHost={}", proxyHost))
        .mapTry(
            proxy ->
                HttpClients.custom()
                    .setProxy(proxy)
                    .setDefaultCredentialsProvider(credentialsProvider)
                    .setConnectionManager(httpClientConnectionManager)
                    .build())
        .mapTry(HttpComponentsClientHttpRequestFactory::new)
        .onSuccess(factory -> log.debug("clientHttpRequestFactory: {}", factory))
        .get();
  }
}
