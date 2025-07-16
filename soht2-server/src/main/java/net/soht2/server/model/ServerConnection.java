/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.model;

import io.vavr.control.Try;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.soht2.common.dto.Soht2Connection;

@Slf4j
@Accessors(fluent = true)
@Value
public class ServerConnection implements Closeable {

  Soht2Connection soht2;

  @Getter(AccessLevel.NONE)
  Socket socket;

  InputStream inputStream;
  OutputStream outputStream;
  Consumer<ServerConnection> postCloseAction;
  AtomicBoolean isOpened = new AtomicBoolean();

  @Builder
  private ServerConnection(
      Soht2Connection soht2, int socketTimeout, Consumer<ServerConnection> postCloseAction) {
    this.soht2 = soht2;
    this.socket =
        Try.of(() -> InetAddress.getByName(soht2.targetHost()))
            .mapTry(inet -> new Socket(inet, soht2.targetPort()))
            .andThenTry(s -> s.setSoTimeout(socketTimeout))
            // .andThenTry(s -> s.setKeepAlive(true))
            .get();
    this.inputStream = Try.of(socket::getInputStream).get();
    this.outputStream = Try.of(socket::getOutputStream).get();
    this.postCloseAction = postCloseAction;
    this.isOpened.set(true);
  }

  public boolean isOpened() {
    return isOpened.get();
  }

  @Override
  public void close() {
    log.info("close: connection={}", soht2);
    this.isOpened.set(false);
    Try.run(inputStream::close);
    Try.run(outputStream::close);
    Try.run(socket::close);
    if (postCloseAction != null) postCloseAction.accept(this);
  }
}
