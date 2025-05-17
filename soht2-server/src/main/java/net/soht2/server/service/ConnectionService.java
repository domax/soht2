/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import static java.util.Optional.ofNullable;
import static net.soht2.server.service.ExceptionHelper.serviceUnavailable;

import io.vavr.control.Try;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.common.dto.ConnectionDto;
import net.soht2.server.config.ConnectorConfig;
import net.soht2.server.model.ConnectionInfo;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

/**
 * Service responsible for managing connections, providing functionality such as adding, retrieving,
 * reading from, writing to, and removing connections.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ConnectionService {

  private final Map<UUID, ConnectionInfo> connections = new ConcurrentHashMap<>();

  private final ConnectorConfig connectorConfig;

  /**
   * Opens a new connection and adds it to the connection pool.
   *
   * @param connection A {@link ConnectionDto} object containing the connection details to be opened
   * @return the same {@link ConnectionDto} object that was provided as input
   */
  public ConnectionDto open(ConnectionDto connection) {
    log.debug("addConnection: connection={}", connection);
    connections.put(connection.id(), ConnectionInfo.builder().connection(connection).build());
    return connection;
  }

  /**
   * Retrieves the {@link ConnectionInfo} associated with the specified unique identifier.
   *
   * @param id the unique identifier of the connection to retrieve
   * @return the {@link ConnectionInfo} associated with the given identifier, or {@code null} if no
   *     connection is found
   */
  @Nullable public ConnectionInfo get(UUID id) {
    return connections.get(id);
  }

  /**
   * Closes the connection associated with the specified unique identifier. If a connection exists,
   * it is first closed before being removed from the connection pool.
   *
   * @param id the unique identifier of the connection to remove
   */
  public void close(UUID id) {
    log.debug("removeConnection: id={}", id);
    ofNullable(get(id)).ifPresent(ConnectionInfo::close);
    connections.remove(id);
  }

  /**
   * Retrieves a collection of all active connections currently managed by the service.
   *
   * @return a {@link Collection} of {@link ConnectionDto} objects representing active connections
   */
  public Collection<ConnectionDto> list() {
    return connections.values().stream().map(ConnectionInfo::connection).toList();
  }

  /**
   * Reads data from the connection identified by the given {@link UUID}.
   *
   * @param id the unique identifier of the connection to read from
   * @param dataBufferFactory the factory to create {@link DataBuffer} instances for data transfer
   * @param retain if {@code true}, keeps the connection open and continues reading until an error
   *     occurs; if {@code false}, closes after timeout
   * @return a {@link Flux} emitting {@link DataBuffer} instances containing the data read from the
   *     connection
   */
  public Flux<DataBuffer> read(UUID id, DataBufferFactory dataBufferFactory, boolean retain) {
    log.debug("read: id={}, retain={}", id, retain);
    val soTimeout = retain ? 0 : (int) connectorConfig.getSocketTimeout().toMillis();
    return Mono.justOrEmpty(ofNullable(get(id)))
        .doOnNext(ci -> Try.run(() -> ci.socket().setSoTimeout(soTimeout)).get())
        .flatMapMany(
            ci ->
                DataBufferUtils.readInputStream(
                    ci::inputStream,
                    dataBufferFactory,
                    (int) connectorConfig.getReadBufferSize().toBytes()))
        .onErrorResume(SocketTimeoutException.class, e -> retain ? Flux.error(e) : Flux.empty())
        .doOnTerminate(() -> Optional.of(id).filter(v -> retain).ifPresent(this::close));
  }

  /**
   * Writes the provided data to the connection identified by the given {@link UUID}.
   *
   * @param id the unique identifier of the connection to write to
   * @param data a {@link Flux} of {@link DataBuffer} objects containing the data to be written
   * @return a {@link Mono} that completes when the write operation is completed, or emits an error
   *     if the connection is unavailable or the writing fails
   */
  public Mono<Void> write(UUID id, Flux<DataBuffer> data) {
    return Mono.justOrEmpty(ofNullable(get(id)).map(ConnectionInfo::outputStream))
        .switchIfEmpty(Mono.error(serviceUnavailable("Connection is closed")))
        .flatMapMany(os -> DataBufferUtils.write(data, os))
        .doOnNext(DataBufferUtils::release)
        .then();
  }
}
