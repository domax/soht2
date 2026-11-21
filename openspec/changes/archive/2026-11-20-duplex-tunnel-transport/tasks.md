# Tasks

## 1. Shared protocol constants (soht2-common)

- [x] 1.1 Add a `Soht2Protocol` constants class in `net.soht2.common.dto` (or `util`) with header names `X-SOHT2-Seq`, `X-SOHT2-EOS`, `X-SOHT2-Transport`, the value `duplex`, and the lane path suffixes `upload` / `download` (design D2, D6). Add the Spotless header; run `./gradlew :soht2-common:spotlessApply :soht2-common:build`.

## 2. Server configuration

- [x] 2.1 Add `downloadHoldTimeout` (`Duration`, default `PT25S`) to `Soht2ServerConfig`, bound as `soht2.server.download-hold-timeout`, and fail startup when it is zero or negative with a message naming the property (spec: Hold timeout is validated).
- [x] 2.2 Add the property to `soht2-server/src/main/resources/application.yaml` if defaults are listed there, and add a config test for the positive and non-positive cases.

## 3. Server connection state (ServerConnection)

- [x] 3.1 Replace the single `lock` in `ServerConnection` with a write lock and a read lock; keep a helper that takes both in fixed order (write, then read) for the simple exchange (design D3).
- [x] 3.2 Add per-connection duplex state: `lastUploadSeq`, `lastDownloadSeq`, `lastDownloadResult` (bytes, empty, or EOS marker), and `targetEof` (design D4).
- [x] 3.3 Update `Soht2Service.exchange` (simple) to use both locks; confirm its behavior is otherwise unchanged by running the existing `Soht2ServiceTest` and `ConnectionControllerTest`.

## 4. Server duplex service logic (Soht2Service)

- [x] 4.1 Add `upload(id, seq, data, encoding)`: 410 for missing/closed connection; under the write lock apply the D4 upload rule (next seq → decompress, write, flush, count bytes; same seq → no write; otherwise 409 and close); update `lastActivity`.
- [x] 4.2 Add `download(id, seq)`: under the read lock apply the D4 download rule (same seq → replay the stored result exactly, including an empty result, without reading the target; next seq → read loop and store the result, including empty; otherwise 409 and close).
- [x] 4.3 Implement the download read loop (design D3): read with the short `SO_TIMEOUT` until data (return a copy), `-1` (set `targetEof`, return EOS), `download-hold-timeout` deadline (return empty), or connection closed (410); update `lastActivity` on each iteration.
- [x] 4.4 After an EOS result has been produced, close and unregister the connection through the existing close path, keeping the stored EOS so a retry of the same seq still returns EOS (or 410 if already removed).
- [x] 4.5 Unit tests in `Soht2ServiceTest` using the echo test server: upload then download round-trip; download returns empty after a short hold timeout; upload while a download is held completes immediately; repeated upload seq is not written twice; repeated download seq returns the same bytes; repeated seq of an empty download returns empty even after the target has sent new data, and that data is returned under the next seq; out-of-order seq yields 409 and closes; target close yields EOS after the final data.

## 5. Server endpoints (ConnectionController)

- [x] 5.1 Add `POST /api/connection/{id}/upload` (octet-stream body, `X-SOHT2-Seq`, optional `Content-Encoding`, returns 204) and `GET /api/connection/{id}/download` (`X-SOHT2-Seq`, `Accept-Encoding`; returns 200 with compressed-as-today bytes, 204 when empty, 410 with `X-SOHT2-EOS: true` for EOS), both with the same `@PreAuthorize` owner check as the simple exchange, and OpenAPI annotations.
- [x] 5.2 Map sequence violations to 409 and keep 410 for missing/closed connections.
- [x] 5.3 Add `X-SOHT2-Transport: duplex` to the response of `POST /api/connection` (open).
- [x] 5.4 Controller tests in `ConnectionControllerTest`: owner can upload/download; another user gets 403; unauthenticated gets 401; open response carries the transport header; EOS response has status 410 and the EOS header; simple `POST /api/connection/{id}` unchanged.
- [x] 5.5 Run `./gradlew :soht2-server:spotlessApply :soht2-server:test`.

