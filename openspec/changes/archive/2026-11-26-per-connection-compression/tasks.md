# Tasks

## 1. Configuration model

- [x] 1.1 In `soht2-client/.../config/Soht2ClientProperties.java`, make these changes (design
  Decisions 1 and 4):
  - Add `private CompressionProperties compression = new CompressionProperties();` with Javadoc to
    `HostProperties`.
  - Remove the top-level `compression` field and its Javadoc.
  - Update the `CompressionProperties` Javadoc to say the settings apply per connection.
  - In `afterPropertiesSet`, validate each connection's `compression`. It must not be null, its
    `type` must not be null, and its `minRequestSize` must not be null or negative. Messages must
    name `soht2.client.connections[].compression...`.

  In `Soht2ClientPropertiesTest`, add tests for:
  - The default `HostProperties().getCompression()` has `type` `NONE` and `minRequestSize` `2KB`.
  - Binding `soht2.client.connections[0].compression.type=gzip` and `.min-request-size=4KB` works.
  - Binding only `.compression.type=gzip` leaves `min-request-size` at `2KB`.
  - Binding a legacy `soht2.client.compression.type=gzip` succeeds, and the connection's
    compression stays `NONE`.
  - `afterPropertiesSet` throws `IllegalArgumentException` for a null `compression` and for a
    negative `minRequestSize`.

  This task does not compile on its own: main and test sources still call the removed getter.
  Run the property tests after task 2.1.

## 2. Client request handling

- [x] 2.1 In `Soht2Client`, add a `CompressionProperties compression` parameter to `exchange`,
  `upload`, and `download`, using the signatures in design Decision 3. Pass it through to
  `acceptEncoding(...)` and `requestExchangeEntity(...)`, and remove every
  `soht2ClientProperties.getCompression()` call. Update the Javadoc `@param` tags.

  In `ConnectionService`, pass `state.host.getCompression()` to all three calls.

  Update every existing call site in tests so the test sources compile, without adding new tests:
  - `ConnectionServiceTest`: update stubs and `verify` calls, using `any()` for the compression
    argument.
  - `Soht2ClientTest`: pass `new CompressionProperties()`.
  - `HttpCompressionTest`: replace `props.getCompression().setType(...)` with a
    `CompressionProperties` passed to `download(...)`.

  Verify with `./gradlew :soht2-client:test`; all existing tests and the task 1.1 tests must pass.
- [x] 2.2 In `Soht2ClientTest`, add tests for:
  - Per-call settings drive the headers. A download with `GZIP` sends `Accept-Encoding: gzip`, and
    a download with `NONE` sends `identity`.
  - An upload with `GZIP` and `minRequestSize` `2KB` sends a 4 KB body gzip-encoded with
    `Content-Encoding: gzip`, and sends a 1 KB body unencoded without `Content-Encoding`.

  Verify with `./gradlew :soht2-client:test --tests '*Soht2ClientTest'`.
- [x] 2.3 In `ConnectionServiceTest`, add a test with two connection entries: one with `NONE` on
  one local port and one with `GZIP` on another. Open a session on each port and verify, with
  argument matchers or captors, that the mocked `soht2Client` receives each entry's own
  `CompressionProperties`. Verify with
  `./gradlew :soht2-client:test --tests '*ConnectionServiceTest'`.

## 3. Defaults and documentation

- [x] 3.1 Remove the top-level `compression:` block from
  `soht2-client/src/main/resources/application.yaml`. Do not add a per-connection default, because
  that file defines no connections.
- [x] 3.2 In the `README.md` client config example, move the `compression` block, with
  `type: none` and `min-request-size: 2KB`, under the connection entry and indent it. Add a
  **Breaking change** note that `soht2.client.compression` moved to
  `soht2.client.connections[].compression` and that the old key is ignored.
- [x] 3.3 In `doc/tips-client.md`, add a "Per-Connection Compression" section with a TOC entry. Show
  an SSH entry without compression and an HTTP entry with `type: gzip`. Explain why encrypted or
  already-compressed streams gain nothing from compression. Show a before/after migration snippet.
  Verify that the property names match `HostProperties` and `CompressionProperties`.
- [x] 3.4 Search the repository for leftover top-level references to `soht2.client.compression`
  outside `openspec/changes/archive` and build outputs, for example in `requests.http` or
  `AGENTS.md`. Update any you find.

## 4. Integration check

- [x] 4.1 Run `./gradlew :soht2-client:spotlessApply :soht2-client:build`. It must pass, and the
  generated `spring-configuration-metadata.json` must not list `soht2.client.compression`. Then
  start the client against a local server with two entries, one `none` and one `gzip`. With TRACE
  logging for `net.soht2.client`, confirm that request headers show `identity` for the first
  entry's sessions and `gzip` for the second's.

## Workflow follow-up

- After review, archive the change with `/opsx-archive` so that the `client-tunneling` delta merges
  into `openspec/specs/client-tunneling/spec.md`.
