/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.controller;

import static java.util.Optional.ofNullable;
import static net.soht2.server.service.ExceptionHelper.badRequest;
import static org.springframework.http.MediaType.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.soht2.common.dto.SohtConnection;
import net.soht2.server.model.ConnectionInfo;
import net.soht2.server.service.ConnectionService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/connection")
public class ConnectionController {

  private static final String PATH_ID = "/{id}";

  private final ConnectionService connectionService;

  @SuppressWarnings("resource")
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public Mono<SohtConnection> open(
      @RequestParam("host") String targetHost,
      @RequestParam("port") Integer targetPort,
      ServerHttpRequest request) {
    return Mono.justOrEmpty(
            ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress))
        .switchIfEmpty(Mono.error(() -> badRequest("No client host found")))
        .map(
            clientHost ->
                connectionService
                    .open(
                        SohtConnection.builder()
                            .username("system")
                            .clientHost(clientHost)
                            .targetHost(targetHost)
                            .targetPort(targetPort)
                            .build())
                    .connection());
  }

  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public Mono<Collection<SohtConnection>> list() {
    return Mono.fromSupplier(connectionService::list);
  }

  @GetMapping(path = PATH_ID, produces = APPLICATION_OCTET_STREAM_VALUE)
  public Flux<DataBuffer> read(@PathVariable("id") UUID connectionId, ServerHttpResponse response) {
    return connectionService.read(connectionId, response.bufferFactory(), true);
  }

  @PostMapping(path = PATH_ID, consumes = APPLICATION_OCTET_STREAM_VALUE)
  public Mono<Void> write(
      @PathVariable("id") UUID connectionId, @RequestBody Flux<DataBuffer> data) {
    return connectionService.write(connectionId, data);
  }

  @PutMapping(
      path = PATH_ID,
      produces = APPLICATION_OCTET_STREAM_VALUE,
      consumes = APPLICATION_OCTET_STREAM_VALUE)
  public Flux<DataBuffer> exchange(
      @PathVariable("id") UUID connectionId,
      @RequestBody Flux<DataBuffer> data,
      ServerHttpResponse response) {
    return connectionService
        .write(connectionId, data)
        .thenMany(connectionService.read(connectionId, response.bufferFactory(), true));
  }

  @DeleteMapping(path = PATH_ID)
  public Mono<Void> close(@PathVariable("id") UUID connectionId) {
    return Mono.fromRunnable(() -> connectionService.close(connectionId));
  }
}
