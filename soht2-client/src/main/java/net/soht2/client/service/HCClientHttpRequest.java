/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.service;

import static org.apache.http.HttpHeaders.CONTENT_ENCODING;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

import io.vavr.control.Try;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.io.EmptyInputStream;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.StringUtils;

/**
 * An HTTP request implementation that uses Apache {@link HttpClient} to execute requests and manage
 * HTTP headers, body content, and execution context. This class extends {@link
 * AbstractClientHttpRequest} and implements {@link StreamingHttpOutputMessage} to enable handling
 * of streaming data and advanced HTTP communication.
 *
 * <p>Key functionalities include:
 *
 * <ul>
 *   <li>Setting and retrieving the HTTP request body.
 *   <li>Header management and integration with the underlying HttpClient.
 *   <li>Execution of HTTP requests with optional streaming.
 * </ul>
 */
@RequiredArgsConstructor
public class HCClientHttpRequest extends AbstractClientHttpRequest
    implements StreamingHttpOutputMessage {

  static final NullEntity NULL_ENTITY = new NullEntity();

  private final HttpClient httpClient;
  private final HttpUriRequest httpRequest;
  private final HttpContext httpContext;

  @Nullable private Body body;
  @Nullable private FastByteArrayOutputStream bodyStream;

  @Override
  public void setBody(Body body) {
    Assert.notNull(body, "Body must not be null");
    assertNotExecuted();
    Assert.state(bodyStream == null, "Invoke either getBody or setBody, not both");
    this.body = body;
  }

  @Override
  protected OutputStream getBodyInternal(HttpHeaders headers) {
    Assert.state(this.body == null, "Invoke either getBody or setBody, not both");
    if (bodyStream == null) bodyStream = new FastByteArrayOutputStream(1024);
    return bodyStream;
  }

  @Override
  protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
    if (this.body == null && this.bodyStream != null)
      this.body = outputStream -> this.bodyStream.writeTo(outputStream);

    return executeInternal(headers, this.body);
  }

  protected ClientHttpResponse executeInternal(HttpHeaders headers, @Nullable Body body)
      throws IOException {
    addHeaders(httpRequest, headers);

    if (httpRequest instanceof HttpEntityEnclosingRequest entityEnclosing)
      entityEnclosing.setEntity(body != null ? new BodyEntity(headers, body) : NULL_ENTITY);

    return new HCClientHttpResponse(httpClient.execute(httpRequest, httpContext));
  }

  @Override
  public HttpMethod getMethod() {
    return HttpMethod.valueOf(httpRequest.getMethod());
  }

  @Override
  public URI getURI() {
    return Try.of(httpRequest::getURI)
        .recoverWith(e -> Try.failure(new IllegalStateException(e.getMessage(), e)))
        .get();
  }

  static void addHeaders(HttpUriRequest httpRequest, HttpHeaders headers) {
    headers.forEach(
        (headerName, headerValues) -> {
          if (HttpHeaders.COOKIE.equalsIgnoreCase(headerName)) { // RFC 6265
            String headerValue = StringUtils.collectionToDelimitedString(headerValues, "; ");
            httpRequest.addHeader(headerName, headerValue);
          } else if (!HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(headerName)
              && !HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(headerName)) {
            for (String headerValue : headerValues) {
              httpRequest.addHeader(headerName, headerValue);
            }
          }
        });
  }

  record BodyEntity(HttpHeaders headers, Body body) implements HttpEntity {

    @Override
    public boolean isRepeatable() {
      return body.repeatable();
    }

    @Override
    public boolean isChunked() {
      return false;
    }

    @Override
    public long getContentLength() {
      return headers.getContentLength();
    }

    @Override
    public Header getContentType() {
      return new BasicHeader(CONTENT_TYPE, headers.getFirst(CONTENT_TYPE));
    }

    @Override
    public Header getContentEncoding() {
      return new BasicHeader(CONTENT_ENCODING, headers.getFirst(CONTENT_ENCODING));
    }

    @Override
    public InputStream getContent() throws UnsupportedOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void writeTo(OutputStream outStream) throws IOException {
      body.writeTo(outStream);
    }

    @Override
    public boolean isStreaming() {
      return false;
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    @Override
    public void consumeContent() throws UnsupportedOperationException {
      throw new UnsupportedOperationException();
    }
  }

  record NullEntity() implements HttpEntity {

    @Override
    public boolean isRepeatable() {
      return true;
    }

    @Override
    public boolean isChunked() {
      return false;
    }

    @Override
    public long getContentLength() {
      return 0;
    }

    @Override
    @Nullable public Header getContentType() {
      return null;
    }

    @Override
    @Nullable public Header getContentEncoding() {
      return null;
    }

    @Override
    public InputStream getContent() throws UnsupportedOperationException {
      return EmptyInputStream.INSTANCE;
    }

    @Override
    public void writeTo(OutputStream outStream) {
      // No content to write
    }

    @Override
    public boolean isStreaming() {
      return false;
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    @Override
    public void consumeContent() {
      // No content to consume
    }
  }
}
