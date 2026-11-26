/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.test;

import static java.util.concurrent.CompletableFuture.runAsync;

import io.vavr.control.Try;
import java.io.*;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@Accessors(fluent = true)
public class EchoServer implements Closeable, Runnable {

  @Getter private final int portNumber;
  @Getter private final int socketTimeout;
  @Getter private final int bufferSize;

  private final AtomicBoolean isRunning;
  private final AtomicBoolean isStopped;
  private final ServerSocket serverSocket;
  private final CompletableFuture<Void> future;

  @Builder
  @SneakyThrows
  private EchoServer(int portNumber, int socketTimeout, int bufferSize) {
    this.portNumber = portNumber;
    this.socketTimeout = socketTimeout;
    this.bufferSize = bufferSize;
    isRunning = new AtomicBoolean();
    isStopped = new AtomicBoolean();
    // bound before returning, so a connection attempt right after the build is never refused
    serverSocket = new ServerSocket(portNumber);
    future = runAsync(this);
    log.info("EchoServer: portNumber={}", portNumber);
  }

  @SneakyThrows
  @Override
  public void run() {
    try (serverSocket;
        val clientSocket = serverSocket.accept();
        val out = clientSocket.getOutputStream();
        val in = clientSocket.getInputStream()) {
      clientSocket.setSoTimeout(socketTimeout);
      isRunning.set(true);
      val buffer = new byte[bufferSize];
      final IntSupplier readBuffer =
          () -> Try.of(() -> in.read(buffer)).recover(SocketTimeoutException.class, 0).get();
      while (!isStopped.get()) {
        int bufferLen;
        while ((bufferLen = readBuffer.getAsInt()) > 0) {
          log.info("EchoServer <->: {}", bufferLen);
          out.write(buffer, 0, bufferLen);
          out.flush();
        }
        if (bufferLen < 0) break;
      }
    } finally {
      isRunning.set(false);
    }
  }

  public boolean isRunning() {
    return isRunning.get();
  }

  @Override
  public void close() {
    isStopped.set(true);
    // releases a pending accept when no client has connected
    Try.run(serverSocket::close);
    Try.run(future::join);
    log.info("EchoServer: stopped");
  }
}
