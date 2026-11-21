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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
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
  AtomicReference<Soht2Connection> soht2;

  @Getter(AccessLevel.NONE)
  Socket socket;

  InputStream inputStream;
  OutputStream outputStream;
  byte[] readBuffer;
  Consumer<ServerConnection> postCloseAction;

  /** Serializes writes to the target: duplex uploads and the write half of simple exchanges. */
  ReentrantLock writeLock = new ReentrantLock();

  /** Serializes reads from the target: duplex downloads and the read half of simple exchanges. */
  ReentrantLock readLock = new ReentrantLock();

  /** The sequence number of the last applied duplex upload request; {@code 0} before the first. */
  AtomicLong lastUploadSeq = new AtomicLong(0);

  /**
   * The sequence number of the last answered duplex download request; {@code 0} before the first.
   */
  AtomicLong lastDownloadSeq = new AtomicLong(0);

  /** The result produced for {@link #lastDownloadSeq}, replayed when that request is retried. */
  AtomicReference<DownloadResult> lastDownloadResult = new AtomicReference<>(DownloadResult.EMPTY);

  /** Whether the target has closed its side of the connection (end-of-stream was read). */
  AtomicBoolean targetEof = new AtomicBoolean();

  @Getter(AccessLevel.NONE)
  AtomicReference<LocalDateTime> lastActivity =
      new AtomicReference<>(LocalDateTime.now(ZoneId.systemDefault()));

  @Getter(AccessLevel.NONE)
  AtomicBoolean isOpened = new AtomicBoolean();

  @Getter(AccessLevel.NONE)
  AtomicLong bytesRead = new AtomicLong(0);

  @Getter(AccessLevel.NONE)
  AtomicLong bytesWritten = new AtomicLong(0);

  @Builder
  private ServerConnection(
      Soht2Connection soht2,
      int socketTimeout,
      int readBufferSize,
      Consumer<ServerConnection> postCloseAction) {
    log.debug("new: connection={}", soht2);
    this.soht2 = new AtomicReference<>(soht2);
    socket =
        Try.of(() -> InetAddress.getByName(soht2.targetHost()))
            .mapTry(inet -> new Socket(inet, soht2.targetPort()))
            .andThenTry(s -> s.setSoTimeout(socketTimeout))
            .andThenTry(s -> s.setKeepAlive(true))
            .get();
    log.debug("new: socket={}", socket);
    inputStream = Try.of(socket::getInputStream).get();
    outputStream = Try.of(socket::getOutputStream).get();
    readBuffer = new byte[readBufferSize];
    this.postCloseAction = postCloseAction;
    isOpened.set(true);
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
    return Duration.between(
        lastActivity.get().atZone(ZoneId.systemDefault()),
        ZonedDateTime.now(ZoneId.systemDefault()));
  }

  /**
   * Returns the age of the connection, calculated as the duration between when the connection was
   * opened and the last activity.
   *
   * @return the duration since the connection was opened
   */
  public Duration connectionAge() {
    return Duration.between(
        soht2.get().openedAt().atZone(ZoneId.systemDefault()),
        lastActivity.get().atZone(ZoneId.systemDefault()));
  }

  /**
   * Adds the specified number of bytes to the total number of bytes read from the connection.
   *
   * @param bytes the number of bytes to add
   */
  public void addBytesRead(long bytes) {
    bytesRead.getAndAdd(bytes);
  }

  /**
   * Adds the specified number of bytes to the total number of bytes written to the connection.
   *
   * @param bytes the number of bytes to add
   */
  public void addBytesWritten(long bytes) {
    bytesWritten.getAndAdd(bytes);
  }

  /** Returns the total number of bytes read from the connection. */
  public long bytesRead() {
    return bytesRead.get();
  }

  /** Returns the total number of bytes written to the connection. */
  public long bytesWritten() {
    return bytesWritten.get();
  }

  /**
   * Acquires both locks in a fixed order (write, then read), so a simple exchange never interleaves
   * with a duplex request on the same connection.
   */
  public void lockBoth() {
    writeLock.lock();
    Try.run(readLock::lock)
        .recoverWith(RuntimeException.class, e -> Try.run(writeLock::unlock).orElse(Try.failure(e)))
        .get();
  }

  /** Releases the locks acquired by {@link #lockBoth()} that are held by the current thread. */
  public void unlockBoth() {
    unlock(readLock);
    unlock(writeLock);
  }

  /**
   * Releases the given lock if it is held by the current thread.
   *
   * @param lock the lock to release
   */
  public static void unlock(ReentrantLock lock) {
    if (lock.isHeldByCurrentThread()) lock.unlock();
  }

  /**
   * Closes the connection, releasing resources associated with the socket and streams. Closing an
   * already closed connection has no effect. Threads blocked in a read or write on the target
   * socket are released with an exception and keep responsibility for unlocking their own locks.
   */
  @Override
  public void close() {
    if (!isOpened.compareAndSet(true, false)) return;
    log.debug("close: connection={}", soht2.get());
    Try.run(inputStream::close);
    Try.run(outputStream::close);
    Try.run(socket::close);
    postCloseAction.accept(this);
  }
}
