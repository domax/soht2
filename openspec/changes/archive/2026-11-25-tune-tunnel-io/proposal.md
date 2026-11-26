# Proposal

## Why

The tunnel's I/O hot paths waste CPU and latency.

**Server:**
- Held duplex downloads wake every `socket-read-timeout` (100 ms) per idle session.
- Target sockets connect with no timeout, so an unreachable target can pin a request thread
  indefinitely.

**Client:**
- Duplex upload loops also wake every 100 ms.

**Both sides:**
- Small interactive writes suffer from Nagle's algorithm on sockets whose `TCP_NODELAY` is unset.
- Each read returns only what one `read()` call yields, which fragments bursts into more HTTP
  round trips.
- The default 1 MB read buffer is allocated per read call per session.

**Authentication hot paths:**
- Regular expressions for allowed targets are compiled on every open request.
- An HMAC `Mac` is instantiated on every authentication cache lookup.

## What Changes

- **Server held downloads:** block on the target socket for the remaining hold time instead of
  waking every `socket-read-timeout`. A held download keeps its connection from being closed as
  abandoned.
- **Bursts:** after a successful read, server and client append bytes that are already
  available, up to the buffer size, before sending. This yields fewer, fuller HTTP payloads.
- **`TCP_NODELAY`:** enabled on server target sockets and client accepted sockets.
- **Connect timeout:** new `soht2.server.target-connect-timeout` (default `PT10S`) bounds target
  connects. Open requests to unreachable targets fail within that time with the existing error
  response.
- **Buffer size:** lowering the `read-buffer-size` default from `1MB` to `256KB` was evaluated
  and not adopted. The benchmark showed a 24% throughput regression (see `design.md`), so the
  default stays `1MB`.
- **Client duplex upload:** waits on the local socket with a fixed 1 s read timeout, which is
  needed only for idle-timeout checks. The simple transport keeps using `socket-read-timeout`.
- **Allowed targets:** compiled patterns are cached.
- **HMAC:** each fingerprint clones a pre-initialized prototype `Mac` instead of creating and initializing a new one.

Assumptions:

- 256 KB is enough per payload once draining combines reads. Tasks include a throughput check
  against 1 MB; if throughput regresses by more than 10%, the default stays at 1 MB.
- 1 s granularity for the client duplex idle timeout check is acceptable (idle timeouts are
  minutes).

Out of scope:

- Changing the wire protocol, sequence numbering, or the hold timeout.
- NIO or selector-based rewrites.
- `LocalDateTime.now` micro-optimizations.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `tunnel-transport`: adds requirements for target connect timeouts, held-download liveness,
  burst coalescing, and low-latency socket options. Existing ordering and idempotency
  requirements are unchanged.

## Impact

- **Server:**
  - `Soht2Service` (`readTarget`, `exchange`, `closeAbandonedConnections`)
  - `ServerConnection` (constructor)
  - `Soht2ServerConfig` (new property, new default)
  - `Soht2User.isAllowedTarget`
  - `CachingAuthenticationProvider.fingerprint`
  - `application.yaml`
- **Client:** `ConnectionService` (`serve`, `upload`, `exchange`), `Soht2ClientProperties`
  default, `application.yaml`.
- **Common:** optional drain helper in `AuxUtil`.
- **Docs:** `README.md` config blocks, `doc/tips-server.md`.
- **Overlap:** this change overlaps `harden-server-exposure` in `Soht2ServerConfig`,
  `application.yaml`, and the `tunnel-transport` spec. Apply it after that change.
