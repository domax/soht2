<!--suppress MarkdownUnresolvedLinkLabel -->
# Design

## Context

See proposal.md (Why) for motivation. The current state that shapes this design:

- **Client** (`ConnectionService.exchange`): one loop per session. It reads the local socket for
  up to `socket-read-timeout`, POSTs the bytes to `POST /api/connection/{id}`, writes the response
  to the local socket, and sleeps per the poll strategy when both directions were empty. The idle
  timeout is checked inside that sleep. `Soht2Client` handles compression on both request and
  response (`Content-Encoding` / `Accept-Encoding`).
- **Server** (`Soht2Service.exchange`): under the per-connection `ReentrantLock`, writes the
  request bytes to the target, then does one blocking read into the per-connection `readBuffer`
  with `SO_TIMEOUT` = `socket-read-timeout`. A target EOF (`read` returns -1) is treated like a
  timeout and returns an empty body, so end-of-stream is never signaled; the client only notices
  when its idle timeout fires or the abandoned-connection job closes the target. A missing or
  closed connection returns 410 Gone.
- **Server housekeeping**: `closeAbandonedConnections` closes connections whose `lastActivity` is
  older than `soht2.server.abandoned-connections.timeout` (default PT1M).
- **Security**: all `/api/connection/{id}` calls are HTTP Basic authenticated and guarded by
  `@soht2Service.isConnectionOwner`. Requests with `X-SOHT2-Client: soht2-client` (sent by the
  client by default) are CSRF-exempt.
- **HTTP stack**: the server runs on virtual threads, so a held request costs little. The client
  uses either the default Spring request factory or Apache HttpClient 4.5 (`HCClientHttpRequestFactory`,
  socket timeout infinite by default) when a proxy or disabled SSL verification is configured.
  Connections are pooled up to `max-http-connections` (100).

## Goals / Non-Goals

**Goals:**
- Per session, upload and download proceed independently, each with at most one request in flight.
- Target data reaches the client within about one round trip, with no client sleeping.
- A request repeated after a lost response neither loses nor duplicates bytes.
- Target EOF closes the local connection promptly.
- Mixed versions work in both directions, with no configuration change required.

**Non-Goals:**
- Changing the simple exchange behavior, including its EOF handling (left as is for old clients).
- Automatic retries of failed requests in the client beyond what is needed for fallback. The
  server's sequence rules make retries safe; adding a client retry policy is a later concern.
- Raising the per-request payload beyond `read-buffer-size`.

## Decisions

### D1. Two lanes per session, one request in flight per lane

Each duplex session uses an upload lane and a download lane. Each lane sends its next request only
after the previous one completes, so byte order within a direction follows from request order.

Alternatives considered:
- **Streaming (chunked, never-ending) responses**: lowest latency, but corporate proxies, including
  NTLM proxies this tool targets, often buffer whole response bodies. Rejected.
- **WebSocket / HTTP/2**: same proxy-compatibility problem, and a larger stack change. Rejected.
- **Several requests in flight per lane**: higher throughput on high-latency links, but needs
  reordering buffers and window management on both sides. Deferred (out of scope).

### D2. Endpoints and wire format

| Lane | Request | Success response |
|---|---|---|
| Upload | `POST /api/connection/{id}/upload`, body = bytes (octet-stream, optional `Content-Encoding`), header `X-SOHT2-Seq: N` | `204 No Content` |
| Download | `GET /api/connection/{id}/download`, header `X-SOHT2-Seq: N`, `Accept-Encoding` | `200` with bytes (optional `Content-Encoding`), or `204` when the hold time elapsed with no data |

- Sequence numbers are per lane, start at 1 and increase by 1 per new request.
- **End-of-stream** is signaled on the download lane as `410 Gone` with header
  `X-SOHT2-EOS: true`. A 410 without that header keeps its current meaning (connection missing or
  closed); the client handles both by closing the session, but logs them differently.
- **Sequence violations** (D4) return `409 Conflict`.
- Both endpoints carry the same `@PreAuthorize` owner check as the simple exchange; 401/403
  behavior is unchanged. The upload is a POST with the client header, so it is CSRF-exempt like
  today's exchange.
