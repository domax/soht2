package net.soht2.client.service;

import static java.util.Optional.ofNullable;

import io.vavr.control.Try;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.soht2.client.config.ConnectionConfig;
import net.soht2.common.dto.SohtConnection;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Service
public class ConnectionRunner implements CommandLineRunner {

  private final ConnectionConfig connectionConfig;
  private final ConnectionClient connectionClient;

  @Override
  public void run(String... args) {
    Try.run(
            CompletableFuture.allOf(
                    connectionConfig.getConnections().stream()
                        .map(
                            host ->
                                CompletableFuture.runAsync(
                                    () ->
                                        connect(
                                            host.getLocalPort(),
                                            host.getRemoteHost(),
                                            host.getRemotePort())))
                        .toArray(CompletableFuture[]::new))
                ::get)
        .onSuccess(v -> log.info("run: terminated"))
        .onFailure(e -> log.atError().setMessage("run: {}").addArgument(e).setCause(e).log())
        .get();

    /*connectionConfig
        .getConnections()
        .forEach(
            host ->
                CompletableFuture.runAsync(
                    () -> connect(host.getLocalPort(), host.getRemoteHost(), host.getRemotePort()),
                    threadExecutor));
    Try.of(() -> threadExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS))
        .onSuccess(terminated -> log.info("run: terminated={}", terminated))
        .onFailure(e -> log.atError().setMessage("run: {}").addArgument(e).setCause(e).log());*/
  }

  void connect(int localPort, String remoteHost, int remotePort) {
    log.info(
        "connect: localPort={}, remoteHost={}, remotePort={}, mode={}",
        localPort,
        remoteHost,
        remotePort,
        connectionConfig.getMode());
    Try.withResources(() -> new ServerSocket(localPort))
        .of(
            serverSocket -> {
              //serverSocket.setSoTimeout((int) connectionConfig.getSocketTimeout().toMillis());
              while (!serverSocket.isClosed()) {
                val connectionRef = new AtomicReference<SohtConnection>();
                Try.withResources(serverSocket::accept)
                    .of(
                        socket ->
                            connectionClient
                                .open(remoteHost, remotePort)
                                .doOnNext(connectionRef::set)
                                .map(SohtConnection::id)
                                .doOnNext(id -> exchange(id, socket))
                                /*.flatMap(
                                    id ->
                                        connectionConfig.getMode() == STATELESS
                                            ? exchangeStateless(id, socket)
                                            : exchangeStateful(id, socket))*/
                                .doOnError(
                                    WebClientResponseException.Gone.class,
                                    e -> Try.run(serverSocket::close))
                                .block())
                    .andFinallyTry(
                        () ->
                            ofNullable(connectionRef.get())
                                .map(SohtConnection::id)
                                .map(connectionClient::close)
                                .ifPresent(Mono::block))
                    .get();
              }
              return null;
            })
        .onFailure(e -> log.atError().setMessage("connect: {}").addArgument(e).setCause(e).log())
        .get();
  }

  /*Mono<Void> read(UUID connectionId, Socket socket) {
    return Try.withResources(socket::getOutputStream)
        .of(
            os ->
                DataBufferUtils.write(connectionClient.read(connectionId), os)
                    .doOnNext(DataBufferUtils::release))
        .get()
        .count()
        .doOnSuccess(v -> log.debug("read: buffers.count={}", v))
        .then();
  }

  Mono<Void> write(UUID connectionId, Socket socket) {
    return connectionClient.write(
        connectionId,
        DataBufferUtils.readInputStream(
            socket::getInputStream,
            DefaultDataBufferFactory.sharedInstance,
            (int) connectionConfig.getReadBufferSize().toBytes()));
  }*/

  void exchange(UUID connectionId, Socket socket) {
    Try.run(() -> socket.setSoTimeout((int) connectionConfig.getSocketTimeout().toMillis()));
    while (!socket.isClosed()) {
      doExchange(connectionId, socket)
          .doOnError(WebClientResponseException.Gone.class, e -> Try.run(socket::close))
          .block();
    }
  }

  Mono<Void> doExchange(UUID connectionId, Socket socket) {
    return Try.withResources(socket::getOutputStream)
        .of(
            os ->
                DataBufferUtils.write(
                        connectionClient.exchange(
                            connectionId,
                            DataBufferUtils.readInputStream(
                                    socket::getInputStream,
                                    DefaultDataBufferFactory.sharedInstance,
                                    (int) connectionConfig.getReadBufferSize().toBytes())
                                .onErrorResume(SocketTimeoutException.class, e -> Flux.empty())),
                        os)
                    .doOnNext(DataBufferUtils::release))
        .getOrElseGet(Flux::error)
        .count()
        .doOnSuccess(v -> log.debug("exchange: buffers.count={}", v))
        .doOnError(e -> log.atError().setMessage("exchange: {}").addArgument(e).setCause(e).log())
        .then();
  }

  /*Mono<Void> exchangeStateless(UUID connectionId, Socket socket) {
    return Mono.fromFuture(
        CompletableFuture.runAsync(
            () -> {
              val isRunning = new AtomicBoolean(true);
              while (isRunning.get()) {
                exchange(connectionId, socket)
                    .doOnError(WebClientResponseException.Gone.class, e -> isRunning.set(false))
                    .block();
              }
            }));
  }*/

  /*Mono<Void> exchangeStateful(UUID connectionId, Socket socket) {
    return Mono.fromFuture(
        CompletableFuture.allOf(
            CompletableFuture.runAsync(() -> read(connectionId, socket).block(), threadExecutor),
            CompletableFuture.runAsync(() -> write(connectionId, socket).block(), threadExecutor)));
  }*/
}
