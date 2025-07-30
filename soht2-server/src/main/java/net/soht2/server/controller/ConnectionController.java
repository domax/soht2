/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.controller;

import static net.soht2.server.controller.UserController.AUTH_REQ;
import static net.soht2.server.entity.UserEntity.ROLE_ADMIN;
import static org.springframework.http.HttpHeaders.CONTENT_ENCODING;
import static org.springframework.http.MediaType.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.service.Soht2Service;
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
   * current user and client host. Connection opens only if a specified target is allowed for the
   * user on the server side.
   *
   * @param targetHost the target host to connect to
   * @param targetPort the target port to connect to
   * @param authentication the current authentication object containing user details
   * @param request the HTTP request containing client information
   * @return a {@link Soht2Connection} object representing the opened connection
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "Connection Requests")
  @Operation(
      summary = "Opens a new SOHT2 connection to the specified target host and port.",
      description = AUTH_REQ + " Connection opens only if a target is allowed for the user.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @SuppressWarnings("resource")
  @PreAuthorize("@soht2Service.isTargetAllowed(authentication, #targetHost, #targetPort)")
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
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "Connection Requests")
  @Operation(
      summary = "Lists all currently open SOHT2 connections.",
      description =
          "For user in "
              + ROLE_ADMIN
              + " role it returns all connections, "
              + "for other users it returns only their own connections.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
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
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "Connection Requests")
  @Operation(
      summary = "Exchanges data with the specified SOHT2 connection.",
      description = AUTH_REQ + " But only user who created connection can use it.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @PreAuthorize("@soht2Service.isConnectionOwner(authentication, #connectionId)")
  @PostMapping(
      path = PATH_ID,
      produces = APPLICATION_OCTET_STREAM_VALUE,
      consumes = APPLICATION_OCTET_STREAM_VALUE)
  public byte[] exchange(
      @PathVariable("id") UUID connectionId,
      @RequestBody(required = false) @Nullable byte[] data,
      @RequestHeader(name = CONTENT_ENCODING, required = false) @Nullable String contentEncoding) {
    return soht2Service.exchange(connectionId, data, contentEncoding).get();
  }

  /**
   * Closes the SOHT2 connection associated with the specified unique identifier.
   *
   * @param connectionId the unique identifier of the connection to close
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "Connection Requests")
  @Operation(
      summary = "Closes the SOHT2 connection by the specified unique identifier.",
      description = "Connection owner or user in " + ROLE_ADMIN + " role can close the connection.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @PreAuthorize(
      "@soht2Service.isConnectionOwner(authentication, #connectionId) || hasAuthority('"
          + ROLE_ADMIN
          + "')")
  @DeleteMapping(path = PATH_ID)
  public void close(@PathVariable("id") UUID connectionId) {
    soht2Service.close(connectionId);
  }
}
