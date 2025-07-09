package net.soht2.server.test;

import io.vavr.control.Try;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class EchoClient implements Closeable {

  private final int bufferSize;
  private final Socket socket;
  private final InputStream in;
  private final OutputStream out;

  public EchoClient(EchoServer echoServer) {
    this(echoServer.portNumber(), echoServer.socketTimeout(), echoServer.bufferSize());
  }

  @SneakyThrows
  @Builder
  private EchoClient(int portNumber, int socketTimeout, int bufferSize) {
    this.bufferSize = bufferSize;
    socket = new Socket("localhost", portNumber);
    socket.setSoTimeout(socketTimeout);
    in = new BufferedInputStream(socket.getInputStream(), bufferSize);
    out = new BufferedOutputStream(socket.getOutputStream(), bufferSize);
    log.info("EchoClient: portNumber={}", portNumber);
  }

  @SneakyThrows
  public byte[] shout(byte[] binData) {
    log.info("shout: original.length={}", binData.length);
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
  }

  @SneakyThrows
  @Override
  public void close() {
    if (!socket.isClosed()) {
      Try.run(in::close);
      Try.run(out::close);
      socket.close();
      log.info("EchoClient: closed");
    } else log.warn("EchoClient is already closed");
  }
}
