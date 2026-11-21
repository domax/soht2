/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.controller;

import static net.soht2.common.dto.Soht2Protocol.*;
import static net.soht2.server.controller.UserController.AUTH_REQ;
import static net.soht2.server.entity.UserEntity.ROLE_ADMIN;
import static org.springframework.http.HttpHeaders.CONTENT_ENCODING;
import static org.springframework.http.MediaType.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.val;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.dto.HistoryPage;
import net.soht2.server.dto.HistoryPaging;
import net.soht2.server.service.Soht2HistoryService;
import net.soht2.server.service.Soht2Service;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing SOHT2 connections. Provides endpoints to open, list, exchange data with
 * (simple transport), upload and download data (duplex transport), and close connections.
 */
@SuppressWarnings("java:S1075")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/connection")
public class ConnectionController {

  private static final String PATH_ID = "/{id}";
  private static final String PATH_ID_UPLOAD = PATH_ID + "/" + PATH_UPLOAD;
  private static final String PATH_ID_DOWNLOAD = PATH_ID + "/" + PATH_DOWNLOAD;
  private static final String TS = "(yyyy-MM-dd'T'HH:mm:ss)";
  private static final String AST =
      ". An asterisk (*) can be used as a wildcard on the start or/and the end of the string.";

  private final Soht2Service soht2Service;
  private final Soht2HistoryService soht2HistoryService;

