/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.controller;

import static org.springframework.http.MediaType.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.soht2.common.dto.Soht2Connection;
import net.soht2.server.service.Soht2Service;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/connection")
public class ConnectionController {

  private static final String PATH_ID = "/{id}";

  private final Soht2Service soht2Service;

  @SuppressWarnings("resource")
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public Soht2Connection open(
      @RequestParam("host") String targetHost,
      @RequestParam("port") Integer targetPort,
      HttpServletRequest request) {
    return soht2Service
        .open(
            Soht2Connection.builder()
                .username("system")
                .clientHost(request.getRemoteHost())
                .targetHost(targetHost)
                .targetPort(targetPort)
                .build())
        .soht2();
  }

  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public Collection<Soht2Connection> list() {
    return soht2Service.list();
  }

  @PostMapping(
      path = PATH_ID,
      produces = APPLICATION_OCTET_STREAM_VALUE,
      consumes = APPLICATION_OCTET_STREAM_VALUE)
  public byte[] exchange(
      @PathVariable("id") UUID connectionId, @RequestBody(required = false) byte[] data) {
    return soht2Service.exchange(connectionId, data).get();
  }

  @DeleteMapping(path = PATH_ID)
  public void close(@PathVariable("id") UUID connectionId) {
    soht2Service.close(connectionId);
  }
}
