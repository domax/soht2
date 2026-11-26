/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static java.util.Optional.ofNullable;
import static net.soht2.common.compress.Compressor.compressorCache;
import static net.soht2.common.dto.Soht2Protocol.*;
import static org.springframework.http.HttpHeaders.ACCEPT_ENCODING;
import static org.springframework.http.HttpHeaders.CONTENT_ENCODING;

import io.vavr.control.Try;
import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.client.config.Soht2ClientProperties.CompressionProperties;
import net.soht2.common.dto.Soht2Connection;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

/**
 * Client for interacting with the SOHT2 server. Provides methods to open and close connections,
 * exchange data (simple transport), and upload and download data (duplex transport).
 */
@SuppressWarnings("java:S1075")
@Slf4j
@RequiredArgsConstructor
@Service
public class Soht2Client {

  private static final byte[] EMPTY = new byte[0];
  private static final ParameterizedTypeReference<byte[]> PTR_BYTES =
      new ParameterizedTypeReference<>() {};

  @SuppressWarnings("java:S1075")
  private static final String PATH_ID = "/{id}";

  private static final String PATH_UPLOAD_ID = PATH_ID + "/" + PATH_UPLOAD;
  private static final String PATH_DOWNLOAD_ID = PATH_ID + "/" + PATH_DOWNLOAD;

  private final RestClient restClient;

  /**
   * The result of opening a connection.
   *
   * @param connection the opened connection
   * @param duplex whether the server advertised support of the duplex transport
   */
  public record OpenResult(Soht2Connection connection, boolean duplex) {}

  /** The kind of response to a duplex download request. */
  public enum DownloadStatus {
    /** The target sent data. */
    DATA,
    /** The hold timeout elapsed on the server without target data. */
    EMPTY,
    /** The target closed its connection (end-of-stream). */
    EOS,
    /** The connection is missing or closed on the server. */
    GONE
  }

  /**
   * The response to a duplex download request.
   *
   * @param status the kind of response
   * @param data the (decompressed) target data; empty unless {@code status} is {@code DATA}
   */
  public record Download(DownloadStatus status, byte[] data) {
    public static final Download EMPTY = new Download(DownloadStatus.EMPTY, Soht2Client.EMPTY);
    public static final Download EOS = new Download(DownloadStatus.EOS, Soht2Client.EMPTY);
    public static final Download GONE = new Download(DownloadStatus.GONE, Soht2Client.EMPTY);

    static Download of(byte[] data) {
      return data.length == 0 ? EMPTY : new Download(DownloadStatus.DATA, data);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      Download download = (Download) o;
      return Objects.deepEquals(data, download.data) && status == download.status;
    }

    @Override
    public int hashCode() {
      return Objects.hash(status, Arrays.hashCode(data));
    }

    @Override
    public String toString() {
      return "Download{"
          + "status="
          + status
          + ", data("
          + data.length
          + ")="
          + (data.length <= 10
              ? Arrays.toString(data)
              : Arrays.toString(Arrays.copyOfRange(data, 0, 10)).replace("]", "...]"))
          + '}';
    }
  }

  /**
   * Opens a connection to the specified remote host and port.
   *
   * @param remoteHost the host name or IP address of the remote machine
   * @param remotePort the port number on the remote machine to connect to
   * @return a {@link Try} containing the {@link OpenResult} if successful, or an error if it fails
   */
  public Try<OpenResult> open(String remoteHost, Integer remotePort) {
    log.debug("open: remoteHost={}, remotePort={}", remoteHost, remotePort);
    return Try.of(() ->
            restClient
                .post()
                .uri(b -> b.queryParam("host", remoteHost).queryParam("port", remotePort).build())
                .retrieve()
                .toEntity(Soht2Connection.class))
        .filter(
            entity -> entity.getBody() != null,
            () -> new NullPointerException("connection is null"))
        .map(entity ->
            new OpenResult(
                Objects.requireNonNull(entity.getBody()),
                TRANSPORT_DUPLEX.equalsIgnoreCase(entity.getHeaders().getFirst(HEADER_TRANSPORT))))
        .onSuccess(r ->
            log.atInfo()
                .setMessage("open: connection={}, duplex={}")
                .addArgument(r::connection)
                .addArgument(r::duplex)
                .log())
        .onFailure(
            e -> log.atError().setMessage("open: {}").addArgument(e::toString).setCause(e).log());
  }

