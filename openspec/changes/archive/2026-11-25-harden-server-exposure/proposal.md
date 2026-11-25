# Proposal

## Why

The server exposes every Spring Boot actuator endpoint (including `heapdump`, `env`, and the
writable `loggers`) to anonymous callers, because only `/api/**` requires authentication. A heap
dump leaks the H2 password (which defaults to the admin password), credential hashes, and cached
session data. Several smaller gaps compound this. Anyone can request stack traces and exception
class names. The admin UI can be framed by any site. The actuator's wildcard CORS policy applies
to the whole application. Tunnel request bodies are unbounded both before and after
decompression, so one authenticated user can exhaust server memory.

## What Changes

- **BREAKING (operations):** Only the actuator `health` endpoint is exposed by default, and it
  reports status only. Any other actuator endpoint an operator chooses to expose requires the
  `ADMIN` role.
- Error responses no longer include stack traces (even with `?trace=true`) or exception class
  names. Human-readable error messages stay, because the UI displays them.
- Responses carry `X-Frame-Options: DENY` (the Spring Security default) instead of disabling
  frame protection.
- CORS is off by default. A new `soht2.server.cors.allowed-origins` list enables it for named
  origins (for example, a Vite dev server). CORS is no longer derived from the actuator settings.
- Tunnel upload and exchange bodies are limited by a new `soht2.server.max-request-size`
  property (default `4MB`). The limit applies both to the received body and to its decompressed
  size. Oversized requests get HTTP 413 and the connection stays open.

Assumptions:

- No shipped component (UI, client, `requests.http`) uses actuator endpoints. A repository search
  found no references.
- The UI is normally served same-origin by the server. Cross-origin use is limited to local UI
  development with `VITE_APP_API_ORIGIN`.
- The default `4MB` limit stays above the client's default `read-buffer-size` (`1MB`) plus any
  compression overhead.

Out of scope:

- Separating the database password from the default admin password.
- Moving actuator to a separate management port.
- Changing authentication or roles, or adding rate limiting.

## Capabilities

### New Capabilities

- `server-exposure`: Which server endpoints, response headers, error details, and cross-origin
  access are available to unauthenticated or untrusted callers.

### Modified Capabilities

- `tunnel-transport`: Tunnel request payloads get an enforced size limit, before and after
  decompression, with a defined rejection behavior.

## Impact

- **Server:** `SecurityConfig` (authorization rules, frame options, CORS source),
  `application.yaml` (`management.*`, `spring.web.error.*`, the new `soht2.server.*` properties),
  `Soht2ServerConfig`, the tunnel upload and exchange paths, and `ExceptionHelper`.
- **Common:** the compressor gets a bounded decompression operation, used by the server.
- **Docs:** the server config block in `README.md`, plus `doc/tips-server.md` notes on exposing
  actuator endpoints and CORS for UI development.
- **Compatibility:** Clients with `read-buffer-size` above `max-request-size` get HTTP 413 on
  large uploads. The fix is documented.
