# Proposal

## Why

Tunnel throughput and latency are limited by avoidable overhead rather than by the network:
every exchange/poll request pays a full password-hash verification on the server, multiple
tunneled connections on the same local port are serialized by a shared client lock, the client
listener drops connections queued while a tunnel is being opened, and several client/server
resources (HTTP connection pool, worker threads, read buffers) are sized or chosen in ways that
throttle concurrent sessions. Fixing these delivers a large performance gain without changing
the wire protocol.

## What Changes

- **Client – concurrent sessions**: tunneled connections accepted on the same local port SHALL
  exchange data independently (no cross-session serialization).
- **Client – listener continuity**: each configured local port SHALL be bound once and keep
  accepting connections for the client's lifetime; opening a tunnel no longer blocks or resets
  other incoming connections. An accepted socket whose tunnel fails to open is closed.
- **Client – HTTP connection pool**: new property `soht2.client.max-http-connections` sets the
  maximum number of concurrent HTTP connections to the server (applied to the Apache HttpClient
  used in proxy and SSL-verification-disabled modes, which today default to 2 per route).
- **Client – worker threads**: when virtual threads are disabled, blocking tunnel work runs on
  dedicated platform threads instead of the shared ForkJoin common pool, so the number of
  sessions is no longer capped by CPU count.
- **Server – authentication cost**: successfully verified HTTP Basic credentials are remembered
  for a bounded time so repeated requests from the same user skip the expensive password-hash
  check; any password change, user update, or deletion takes effect immediately.
- **Server – request handling**: enable virtual threads for request handling and disable
  open-session-in-view, so blocking exchange requests do not exhaust the platform thread pool or
  hold persistence resources.
- **Server – buffer reuse**: the socket read buffer is allocated once per tunnel connection
  instead of on every exchange request.

Assumptions:
- Direction 2 is implemented as a server-side verified-credential cache (no protocol or client
  change). Token-based or cookie-session authentication is not pursued.
- The credential cache reuses the existing `soht2.server.user-cache-ttl` as its time-to-live; no
  new server property is introduced.
- Default for `soht2.client.max-http-connections` is 100.

Out of scope:
- Wire-protocol changes (pipelining, long-polling, multiple in-flight requests per session).
- Migrating the client from Apache HttpClient 4 (and thus making virtual threads safe with
  proxy/SSL modes on Java 21).
- Compression optimizations in `soht2-common` and minor per-request micro-optimizations.
- Pre-existing username-case inconsistencies in user-cache eviction.

## Capabilities

### New Capabilities
- `client-tunneling`: How the client accepts local connections and relays them to the server
  (listener lifetime, session independence, failure handling, connection-pool sizing).
- `server-authentication`: How the server authenticates API requests, including reuse of
  recently verified credentials and immediate effect of credential changes.

### Modified Capabilities
<!-- none -->

## Impact

- `soht2-client`: `ConnectionService`, `Soht2ClientConfig`, `Soht2ClientProperties`,
  `ProxyConfig`, `TrustingSSLConfig`, `ConnectionServiceTest`; README client config reference.
- `soht2-server`: `SecurityConfig` (new caching authentication provider), `Soht2UserService`,
  `Soht2Service`, `ServerConnection`, `src/main/resources/application.yaml`; related tests.
- `soht2-common`: no changes.
- No API, DTO, database schema, or UI changes. Wire protocol unchanged; old clients and servers
  remain compatible.
