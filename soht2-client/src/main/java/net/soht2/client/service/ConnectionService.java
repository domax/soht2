/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.runAsync;

import io.vavr.control.Try;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.client.config.Soht2ClientProperties;
import net.soht2.client.config.Soht2ClientProperties.HostProperties;
import net.soht2.common.dto.Soht2Connection;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing connections to SOHT2 servers. It handles starting and stopping
 * connections, as well as data exchange between client and server.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ConnectionService {

  private final Soht2ClientProperties soht2ClientProperties;
  private final Soht2Client soht2Client;
  private final PollStrategy pollStrategy;
  private final ExecutorService threadExecutor;

  private final AtomicBoolean isRunning = new AtomicBoolean();
  private final Map<UUID, SessionState> sessions = new ConcurrentHashMap<>();
  private final Set<ServerSocket> serverSockets = ConcurrentHashMap.newKeySet();
  private volatile CompletableFuture<Void> listeners = // NOSONAR java:S3077
      CompletableFuture.completedFuture(null);

  /**
   * Starts connections to the specified hosts defined in the client properties.
   *
   * @return a {@link CompletableFuture} that completes when all connections are started
   */
  public CompletableFuture<Void> startConnections() {
    return startConnections(null);
  }

  /**
   * Starts connections to the specified hosts defined in the client properties.
   *
   * @param socketOpenedCallback a callback that is invoked when a socket connection is opened
   * @return a {@link CompletableFuture} that completes when all connections are started
   */
  public CompletableFuture<Void> startConnections(
      @Nullable Consumer<HostProperties> socketOpenedCallback) {
    isRunning.set(true);
    listeners =
        CompletableFuture.allOf(
                soht2ClientProperties.getConnections().stream()
                    .map(
                        host -> runAsync(() -> connect(host, socketOpenedCallback), threadExecutor))
                    .toArray(CompletableFuture[]::new))
            .thenRun(() -> {
              isRunning.set(false);
              log.info("startConnections: terminated");
            });
    return listeners;
  }

  /**
   * Gratefully stops all active connections and releases the local ports. The shared thread
   * executor is left running so that connections can be started again; it is shut down together
   * with the application context.
   */
  @PreDestroy
  public void stopConnections() {
    isRunning.set(false);
    serverSockets.forEach(serverSocket -> Try.run(serverSocket::close));
    serverSockets.clear();
    Try.run(() -> listeners.get(5, TimeUnit.SECONDS))
        .onFailure(e -> log.warn("stopConnections: listeners not stopped - {}", e.toString()));
    sessions.keySet().forEach(soht2Client::close);
    sessions.clear();
  }

  /**
   * Checks if a server connection is open for the given connection ID.
   *
   * @param connectionId the unique identifier of the connection
   * @return true if the server connection is open, false otherwise
   */
  public boolean isSessionClosed(UUID connectionId) {
    return !sessions.containsKey(connectionId);
  }

  /**
   * Binds a server socket on the local port of the specified host once and keeps accepting incoming
   * connections while the service is running. Each accepted connection is served on its own task.
   *
   * @param host the properties of the host to connect to
   * @param socketOpenedCallback an optional callback that is invoked when the server socket is
   *     bound
   */
  void connect(HostProperties host, @Nullable Consumer<HostProperties> socketOpenedCallback) {
    log.info("connect: host={}", host);

    try (val serverSocket = new ServerSocket(host.getLocalPort())) {
      serverSockets.add(serverSocket);
      log.debug("connect: serverSocket={}", serverSocket);
      ofNullable(socketOpenedCallback).ifPresent(cb -> cb.accept(host));

      while (isRunning.get()) if (!serveAsync(host, serverSocket)) break;
      serverSockets.remove(serverSocket);
    } catch (Exception e) {
      if (e instanceof BindException && "Address already in use".equals(e.getMessage())) {
        log.warn("connect: interrupted with exception: {}", e.toString());
        isRunning.set(false);
      } else log.atError().setMessage("connect: {}").addArgument(e::toString).setCause(e).log();
    }
    log.info("connect: stopped host={}", host);
  }

  /**
   * Accepts an incoming connection on the server socket and serves it asynchronously. If the
   * service is no longer running, the connection is not accepted.
   *
   * @param host the properties of the host to connect to
   * @param serverSocket the server socket to accept connections on
   * @return {@code true} if the connection was accepted and served, {@code false} if the service is
   *     no longer running
   */
  boolean serveAsync(HostProperties host, ServerSocket serverSocket) {
    try {
      val socket = serverSocket.accept();
      log.debug("connect: socket={}", socket);
      runAsync(() -> serve(host, socket), threadExecutor);
    } catch (Exception e) {
      if (!isRunning.get()
          || serverSocket.isClosed()
          || e instanceof RejectedExecutionException
          || e instanceof SocketException && "Closed by interrupt".equals(e.getMessage())) {
        log.warn("serveAsync: interrupted with exception: {}", e.toString());
        return false;
      }
      log.atError().setMessage("connect: {}").addArgument(e::toString).setCause(e).log();
    }
    return true;
  }

  /**
   * Opens a tunnel for the accepted local socket and exchanges data until the session ends. The
   * local socket is always closed afterward, including when the tunnel cannot be opened.
   *
   * @param host the properties of the host the socket was accepted for
   * @param socket the accepted local socket
   */
  void serve(HostProperties host, Socket socket) {
    try (socket) {
      socket.setSoTimeout((int) soht2ClientProperties.getSocketReadTimeout().toMillis());
      socket.setKeepAlive(true);

      val state =
          SessionState.builder()
              .host(host)
              .connection(soht2Client.open(host.getRemoteHost(), host.getRemotePort()).get())
              .in(socket.getInputStream())
              .out(socket.getOutputStream())
              .build();
      sessions.put(state.connection.id(), state);
      log.debug("serve: connection={}", state.connection);

      exchange(state);
    } catch (Exception e) {
      log.atError().setMessage("serve: {}").addArgument(e::toString).setCause(e).log();
    }
  }

  /**
   * Exchanges data between the client and server for the given session state.
   *
   * @param state the session state containing connection and I/O streams
   */
  void exchange(SessionState state) {
    val connectionId = state.connection.id();
    val readSize = new AtomicInteger();
    val writeSize = new AtomicInteger();
    val bufferSize = (int) soht2ClientProperties.getReadBufferSize().toBytes();
    val buffer = new byte[bufferSize];

    while (sessions.containsKey(connectionId)) {
      readSize.set(0);
      writeSize.set(0);

      Try.of(() -> state.in.read(buffer))
          .filter(bufferLen -> bufferLen >= 0, () -> new SocketException("Connection reset"))
          .recover(SocketTimeoutException.class, 0)
          .andThen(readSize::set)
          .flatMap(bufferLen ->
              soht2Client.exchange(
                  connectionId, bufferLen < bufferSize ? Arrays.copyOf(buffer, bufferLen) : buffer))
          .flatMapTry(bytes ->
              Optional.of(bytes)
                  .filter(b -> b.length > 0)
                  .map(b -> Try.run(() -> state.out.write(b)).andThenTry(state.out::flush))
                  .orElse(Try.success(null))
                  .andThenTry(v -> writeSize.set(bytes.length)))
          .andThenTry(() -> delay(state, readSize.get() == 0 && writeSize.get() == 0))
          .recoverWith(
              SocketException.class,
              // Message "Connection reset" is expected in 2 cases:
              // 1. The OS resets the socket connection.
              // 2. This loop is interrupted by throwing a SocketException above.
              // Message "Idle timeout" is thrown when the idle TTL is exceeded.
              e ->
                  "Connection reset".equals(e.getMessage()) || "Idle timeout".equals(e.getMessage())
                      ? Try.failure(e)
                      : Try.success(null))
          .onFailure(e -> {
            sessions.remove(connectionId);
            soht2Client.close(connectionId);
          });
    }
  }

  @SneakyThrows
  private void delay(SessionState state, boolean isEmptyExchange) {
    if (isEmptyExchange) {
      val delay = pollStrategy.getDelay(state.emptyExchangeCount.getAndIncrement());
      if (!delay.isZero()) {
        log.debug("delay: id={} - {}", state.connection.id(), delay);
        Thread.sleep(delay.toMillis());
      }
      checkIdleTimeout(state);
    } else {
      state.emptyExchangeCount.set(0);
      state.lastTrafficNanos.set(System.nanoTime());
    }
  }

  /**
   * Checks the per-connection idle TTL. If the timeout is enabled and no traffic has been exchanged
   * for longer than the configured duration, the exchange loop is interrupted.
   *
   * @param state the session state containing the last traffic timestamp
   */
  @SneakyThrows
  private void checkIdleTimeout(SessionState state) {
    val timeout = soht2ClientProperties.getDisconnectOnIdleTimeout();
    if (timeout.isZero()) return;

    val idle = Duration.ofNanos(System.nanoTime() - state.lastTrafficNanos.get());
    if (idle.compareTo(timeout) >= 0) {
      log.info("checkIdleTimeout: id={} idle={} timeout={}", state.connection.id(), idle, timeout);
      throw new SocketException("Idle timeout");
    }
  }

  @Builder
  @RequiredArgsConstructor
  static class SessionState {
    final HostProperties host;
    final Soht2Connection connection;
    final InputStream in;
    final OutputStream out;
    final AtomicInteger emptyExchangeCount = new AtomicInteger(0);

    /** The timestamp of the last in/out traffic, used to track the idle TTL. */
    final AtomicLong lastTrafficNanos = new AtomicLong(System.nanoTime());
  }
}
