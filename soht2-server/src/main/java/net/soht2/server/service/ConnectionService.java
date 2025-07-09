/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import static java.util.Optional.ofNullable;
import static net.soht2.common.util.AuxUtil.peek;
import static net.soht2.server.service.ExceptionHelper.gone;

import io.vavr.control.Try;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.common.dto.SohtConnection;
import net.soht2.server.config.ConnectorConfig;
import net.soht2.server.model.ConnectionInfo;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
   * @param connection A {@link SohtConnection} object containing the connection details to be
   *     opened
   * @return the same {@link SohtConnection} object that was provided as input
   */
  public ConnectionInfo open(SohtConnection connection) {
    log.info("open: connection={}", connection);
    val result = ConnectionInfo.builder().connection(connection).build();
    connections.put(connection.id(), result);
    return result;
  }

  /**
   * Retrieves the {@link ConnectionInfo} associated with the specified unique identifier.
   *
   * @param id the unique identifier of the connection to retrieve
   * @return the {@link ConnectionInfo} associated with the given identifier, or {@code null} if no
   *     connection is found
   */
  public Optional<ConnectionInfo> get(UUID id) {
    return ofNullable(connections.get(id));
  }

  /**
   * Closes the connection associated with the specified unique identifier. If a connection exists,
   * it is first closed before being removed from the connection pool.
   *
   * @param id the unique identifier of the connection to remove
   */
  public void close(UUID id) {
    ofNullable(connections.remove(id))
        .map(peek(ci -> log.info("close: connection={}", ci.connection())))
        .ifPresent(ConnectionInfo::close);
  }

  /**
   * Retrieves a collection of all active connections currently managed by the service.
   *
   * @return a {@link Collection} of {@link SohtConnection} objects representing active connections
   */
  public Collection<SohtConnection> list() {
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
    return Mono.justOrEmpty(get(id))
        .switchIfEmpty(Mono.error(gone("Connection is unavailable")))
        .doOnNext(ci -> Try.run(() -> ci.socket().setSoTimeout(soTimeout)).get())
        .flatMapMany(
            ci ->
                DataBufferUtils.readInputStream(
                    ci::inputStream,
                    dataBufferFactory,
                    (int) connectorConfig.getReadBufferSize().toBytes()))
        .onErrorResume(SocketTimeoutException.class, e -> retain ? Flux.error(e) : Flux.empty())
        /*.doOnTerminate(() -> Optional.of(id).filter(v -> retain).ifPresent(this::close))*/;
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
    log.debug("write: id={}, data={}", id, data);
    return Mono.justOrEmpty(get(id).map(ConnectionInfo::outputStream))
        .switchIfEmpty(Mono.error(gone("Connection is closed")))
        .flatMapMany(os -> DataBufferUtils.write(data, os))
        .doOnNext(DataBufferUtils::release)
        .then();
  }

  /*public Flux<DataBuffer> exchange(
      UUID id, Flux<DataBuffer> data, DataBufferFactory dataBufferFactory) {
    log.debug("exchange: id={}, data={}", id, data);

    val result = new ByteArrayOutputStream(binData.length);
    val dataIn = new ByteArrayInputStream(binData);
    val buffer = new byte[bufferSize];
    int bufferLen;
    while ((bufferLen = dataIn.read(buffer)) > 0) {
      log.info("EchoClient -->: {}", bufferLen);
      out.write(buffer, 0, bufferLen);
      out.flush();
      Arrays.fill(buffer, (byte) 0);
      bufferLen = Try.of(() -> in.read(buffer)).recover(SocketTimeoutException.class, 0).get();
      if (bufferLen == 0) break;
      log.info("EchoClient <--: {}", bufferLen);
      if (bufferLen > 0) result.write(buffer, 0, bufferLen);
    }
    return result.toByteArray();

  }*/
}
