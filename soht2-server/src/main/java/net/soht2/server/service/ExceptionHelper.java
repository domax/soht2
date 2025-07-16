/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import static org.springframework.http.HttpStatus.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * A utility class that provides methods for creating HTTP exceptions in a standardized way. This
 * class serves as a helper for creating specific exceptions used in the application to signify
 * client or server errors in HTTP communication.
 *
 * <p>This is a final class with a private constructor, ensuring it cannot be instantiated or
 * subclassed. The methods provided are static and can be accessed directly.
 */
@SuppressWarnings({"java:S4449", "unused"})
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExceptionHelper {

  public static HttpClientErrorException badRequest(String statusText) {
    return HttpClientErrorException.create(BAD_REQUEST, statusText, null, null, null);
  }

  public static HttpClientErrorException gone(String statusText) {
    return HttpClientErrorException.create(GONE, statusText, null, null, null);
  }

  public static HttpServerErrorException serviceUnavailable(String statusText) {
    return HttpServerErrorException.create(SERVICE_UNAVAILABLE, statusText, null, null, null);
  }
}
