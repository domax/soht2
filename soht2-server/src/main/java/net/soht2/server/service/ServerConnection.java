/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.service;

import io.vavr.control.Try;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.soht2.common.dto.Soht2Connection;

/**
 * Represents a connection to a SOHT2 server, encapsulating the socket and streams for
 * communication. Implements {@link Closeable} to allow for resource management.
 */
@Slf4j
@Accessors(fluent = true)
@Value
public class ServerConnection implements Closeable {

  @Getter(AccessLevel.NONE)
  AtomicReference<Soht2Connection> soht2 = new AtomicReference<>();

  @Getter(AccessLevel.NONE)
  Socket socket;

  InputStream inputStream;
  OutputStream outputStream;
  Consumer<ServerConnection> postCloseAction;

  @Getter(AccessLevel.NONE)
  AtomicReference<LocalDateTime> lastActivity = new AtomicReference<>(LocalDateTime.now());

  @Getter(AccessLevel.NONE)
  AtomicBoolean isOpened = new AtomicBoolean();

  @Builder
  private ServerConnection(
      Soht2Connection soht2, int socketTimeout, Consumer<ServerConnection> postCloseAction) {
    log.debug("new: connection={}", soht2);
    this.soht2.set(soht2);
    this.socket =
        Try.of(() -> InetAddress.getByName(soht2.targetHost()))
            .mapTry(inet -> new Socket(inet, soht2.targetPort()))
            .andThenTry(s -> s.setSoTimeout(socketTimeout))
            .andThenTry(s -> s.setKeepAlive(true))
            .get();
    log.debug("new: socket={}", socket);
    this.inputStream = Try.of(socket::getInputStream).get();
    this.outputStream = Try.of(socket::getOutputStream).get();
    this.postCloseAction = postCloseAction;
    this.isOpened.set(true);
  }

  public Soht2Connection soht2() {
    return soht2.get();
  }

  public void soht2(Soht2Connection soht2) {
    log.debug("soht2: connection={}", soht2);
    this.soht2.set(soht2);
  }

  /**
   * Checks if the connection is currently opened.
   *
   * @return true if the connection is opened, false otherwise
   */
  public boolean isOpened() {
    return isOpened.get();
  }

  /** Updates the last activity timestamp to the current time. */
  public void lastActivity(LocalDateTime dateTime) {
    lastActivity.set(dateTime);
  }

  /**
   * Returns the duration since the last activity on this connection.
   *
   * @return the duration since the last activity
   */
  public Duration activityAge() {
    return Duration.between(lastActivity.get(), LocalDateTime.now());
  }

  /**
   * Returns the age of the connection, calculated as the duration between when the connection was
   * opened and the last activity.
   *
   * @return the duration since the connection was opened
   */
  public Duration connectionAge() {
    return Duration.between(soht2.get().openedAt(), lastActivity.get());
  }

  /** Closes the connection, releasing resources associated with the socket and streams. */
  @Override
  public void close() {
    log.debug("close: connection={}", soht2.get());
    this.isOpened.set(false);
    Try.run(inputStream::close);
    Try.run(outputStream::close);
    Try.run(socket::close);
    if (postCloseAction != null) postCloseAction.accept(this);
  }
}
