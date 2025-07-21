/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static java.util.Optional.ofNullable;
import static net.soht2.common.compress.Compressor.compressorCache;
import static org.springframework.http.HttpHeaders.ACCEPT_ENCODING;
import static org.springframework.http.HttpHeaders.CONTENT_ENCODING;

import io.vavr.control.Try;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.client.config.Soht2ClientProperties;
import net.soht2.common.dto.Soht2Connection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Client for interacting with the SOHT2 server. Provides methods to open and close connections, and
 * exchange data.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class Soht2Client {

  private static final byte[] EMPTY = new byte[0];
  private static final ParameterizedTypeReference<byte[]> PTR_BYTES =
      new ParameterizedTypeReference<>() {};

  @SuppressWarnings("java:S1075")
  private static final String PATH_ID = "/{id}";

  private final RestClient restClient;
  private final Soht2ClientProperties soht2ClientProperties;

  /**
   * Opens a connection to the specified remote host and port.
   *
   * @param remoteHost the host name or IP address of the remote machine
   * @param remotePort the port number on the remote machine to connect to
   * @return a {@link Try} containing the Soht2Connection if successful, or an error if it fails
   */
  public Try<Soht2Connection> open(String remoteHost, Integer remotePort) {
    log.debug("open: remoteHost={}, remotePort={}", remoteHost, remotePort);
    return Try.of(
            () ->
                restClient
                    .post()
                    .uri(
                        b ->
                            b.queryParam("host", remoteHost).queryParam("port", remotePort).build())
                    .retrieve()
                    .body(Soht2Connection.class))
        .onSuccess(connection -> log.info("open: connection={}", connection))
        .onFailure(
            e -> log.atError().setMessage("open: {}").addArgument(e::toString).setCause(e).log());
  }

  /**
   * Closes the connection with the specified ID.
   *
   * @param connectionId the ID of the connection to close
   * @return a {@link Try} containing Void if successful, or an error if it fails
   */
  @SuppressWarnings("java:S1905")
  public Try<Void> close(UUID connectionId) {
    log.debug("close: id={}", connectionId);
    return Try.of(
            () ->
                restClient
                    .delete()
                    .uri(PATH_ID, Map.of("id", connectionId))
                    .retrieve()
                    .toBodilessEntity())
        .onSuccess(re -> log.info("close: id={} - {}", connectionId, re.getStatusCode()))
        .map(re -> (Void) null)
        .onFailure(
            e ->
                log.atError()
                    .setMessage("close: id={} - {}")
                    .addArgument(connectionId)
                    .addArgument(e::toString)
                    .log());
  }

  /**
   * Exchanges data with the server using the specified connection ID.
   *
   * @param connectionId the ID of the connection to use for the exchange
   * @param data the data to send to the server
   * @return a {@link Try} containing the response bytes if successful, or an error if it fails
   */
  public Try<byte[]> exchange(UUID connectionId, byte[] data) {
    if (log.isTraceEnabled())
      Optional.of(data)
          .filter(v -> v.length > 0)
          .ifPresent(v -> log.trace("exchange: id={}, in.length={}", connectionId, v.length));
    return Try.of(() -> requestExchangeEntity(data))
        .peek(
            entity ->
                log.trace("exchange: id={}, request.headers={}", connectionId, entity.getHeaders()))
        .mapTry(
            entity ->
                restClient
                    .post()
                    .uri(PATH_ID, Map.of("id", connectionId))
                    .headers(h -> h.putAll(entity.getHeaders()))
                    .body(ofNullable(entity.getBody()).orElse(EMPTY), PTR_BYTES)
                    .retrieve()
                    .toEntity(PTR_BYTES))
        .peek(
            entity ->
                log.trace(
                    "exchange: id={}, response.headers={}", connectionId, entity.getHeaders()))
        .mapTry(this::responseExchangeEntity)
        .onSuccess(
            bytes ->
                Optional.of(bytes)
                    .filter(v -> v.length > 0)
                    .filter(v -> log.isTraceEnabled())
                    .ifPresent(
                        v -> log.trace("exchange: id={}, out.length={}", connectionId, v.length)))
        .onFailure(
            e ->
                log.atError()
                    .setMessage("exchange: id={}, {}")
                    .addArgument(connectionId)
                    .addArgument(e::toString)
                    // .setCause(e)
                    .log());
  }

  private HttpEntity<byte[]> requestExchangeEntity(byte[] body) {
    val compression = soht2ClientProperties.getCompression();
    val compressor = compressorCache.apply(compression.getType().name().toLowerCase());
    val headers = new HttpHeaders();
    headers.set(ACCEPT_ENCODING, compressor.getAcceptEncoding());
    if (body.length >= compression.getMinRequestSize().toBytes()) {
      val compressedBody = compressor.compress(body);
      headers.setContentLength(compressedBody.length);
      ofNullable(compressor.getContentEncoding()).ifPresent(v -> headers.set(CONTENT_ENCODING, v));
      return new HttpEntity<>(compressedBody, headers);
    }
    return new HttpEntity<>(body, headers);
  }

  private byte[] responseExchangeEntity(HttpEntity<byte[]> entity) {
    return Optional.of(entity.getHeaders()).map(h -> h.get(CONTENT_ENCODING)).stream()
        .flatMap(Collection::stream)
        .findAny()
        .map(compressorCache::apply)
        .map(compressor -> compressor.decompress(entity.getBody()))
        .or(() -> ofNullable(entity.getBody()))
        .orElse(EMPTY);
  }
}
