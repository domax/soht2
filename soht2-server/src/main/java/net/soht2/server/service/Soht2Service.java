/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static net.soht2.common.compress.Compressor.compressorCache;
import static net.soht2.common.util.AuxUtil.peek;
import static net.soht2.server.service.ExceptionHelper.gone;
import static net.soht2.server.service.Soht2UserService.EMPTY_CU;
import static net.soht2.server.service.Soht2UserService.getCurrentUser;

import io.vavr.control.Try;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2User;
import net.soht2.server.config.Soht2ServerConfig;
import net.soht2.server.entity.UserEntity;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing connections, providing functionality such as adding, retrieving,
 * reading from, writing to, and removing connections.
 */
@Slf4j
@RequiredArgsConstructor
@Service("soht2Service")
public class Soht2Service {

  private static final byte[] EMPTY = new byte[0];

  private final Map<UUID, ServerConnection> connections = new ConcurrentHashMap<>();
  private final Soht2ServerConfig soht2ServerConfig;
  private final Soht2UserService soht2UserService;
  private final Soht2HistoryService soht2HistoryService;

  /**
   * Opens a new SOHT2 connection and adds it to the connection pool.
   *
   * @param soht2 A {@link Soht2Connection} object containing the SOHT2 connection details to be
   *     opened
   * @param authentication the authentication object representing the current user
   * @return the same {@link Soht2Connection} object that was provided as input
   */
  public ServerConnection open(Soht2Connection soht2, Authentication authentication) {
    log.info("open: soht2={}", soht2);
    val connection =
        ServerConnection.builder()
            .soht2(soht2)
            .socketTimeout((int) soht2ServerConfig.getSocketReadTimeout().toMillis())
            .postCloseAction(this::postCloseAction)
            .build();
    connections.put(soht2.id(), connection);
    return updateConnectionWithUser(connection, authentication);
  }

  /**
   * Closes the connection associated with the specified unique identifier. If a connection exists,
   * it is first closed before being removed from the connection pool.
   *
   * @param id the unique identifier of the connection to remove
   */
  public void close(UUID id) {
    ofNullable(connections.get(id))
        .map(peek(ci -> log.info("close: soht2={}", ci.soht2())))
        .ifPresent(ServerConnection::close);
  }

  /**
   * Retrieves the {@link Soht2Connection} associated with the specified unique identifier.
   *
   * @param id the unique identifier of the connection to retrieve
   * @param authentication the authentication object representing the current user
   * @return the {@link Soht2Connection} associated with the given identifier, or {@code null} if no
   *     connection is found
   */
  public Optional<Soht2Connection> get(UUID id, Authentication authentication) {
    log.info("get: id={}, authentication={}", id, authentication);
    return ofNullable(connections.get(id))
        .map(c -> updateConnectionWithUser(c, authentication))
        .map(ServerConnection::soht2);
  }

  /**
   * Retrieves a collection of all active {@link Soht2Connection} instances. The method filters the
   * connections based on the current user's permissions, allowing only those connections that
   * belong to the user or if the user has admin privileges.
   *
   * @param authentication the authentication object representing the current user
   * @return a collection of {@link Soht2Connection} objects representing all active connections
   */
  public Collection<Soht2Connection> list(Authentication authentication) {
    log.info("list: authentication={}", authentication);
    val cu = getCurrentUser(authentication).orElse(EMPTY_CU);

    val usernames =
        connections.values().stream()
            .map(ServerConnection::soht2)
            .map(Soht2Connection::user)
            .map(Soht2User::username)
            .map(String::toLowerCase)
            .filter(un -> cu.isAdmin() || un.equals(cu.name()))
            .collect(toSet());
    val users =
        soht2UserService.listUsers(usernames).stream()
            .collect(toMap(Soht2User::username, Function.identity()));

    return connections.values().stream()
        .filter(sc -> usernames.contains(sc.soht2().user().username().toLowerCase()))
        .map(sc -> updateConnectionWithUser(sc, users::get))
        .map(sc -> sc.soht2().withBytesExchanged(sc.bytesRead(), sc.bytesWritten()))
        .toList();
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
                val dataOut = compressorCache.apply(encoding).decompress(data);
                connection.outputStream().write(dataOut);
                connection.outputStream().flush();
                connection.addBytesWritten(dataOut.length);
              }
              val buffer = new byte[(int) soht2ServerConfig.getReadBufferSize().toBytes()];
              val bufferLen = connection.inputStream().read(buffer);
              if (bufferLen <= 0) return EMPTY;
              connection.addBytesRead(bufferLen);
              return bufferLen >= buffer.length ? buffer : Arrays.copyOf(buffer, bufferLen);
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
  @Scheduled(fixedRateString = "${soht2.server.abandoned-connections.check-interval}")
  public void closeAbandonedConnections() {
    val ttl = soht2ServerConfig.getAbandonedConnections().getTimeout();
    connections.values().stream()
        .filter(ServerConnection::isOpened)
        .peek(
            c ->
                log.atDebug()
                    .setMessage("closeAbandonedConnections: id={}, age={}/{}")
                    .addArgument(c.soht2()::id)
                    .addArgument(c::activityAge)
                    .addArgument(c::connectionAge)
                    .log())
        .filter(c -> c.activityAge().compareTo(ttl) >= 0)
        .peek(c -> log.warn("closeAbandonedConnections: soht2={}", c.soht2()))
        .forEach(ServerConnection::close);
  }

  /**
   * Checks if the given authentication belongs to the owner of the connection identified by the
   * specified unique identifier.
   *
   * @param authentication the authentication object representing the user
   * @param connectionId the unique identifier of the connection to check ownership for
   * @return {@code true} if the user is the owner of the connection, {@code false} otherwise
   */
  public boolean isConnectionOwner(Authentication authentication, UUID connectionId) {
    return ofNullable(connections.get(connectionId))
        .map(ServerConnection::soht2)
        .filter(c -> c.id().equals(connectionId))
        .filter(c -> c.user().username().equals(authentication.getName()))
        .isPresent();
  }

  /**
   * Checks if the target host and port are allowed for the user associated with the given
   * authentication.
   *
   * @param authentication the authentication object representing the user
   * @param targetHost the target host to check
   * @param targetPort the target port to check
   * @return {@code true} if the target is allowed, {@code false} otherwise
   */
  public boolean isTargetAllowed(Authentication authentication, String targetHost, int targetPort) {
    return soht2UserService
        .getCachedUserEntity(authentication.getName())
        .map(UserEntity::toSoht2User)
        .filter(su -> su.isAllowedTarget(targetHost, targetPort))
        .isPresent();
  }

  private ServerConnection updateConnectionWithUser(
      ServerConnection connection, Authentication authentication) {
    soht2UserService
        .getCachedUserEntity(authentication.getName())
        .map(UserEntity::toSoht2User)
        .map(connection.soht2()::withUser)
        .ifPresent(connection::soht2);
    return connection;
  }

  private ServerConnection updateConnectionWithUser(
      ServerConnection connection, Function<String, Soht2User> userFunction) {
    Optional.of(connection.soht2().user().username())
        .map(userFunction)
        .map(connection.soht2()::withUser)
        .ifPresent(connection::soht2);
    return connection;
  }

  private void postCloseAction(ServerConnection connection) {
    val soht2 =
        connection
            .soht2()
            .withClosedAt(LocalDateTime.now())
            .withBytesExchanged(connection.bytesRead(), connection.bytesWritten());
    connections.remove(soht2.id());
    if (soht2ServerConfig.isEnableHistory()) soht2HistoryService.addHistory(soht2);
  }
}
