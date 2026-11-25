/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

class RequestSizeLimitFilterTest {

  static final int MAX = 1024;

  /** Simulates a chunked request body that has no declared Content-Length. */
  static class ChunkedRequest extends MockHttpServletRequest {
    ChunkedRequest(String uri, byte[] body) {
      super("POST", uri);
      setContent(body);
    }

    @Override
    public long getContentLengthLong() {
      return -1;
    }

    @Override
    public int getContentLength() {
      return -1;
    }
  }

  static RequestSizeLimitFilter filter() {
    val config = new Soht2ServerConfig();
    config.setMaxRequestSize(DataSize.ofBytes(MAX));
    return new RequestSizeLimitFilter(config);
  }

  @SuppressWarnings("java:S5778")
  @Test
  void chunked_oversized_CONTENT_TOO_LARGE() {
    val request = new ChunkedRequest("/api/connection/abc/upload", new byte[MAX + 1]);
    FilterChain chain = (rq, rs) -> rq.getInputStream().readAllBytes();
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> filter().doFilter(request, new MockHttpServletResponse(), chain))
        .satisfies(e -> assertThat(e.getStatusCode().value()).isEqualTo(413));
  }

  @Test
  void chunked_withinLimit_OK() throws Exception {
    val request = new ChunkedRequest("/api/connection/abc", new byte[MAX]);
    val read = new AtomicReference<byte[]>();
    FilterChain chain = (rq, rs) -> read.set(rq.getInputStream().readAllBytes());
    filter().doFilter(request, new MockHttpServletResponse(), chain);
    assertThat(read.get()).hasSize(MAX);
  }

  @Test
  void contentLength_oversized_rejectedBeforeChain() throws Exception {
    val request = new MockHttpServletRequest("POST", "/api/connection/abc/upload");
    request.setContent(new byte[MAX + 1]);
    val response = new MockHttpServletResponse();
    val called = new AtomicBoolean();
    filter().doFilter(request, response, (rq, rs) -> called.set(true));
    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(called).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /api/connection",
    "POST, /api/user",
    "PUT, /api/connection/abc",
    "GET, /api/connection/abc/download"
  })
  void otherRequests_notLimited(String method, String uri) throws Exception {
    val request = new MockHttpServletRequest(method, uri);
    request.setContent(new byte[MAX + 1]);
    val response = new MockHttpServletResponse();
    val read = new AtomicReference<byte[]>();
    filter().doFilter(request, response, (rq, rs) -> read.set(rq.getInputStream().readAllBytes()));
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(read.get()).hasSize(MAX + 1);
  }
}
