# Tasks

## 1. Client listener and session handling

- [x] 1.1 Refactor `ConnectionService.connect` to bind one `ServerSocket` per host, loop on `accept()`, and hand each accepted socket to `threadExecutor` (open tunnel, register session, run `exchange`); invoke `socketOpenedCallback` once after bind (design D1). Verify `./gradlew :soht2-client:test` passes.
- [x] 1.2 Close the accepted socket when `soht2Client.open` fails and keep the accept loop running; add a `ConnectionServiceTest` case where `open` returns a failure and assert the local client sees EOF and a second connection is accepted.
- [x] 1.3 Track opened `ServerSocket`s and close them in `stopConnections()`; add a test that starts, stops, and restarts connections on the same port without "Address already in use" (removes the limitation noted at the end of `ConnectionServiceTest`).
- [x] 1.4 Remove the `locks` map and lock/unlock from `ConnectionService.exchange` (design D2); add a `ConnectionServiceTest` case with two concurrent `EchoClient`s on the same port, where the mocked `exchange` for one session blocks, and assert the other session's data is still exchanged.

## 2. Client transport and threads

- [x] 2.1 Add `maxHttpConnections` (default 100, validated ≥ 1) to `Soht2ClientProperties`; extend `Soht2ClientPropertiesTest` to cover the default, binding of `soht2.client.max-http-connections`, and rejection of 0.
- [x] 2.2 Apply the limit as max total and max per route in `TrustingSSLConfig` (connection manager) and `ProxyConfig` (builder, when no manager bean) per design D3; add a unit test asserting the configured `PoolingHttpClientConnectionManager` reports the expected `getMaxTotal()`/`getDefaultMaxPerRoute()`.
- [x] 2.3 In `Soht2ClientConfig.threadExecutor`, use `TimeoutAwareExecutorService(Executors.newCachedThreadPool())` when virtual threads are disabled (design D4); verify with a test that the bean is not `ForkJoinPool.commonPool()` when `spring.threads.virtual.enabled=false`.
- [x] 2.4 Document `max-http-connections` in the README client configuration reference (and the client example YAML block there); verify the README property name matches `Soht2ClientProperties`.

## 3. Server authentication

- [x] 3.1 Remove `@Transactional(readOnly = true)` from `Soht2UserService.loadUserByUsername` (design D6); verify `./gradlew :soht2-server:test` passes.
- [x] 3.2 Implement `CachingAuthenticationProvider` (HMAC fingerprint with per-process random key, Caffeine cache bounded to 1000 entries with TTL `user-cache-ttl`, hit only when stored hash and fingerprint match, failures never cached) and register it as the sole `AuthenticationProvider` in `SecurityConfig` (design D5); add unit tests covering: hit skips `PasswordEncoder.matches`, wrong password rejected, failures not cached, expiry forces re-verification, changed stored hash forces re-verification.
- [x] 3.3 Evict the credential cache wherever `userCache` is evicted in `Soht2UserService` (update, delete, password change); add tests asserting that after a password change the old password is rejected and the new one accepted, after delete the credentials are rejected, and after a role change the new authorities are applied.
- [x] 3.4 Add a `@WebMvcTest`/integration check that two consecutive authenticated requests with the same credentials invoke `PasswordEncoder.matches` only once, and that only the caching provider is active.

## 4. Server request handling and buffers

- [x] 4.1 Set `spring.threads.virtual.enabled: true` and `spring.jpa.open-in-view: false` in `soht2-server/src/main/resources/application.yaml` (design D7); verify `./gradlew :soht2-server:test` passes and the server starts with `./gradlew :soht2-server:bootRun` without OSIV warnings.
- [x] 4.2 Add a per-connection read buffer to `ServerConnection` and use it in `Soht2Service.exchange`, always returning a copy of the read bytes (design D8); extend `Soht2ServiceTest` to assert successive exchanges return independent arrays with correct content, including a full-buffer read.
- [x] 4.3 Update README server notes / `doc/tips-server.md` to mention the credential cache's relation to `user-cache-ttl`; verify the documented property name matches `Soht2ServerConfig`.

## 5. Integration verification

- [x] 5.1 Run `./gradlew spotlessCheck build` and confirm all modules build and tests pass.
- [x] 5.2 Manual end-to-end check: run server and client locally, open several concurrent connections through one local port (e.g. parallel `curl` through a tunneled HTTP service) and confirm all succeed and server logs show no per-request authentication slowdowns.

## Workflow follow-up

- Archive the change with `/opsx-archive` after review.
