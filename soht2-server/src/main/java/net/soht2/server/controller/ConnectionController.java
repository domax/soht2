/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.controller;

import static org.springframework.http.MediaType.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.service.Soht2Service;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing SOHT2 connections. Provides endpoints to open, list, exchange data with,
 * and close connections.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/connection")
public class ConnectionController {

  private static final String PATH_ID = "/{id}";

  private final Soht2Service soht2Service;

  /**
   * Opens a new SOHT2 connection to the specified target host and port, associating it with the
   * current user and client host.
   *
   * @param targetHost the target host to connect to
   * @param targetPort the target port to connect to
   * @param authentication the current authentication object containing user details
   * @param request the HTTP request containing client information
   * @return a {@link Soht2Connection} object representing the opened connection
   */
  @SuppressWarnings("resource")
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public Soht2Connection open(
      @RequestParam("host") String targetHost,
      @RequestParam("port") Integer targetPort,
      Authentication authentication,
      HttpServletRequest request) {
    return soht2Service
        .open(
            Soht2Connection.builder()
                .user(Soht2User.builder().username(authentication.getName()).build())
                .clientHost(request.getRemoteHost())
                .targetHost(targetHost)
                .targetPort(targetPort)
                .build(),
            authentication)
        .soht2();
  }

  /**
   * Lists all currently open SOHT2 connections.
   *
   * @param authentication the current authentication object containing user details
   * @return a collection of {@link Soht2Connection} objects representing the open connections
   */
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public Collection<Soht2Connection> list(Authentication authentication) {
    return soht2Service.list(authentication);
  }

  /**
   * Exchanges data with the specified SOHT2 connection, sending the provided data and receiving a
   * response.
   *
   * @param connectionId the unique identifier of the SOHT2 connection
   * @param data the data to send to the connection (optional)
   * @param contentEncoding the content encoding of the data (optional)
   * @return the response data received from the connection
   */
  @PreAuthorize("@soht2Service.isConnectionOwner(authentication, #connectionId)")
  @PostMapping(
      path = PATH_ID,
      produces = APPLICATION_OCTET_STREAM_VALUE,
      consumes = APPLICATION_OCTET_STREAM_VALUE)
  public byte[] exchange(
      @PathVariable("id") UUID connectionId,
      @RequestBody(required = false) @Nullable byte[] data,
      @RequestHeader(name = HttpHeaders.CONTENT_ENCODING, required = false) @Nullable String contentEncoding) {
    return soht2Service.exchange(connectionId, data, contentEncoding).get();
  }

  /**
   * Closes the SOHT2 connection associated with the specified unique identifier.
   *
   * @param connectionId the unique identifier of the connection to close
   */
  @PreAuthorize("@soht2Service.isConnectionOwner(authentication, #connectionId)")
  @DeleteMapping(path = PATH_ID)
  public void close(@PathVariable("id") UUID connectionId) {
    soht2Service.close(connectionId);
  }
}
