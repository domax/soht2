/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.server.config;

import static net.soht2.server.service.ExceptionHelper.contentTooLarge;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits the body size of tunnel data requests ({@code POST /api/connection/{id}} and {@code POST
 * /api/connection/{id}/upload}) to {@code soht2.server.max-request-size}. Requests declaring a
 * larger {@code Content-Length} are rejected with {@code 413} before the body is read; bodies
 * without a declared length (chunked) fail with {@code 413} once the limit is exceeded.
 *
 * <p>The filter runs after the Spring Security filter chain, so unauthenticated requests are still
 * rejected with {@code 401} first.
 */
@Slf4j
@Component
@Order
@RequiredArgsConstructor
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  private static final Pattern TUNNEL_DATA_PATH =
      Pattern.compile("^/api/connection/[^/]+(/upload)?$");

  private final Soht2ServerConfig soht2ServerConfig;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!HttpMethod.POST.matches(request.getMethod())) return true;
    val path = request.getRequestURI().substring(request.getContextPath().length());
    return !TUNNEL_DATA_PATH.matcher(path).matches();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    val maxBytes = soht2ServerConfig.getMaxRequestSize().toBytes();
    val contentLength = request.getContentLengthLong();
    if (contentLength > maxBytes) {
      log.warn(
          "doFilterInternal: {} - Content-Length {} exceeds {} bytes",
          request.getRequestURI(),
          contentLength,
          maxBytes);
      response.sendError(
          HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
          "Request exceeds " + maxBytes + " bytes");
      return;
    }
    filterChain.doFilter(new LimitedRequest(request, maxBytes), response);
  }

  private static final class LimitedRequest extends HttpServletRequestWrapper {

    private final long maxBytes;
    private ServletInputStream inputStream;

    LimitedRequest(HttpServletRequest request, long maxBytes) {
      super(request);
      this.maxBytes = maxBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (inputStream == null)
        inputStream = new LimitedInputStream(super.getInputStream(), maxBytes);
      return inputStream;
    }
  }

  @RequiredArgsConstructor
  private static final class LimitedInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long maxBytes;
    private long count;

    @Override
    public int read() throws IOException {
      val b = delegate.read();
      if (b != -1) count(1);
      return b;
    }

    @Override
    public int read(byte @NonNull [] b, int off, int len) throws IOException {
      val n = delegate.read(b, off, len);
      if (n > 0) count(n);
      return n;
    }

    private void count(int n) {
      count += n;
      if (count > maxBytes) throw contentTooLarge("Request exceeds " + maxBytes + " bytes");
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      delegate.setReadListener(readListener);
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
