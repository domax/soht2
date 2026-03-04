/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.service;

import static java.util.Optional.ofNullable;

import io.vavr.control.Try;
import java.io.Closeable;
import java.net.URI;
import java.util.function.BiFunction;
import lombok.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * A factory for creating {@link ClientHttpRequest} objects using a provided {@link HttpClient}.
 * Designed to support advanced configuration options for HTTP requests, such as timeouts and custom
 * HTTP contexts.
 *
 * <p>This class implements the {@link ClientHttpRequestFactory} interface for creating requests and
 * the {@link DisposableBean} interface for releasing resources held by the associated {@link
 * HttpClient}.
 *
 * <p>The following features are available for customization:
 *
 * <ul>
 *   <li>Timeout configurations: connection timeout, connection request timeout, and socket timeout.
 *   <li>HTTP context creation using a {@link BiFunction} that allows custom handling of HTTP
 *       requests based on the method and URI.
 *   <li>Post-processing of HTTP requests for further modifications after request creation.
 *   <li>Configuration merging with a parent {@link RequestConfig}, enabling customization while
 *       inheriting default settings from the provided {@link HttpClient}.
 * </ul>
 */
@RequiredArgsConstructor
@Getter
@Setter
@ToString
public class HCClientHttpRequestFactory implements ClientHttpRequestFactory, DisposableBean {

  private final HttpClient httpClient;

  @Nullable private BiFunction<HttpMethod, URI, HttpContext> httpContextFactory;

  private int connectTimeout = -1;
  private int connectionRequestTimeout = -1;
  private int socketTimeout = -1;

  @Override
  public void destroy() {
    Try.success(httpClient)
        .filter(Closeable.class::isInstance)
        .mapTry(Closeable.class::cast)
        .andThenTry(Closeable::close);
  }

  @Override
  public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
    val httpRequest = createHttpUriRequest(httpMethod, uri);
    postProcessHttpRequest(httpRequest);
    val ctx = ofNullable(createHttpContext(httpMethod, uri)).orElseGet(HttpClientContext::create);

    // No custom request configuration was set
    if (!hasCustomRequestConfig(ctx)) {
      RequestConfig config = null;
      // Use request configuration given by the user, when available
      if (httpRequest instanceof Configurable configurable) config = configurable.getConfig();

      if (config == null) config = createRequestConfig(httpClient);
      if (config != null) {
        if (ctx instanceof HttpClientContext hcc) hcc.setRequestConfig(config);
        ctx.setAttribute(HttpClientContext.REQUEST_CONFIG, config);
      }
    }
    return new HCClientHttpRequest(httpClient, httpRequest, ctx);
  }

  private static boolean hasCustomRequestConfig(HttpContext context) {
    if (context instanceof HttpClientContext clientContext) {
      val requestConfig = clientContext.getRequestConfig();
      return requestConfig != null && !requestConfig.equals(RequestConfig.DEFAULT);
    }
    return context.getAttribute(HttpClientContext.REQUEST_CONFIG) != null;
  }

  protected HttpUriRequest createHttpUriRequest(HttpMethod httpMethod, URI uri) {
    return switch (httpMethod.name()) {
      case "GET" -> new HttpGet(uri);
      case "HEAD" -> new HttpHead(uri);
      case "POST" -> new HttpPost(uri);
      case "PUT" -> new HttpPut(uri);
      case "PATCH" -> new HttpPatch(uri);
      case "DELETE" -> new HttpDelete(uri);
      case "OPTIONS" -> new HttpOptions(uri);
      case "TRACE" -> new HttpTrace(uri);
      default -> throw new IllegalArgumentException("Invalid HTTP method: " + httpMethod);
    };
  }

  protected void postProcessHttpRequest(HttpUriRequest request) {
    Assert.notNull(request, "HttpUriRequest must not be null");
  }

  @Nullable protected HttpContext createHttpContext(HttpMethod httpMethod, URI uri) {
    return httpContextFactory != null ? httpContextFactory.apply(httpMethod, uri) : null;
  }

  @Nullable protected RequestConfig createRequestConfig(Object client) {
    return mergeRequestConfig(
        client instanceof Configurable configurableClient
            ? configurableClient.getConfig()
            : RequestConfig.DEFAULT);
  }

  protected RequestConfig mergeRequestConfig(RequestConfig clientConfig) {
    if (connectTimeout == -1 && connectionRequestTimeout == -1 && socketTimeout == -1)
      return clientConfig;

    val rc = RequestConfig.copy(clientConfig);
    if (connectTimeout >= 0) rc.setConnectTimeout(connectTimeout);
    if (connectionRequestTimeout >= 0) rc.setConnectionRequestTimeout(connectionRequestTimeout);
    if (socketTimeout >= 0) rc.setSocketTimeout(socketTimeout);
    return rc.build();
  }
}