- Header names and the EOS marker are defined once in `soht2-common` and used by both sides.

Alternatives considered:
- **Sequence as a query parameter**: equally valid; a header keeps URLs stable in proxy and
  server access logs and matches how encoding is already passed. Chosen: header.
- **EOS as a 200 with a flag header**: lets the final bytes and EOS share one response, but makes
  "empty 200 vs. EOS" a header check on every response. 410 is already treated as terminal by the
  client and fits existing error handling. Chosen: 410 + header.
- **GET with a body for upload**: not reliably supported by proxies. Rejected.

### D3. Server read and write paths

`ServerConnection` gets separate locks for the write side and the read side instead of the single
`lock`:

- **Upload** takes the write lock, applies the sequence rule (D4), decompresses and writes to the
  target output stream, and returns.
- **Download** takes the read lock, applies the sequence rule, then reads the target in a loop with
  the short `SO_TIMEOUT` (`socket-read-timeout`) until one of these happens:
  - data is read: return it;
  - `read` returns -1: mark the connection `targetEof`, return EOS (D2);
  - the deadline `download-hold-timeout` passes: return 204;
  - the connection is closed by another thread (abandoned job, `DELETE`): return 410.
- The simple exchange takes both locks (write then read, always in that order) so it never
  interleaves with a duplex request on the same connection.
- `lastActivity` is updated when a request starts and when it completes. A download that is being
  held updates it on each short-timeout iteration, so a held request never looks abandoned even if
  `abandoned-connections.timeout` were configured below the hold time.
- On `targetEof`, the connection stays registered until the EOS response has been produced, so a
  final data chunk can still be resent on retry (D4); afterwards the normal close path runs.

Alternatives considered:
- **NIO selector or async servlet**: avoids a thread per held request, but virtual threads already
  make blocking cheap and keep the code close to today's. Rejected.
- **Background reader thread per connection with a queue**: would allow prefetching, but adds a
  thread and memory per session and complicates EOF/close. Rejected.

### D4. Retry safety with per-lane sequence numbers

The server keeps per connection: `lastUploadSeq`, `lastDownloadSeq`, and `lastDownloadResult`
(the outcome produced for `lastDownloadSeq`: data bytes, *empty*, or the EOS marker).

| Lane | Received seq | Action |
|---|---|---|
| Upload | `lastUploadSeq + 1` | write bytes, store seq, 204 |
| Upload | `lastUploadSeq` | duplicate: do not write, 204 |
| Download | `lastDownloadSeq + 1` | discard stored result, read new data (D3), store result and seq |
| Download | `lastDownloadSeq` | replay the stored result exactly: same bytes (200), empty (204), or EOS; never read the target |
| Either | anything else | 409, close the connection |

- Every download outcome, including *empty*, is bound to its sequence number. A retry of the
  same seq is a pure replay and never reads the target, so bytes that arrive later can only be
  delivered under a new seq. Otherwise a retried empty response could return new data that the
  client already received (or will receive) under another seq, breaking the no-loss /
  no-duplication guarantee.
- Consequently the client SHALL increment the download seq before every new long poll, including
  right after an empty (204) response. Re-sending the same seq is reserved for retrying a request
  whose response was not received.
- The stored data is a copy (the read buffer is reused), held only until the next download seq,
  so memory is at most one `read-buffer-size` per session.
- A retried download can arrive while the original is still held (the client gave up first).
  The read lock serializes them; the retry waits for the original to finish, then gets the
  same result from `lastDownloadResult`.

Alternatives considered:
- **No sequence numbers, rely on TCP**: a long-held response is the most likely request to be cut
  by a proxy; losing it silently corrupts the stream. Rejected.
- **Byte offsets instead of sequence numbers**: allows partial acknowledgment, but needs a
  retransmit buffer sized by unacknowledged bytes. Not needed with one request in flight. Rejected.

### D5. Client session loops

A duplex session runs two tasks on the existing session executor:

- **Upload loop**: blocking read from the local socket (`socket-read-timeout` used only to check
  for shutdown and idle timeout); when bytes arrive, POST them with the next upload seq. Local EOF
  closes the session.
