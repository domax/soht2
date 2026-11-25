/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.runAsync;

import io.vavr.control.Try;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
import net.soht2.client.service.Soht2Client.OpenResult;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.common.dto.Soht2Protocol.TransportType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Service responsible for managing connections to SOHT2 servers. It handles starting and stopping
 * connections, as well as data exchange between client and server.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ConnectionService {

  private static final String ERR_CONN_RESET = "Connection reset";

  private final Soht2ClientProperties soht2ClientProperties;
  private final Soht2Client soht2Client;
  private final PollStrategy pollStrategy;
  private final ExecutorService threadExecutor;

  private final AtomicBoolean isRunning = new AtomicBoolean();
  private final AtomicBoolean isFallbackLogged = new AtomicBoolean();
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
        .onFailure(e ->
            log.atWarn()
                .setMessage("stopConnections: listeners not stopped - {}")
                .addArgument(e::toString)
                .log());
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

    try (val serverSocket = new ServerSocket()) {
      serverSocket.bind(
          new InetSocketAddress(InetAddress.getByName(host.getLocalHost()), host.getLocalPort()));
      log.info("connect: listening on {}", serverSocket.getLocalSocketAddress());
      if (serverSocket.getInetAddress().isAnyLocalAddress())
        log.warn(
            "connect: port {} accepts connections from other hosts (local-host={})",
            host.getLocalPort(),
            host.getLocalHost());
      serverSockets.add(serverSocket);
      log.debug("connect: serverSocket={}", serverSocket);
      ofNullable(socketOpenedCallback).ifPresent(cb -> cb.accept(host));

      while (isRunning.get()) if (!serveAsync(host, serverSocket)) break;
      serverSockets.remove(serverSocket);
    } catch (Exception e) {
      if (e instanceof BindException && "Address already in use".equals(e.getMessage())) {
        log.atWarn()
            .setMessage("connect: interrupted with exception: {}")
            .addArgument(e::toString)
            .log();
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
      log.debug("serveAsync: socket={}", socket);
      runAsync(() -> serve(host, socket), threadExecutor);
    } catch (Exception e) {
      if (!isRunning.get()
          || serverSocket.isClosed()
          || e instanceof RejectedExecutionException
          || e instanceof SocketException && "Closed by interrupt".equals(e.getMessage())) {
        log.atWarn()
            .setMessage("serveAsync: interrupted with exception: {}")
            .addArgument(e::toString)
            .log();
        return false;
      }
      log.atError().setMessage("serveAsync: {}").addArgument(e::toString).setCause(e).log();
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

      val opened = soht2Client.open(host.getRemoteHost(), host.getRemotePort()).get();
      val state =
          SessionState.builder()
              .host(host)
              .connection(opened.connection())
              .socket(socket)
              .in(socket.getInputStream())
              .out(socket.getOutputStream())
              .build();
      sessions.put(state.connection.id(), state);
      log.debug("serve: connection={}", state.connection);

      if (isDuplex(opened)) duplex(state);
      else exchange(state);
    } catch (Exception e) {
      log.atError().setMessage("serve: {}").addArgument(e::toString).setCause(e).log();
    }
  }

  /**
   * Exchanges data between the client and server for the given session state using the simple
   * transport: one request per round trip carries both directions, with client-side polling.
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
          .filter(bufferLen -> bufferLen >= 0, () -> new SocketException(ERR_CONN_RESET))
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
                  ERR_CONN_RESET.equals(e.getMessage()) || "Idle timeout".equals(e.getMessage())
                      ? Try.failure(e)
                      : Try.success(null))
          .onFailure(e -> {
            sessions.remove(connectionId);
            soht2Client.close(connectionId);
          });
    }
  }

  /**
   * Chooses the transport of a session: duplex when it is configured and the server advertised it
   * on open, the simple exchange otherwise. A fallback because of an older server is logged once.
   *
   * @param opened the result of opening the connection
   * @return {@code true} to use the duplex transport, {@code false} to use the simple exchange
   */
  boolean isDuplex(OpenResult opened) {
    if (soht2ClientProperties.getTransport() == TransportType.SIMPLE) return false;
    if (opened.duplex()) return true;
    if (isFallbackLogged.compareAndSet(false, true))
      log.warn("isDuplex: the server does not support the duplex transport, using simple");
    return false;
  }

  /**
   * Tunnels a session with the duplex transport: the download loop runs on its own task, the upload
   * loop on the current one. The session is closed once when either loop ends.
   *
   * @param state the session state containing connection and I/O streams
   */
  void duplex(SessionState state) {
    Try.run(() -> runAsync(() -> download(state), threadExecutor))
        .onFailure(e -> closeSession(state, "duplex", e));
    upload(state);
  }

  /**
   * Duplex upload loop: sends bytes read from the local socket to the server as soon as they
   * arrive, without waiting for the download lane. Local EOF or a failed request ends the session.
   *
   * @param state the session state containing connection and I/O streams
   */
  @SuppressWarnings("java:S3358")
  void upload(SessionState state) {
    val connectionId = state.connection.id();
    val bufferSize = (int) soht2ClientProperties.getReadBufferSize().toBytes();
    val buffer = new byte[bufferSize];

    while (sessions.containsKey(connectionId))
      Try.of(() -> state.in.read(buffer))
          .filter(bufferLen -> bufferLen >= 0, () -> new SocketException(ERR_CONN_RESET))
          .recover(SocketTimeoutException.class, 0)
          .flatMap(bufferLen ->
              bufferLen == 0
                  ? Try.success(0)
                  : soht2Client
                      .upload(
                          connectionId,
                          state.uploadSeq.incrementAndGet(),
                          bufferLen < bufferSize ? Arrays.copyOf(buffer, bufferLen) : buffer)
                      .map(v -> bufferLen))
          .andThen(bufferLen -> {
            if (bufferLen > 0) state.lastTrafficNanos.set(System.nanoTime());
          })
          .andThenTry(() -> checkIdleTimeout(state))
          .onFailure(e -> closeSession(state, "upload", e));
  }

  /**
   * Duplex download loop: issues a held download request with the next sequence number right after
   * the previous one completes, and writes received bytes to the local socket. End-of-stream, a
   * gone connection, or a failed request ends the session.
   *
   * @param state the session state containing connection and I/O streams
   */
  void download(SessionState state) {
    val connectionId = state.connection.id();

    while (sessions.containsKey(connectionId))
      soht2Client
          .download(connectionId, state.downloadSeq.incrementAndGet())
          .andThenTry(d -> {
            switch (d.status()) {
              case DATA -> {
                state.out.write(d.data());
                state.out.flush();
                state.lastTrafficNanos.set(System.nanoTime());
              }
              case EMPTY ->
                  log.atTrace()
                      .setMessage("download: id={} - no data")
                      .addArgument(connectionId::toString)
                      .log();
              case EOS -> throw new ServerClosedException("Target end-of-stream");
              case GONE -> throw new ServerClosedException("Connection gone");
            }
          })
          .andThenTry(() -> checkIdleTimeout(state))
          .onFailure(e -> closeSession(state, "download", e));
  }

  /**
   * Closes a duplex session exactly once: removes the session (which stops both loops), closes the
   * local socket (which unblocks the upload loop), and closes the server connection (which releases
   * a held download), unless the server has already closed it.
   */
  private void closeSession(SessionState state, String lane, Throwable cause) {
    val connectionId = state.connection.id();
    if (sessions.remove(connectionId) == null) return;
    log.atInfo()
        .setMessage("closeSession: id={}, lane={} - {}")
        .addArgument(connectionId::toString)
        .addArgument(lane)
        .addArgument(() -> ofNullable(cause.getMessage()).orElseGet(cause::toString))
        .log();
    ofNullable(state.socket).ifPresent(socket -> Try.run(socket::close));
    if (!(cause instanceof ServerClosedException || cause instanceof HttpClientErrorException.Gone))
      soht2Client.close(connectionId);
  }

  @SneakyThrows
  private void delay(SessionState state, boolean isEmptyExchange) {
    if (isEmptyExchange) {
      val delay = pollStrategy.getDelay(state.emptyExchangeCount.getAndIncrement());
      if (!delay.isZero()) {
        log.atDebug()
            .setMessage("delay: id={} - {}")
            .addArgument(state.connection::id)
            .addArgument(delay::toString)
            .log();
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
      log.atInfo()
          .setMessage("checkIdleTimeout: id={} idle={} timeout={}")
          .addArgument(state.connection::id)
          .addArgument(idle::toString)
          .addArgument(timeout::toString)
          .log();
      throw new SocketException("Idle timeout");
    }
  }

  @Builder
  @RequiredArgsConstructor
  static class SessionState {
    final HostProperties host;
    final Soht2Connection connection;
    final @Nullable Socket socket;
    final InputStream in;
    final OutputStream out;

    /** The number of consecutive empty exchanges, used to calculate the polling delay. */
    final AtomicInteger emptyExchangeCount = new AtomicInteger(0);

    /** The timestamp of the last in/out traffic, used to track the idle TTL. */
    final AtomicLong lastTrafficNanos = new AtomicLong(System.nanoTime());

    /** The sequence number of the last duplex upload request. */
    final AtomicLong uploadSeq = new AtomicLong(0);

    /** The sequence number of the last duplex download request. */
    final AtomicLong downloadSeq = new AtomicLong(0);
  }

  /** Signals that the server has already closed the connection (end-of-stream or gone). */
  static final class ServerClosedException extends IOException {
    ServerClosedException(String message) {
      super(message);
    }
  }
}
