# Tasks

## 1. Server target socket

- [x] 1.1 Add `targetConnectTimeout` (default `PT10S`, validated as positive) to
  `Soht2ServerConfig` and `application.yaml`. In `ServerConnection`, connect with
  `new Socket()` and `connect(addr, timeout)`, then call `setTcpNoDelay(true)`. Add tests:
  - an open to a non-routable address (for example `10.255.255.1`, or a test hook with a
    `ServerSocket` backlog trick) fails within the timeout with the existing open-failure status
  - a successful open has `TCP_NODELAY` set

  Verify with `./gradlew :soht2-server:test`.

## 2. Held downloads

- [x] 2.1 Change `Soht2Service.readTarget` to block for the remaining hold time (design
  decision 1), with simple `exchange` restoring `socket-read-timeout` under the same lock. Add
  tests: an empty hold returns after about `download-hold-timeout`, data during the hold returns
  immediately, and closing the connection during the hold returns promptly. Verify the server
  tests pass.
- [x] 2.2 Make `closeAbandonedConnections` skip connections with an in-flight held download and
  touch `lastActivity` at hold start and end (design decision 2). Add tests: a connection with a
  held download older than the abandoned timeout is kept, and an idle connection with no held
  request is still closed. Verify the server tests pass.

## 3. Coalescing reads

- [x] 3.1 Add `AuxUtil.readAvailable` in `soht2-common` with unit tests:
  - drains up to the buffer length
  - never blocks when `available()` is 0
  - preserves order

  Verify with `./gradlew :soht2-common:test`.
- [x] 3.2 Use it in server `readTarget`/`exchange` and client duplex `upload`/simple `exchange`.
  Add a server test where the target writes 3×10 KB before the read and the payload is 30 KB in
  order. Add one asserting the payload is ≤ `read-buffer-size`. Verify server and client tests
  pass.

## 4. Client sockets

- [x] 4.1 In `ConnectionService.serve`, set `TCP_NODELAY` on accepted sockets. In the duplex
  upload loop, use a `min(1s, idle-timeout)` socket timeout, with simple keeping
  `socket-read-timeout`. Add tests: an accepted socket has `TCP_NODELAY`, an idle duplex session
  is still closed after the idle timeout, and local data is uploaded promptly. Verify with
  `./gradlew :soht2-client:test`.

## 5. Buffer default

- [x] 5.1 Benchmark before changing the default. With a local server, client, and target
  (`nc -l` sink), transfer 500 MB through a duplex tunnel with `read-buffer-size` set to 1 MB
  and then 256 KB, three runs each. Record median throughput in the PR.
- [x] 5.2 If 256 KB is within 10% of 1 MB, change the default to `256KB` in
  `Soht2ServerConfig`, `Soht2ClientProperties`, both `application.yaml` files, and the
  `README.md` config blocks. Otherwise, leave it at 1 MB and note the result in `design.md`.
  Verify that config binding tests pass.

## 6. Authentication and open hot paths

- [x] 6.1 Cache compiled allowed-target patterns in `Soht2User` (bounded, design decision 8).
  Add tests: the same results as before for matching, non-matching, and invalid expressions.
  Verify the server tests pass.
- [x] 6.2 Reuse `Mac` in `CachingAuthenticationProvider` by cloning a prototype. Add a test that
  fingerprints are identical to the previous implementation for fixed inputs and are
  thread-safe under concurrent calls. Verify the server tests pass.

## 7. Documentation and final check

- [x] 7.1 Document `target-connect-timeout` and the new `read-buffer-size` default (if changed)
  in `README.md` and `doc/tips-server.md`. Verify the README config keys match
  `Soht2ServerConfig` and `Soht2ClientProperties`.
- [x] 7.2 Run `./gradlew spotlessCheck build`. Manually measure idle CPU with 50 idle duplex
  sessions before and after (for example with `top`/`ps`) and record it in the PR. Verify the
  build passes and idle CPU does not increase.
