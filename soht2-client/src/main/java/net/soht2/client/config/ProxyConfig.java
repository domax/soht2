/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.config;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import java.net.InetAddress;
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
public class ProxyConfig {

  @SuppressWarnings({"deprecation", "java:S1874"})
  @ConditionalOnProperty("soht2.client.proxy.username")
  @ConditionalOnProperty("soht2.client.proxy.password")
  @ConditionalOnProperty("soht2.client.proxy.domain")
  @Bean
  Credentials ntlmCredentials(Soht2ClientProperties properties) {
    return Try.of(properties::getProxy)
        .mapTry(p -> Tuple.of(p.getUsername(), p.getPassword().toCharArray(), p.getDomain()))
        .mapTry(t -> t.append(InetAddress.getLocalHost().getHostName()))
        .mapTry(t -> t.apply((un, pw, dm, hn) -> new NTCredentials(un, pw, hn, dm)))
        .onSuccess(credentials -> log.debug("ntlmCredentials: {}", credentials))
        .get();
  }

  @ConditionalOnProperty("soht2.client.proxy.username")
  @ConditionalOnProperty("soht2.client.proxy.password")
  @ConditionalOnMissingBean(value = Credentials.class, name = "ntlmCredentials")
  @Bean
  Credentials basicCredentials(Soht2ClientProperties properties) {
    return Try.of(properties::getProxy)
        .mapTry(p -> Tuple.of(p.getUsername(), p.getPassword().toCharArray()))
        .mapTry(t -> t.apply(UsernamePasswordCredentials::new))
        .onSuccess(credentials -> log.info("basicCredentials: {}", credentials))
        .get();
  }

  @ConditionalOnBean(value = Credentials.class)
  @Bean
  CredentialsProvider credentialsProvider(
      Soht2ClientProperties properties, Credentials credentials) {
    return Try.of(properties::getProxy)
        .mapTry(p -> new AuthScope(p.getHost(), p.getPort()))
        .mapTry(auth -> Tuple.of(auth, new BasicCredentialsProvider()))
        .andThenTry(t -> t._2.setCredentials(t._1, credentials))
        .mapTry(Tuple2::_2)
        .onSuccess(provider -> log.info("credentialsProvider: {}", provider))
        .get();
  }

  @ConditionalOnProperty("soht2.client.proxy.host")
  @Bean
  ClientHttpRequestFactory clientHttpRequestFactory(
      Soht2ClientProperties properties,
      @Autowired(required = false) CredentialsProvider credentialsProvider,
      @Autowired(required = false) HttpClientConnectionManager httpClientConnectionManager) {
    return Try.of(properties::getProxy)
        .mapTry(p -> new HttpHost(p.getHost(), p.getPort()))
        .onSuccess(proxy -> log.info("clientHttpRequestFactory: proxy={}", proxy))
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