  /**
   * Opens a new SOHT2 connection to the specified target host and port, associating it with the
   * current user and client host. Connection opens only if a specified target is allowed for the
   * user on the server side.
   *
   * @param targetHost the target host to connect to
   * @param targetPort the target port to connect to
   * @param authentication the current authentication object containing user details
   * @param request the HTTP request containing client information
   * @return a {@link Soht2Connection} object representing the opened connection; the response
   *     header {@code X-SOHT2-Transport: duplex} advertises support of the duplex transport
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "Connection Requests")
  @Operation(
      summary = "Opens a new SOHT2 connection to the specified target host and port.",
      description =
          AUTH_REQ
              + " Connection opens only if a target is allowed for the user."
              + " Response header "
              + HEADER_TRANSPORT
              + ": "
              + TRANSPORT_DUPLEX
              + " advertises support of the duplex transport (upload and download requests).")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(
      responseCode = "200",
      headers =
          @Header(
              name = HEADER_TRANSPORT,
              description = "Supported transport",
              schema =
                  @Schema(
                      type = "string",
                      allowableValues = {TRANSPORT_DUPLEX, TRANSPORT_SIMPLE})))
  @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @SuppressWarnings("resource")
  @PreAuthorize("@soht2Service.isTargetAllowed(authentication, #targetHost, #targetPort)")
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Soht2Connection> open(
      @RequestParam("host") String targetHost,
      @RequestParam("port") Integer targetPort,
      Authentication authentication,
      HttpServletRequest request) {
    return ResponseEntity.ok()
        .header(HEADER_TRANSPORT, TRANSPORT_DUPLEX)
        .body(
            soht2Service
                .open(
                    Soht2Connection.builder()
                        .user(Soht2User.builder().username(authentication.getName()).build())
                        .clientHost(request.getRemoteHost())
                        .targetHost(targetHost)
                        .targetPort(targetPort)
                        .build(),
                    authentication)
                .soht2());
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
   * Retrieves a paginated list of connection history records based on specified filters and sorting
   * criteria.
   *
   * @param username a substring to filter the records by username. Fetches records matching the
   *     substring if provided.
   * @param connectionId a substring to filter the records by connection ID. Fetches records
   *     matching the substring if provided.
   * @param clientHost a substring to filter the records by client host. Fetches records matching
   *     the substring if provided.
   * @param targetHost a substring to filter the records by target host. Fetches records matching
   *     the substring if provided.
   * @param targetPorts a set of target ports to filter the history records. Fetches records for
   *     specified target ports if provided.
   * @param openedAfter a timestamp to filter the records by connections opened after. Fetches
   *     records opened after this timestamp if provided.
   * @param openedBefore a timestamp to filter the records by connections opened before. Fetches
   *     records opened before this timestamp if provided.
   * @param closedAfter a timestamp to filter the records by connections closed after. Fetches
   *     records closed after this timestamp if provided.
   * @param closedBefore a timestamp to filter the records by connections closed before. Fetches
   *     records closed before this timestamp if provided.
   * @param sortBy sorting criteria for the records. Supports fields such as userName, connectionId,
   *     clientHost, targetHost, targetPort, openedAt, and closedAt. The default is {@code
   *     "openedAt:desc"}.
   * @param pageNumber the 0-based page number to retrieve. Defaults to {@code 0} if not specified.
   * @param pageSize the number of records per page, with a maximum value of {@code 1000}. Defaults
   *     to {@code 10} if not specified.
   * @param authentication the current authenticated user's authentication context.
   * @return a paginated object containing the history records matching the specified filters and
   *     sorting criteria.
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "Connection Requests")
  @Operation(
      summary =
          "Retrieves a paginated list of connection history records "
              + "based on specified filters and sorting criteria.",
      description =
          "For user in "
              + ROLE_ADMIN
              + " role it searches for history of all users, "
              + "for other users it returns only their own history.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @GetMapping(path = "/history", produces = APPLICATION_JSON_VALUE)
  public HistoryPage searchHistory(
      // <editor-fold desc="un, id, ch, th, tp, oa, ob, ca, cb, sort, pg, sz>
      @Parameter(description = "Get only history of specified substring in username" + AST)
          @RequestParam(name = "un", required = false)
          String username,
      @Parameter(description = "Get only history of specified substring in connection ID" + AST)
          @RequestParam(name = "id", required = false)
          String connectionId,
      @Parameter(description = "Get only history of specified substring in client host" + AST)
          @RequestParam(name = "ch", required = false)
          String clientHost,
      @Parameter(description = "Get only history of specified substring in target host" + AST)
          @RequestParam(name = "th", required = false)
          String targetHost,
      @Parameter(description = "Get only history of specified target ports")
          @RequestParam(name = "tp")
          Optional<Set<Integer>> targetPorts,
      @Parameter(description = "Get only connections opened after timestamp " + TS)
          @RequestParam(name = "oa", required = false)
          LocalDateTime openedAfter,
      @Parameter(description = "Get only connections opened before timestamp " + TS)
          @RequestParam(name = "ob", required = false)
          LocalDateTime openedBefore,
      @Parameter(description = "Get only connections closed after timestamp " + TS)
          @RequestParam(name = "ca", required = false)
          LocalDateTime closedAfter,
      @Parameter(description = "Get only connections closed before timestamp " + TS)
          @RequestParam(name = "cb", required = false)
          LocalDateTime closedBefore,
      @Parameter(
              description =
                  "Sorting criteria (supported fields: "
                      + "username, id, clientHost, targetHost, targetPort, "
                      + "openedAt, closedAt, bytesRead, bytesWritten)",
              example = "openedAt:desc",
              array =
                  @ArraySchema(
                      maxItems = 3,
                      uniqueItems = true,
                      schema =
                          @Schema(
                              type = "string",
                              pattern =
                                  "^(username|id|clientHost|targetHost|targetPort"
                                      + "|openedAt|closedAt|bytesRead|bytesWritten)"
                                      + ":(asc|desc)$")))
          @RequestParam(name = "sort", required = false, defaultValue = "openedAt:desc")
          List<String> sortBy,
      @Parameter(description = "0-based page number")
          @RequestParam(name = "pg", required = false, defaultValue = "0")
          @Min(0) int pageNumber,
      @Parameter(description = "Number of records per page (up to 1000)")
          @RequestParam(name = "sz", required = false, defaultValue = "10")
          @Max(1000) int pageSize,
      // </editor-fold>
      Authentication authentication) {
    return soht2HistoryService.searchHistory(
        username,
        connectionId,
        clientHost,
        targetHost,
        targetPorts.orElse(Set.of()),
        openedAfter,
        openedBefore,
        closedAfter,
        closedBefore,
        HistoryPaging.fromRequest(pageNumber, pageSize, sortBy),
        authentication);
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
      @RequestBody(required = false) byte @Nullable [] data,
      @RequestHeader(name = CONTENT_ENCODING, required = false) String contentEncoding) {
    return soht2Service.exchange(connectionId, data, contentEncoding).get();
  }

  /**
   * Duplex transport upload: writes the provided data to the target of the specified SOHT2
   * connection. The request is safe to retry: repeating the last sequence number is acknowledged
   * without writing again.
   *
   * @param connectionId the unique identifier of the SOHT2 connection
   * @param seq the per-lane sequence number of the request, starting at 1
   * @param data the data to send to the target (optional)
   * @param contentEncoding the content encoding of the data (optional)
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "Connection Requests")
  @Operation(
      summary = "Duplex transport: uploads data to the target of the SOHT2 connection.",
      description =
          AUTH_REQ
              + " But only user who created connection can use it."
              + " Header "
              + HEADER_SEQ
              + " must be the next upload sequence number (starting at 1);"
              + " repeating the last one is acknowledged without writing the data again.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "204")
  @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(
      responseCode = "409",
      description = "Out-of-order sequence number; the connection is closed",
      content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(
      responseCode = "410",
      description = "The connection is missing or closed",
      content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @PreAuthorize("@soht2Service.isConnectionOwner(authentication, #connectionId)")
  @PostMapping(path = PATH_ID_UPLOAD, consumes = APPLICATION_OCTET_STREAM_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void upload(
      @PathVariable("id") UUID connectionId,
      @RequestHeader(HEADER_SEQ) long seq,
      @RequestBody(required = false) byte @Nullable [] data,
      @RequestHeader(name = CONTENT_ENCODING, required = false) String contentEncoding) {
    soht2Service.upload(connectionId, seq, data, contentEncoding).get();
  }

  /**
   * Duplex transport download: waits until the target of the specified SOHT2 connection sends data,
   * closes its connection, or the download hold timeout elapses. The request is safe to retry:
   * repeating the last sequence number replays the same result.
   *
   * @param connectionId the unique identifier of the SOHT2 connection
   * @param seq the per-lane sequence number of the request, starting at 1
   * @return {@code 200} with data, {@code 204} when the hold timeout elapsed without data, or
   *     {@code 410} with header {@code X-SOHT2-EOS: true} when the target closed its connection
   */
  // <editor-fold desc="OpenAPI Annotations">
  @Tag(name = "Connection Requests")
  @Operation(
      summary = "Duplex transport: downloads data from the target of the SOHT2 connection.",
      description =
          AUTH_REQ
              + " But only user who created connection can use it."
              + " Header "
              + HEADER_SEQ
              + " must be the next download sequence number (starting at 1), also after an empty"
              + " response; repeating the last one replays the same result.")
  @SecurityRequirement(name = "Basic Authentication")
  @ApiResponse(responseCode = "200")
  @ApiResponse(
      responseCode = "204",
      description = "The hold timeout elapsed without target data",
      content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(
      responseCode = "409",
      description = "Out-of-order sequence number; the connection is closed",
      content = @Content(schema = @Schema(hidden = true)))
  @ApiResponse(
      responseCode = "410",
      description =
          "With header "
              + HEADER_EOS
              + ": true - the target closed its connection (end-of-stream);"
              + " without it - the connection is missing or closed",
      headers =
          @Header(
              name = HEADER_EOS,
              description = "End-of-stream marker",
              schema = @Schema(type = "string", allowableValues = EOS_VALUE)),
      content = @Content(schema = @Schema(hidden = true)))
  // </editor-fold>
  @PreAuthorize("@soht2Service.isConnectionOwner(authentication, #connectionId)")
  @GetMapping(path = PATH_ID_DOWNLOAD, produces = APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<byte[]> download(
      @PathVariable("id") UUID connectionId, @RequestHeader(HEADER_SEQ) long seq) {
    val result = soht2Service.download(connectionId, seq).get();
    if (result.eos())
      return ResponseEntity.status(HttpStatus.GONE).header(HEADER_EOS, EOS_VALUE).build();
    if (result.isEmpty()) return ResponseEntity.noContent().build();
    return ResponseEntity.ok().contentType(APPLICATION_OCTET_STREAM).body(result.data());
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
