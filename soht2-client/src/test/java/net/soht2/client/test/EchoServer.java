/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.client.test;

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

@SuppressWarnings("unused")
@Slf4j
@Accessors(fluent = true)
public class EchoServer implements Closeable, Runnable {

  @Getter private final int portNumber;
  @Getter private final int socketTimeout;
  @Getter private final int bufferSize;

  private final AtomicBoolean isRunning;
  private final CompletableFuture<Void> future;

  @Builder
  @SneakyThrows
  private EchoServer(int portNumber, int socketTimeout, int bufferSize) {
    this.portNumber = portNumber;
    this.socketTimeout = socketTimeout;
    this.bufferSize = bufferSize;
    isRunning = new AtomicBoolean();
    future = runAsync(this);
    log.info("EchoServer: portNumber={}", portNumber);
  }

  @SneakyThrows
  @Override
  public void run() {
    try (val serverSocket = new ServerSocket(portNumber);
        val clientSocket = serverSocket.accept();
        val out = clientSocket.getOutputStream();
        val in = clientSocket.getInputStream()) {
      clientSocket.setSoTimeout(socketTimeout);
      isRunning.set(true);
      while (isRunning.get()) {
        val buffer = new byte[bufferSize];
        final IntSupplier readBuffer =
            () -> Try.of(() -> in.read(buffer)).recover(SocketTimeoutException.class, 0).get();
        int bufferLen;
        while ((bufferLen = readBuffer.getAsInt()) > 0) {
          log.info("EchoServer <->: {}", bufferLen);
          out.write(buffer, 0, bufferLen);
          out.flush();
        }
      }
    }
  }

  public boolean isRunning() {
    return isRunning.get();
  }

  @SneakyThrows
  @Override
  public void close() {
    isRunning.set(false);
    if (!future.isDone()) future.join();
    log.info("EchoServer: stopped");
  }
}