## 6. Client configuration

- [x] 6.1 Add `transport` to `Soht2ClientProperties` (enum `DUPLEX`/`SIMPLE`, default `DUPLEX`, bound from `duplex`/`simple`); an unsupported value fails startup with a message naming `soht2.client.transport` (spec: Transport is selectable).
- [x] 6.2 Ensure the client HTTP read timeout is at least 60 s for the default request factory in `Soht2ClientConfig`; leave `HCClientHttpRequestFactory` (no read timeout) as is (design D5).

## 7. Client HTTP calls (Soht2Client)

- [x] 7.1 Make `open` return whether the server advertised `X-SOHT2-Transport: duplex` alongside the `Soht2Connection` (e.g. a small result record), without changing the `Soht2Connection` record.
- [x] 7.2 Add `upload(connection, seq, bytes)` with the same request compression logic as `exchange`.
- [x] 7.3 Add `download(connection, seq)` returning data, empty (204), or EOS (410 + EOS header); decompress per `Content-Encoding` as in `exchange`; treat 410 without the EOS header as connection gone.
- [x] 7.4 Tests in `Soht2ClientTest` for upload, download (data, empty, EOS, gone), and the open transport flag with and without the header.

## 8. Client session loops (ConnectionService)

- [x] 8.1 Choose the transport per session: `simple` setting or missing open header → existing simple loop (log the fallback once per client run at INFO); otherwise duplex (design D6).
- [x] 8.2 Implement the duplex upload loop: read local socket; on bytes, POST with the next upload seq; on local EOF, end the session; idle check on each iteration.
- [x] 8.3 Implement the duplex download loop: GET with the next download seq; write data to the local socket; on empty increment the seq and issue the next GET immediately (reuse a seq only to retry a lost response); on EOS or gone end the session; idle check on each iteration.
- [x] 8.4 Share `lastTrafficNanos` between both loops and update it only when data is carried; apply `disconnect-on-idle-timeout` as in the spec (both lanes idle).
- [x] 8.5 Close the session exactly once when either loop ends: stop the other loop, close the local socket, send `DELETE /api/connection/{id}` unless the server already closed it (EOS or 410 gone), and remove session state.
- [x] 8.6 Tests in `ConnectionServiceTest` (mocked `Soht2Client`): upload proceeds while download is blocked; server data is written locally without poll delay; EOS closes the local socket; idle timeout fires only when both lanes are idle; simple setting and missing open header use the simple exchange.
- [x] 8.7 Run `./gradlew :soht2-client:spotlessApply :soht2-client:test` (run client and server tests separately; both use port 12345).

## 9. Documentation

- [x] 9.1 README: add `soht2.client.transport` to the client config block and `soht2.server.download-hold-timeout` to the server config block, with the note that proxies and custom client read timeouts must allow requests longer than the hold time, and that rollback is `transport: simple`.
- [x] 9.2 `requests.http`: add upload and download examples with `X-SOHT2-Seq`.
- [x] 9.3 `doc/tips-client.md` / `doc/tips-server.md`: short notes on the duplex transport and the connection budget (two HTTP requests per active session vs. `max-http-connections`).

## 10. Verification

- [x] 10.1 Run `./gradlew spotlessCheck build` and fix any failures.
- [x] 10.2 Manual end-to-end with a local server and client (duplex): interactive echo shows sub-second response after a 5 s idle period; 30 concurrent downloads complete with correct checksums; target close closes the local connection.
- [x] 10.3 Repeat 10.2 with `soht2.client.transport=simple`, and once with a new client against a server that does not send the transport header (fallback), confirming correct data.

## Workflow follow-up

- Archive with `/opsx-archive` after implementation is verified.
