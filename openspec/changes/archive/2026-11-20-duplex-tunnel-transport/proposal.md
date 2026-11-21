# Proposal

## Why

Each tunneled session is limited to one HTTP request at a time. That request carries client→target
bytes, then waits up to `socket-read-timeout` (100 ms) for target→client bytes. When a session is
idle, the client sleeps according to its poll strategy (up to `poll.max-delay`, 1 s by default).
As a result:
- Server-side data waits for the client's next poll, so interactive latency is up to ~1 s after
  idle periods.
- Uploads queue behind downloads.
- Throughput is capped at one `read-buffer-size` chunk per HTTP round trip, in either direction.

The protocol itself now limits throughput, after the per-request costs were removed in
`optimize-tunnel-performance`.

## What Changes

- Add a **duplex transport** that gives each session two independent HTTP lanes:
  - **Upload lane**: client→target bytes are sent as soon as they are read from the local socket,
    without waiting for target data.
  - **Download lane**: a long-poll request that the server answers as soon as target data is
    available, or with an empty response after a bounded hold time. This replaces client-side
    poll sleeping with server-driven delivery.
- Make both lanes **safe to retry** with per-lane sequence numbers. A request repeated after a
  lost response neither duplicates nor drops bytes.
- Signal **target end-of-stream** explicitly on the download lane, so the client closes the local
  connection promptly instead of waiting for an error.
- Keep the existing single-request exchange endpoint unchanged as the **simple transport**:
  - Older clients keep working against a new server.
  - A new client falls back to the simple transport when the server does not offer duplex.
- New configuration:
  - `soht2.client.transport` (`duplex` default, `simple`).
  - `soht2.server.download-hold-timeout` (default 25 s).
- Neither existing direction is a **BREAKING** change: the old client works with the new server,
  and the new client works with the old server.

## Capabilities

### New Capabilities
- `tunnel-transport`: How tunneled bytes travel between the client and server over HTTP. Covers
  independent upload/download lanes, long-poll delivery, retry safety, end-of-stream signaling,
  transport selection and simple compatibility.

### Modified Capabilities
- None. `client-tunneling` (listener, session independence, HTTP connection limit) and
  `server-authentication` requirements are unchanged; the new transport must satisfy them as-is.

## Impact

- **soht2-server**:
  - `ConnectionController`: new upload and download endpoints, with the same owner checks as the
    existing exchange endpoint.
  - `Soht2Service` and `ServerConnection`: separate write and read paths, per-lane sequence
    state, retained last download chunk, and end-of-stream tracking.
  - `Soht2ServerConfig`: new `download-hold-timeout`.
  - Abandoned-connection detection must account for held download requests.
- **soht2-client**:
  - `Soht2Client`: upload and download calls, with duplex support detection.
  - `ConnectionService`: two concurrent loops per session, a shared idle timeout, and close
    handling.
  - `Soht2ClientProperties`: new `transport`; the HTTP read timeout must exceed the server hold
    time.
- **soht2-common**: small shared constants (header names, end-of-stream signal) if needed by both
  sides.
- **API**:
  - Two new endpoints under `/api/connection/{id}`, documented in OpenAPI.
  - The existing `POST /api/connection/{id}` is unchanged.
- **Resources**:
  - Up to 2 concurrent HTTP requests per active session; the existing `max-http-connections`
    default of 100 covers 50 busy sessions.
  - The server holds one request per idle session; it runs on virtual threads, so this is cheap.
- **Docs**: README client and server configuration reference; `requests.http`.

## Assumptions

- Proxies between client and server may buffer response bodies, so streaming (chunked,
  never-ending) responses are not used. Every response is a complete, bounded body.
- Proxies and load balancers usually allow requests of at least 30 s. The default hold time of
  25 s stays below that and is configurable.
- Byte order matters only within each direction. Each lane has at most one request in flight per
  session.

## Out of Scope

- Pipelining several upload or download requests per lane (more than one in flight per direction).
- WebSocket, HTTP/2 server push, or other non-request/response transports.
- Multiplexing several sessions into one HTTP request.
- Half-close semantics (closing one direction while keeping the other open). End-of-stream in
  either direction still closes the whole session, as today.
- Changes to compression algorithms, authentication, or the UI.
