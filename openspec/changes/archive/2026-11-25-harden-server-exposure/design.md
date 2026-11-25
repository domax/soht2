<!--suppress MarkdownUnresolvedLinkLabel -->
# Design

## Context

- `SecurityConfig` authorizes `/api/**` as authenticated and everything else with `permitAll()`.
  `application.yaml` sets `management.endpoints.web.exposure.include: '*'`, so every actuator
  endpoint is anonymous. The default `spring.datasource.password` is
  `${soht2.server.default-admin-password}`.
- `spring.web.error` uses `include-message: always`, `include-stacktrace: on_param`, and
  `include-exception: true`. The UI's `ErrorAlert` shows `detail.message` and
  `errors[0].defaultMessage`, so `message` must stay.
- `SecurityConfig.corsConfigurationSource` registers the actuator's `CorsEndpointProperties`
  (configured with `allowed-origins: '*'`) for `/**`. `ConnectionControllerTest` enables
  `CorsEndpointProperties` for that reason.
- `ConnectionController.exchange` and `upload` bind `@RequestBody byte[]`, which reads the whole
  body. `Soht2Service` then calls `compressorCache.apply(encoding).decompress(data)`, which runs
  `InputStream.readAllBytes()` with no limit (`BaseIOStreamCompressor`).

## Goals / Non-Goals

**Goals:**

- Close anonymous access to diagnostics with configuration plus one security rule, with no UI
  impact.
- Bound tunnel memory per request in both the received and decompressed forms.

**Non-Goals:**

- A separate management port, rate limiting, or a new authentication scheme.
- Limits on non-tunnel endpoints (the user API bodies are small JSON).

## Decisions

1. **Actuator exposure.** Set `management.endpoints.web.exposure.include: health` and
   `management.endpoint.health.show-details: never`. In `SecurityConfig`, add
   `requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()`, then
   `requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole(ADMIN)` before the existing rules.
   - Defense in depth: an operator who widens exposure still gets admin-only access.
   - Alternative: disable actuator entirely. Rejected because health probes are useful behind
     load balancers.
   - `EndpointRequest` matchers respect a custom `management.endpoints.web.base-path`.
2. **Error details.** Set `include-stacktrace: never` and `include-exception: false`. Keep
   `include-message: always`, because tunnel and user-API messages are authored by the
   application (`ExceptionHelper`) and the UI displays them.
3. **Frame options.** Remove `.headers(h -> h.frameOptions(...disable))` so the Spring Security
   default (`DENY`) applies. The UI and Swagger UI use no frames. A repository search found no
   `iframe` or frame usage in `soht2-ui/src`.
4. **CORS.** Add `Soht2ServerConfig.Cors` with `List<String> allowedOrigins` (empty by default).
   - When the list is non-empty, register a `CorsConfiguration` for `/**` with those origins.
   - Methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`.
   - Allowed headers: `Authorization`, `Content-Type`, `Content-Encoding`, `X-XSRF-TOKEN`,
     `X-SOHT2-Client`.
   - No exposed headers, because only the browser UI uses CORS and it does not call the tunnel
     endpoints.
   - `allowCredentials` stays `false`.
   - When the list is empty, the source returns no configuration.
   - Remove the `management.endpoints.web.cors` block and the `CorsEndpointProperties`
     dependency.
5. **Request-size limit.** Add `soht2.server.max-request-size` (`DataSize`, default `4MB`), which
   must be positive.
   - **Received bytes:** a `OncePerRequestFilter` registered for `POST /api/connection/*` and
     `POST /api/connection/*/upload` does two things:
     - If `Content-Length` is larger than the limit, it responds 413 before the body is read.
     - Otherwise it wraps the request so reading more than the limit throws
       `PayloadTooLargeException`, which is mapped to 413. This covers chunked bodies.
   - The filter runs after Spring Security, so unauthenticated callers still get 401.
   - Alternative: Tomcat `maxPostSize`. Rejected because it applies only to form posts.
6. **Bounded decompression.** Add `byte[] decompress(byte[] data, long maxBytes)` to `Compressor`.
   - The default implementation calls `decompress` and then checks the length. Identity uses it.
   - `BaseIOStreamCompressor` overrides it to read in chunks into a growing buffer and throw
     `DecompressedSizeExceededException` (in common, unchecked) once `maxBytes` is passed.
   - `Soht2Service.applyUpload` and `exchange` call the bounded variant and translate the
     exception into `ExceptionHelper.contentTooLarge(...)` (new, HTTP 413).
   - The size check happens before the write and before `lastUploadSeq` is set, so the sequence
     is not advanced and the connection stays open.
   - The simple `exchange` maps the exception to 413 instead of swallowing it. Today the
     exception is not a `SocketException`, so it would surface as a 500.

## Risks / Trade-offs

- [Operators relying on actuator endpoints such as `metrics` or `prometheus`] → They must add the
  endpoints to `exposure.include` and call them with admin credentials. This is called out in the
  README as a breaking operational change.
- [UI development against a remote server breaks without CORS] → Document
  `soht2.server.cors.allowed-origins: [http://localhost:5173]` in `doc/tips-server.md`.
- [A client `read-buffer-size` larger than the server limit causes 413 on bulk uploads] →
  Document the relationship in the README next to both properties. The client treats 413 as a
  failed request and closes the session, which is visible in client logs.
- [`include-message: always` can still echo framework exception messages on 500] → Accepted
  because the UI depends on messages. Stack traces and class names, the high-value leaks, are
  removed.

## Migration Plan

- Deploy the server. No database migration is needed. Rollback means restoring the previous JAR
  or `application-server.yaml` overrides.
- Operators who need extra actuator endpoints set
  `management.endpoints.web.exposure.include` and use an `ADMIN` account.
