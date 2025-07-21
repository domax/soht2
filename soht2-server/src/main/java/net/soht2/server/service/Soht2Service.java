/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import static java.util.Optional.ofNullable;
import static net.soht2.common.compress.Compressor.compressorCache;
import static net.soht2.common.util.AuxUtil.peek;
import static net.soht2.server.service.ExceptionHelper.gone;

import io.vavr.control.Try;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.server.config.Soht2ServerConfig;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing connections, providing functionality such as adding, retrieving,
 * reading from, writing to, and removing connections.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class Soht2Service {

  private static final byte[] EMPTY = new byte[0];

  private final Map<UUID, ServerConnection> connections = new ConcurrentHashMap<>();
  private final Soht2ServerConfig soht2ServerConfig;

  /**
   * Opens a new SOHT2 connection and adds it to the connection pool.
   *
   * @param soht2 A {@link Soht2Connection} object containing the SOHT2 connection details to be
   *     opened
   * @return the same {@link Soht2Connection} object that was provided as input
   */
  public ServerConnection open(Soht2Connection soht2) {
    log.info("open: connection={}", soht2);
    val result =
        ServerConnection.builder()
            .soht2(soht2)
            .socketTimeout((int) soht2ServerConfig.getSocketReadTimeout().toMillis())
            .postCloseAction(ci -> connections.remove(ci.soht2().id()))
            .build();
    connections.put(soht2.id(), result);
    return result;
  }

  /**
   * Closes the connection associated with the specified unique identifier. If a connection exists,
   * it is first closed before being removed from the connection pool.
   *
   * @param id the unique identifier of the connection to remove
   */
  public void close(UUID id) {
    ofNullable(connections.get(id))
        .map(peek(ci -> log.info("close: connection={}", ci.soht2())))
        .ifPresent(ServerConnection::close);
  }

  /**
   * Retrieves the {@link Soht2Connection} associated with the specified unique identifier.
   *
   * @param id the unique identifier of the connection to retrieve
   * @return the {@link Soht2Connection} associated with the given identifier, or {@code null} if no
   *     connection is found
   */
  public Optional<Soht2Connection> get(UUID id) {
    return ofNullable(connections.get(id)).map(ServerConnection::soht2);
  }

  /**
   * Retrieves a collection of all active connections currently managed by the service.
   *
   * @return a {@link Collection} of {@link Soht2Connection} objects representing active connections
   */
  public Collection<Soht2Connection> list() {
    return connections.values().stream().map(ServerConnection::soht2).toList();
  }

  /**
   * Sends data to a connection identified by the unique identifier and retrieves the response data.
   * The method writes the provided data to the connection's output stream and reads the response
   * data from its input stream. Handles socket timeout exceptions by returning an empty byte array.
   *
   * @param id the unique identifier of the connection to communicate with
   * @param data the byte array containing the data to be sent to the connection
   * @return a {@link Try} containing the byte array received from the connection's input stream if
   *     the operation is successful, or an empty byte array in case of a timeout
   */
  public Try<byte[]> exchange(UUID id, @Nullable byte[] data, @Nullable String encoding) {
    if (log.isTraceEnabled())
      ofNullable(data)
          .map(v -> v.length)
          .filter(v -> v > 0)
          .ifPresent(v -> log.trace("exchange: id={}, in.length={}, encoding={}", id, v, encoding));
    return Try.of(() -> connections.get(id))
        .filter(Objects::nonNull, () -> gone("Connection " + id + " not found"))
        .filter(ServerConnection::isOpened, () -> gone("Connection " + id + " is closed"))
        .andThenTry(c -> c.lastActivity(LocalDateTime.now()))
        .mapTry(
            connection -> {
              if (ofNullable(data).filter(v -> v.length > 0).isPresent()) {
                connection.outputStream().write(compressorCache.apply(encoding).decompress(data));
                connection.outputStream().flush();
              }
              val buffer = new byte[(int) soht2ServerConfig.getReadBufferSize().toBytes()];
              val bufferLen = connection.inputStream().read(buffer);
              if (bufferLen <= 0) return EMPTY;
              if (bufferLen >= buffer.length) return buffer;
              return Arrays.copyOf(buffer, bufferLen);
            })
        .recover(SocketTimeoutException.class, e -> EMPTY)
        .onFailure(e -> log.error("exchange: id={} - {}", id, e.toString()))
        .recover(SocketException.class, e -> EMPTY)
        .andThenTry(
            bytes ->
                ofNullable(bytes)
                    .filter(v -> log.isTraceEnabled())
                    .map(v -> v.length)
                    .filter(v -> v > 0)
                    .ifPresent(v -> log.trace("exchange: id={}, out.length={}", id, v)));
  }

  /**
   * Closes all connections that have been abandoned for a duration longer than the configured
   * timeout. This method is scheduled to run periodically to ensure that stale connections are
   * cleaned up.
   */
  @SuppressWarnings("java:S3864")
  @Scheduled(fixedRateString = "${soht2.server.abandoned-connections-check-interval}")
  public void closeAbandonedConnections() {
    val ttl = soht2ServerConfig.getAbandonedConnectionsTimeout();
    connections.values().stream()
        .filter(ServerConnection::isOpened)
        .peek(
            c ->
                log.atDebug()
                    .setMessage("closeAbandonedConnections: id={}, age={}/{}")
                    .addArgument(() -> c.soht2().id())
                    .addArgument(c::activityAge)
                    .addArgument(c::connectionAge)
                    .log())
        .filter(c -> c.activityAge().compareTo(ttl) >= 0)
        .peek(c -> log.warn("closeAbandonedConnections: connection={}", c.soht2()))
        .forEach(ServerConnection::close);
  }
}
