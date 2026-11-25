# Tasks

## 1. Actuator and error exposure

- [x] 1.1 In `soht2-server/src/main/resources/application.yaml`, set
  `management.endpoints.web.exposure.include: health` and
  `management.endpoint.health.show-details: never`, and remove the `management.endpoints.web.cors`
  block. Verify the YAML parses with `./gradlew :soht2-server:processResources`.
- [x] 1.2 In `SecurityConfig`, add `EndpointRequest.to(HealthEndpoint.class)` → `permitAll()` and
  `EndpointRequest.toAnyEndpoint()` → `hasRole(ROLE_ADMIN)` ahead of the existing matchers. Add
  security tests:
  - Anonymous `/actuator/health` gets 200 with only `status`.
  - `/actuator/heapdump` is not served by default.
  - With `env` exposed, anonymous gets 401, `USER` gets 403, and `ADMIN` gets 200.

  Verify `./gradlew :soht2-server:test` passes.
- [x] 1.3 Set `spring.web.error.include-stacktrace: never` and `include-exception: false`, keeping
  `include-message: always`. Add a test where a failing request with `?trace=true` returns no
  `trace` and no `exception` field but keeps `message`. Verify the server tests pass.

## 2. Frame options and CORS

- [x] 2.1 Remove the `frameOptions(...disable)` customization from `SecurityConfig`. Add a test
  asserting `X-Frame-Options: DENY` on `GET /`. Verify the server tests pass.
- [x] 2.2 Add `soht2.server.cors.allowed-origins` (a `List<String>`, default empty) to
  `Soht2ServerConfig`. Rewrite `corsConfigurationSource` per design decision 4, drop the
  `CorsEndpointProperties` dependency, and update `ConnectionControllerTest` to stop enabling
  `CorsEndpointProperties`. Add tests for the default (no ACAO header), a listed origin (echoed),
  and an unlisted origin (no ACAO header). Verify the server tests pass.

## 3. Bounded tunnel payloads

- [x] 3.1 In `soht2-common`, add `Compressor.decompress(byte[] data, long maxBytes)`, with a
  default implementation that checks the length after decompressing. Add the chunked override in
  `BaseIOStreamCompressor` that throws `DecompressedSizeExceededException` once `maxBytes` is
  exceeded. Extend `CompressorTest` for GZIP, DEFLATE, and identity within and above the limit,
  including a highly compressible 100 MB-equivalent input that fails without allocating its full
  size. Verify with `./gradlew :soht2-common:test`.
- [x] 3.2 Add `soht2.server.max-request-size` (`DataSize`, default `4MB`) to `Soht2ServerConfig`,
  with a positive-value assertion in `afterPropertiesSet`. Add `ExceptionHelper.contentTooLarge`
  (HTTP 413). Extend `Soht2ServerConfigTest` so `0B` fails startup with the property name. Verify
  the server tests pass.
- [x] 3.3 Add the request-size filter for `POST /api/connection/{id}` and
  `POST /api/connection/{id}/upload`, ordered after the security filter. It rejects 413 on an
  oversized `Content-Length` and wraps the stream to cap chunked bodies. Add MockMvc tests that
  cover:
  - An oversized upload gets 413 and nothing is written.
  - A within-limit body succeeds.
  - Unauthenticated oversized requests still get 401.

  Verify the server tests pass.
- [x] 3.4 Switch `Soht2Service.applyUpload` and `exchange` to the bounded decompress and map the
  exception to `payloadTooLarge`. Do it before writing and before setting `lastUploadSeq`. Add
  `Soht2ServiceTest` cases:
  - A compression bomb gets 413, the target receives nothing, and the connection stays open.
  - A retry with the same sequence number and a valid body is written and acknowledged.

  Verify the server tests pass.

## 4. Documentation

- [x] 4.1 Update the `README.md` server config block with `max-request-size` and
  `cors.allowed-origins`. Add a note that the client's `read-buffer-size` must stay below the
  server's `max-request-size`, and a breaking-change note that only `health` is exposed and other
  actuator endpoints need `ADMIN`. Add a `doc/tips-server.md` section on exposing actuator
  endpoints and enabling CORS for UI development. Verify the documented YAML matches the
  `@ConfigurationProperties` names.

## 5. Integration check

- [x] 5.1 Run `./gradlew spotlessCheck build`. Then start the server with `bootRun` and check
  manually:
  - `curl /actuator/health` returns `{"status":"UP"}`.
  - `curl /actuator/heapdump` is not 200.
  - A UI login and a user edit still work in the browser.
  - A client tunnel (`telnet localhost <port>`) still relays data.