  /**
   * Closes the connection with the specified ID.
   *
   * @param connectionId the ID of the connection to close
   * @return a {@link Try} containing Void if successful, or an error if it fails
   */
  @SuppressWarnings({"java:S1905", "LoggingSimilarMessage"})
  public Try<Void> close(UUID connectionId) {
    log.debug("close: id={}", connectionId);
    return Try.of(() ->
            restClient
                .delete()
                .uri(PATH_ID, Map.of("id", connectionId))
                .retrieve()
                .toBodilessEntity())
        .onSuccess(re ->
            log.atInfo()
                .setMessage("close: id={} - {}")
                .addArgument(connectionId)
                .addArgument(re::getStatusCode)
                .log())
        .map(re -> (Void) null)
        .onFailure(e ->
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
   * @param compression the compression settings of the connection
   * @param data the data to send to the server
   * @return a {@link Try} containing the response bytes if successful, or an error if it fails
   */
  @SuppressWarnings("LoggingSimilarMessage")
  public Try<byte[]> exchange(UUID connectionId, CompressionProperties compression, byte[] data) {
    if (log.isTraceEnabled() && data.length > 0)
      log.trace("exchange: id={}, in.length={}", connectionId, data.length);
    return Try.of(() -> requestExchangeEntity(compression, data))
        .peek(entity ->
            log.atTrace()
                .setMessage("exchange: id={}, request.headers={}")
                .addArgument(connectionId)
                .addArgument(entity::getHeaders)
                .log())
        .mapTry(entity ->
            restClient
                .post()
                .uri(PATH_ID, Map.of("id", connectionId))
                .headers(h -> h.putAll(entity.getHeaders()))
                .body(ofNullable(entity.getBody()).orElse(EMPTY), PTR_BYTES)
                .retrieve()
                .toEntity(PTR_BYTES))
        .peek(entity ->
            log.atTrace()
                .setMessage("exchange: id={}, response.headers={}")
                .addArgument(connectionId)
                .addArgument(entity::getHeaders)
                .log())
        .mapTry(this::responseExchangeEntity)
        .onSuccess(bytes ->
            Optional.of(bytes)
                .filter(v -> v.length > 0)
                .ifPresent(
                    v -> log.trace("exchange: id={}, out.length={}", connectionId, v.length)))
        .onFailure(e ->
            log.atError()
                .setMessage("exchange: id={}, {}")
                .addArgument(connectionId)
                .addArgument(e::toString)
                .log());
  }

  /**
   * Sends data to the server on the duplex upload lane of the specified connection.
   *
   * @param connectionId the ID of the connection
   * @param seq the upload sequence number; the next one for a new request, the same one for a retry
   * @param compression the compression settings of the connection
   * @param data the data to send to the server
   * @return a {@link Try} containing Void if successful, or an error if it fails
   */
  @SuppressWarnings("java:S1905")
  public Try<Void> upload(
      UUID connectionId, long seq, CompressionProperties compression, byte[] data) {
    log.atTrace()
        .setMessage("upload: id={}, seq={}, in.length={}")
        .addArgument(connectionId)
        .addArgument(seq)
        .addArgument(data.length)
        .log();
    return Try.of(() -> requestExchangeEntity(compression, data))
        .mapTry(entity ->
            restClient
                .post()
                .uri(PATH_UPLOAD_ID, Map.of("id", connectionId))
                .headers(h -> h.putAll(entity.getHeaders()))
                .header(HEADER_SEQ, Long.toString(seq))
                .body(ofNullable(entity.getBody()).orElse(EMPTY), PTR_BYTES)
                .retrieve()
                .toBodilessEntity())
        .map(re -> (Void) null)
        .onFailure(e ->
            log.atError()
                .setMessage("upload: id={}, seq={} - {}")
                .addArgument(connectionId)
                .addArgument(seq)
                .addArgument(e::toString)
                .log());
  }

  /**
   * Requests data from the server on the duplex download lane of the specified connection. The
   * server holds the request until target data is available, the target closes its connection, or
   * its download hold timeout elapses.
   *
   * @param connectionId the ID of the connection
   * @param seq the download sequence number; the next one for every new request (including after an
   *     empty response), the same one only to retry a request whose response was lost
   * @param compression the compression settings of the connection
   * @return a {@link Try} containing the {@link Download} if successful, or an error if it fails
   */
  public Try<Download> download(UUID connectionId, long seq, CompressionProperties compression) {
    log.trace("download: id={}, seq={}", connectionId, seq);
    return Try.of(() ->
            restClient
                .get()
                .uri(PATH_DOWNLOAD_ID, Map.of("id", connectionId))
                .header(HEADER_SEQ, Long.toString(seq))
                .header(ACCEPT_ENCODING, acceptEncoding(compression))
                .exchange((request, response) -> toDownload(response)))
        .onSuccess(d ->
            log.atTrace()
                .setMessage("download: id={}, seq={}, status={}, out.length={}")
                .addArgument(connectionId)
                .addArgument(seq)
                .addArgument(d::status)
                .addArgument(() -> d.data().length)
                .log())
        .onFailure(e ->
            log.atError()
                .setMessage("download: id={}, seq={} - {}")
                .addArgument(connectionId)
                .addArgument(seq)
                .addArgument(e::toString)
                .log());
  }

  private Download toDownload(ConvertibleClientHttpResponse response) throws IOException {
    val status = response.getStatusCode();
    if (status.isSameCodeAs(HttpStatus.GONE))
      return EOS_VALUE.equalsIgnoreCase(response.getHeaders().getFirst(HEADER_EOS))
          ? Download.EOS
          : Download.GONE;
    if (status.isSameCodeAs(HttpStatus.NO_CONTENT)) return Download.EMPTY;
    if (!status.is2xxSuccessful()) throw response.createException();
    try (val body = response.getBody()) {
      return Download.of(decompress(response.getHeaders(), body.readAllBytes()));
    }
  }

  private static String acceptEncoding(CompressionProperties compression) {
    return compressorCache.apply(compression.getType().name()).getAcceptEncoding();
  }

  private static HttpEntity<byte[]> requestExchangeEntity(
      CompressionProperties compression, byte[] body) {
    val compressor = compressorCache.apply(compression.getType().name());
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
    return decompress(entity.getHeaders(), entity.getBody());
  }

  private static byte[] decompress(HttpHeaders headers, byte @Nullable [] body) {
    return Optional.of(headers).map(h -> h.get(CONTENT_ENCODING)).stream()
        .flatMap(Collection::stream)
        .findAny()
        .map(compressorCache::apply)
        .map(compressor -> compressor.decompress(body))
        .or(() -> ofNullable(body))
        .orElse(EMPTY);
  }
}