- **Download loop**: GET with the next download seq; on 200 write bytes to the local socket; on 204
  increment the seq and issue the next GET immediately (the same seq is used only to retry a
  request whose response was lost); on 410 (EOS or gone) close the session.
- Both loops update the shared `lastTrafficNanos` only when they carry data; the idle check runs
  in both loops (spec: idle timeout counts both lanes).
- When either loop ends, for any reason, the session is closed once (guarded by removing the
  session state): the local socket is closed (which unblocks the upload loop), and
  `DELETE /api/connection/{id}` is sent, which also releases a held download on the server; both
  loops stop when they see the session state removed. Pooled threads are not interrupted, so no
  interrupt flag leaks into the executor. The `DELETE` is skipped when the server has already
  closed the connection (download EOS or 410 gone), since it would only be rejected.
- The poll strategy is not used in duplex mode.
- **HTTP read timeout**: a download request must not time out on the client before the server
  answers. `HCClientHttpRequestFactory` already has no read timeout by default. For the default
  request factory, the client sets a read timeout of at least 60 s, which covers the server
  default hold time (25 s) with margin. README documents that a custom client read timeout must
  exceed the server's `download-hold-timeout`.

Alternatives considered:
- **Keep a single loop and only add server long-poll**: removes poll latency but keeps uploads
  blocked behind held downloads, defeating the purpose. Rejected.

### D6. Transport selection and fallback

- `soht2.client.transport`: `duplex` (default) or `simple`; any other value fails startup with a
  message naming the property.
- The server advertises duplex support with response header `X-SOHT2-Transport: duplex` on
  `POST /api/connection` (open). The client chooses per session:
  - setting `simple`: simple loop;
  - setting `duplex` and header present: duplex loops;
  - setting `duplex` and header absent (older server): simple loop, logged once per client run at
    INFO.
- The simple loop and the simple endpoint are unchanged.

Alternatives considered:
- **Detect on first 404/405 from a duplex endpoint**: works without any open-time signal, but the
  first upload's bytes must then be resent via the simple endpoint, and a 404 can also come from
  a misconfigured proxy. Rejected in favor of the explicit, side-effect-free header.
- **Add a field to the `Soht2Connection` JSON**: also works (unknown fields are ignored by older
  clients), but changes a shared record and its constructor for a transport concern. Rejected.
- **Separate capability endpoint**: an extra round trip per run for no gain. Rejected.

### D7. Server configuration

- `soht2.server.download-hold-timeout`: `Duration`, default `PT25S`, must be positive (startup
  fails otherwise). Bound in `Soht2ServerConfig` alongside `socket-read-timeout`.
- Documented in the README server config block; `soht2.client.transport` in the client block,
  with the guidance that proxies must allow requests longer than the hold time.

## Risks / Trade-offs

- [A proxy or load balancer cuts requests shorter than the hold time] → The download is retried
  with the same seq (no data loss, D4); the hold time is configurable; README calls out the
  constraint. As a last resort, `soht2.client.transport=simple`.
- [Twice as many concurrent HTTP requests per session] → `max-http-connections` (100) covers 50
  fully active sessions; documented. Idle sessions hold one connection (download) instead of
  polling, so request count per idle session drops sharply.
- [More open requests on the server] → one held request per idle session on a virtual thread;
  memory is the stored last chunk at most.
- [Deadlock between simple and duplex on the same connection] → a connection is used by only one
  transport per session in practice; the simple path still takes locks in a fixed order
  (write, then read).
- [Abandoned job closing a connection while a download is held] → the held download sees the
  closed state on its next short-timeout iteration and returns 410; the client closes the session.
- [Duplicate download retries in flight] → serialized by the read lock and answered from
  `lastDownloadResult` (D4).
- [Throughput still capped at one chunk per round trip per direction] → accepted; pipelining is
  out of scope (D1). Upload and download no longer share that cap.

## Migration Plan

1. Deploy the new server. Old clients keep using the simple endpoint (no change for them).
2. Roll out new clients. They use duplex automatically when the server advertises it.
3. A new client against an old server falls back to simple automatically.
4. Rollback: set `soht2.client.transport=simple` on clients (no server change needed), or redeploy
   the previous server version; new clients then fall back on their own.
