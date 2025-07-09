package net.soht2.server.service;

import static java.util.concurrent.CompletableFuture.runAsync;
import static org.assertj.core.api.Assertions.assertThat;

import io.vavr.Tuple;
import io.vavr.control.Try;
import java.io.*;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.common.dto.SohtConnection;
import net.soht2.server.config.ConnectorConfig;
import net.soht2.server.test.EchoServer;
import net.soht2.server.test.UTHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
@SpringBootTest(classes = {ConnectorConfig.class, ConnectionService.class})
@ActiveProfiles("test")
class ConnectionServiceTest {

  static final int PORT_NUMBER = 12345;
  static final int DATA_SIZE = 120000;

  @Autowired ConnectionService connectionService;
  @Autowired ConnectorConfig connectorConfig;

  int socketTimeout;
  int bufferSize;
  DataBufferFactory dataBufferFactory;

  @BeforeEach
  void beforeEach() {
    socketTimeout = (int) connectorConfig.getSocketTimeout().toMillis();
    bufferSize = (int) connectorConfig.getReadBufferSize().toBytes();
    dataBufferFactory = DefaultDataBufferFactory.sharedInstance;
  }

  @Test
  void testEchoClassic() {
    assertThat(
            UTHelper.echo(
                Stream.of(UTHelper.createBinData(DATA_SIZE), UTHelper.createBinData(DATA_SIZE)),
                PORT_NUMBER,
                socketTimeout,
                bufferSize))
        .allSatisfy(data -> assertThat(data.echoed()).isEqualTo(data.original()));
  }

  @SneakyThrows
  Mono<byte[]> echoClient(UUID connectionId, byte[] binData) {
    val result = new ByteArrayOutputStream(binData.length);
    val fluxIn =
        DataBufferUtils.readInputStream(
                () -> new ByteArrayInputStream(binData), dataBufferFactory, bufferSize)
            .doOnNext(db -> log.info("echoClient -->: {}", db.readableByteCount()));

    return connectionService
        .write(connectionId, fluxIn)
        .thenMany(connectionService.read(connectionId, dataBufferFactory, true))

//    runAsync(() -> connectionService.write(connectionId, fluxIn).subscribe());
//    return connectionService
//        .read(connectionId, dataBufferFactory, true)
        .map(
            db ->
                Try.withResources(() -> db.asInputStream(true)).of(InputStream::readAllBytes).get())
        .doOnNext(bytes -> log.info("echoClient <--: {}", bytes.length))
        .doOnNext(bytes -> Try.run(() -> result.write(bytes)).get())
        .count()
        .map(v -> result.toByteArray());
  }

  @SuppressWarnings("resource")
  @Test
  void testEcho() {
    try (val ignored =
        EchoServer.builder()
            .portNumber(PORT_NUMBER)
            .socketTimeout(socketTimeout)
            .bufferSize(bufferSize)
            .build()) {
      val connection =
          connectionService
              .open(
                  SohtConnection.builder()
                      .username("system")
                      .clientHost("localhost")
                      .targetHost("localhost")
                      .targetPort(PORT_NUMBER)
                      .build())
              .connection();

      Flux.fromStream(
              Stream.of(UTHelper.createBinData(DATA_SIZE), UTHelper.createBinData(DATA_SIZE)))
          .delayElements(Duration.ofMillis(500))
          .flatMap(
              binData ->
                  echoClient(connection.id(), binData).map(result -> Tuple.of(binData, result)))
          .as(StepVerifier::create)
          .assertNext(tuple -> assertThat(tuple._2).isEqualTo(tuple._1))
          .assertNext(tuple -> assertThat(tuple._2).isEqualTo(tuple._1))
          .verifyComplete();

      connectionService.close(connection.id());
    }

    /*final AtomicReference<byte[]> binDataRef = new AtomicReference<>();
    try {
      log.info("testEcho: pass 1");
      binDataRef.set(UTHelper.createBinData(DATA_SIZE));
      echoClient(connection.id(), binDataRef.get())
          .as(StepVerifier::create)
          .assertNext(data -> assertThat(data).isEqualTo(binDataRef.get()))
          .verifyComplete();
      Thread.sleep(1000);

      log.info("testEcho: pass 2");
      binDataRef.set(UTHelper.createBinData(DATA_SIZE));
      echoClient(connection.id(), binDataRef.get())
          .as(StepVerifier::create)
          .assertNext(data -> assertThat(data).isEqualTo(binDataRef.get()))
          .verifyComplete();
      isRunning.set(false);
    } finally {
      connectionService.close(connection.id());
    }*/
  }
}
