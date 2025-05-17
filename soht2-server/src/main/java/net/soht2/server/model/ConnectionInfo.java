/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.model;

import io.vavr.control.Try;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import net.soht2.common.dto.ConnectionDto;

@Accessors(fluent = true)
@Value
public class ConnectionInfo implements Closeable {
  ConnectionDto connection;
  Socket socket;
  InputStream inputStream;
  OutputStream outputStream;

  @Builder
  private ConnectionInfo(ConnectionDto connection) {
    this.connection = connection;
    this.socket =
        Try.of(() -> InetAddress.getByName(connection.targetHost()))
            .mapTry(inet -> new Socket(inet, connection.targetPort()))
            .get();
    this.inputStream = Try.of(socket::getInputStream).get();
    this.outputStream = Try.of(socket::getOutputStream).get();
  }

  @Override
  public void close() {
    Try.run(inputStream::close);
    Try.run(outputStream::close);
    Try.run(socket::close);
  }
}
