/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.config;

import static java.util.Optional.ofNullable;

import io.vavr.control.Try;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.client.config.Soht2ClientProperties.ProxyAuth;
import net.soht2.client.config.Soht2ClientProperties.ProxyProperties;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.NTLMSchemeFactory;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Configures the Apache HttpClient 5 transport used to reach the SOHT2 server in every mode
 * (direct, through an HTTP proxy, and with SSL verification disabled).
 *
 * <p>The customizer is applied to the Spring Boot built {@link
 * HttpComponentsClientHttpRequestFactoryBuilder}, so {@code spring.http.clients.*} settings such as
 * {@code read-timeout} and {@code connect-timeout} keep applying. It:
 *
 * <ul>
 *   <li>sizes the connection pool (total and per route) by {@code
 *       soht2.client.max-http-connections};
 *   <li>disables automatic response decompression, because SOHT2 negotiates and decodes tunnel
 *       compression itself;
 *   <li>routes requests through {@code soht2.client.proxy.host} with Basic or NTLM credentials,
 *       when configured;
 *   <li>trusts any server certificate and host name when {@code
 *       soht2.client.disable-ssl-verification} is set.
 * </ul>
 */
@Slf4j
@Configuration
public class HttpClientConfig {

  static final long VALIDATE_AFTER_INACTIVITY_SECONDS = 2;

  @Bean
  @SuppressWarnings("deprecation")
  ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder>
      soht2HttpClientCustomizer(Soht2ClientProperties properties) {
    val maxConnections = properties.getMaxHttpConnections();
    val proxy = properties.getProxy();
    val proxyHost = StringUtils.hasText(proxy.getHost()) ? proxy.getHost() : null;
    val proxyAuth =
        proxyHost != null && StringUtils.hasText(proxy.getUsername()) ? proxy.getAuth() : null;
    log.info(
        "soht2HttpClientCustomizer: maxHttpConnections={}, proxy={}, proxyAuth={},"
            + " disableSslVerification={}",
        maxConnections,
        proxyHost != null ? proxyHost + ":" + proxy.getPort() : "none",
        ofNullable(proxyAuth).map(Enum::name).orElse("none"),
        properties.isDisableSslVerification());

    return builder -> {
      var result =
          builder
              .withConnectionManagerCustomizer(
                  cm -> cm.setMaxConnTotal(maxConnections).setMaxConnPerRoute(maxConnections))
              .withConnectionConfigCustomizer(cc ->
                  cc.setValidateAfterInactivity(
                      VALIDATE_AFTER_INACTIVITY_SECONDS, TimeUnit.SECONDS))
              .withHttpClientCustomizer(HttpClientBuilder::disableContentCompression);
      if (proxyHost != null) {
        val httpHost = new HttpHost(proxyHost, proxy.getPort());
        result =
            result
                .withHttpClientCustomizer(b -> configureProxy(b, httpHost, proxy, proxyAuth))
                .withDefaultRequestConfigCustomizer(rc ->
                    rc.setProxyPreferredAuthSchemes(
                        List.of(StandardAuthScheme.BASIC, StandardAuthScheme.NTLM)));
      }
      if (properties.isDisableSslVerification()) {
        val sslContext = trustAllSslContext();
        result =
            result.withTlsSocketStrategyFactory(bundle ->
                new DefaultClientTlsStrategy(
                    sslContext, HostnameVerificationPolicy.CLIENT, NoopHostnameVerifier.INSTANCE));
      }
      return result;
    };
  }

  private static void configureProxy(
      HttpClientBuilder builder, HttpHost httpHost, ProxyProperties proxy, ProxyAuth proxyAuth) {
    builder.setProxy(httpHost).setDefaultAuthSchemeRegistry(authSchemeRegistry());
    if (proxyAuth != null) {
      val credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(new AuthScope(httpHost), proxyCredentials(proxy));
      builder.setDefaultCredentialsProvider(credentialsProvider);
    }
  }

  @SuppressWarnings("deprecation")
  static Lookup<AuthSchemeFactory> authSchemeRegistry() {
    return RegistryBuilder.<AuthSchemeFactory>create()
        .register(StandardAuthScheme.BASIC, BasicSchemeFactory.INSTANCE)
        .register(StandardAuthScheme.DIGEST, DigestSchemeFactory.INSTANCE)
        .register(StandardAuthScheme.NTLM, NTLMSchemeFactory.INSTANCE)
        .build();
  }

  @SuppressWarnings("deprecation")
  static Credentials proxyCredentials(ProxyProperties proxy) {
    val password = ofNullable(proxy.getPassword()).orElse("").toCharArray();
    return proxy.getAuth() == ProxyAuth.NTLM
        ? new NTCredentials(
            proxy.getUsername(),
            password,
            Try.of(() -> InetAddress.getLocalHost().getHostName()).getOrNull(),
            proxy.getDomain())
        : new UsernamePasswordCredentials(proxy.getUsername(), password);
  }

  static SSLContext trustAllSslContext() {
    return Try.of(
            () -> SSLContextBuilder.create().loadTrustMaterial(TrustAllStrategy.INSTANCE).build())
        .get();
  }
}
