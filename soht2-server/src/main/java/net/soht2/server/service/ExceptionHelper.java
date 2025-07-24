/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import static org.springframework.http.HttpStatus.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;

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

  public static ResponseStatusException badRequest(String message) {
    return createClientException(BAD_REQUEST, message);
  }

  public static ResponseStatusException unauthorized(String message) {
    return createClientException(UNAUTHORIZED, message);
  }

  public static ResponseStatusException forbidden(String message) {
    return createClientException(FORBIDDEN, message);
  }

  public static ResponseStatusException notFound(String message) {
    return createClientException(NOT_FOUND, message);
  }

  public static ResponseStatusException gone(String message) {
    return createClientException(GONE, message);
  }

  public static ResponseStatusException notImplemented(String message) {
    return createServerException(NOT_IMPLEMENTED, message);
  }

  public static ResponseStatusException serviceUnavailable(String message) {
    return createServerException(SERVICE_UNAVAILABLE, message);
  }

  private static ResponseStatusException createClientException(HttpStatus status, String message) {
    return new ResponseStatusException(
        status,
        message,
        HttpClientErrorException.create(
            message, status, status.getReasonPhrase(), null, message.getBytes(), null));
  }

  private static ResponseStatusException createServerException(HttpStatus status, String message) {
    return new ResponseStatusException(
        status,
        message,
        HttpServerErrorException.create(
            message, status, status.getReasonPhrase(), null, null, null));
  }
}
