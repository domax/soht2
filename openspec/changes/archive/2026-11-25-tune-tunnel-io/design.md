<!--suppress MarkdownUnresolvedLinkLabel -->
# Design

## Context

- **Server held download (`Soht2Service.readTarget`):**
  - Loops `in.read()` with `soTimeout = socket-read-timeout` (100 ms) until data arrives or the
    hold expires, updating `lastActivity` on every timeout.
  - The `simple` exchange path reads the same socket under the connection's locks.
  - `closeAbandonedConnections` closes connections whose `lastActivity` is older than the
    abandoned timeout (default 1 min).
- **Server target socket:** `ServerConnection` creates the socket with `new Socket(inet, port)`,
  which has no connect timeout and default socket options.
- **Client:**
  - `ConnectionService.serve` configures accepted sockets with `soTimeout` only.
  - The duplex upload loop reads the local socket with `socket-read-timeout` to check the idle
    timeout periodically.
- **Read buffers:** each read allocates `read-buffer-size` bytes (default 1 MB).
- **`Soht2User.isAllowedTarget`:** compiles each allowed-target regex per call.
- **`CachingAuthenticationProvider.fingerprint`:** calls `Mac.getInstance` per call.

## Goals / Non-Goals

**Goals:**

- Remove periodic wake-ups for idle duplex sessions on both sides.
- Bound target connects.
- Reduce latency for small writes.
- Fewer, fuller payloads for bursts.
- Less allocation and CPU on authentication and open paths.

**Non-Goals:**

- Protocol changes, NIO, or changing the hold or idle timeout semantics.

## Decisions

1. **Block for the remaining hold time.**
   - `readTarget` sets `socket.setSoTimeout(max(1, remainingHoldMillis))` before each read. It
     loops only if the remaining time is still positive, which guards against spurious wakeups.
   - When the server shuts down or the connection closes, closing the socket unblocks the read
     with a `SocketException`. This is already mapped to the gone/closed path.
   - The `simple` exchange path keeps `socket-read-timeout`. Both paths acquire the connection's
     read lock, so each sets `soTimeout` while holding the lock, and they never interleave.
   - Alternative: NIO selector. Rejected as too large for this change.
2. **Liveness of held connections.**
   - `closeAbandonedConnections` skips connections whose read lock is currently held
     (`ReentrantLock.isLocked()`), or whose new volatile `downloadInFlight` flag is set.
   - `lastActivity` is updated when the hold starts and when it ends.
   - Result: a session whose client keeps polling is never abandoned. A session whose client
     disappears is abandoned once no request has been held for the abandoned timeout.
3. **Drain helper.** Add `AuxUtil.readAvailable(InputStream in, byte[] buf, int firstRead)` that
   loops `while (n < buf.length && in.available() > 0)` and appends `in.read(buf, n,
   min(available, buf.length - n))`. It never blocks beyond data already reported as available.
   Use it in:
   - server `readTarget` and simple `exchange`
   - client duplex `upload` and simple `exchange`
4. **`TCP_NODELAY`.**
   - `ServerConnection`: `socket.setTcpNoDelay(true)`.
   - `ConnectionService.serve`: `socket.setTcpNoDelay(true)` on the accepted socket.

   The client→server HTTP connection is managed by the HTTP library and stays unchanged.
5. **Connect timeout.**
   - New `Duration targetConnectTimeout` in `Soht2ServerConfig` (default `PT10S`, must be
     positive; validated like the other durations).
   - `ServerConnection` uses `new Socket()` and then `connect(new InetSocketAddress(inet, port),
     timeoutMillis)`.
   - `SocketTimeoutException` is handled by the existing open-failure mapping, which keeps the
     same HTTP status as other connect failures. A test pins the status.
6. **Buffer default 256 KB.** Change the default in `Soht2ServerConfig`, `Soht2ClientProperties`,
   both `application.yaml` files, and the README. Combined with draining (3), bursts still fill
   large payloads. This also lowers worst-case memory per concurrent read from 1 MB to 256 KB.
   - **Outcome (task 5.1): not adopted; the default stays 1 MB.** 500 MB of random data was sent
     through a local duplex tunnel (server, client, and Python sink on one macOS host, 12 cores),
     with server and client `read-buffer-size` set together, and a warm-up run discarded. The
     median of three runs was 524.4 MB/s with 1 MB (409.6, 524.4, 549.5) and 400.1 MB/s with
     256 KB (253.9, 400.1, 433.8). That is a 24% regression, above the 10% limit.
7. **Client duplex upload wake interval.**
   - The duplex upload loop sets the local socket `soTimeout` to `min(1s, idle-timeout)`.
   - `SocketTimeoutException` only triggers the idle check.
   - The simple transport keeps `socket-read-timeout`, because it must also poll the server.
8. **Pattern cache.**
   - A `static final Map<String, Pattern>` (concurrent, bounded at 1024 entries; when full,
     compile without caching) in `Soht2User`, keyed by allowed-target expression.
   - Invalid expressions behave as today.
9. **Mac reuse.** `CachingAuthenticationProvider` initializes one prototype `Mac` with its key.
   Each fingerprint call uses `prototype.clone()`, falling back to `Mac.getInstance` plus `init`
   if cloning is unsupported.
   - Alternative: `ThreadLocal<Mac>`. Rejected because the server uses virtual threads, which
     would create one instance per thread.

## Risks / Trade-offs

- [A longer blocking read could delay server shutdown] → Shutdown closes target sockets, which
  unblocks reads immediately. A test covers it.
- [`available()` may return 0 for TLS or other streams] → Draining is an optimization only. The
  correctness of ordering does not depend on it.
- [256 KB could reduce bulk throughput] → Task 5.1 benchmarks 1 MB vs 256 KB with `iperf3` or
  `dd`/`nc` through the tunnel. If the regression is above 10%, keep 1 MB and record the result.
- [Abandoned-connection logic change could leak connections] → A test checks that a connection
  with no held request and stale `lastActivity` is still closed.

## Migration Plan

No action is required. Operators who set `read-buffer-size` explicitly keep their value. The new
`target-connect-timeout` has a safe default. Rollback means redeploying the previous JARs.

## Verification Results

Idle CPU (task 7.2) was measured with 50 idle duplex sessions on one macOS host: CPU time used
over 30 s after a 15 s warm-up, from `ps`. The baseline was built from the commit before this
change.

| Process | Before | After |
|---------|--------|-------|
| Server  | 1580 ms | 230 ms |
| Client  | 870 ms  | 260 ms |
