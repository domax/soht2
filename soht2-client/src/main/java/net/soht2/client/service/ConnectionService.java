/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.service;

import static java.util.Optional.ofNullable;

import io.vavr.control.Try;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.client.config.Soht2ClientProperties;
import net.soht2.client.config.Soht2ClientProperties.HostProperties;
import net.soht2.common.dto.Soht2Connection;
import org.springframework.lang.Nullable;
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

  private final AtomicBoolean isRunning = new AtomicBoolean();
  private final Map<UUID, SessionState> sessions = new ConcurrentHashMap<>();

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
    return CompletableFuture.allOf(
            soht2ClientProperties.getConnections().stream()
                .map(host -> CompletableFuture.runAsync(() -> connect(host, socketOpenedCallback)))
                .toArray(CompletableFuture[]::new))
        .thenRun(() -> log.info("startConnections: terminated"));
  }

  /** Gratefully stops all active connections. */
  @SuppressWarnings("unused")
  public void stopConnections() {
    isRunning.set(false);
  }

  /**
   * Checks if a server connection is open for the given connection ID.
   *
   * @param connectionId the unique identifier of the connection
   * @return true if the server connection is open, false otherwise
   */
  public boolean isServerOpen(UUID connectionId) {
    return sessions.containsKey(connectionId);
  }

  /**
   * Connects to the specified host and starts a server socket to accept incoming connections.
   *
   * @param host the properties of the host to connect to
   * @param socketOpenedCallback an optional callback that is invoked when a socket connection is
   *     opened
   */
  @SneakyThrows
  void connect(HostProperties host, @Nullable Consumer<HostProperties> socketOpenedCallback) {
    log.info("connect: host={}", host);

    while (isRunning.get()) {
      try (val serverSocket = new ServerSocket(host.getLocalPort())) {
        log.debug("connect: serverSocket={}", serverSocket);
        ofNullable(socketOpenedCallback).ifPresent(cb -> cb.accept(host));

        val socket = serverSocket.accept();
        socket.setSoTimeout((int) soht2ClientProperties.getSocketReadTimeout().toMillis());
        socket.setKeepAlive(true);
        log.debug("connect: socket={}", socket);

        val state =
            SessionState.builder()
                .host(host)
                .connection(soht2Client.open(host.getRemoteHost(), host.getRemotePort()).get())
                .in(socket.getInputStream())
                .out(socket.getOutputStream())
                .build();
        sessions.put(state.connection.id(), state);
        log.debug("connect: connection={}", state.connection);

        CompletableFuture.runAsync(() -> exchange(state));
      } catch (Exception e) {
        log.atError().setMessage("connect: {}").addArgument(e::toString).setCause(e).log();
      }
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
          .flatMap(
              bufferLen ->
                  soht2Client.exchange(
                      connectionId,
                      bufferLen < bufferSize ? Arrays.copyOf(buffer, bufferLen) : buffer))
          .flatMapTry(
              bytes ->
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
              e -> "Connection reset".equals(e.getMessage()) ? Try.failure(e) : Try.success(null))
          .onFailure(
              e -> {
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
    } else state.emptyExchangeCount.set(0);
  }

  @Builder
  @RequiredArgsConstructor
  static class SessionState {
    final HostProperties host;
    final Soht2Connection connection;
    final InputStream in;
    final OutputStream out;
    final AtomicInteger emptyExchangeCount = new AtomicInteger(0);
  }
}
