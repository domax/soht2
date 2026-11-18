# Design

## Context

See proposal.md for motivation. Relevant current state:

- `ConnectionService.connect` opens a `ServerSocket`, accepts **one** socket, calls
  `soht2Client.open(...)` synchronously (HTTP round trip), starts `exchange`, then closes the
  `ServerSocket` at the end of the try-with-resources block and re-binds on the next loop.
  Backlogged connections are reset when the socket closes. A failed `open(...).get()` leaks the
  accepted socket.
- `ConnectionService` keeps `locks: Map<localPort, ReentrantLock>`; each accept *replaces* the
  port's lock, and every `exchange` iteration looks it up again, so all sessions on a port share
  the newest lock and hold it across a socket read (up to `socket-read-timeout`) plus the HTTP
  round trip. Each session already owns its own socket and loop; nothing shared needs guarding.
- `ProxyConfig` builds `HttpClients.custom()` with an optional connection manager;
  `TrustingSSLConfig` builds a `PoolingHttpClientConnectionManager` with defaults. HttpClient 4
  defaults are 2 per route / 20 total. Without proxy or SSL bypass, Spring Boot's default
  `RestClient` transport (JDK `HttpClient`) is used and is not pooled-limited.
- `Soht2ClientConfig.threadExecutor` returns `ForkJoinPool.commonPool()` when virtual threads are
  disabled (the `dist` example disables them), so blocking listeners/sessions compete for
  `cores - 1` threads.
- Server: `SecurityConfig` uses `httpBasic` with default (request-attribute) security context
  repository, so every request goes through `DaoAuthenticationProvider` →
  `PasswordEncoder.matches` (BCrypt via delegating encoder). `Soht2UserService.loadUserByUsername`
  is `@Transactional(readOnly = true)`, which acquires a DB connection per request even when the
  user comes from `userCache`.
- `Soht2Service.exchange` allocates `new byte[readBufferSize]` (1 MB default) per request.
- Server `application.yaml`: `jpa.open-in-view: true`, no virtual threads. Entities have no lazy
  associations.

## Goals / Non-Goals

**Goals:**
- Remove client-side cross-session serialization and listener churn.
- Remove per-request BCrypt and DB-connection cost for authenticated API calls.
- Remove thread-pool and HTTP-pool caps that throttle concurrent sessions.
- Reduce per-exchange allocation on the server.

**Non-Goals:**
- Any wire-protocol or client/server compatibility change.
- Replacing Apache HttpClient 4 on the client; changing the client's default virtual-thread
  setting.
- Changes to `soht2-common` compression.

## Decisions

### D1. Client listener: bind once, accept loop, hand off per connection
`connect(host)` binds a single `ServerSocket` and loops on `accept()` while running. For each
accepted socket it submits a task to `threadExecutor` that configures the socket, opens the
tunnel, registers the session and runs `exchange`, closing the socket in a `finally`/failure path
(including when `open` fails). The `socketOpenedCallback` fires once after bind. Opened
`ServerSocket`s are tracked in a concurrent set; `stopConnections()` closes them so a blocked
`accept()` returns on both virtual and platform threads (platform-thread `accept` is not
interruptible). A `SocketException` from a closed server socket while not running ends the loop
quietly; `BindException` keeps current behavior (log and stop).
- *Alternative*: keep re-binding but defer `close` — still has a re-bind gap; rejected.

### D2. Remove the per-port lock
Delete `locks` and lock/unlock in `exchange`. Each session's loop is single-threaded and uses its
own streams; `Soht2Client`/`RestClient` are thread-safe.
- *Alternative*: per-session lock — guards nothing; rejected.

### D3. Client HTTP pool sizing
Add `maxHttpConnections` (`int`, default 100, `@Min(1)` style validation in `afterPropertiesSet`
consistent with existing checks) to `Soht2ClientProperties`. Apply as both max total and max per
route:
- `TrustingSSLConfig`: `cm.setMaxTotal(n)`, `cm.setDefaultMaxPerRoute(n)`.
- `ProxyConfig`: when no connection manager bean exists, `setMaxConnTotal(n)` and
  `setMaxConnPerRoute(n)` on the builder (these are ignored if a manager is supplied, which is
  then already sized by `TrustingSSLConfig`).
Single target route (the SOHT2 server) means total == per-route is the right model.

