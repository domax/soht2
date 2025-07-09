package net.soht2.client.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.soht2.common.dto.SohtConnection;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Service
public class ConnectionClient {

  @SuppressWarnings("java:S1075")
  private static final String PATH_ID = "/{id}";

  private final WebClient webClient;

  public Mono<SohtConnection> open(String remoteHost, Integer remotePort) {
    log.debug("open: remoteHost={}, remotePort={}", remoteHost, remotePort);
    return webClient
        .post()
        .uri(b -> b.queryParam("host", remoteHost).queryParam("port", remotePort).build())
        .retrieve()
        .bodyToMono(SohtConnection.class)
        .doOnSuccess(connection -> log.info("open: connectionDto={}", connection))
        .doOnError(e -> log.atError().setMessage("open: {}").addArgument(e).setCause(e).log());
  }

  public Mono<Void> close(UUID connectionId) {
    log.debug("close: connectionId={}", connectionId);
    return webClient
        .delete()
        .uri(PATH_ID, connectionId)
        .retrieve()
        .toBodilessEntity()
        .doOnSuccess(response -> log.info("close: {}", response.getStatusCode()))
        .doOnError(e -> log.atError().setMessage("close: {}").addArgument(e).setCause(e).log())
        .then();
  }

  public Flux<DataBuffer> read(UUID connectionId) {
    log.debug("read: connectionId={}", connectionId);
    return webClient
        .get()
        .uri(PATH_ID, connectionId)
        .retrieve()
        .bodyToFlux(DataBuffer.class)
        .doOnNext(db -> log.debug("read: dataBuffer.size={}", db.readableByteCount()))
        .doOnError(e -> log.atError().setMessage("read: {}").addArgument(e).setCause(e).log());
  }

  public Mono<Void> write(UUID connectionId, Flux<DataBuffer> data) {
    log.debug("write: connectionId={}", connectionId);
    return webClient
        .post()
        .uri(PATH_ID, connectionId)
        .body(data, DataBuffer.class)
        .retrieve()
        .toBodilessEntity()
        .doOnSuccess(response -> log.debug("write: response={}", response))
        .doOnError(e -> log.atError().setMessage("write: {}").addArgument(e).setCause(e).log())
        .then();
  }

  public Flux<DataBuffer> exchange(UUID connectionId, Flux<DataBuffer> data) {
    log.debug("exchange: connectionId={}", connectionId);
    return webClient
        .put()
        .uri(PATH_ID, connectionId)
        .body(data, DataBuffer.class)
        .retrieve()
        .bodyToFlux(DataBuffer.class)
        .doOnNext(db -> log.debug("exchange: dataBuffer.size={}", db.readableByteCount()))
        .doOnError(e -> log.atError().setMessage("exchange: {}").addArgument(e).setCause(e).log());
  }
}
