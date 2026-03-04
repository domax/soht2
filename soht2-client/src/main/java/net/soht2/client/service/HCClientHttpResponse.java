/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.service;

import io.vavr.Function1;
import io.vavr.control.Try;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Implementation of the {@link ClientHttpResponse} interface, utilizing {@link HttpResponse}
 * internally to represent and handle HTTP responses.
 *
 * <p>This class wraps an HTTP response object from Apache's HTTP client library and provides a
 * Spring-compatible interface for accessing key HTTP response details such as status code, status
 * text, headers, and the response body. Additionally, it ensures proper resource handling, such as
 * closing streams or connections.
 */
@RequiredArgsConstructor
public class HCClientHttpResponse implements ClientHttpResponse {

  private final HttpResponse httpResponse;

  private final Function1<HttpResponse, HttpHeaders> httpHeadersCache =
      Function1.<HttpResponse, HttpHeaders>of(
              r -> {
                val headers = new HttpHeaders();
                for (val header : r.getAllHeaders())
                  headers.add(header.getName(), header.getValue());
                return HttpHeaders.readOnlyHttpHeaders(headers);
              })
          .memoized();

  @Override
  public HttpStatusCode getStatusCode() {
    return HttpStatusCode.valueOf(httpResponse.getStatusLine().getStatusCode());
  }

  @Override
  public String getStatusText() {
    return httpResponse.getStatusLine().getReasonPhrase();
  }

  @Override
  public void close() {
    Try.run(() -> EntityUtils.consume(httpResponse.getEntity()))
        .andFinally(
            () ->
                Try.success(httpResponse)
                    .filter(Closeable.class::isInstance)
                    .mapTry(Closeable.class::cast)
                    .andThenTry(Closeable::close));
  }

  @Override
  public InputStream getBody() throws IOException {
    val entity = httpResponse.getEntity();
    return entity != null ? entity.getContent() : InputStream.nullInputStream();
  }

  @Override
  public HttpHeaders getHeaders() {
    return httpHeadersCache.apply(httpResponse);
  }
}