### D4. Platform-thread executor
When virtual threads are disabled, use
`new TimeoutAwareExecutorService(Executors.newCachedThreadPool())` instead of the common pool, so
shutdown semantics match the virtual-thread path.
- *Alternative*: fixed pool — reintroduces a cap; rejected.

### D5. Server: caching authentication provider
Add a `CachingAuthenticationProvider` (server `config` or `service` package) registered as the
sole `AuthenticationProvider` bean, wrapping a `DaoAuthenticationProvider` built from
`Soht2UserService` and the `PasswordEncoder`:
1. Only handles `UsernamePasswordAuthenticationToken`.
2. Loads `UserDetails` via `loadUserByUsername` (served from `userCache`). Missing user → delegate
   (preserves existing error semantics).
3. Computes `fp = HMAC-SHA256(processKey, username + ':' + password)`; `processKey` is a random
   32-byte key generated at startup (plaintext and raw hashes are never stored).
4. Caffeine cache `username(lowercase) -> (storedPasswordHash, fp)`, max 1000 entries,
   `expireAfterWrite = user-cache-ttl`. Hit is valid only if `storedPasswordHash` equals the
   *current* `UserDetails.getPassword()` and `fp` matches (`MessageDigest.isEqual`), and the user
   is enabled/non-locked. On hit, return an authenticated token with the current authorities.
5. On miss, delegate; on success, store the entry. Failures are never stored.
Implementation note: the provider extends `DaoAuthenticationProvider` and overrides only
`additionalAuthenticationChecks` (the single place the password hash is checked), so user lookup,
account status checks, timing-attack mitigation and success-token creation stay unchanged. The
credential cache is private to the provider; `Soht2UserService` evicts it through an
`ObjectProvider` to avoid a bean cycle.
Invalidation: password change, update, and delete already evict `userCache`, so the next
`loadUserByUsername` returns the new hash (or no user) and the entry no longer matches. Explicit
eviction of the credential cache is also added at the same three call sites for clarity.
- *Alternatives*: session cookies (client cookie handling differs by transport; adds session
  state), token from `open` (protocol change), lowering BCrypt cost (weakens storage). Rejected
  for this change.
- Spring Security uses a single `AuthenticationProvider` bean in preference to auto-configuring a
  `DaoAuthenticationProvider` from the `UserDetailsService`; verify in tests that only the caching
  provider is active.

### D6. Drop transaction from `loadUserByUsername`
Remove `@Transactional(readOnly = true)` from `loadUserByUsername`; the repository call inside
`getCachedUserEntity` is already transactional via Spring Data, so a cache hit no longer touches
the pool.

### D7. Server request threads and OSIV
In `soht2-server/src/main/resources/application.yaml`: `spring.threads.virtual.enabled: true` and
`spring.jpa.open-in-view: false`. Server code uses `ReentrantLock` (no `synchronized` hot paths)
and plain `java.net.Socket`, which are virtual-thread friendly. No lazy associations exist, so
disabling OSIV does not change responses.

### D8. Per-connection read buffer
`ServerConnection` owns `byte[] readBuffer` sized from `read-buffer-size`, created with the
connection. `Soht2Service.exchange` reads into it under the existing connection lock and always
returns `Arrays.copyOf(buffer, len)` (never the shared array). The empty-read path keeps returning
the shared `EMPTY`.
- Trade-off: 1 MB retained per open connection vs. 1 MB garbage per request; open connections are
  bounded by abandoned-connection cleanup.

## Risks / Trade-offs

- [Credential cache keeps a derived secret in memory] → HMAC with per-process random key, bounded
  size and TTL, constant-time compare, never caches failures.
- [Stale `userCache` due to pre-existing case-sensitive eviction keys] → credential cache is
  evicted using the same keys and also lowercased; residual staleness is bounded by the TTL and no
  worse than today's `userCache`.
- [Virtual threads on server pin on `synchronized` in H2/Hikari (Java 21)] → only affects
  history/user DB calls, not the exchange hot path; can be reverted via config.
- [Bound listener with platform threads not interruptible] → closing tracked `ServerSocket`s in
  `stopConnections()`.
- [Higher concurrent HTTP connections may hit proxy limits] → configurable via
  `max-http-connections`.
- [Behavior change in tests] → `ConnectionServiceTest` relies on the open callback; it fires once
  per bind, which matches its usage.

## Migration Plan

No data or protocol migration. Deploy server and client independently in any order. Rollback:
revert the release; the server's virtual threads/OSIV can also be reverted via configuration.
