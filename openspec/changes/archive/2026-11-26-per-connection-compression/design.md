<!--suppress MarkdownUnresolvedLinkLabel -->
# Design

## Context

`Soht2ClientProperties` has one top-level `CompressionProperties compression` field. `Soht2Client`
reads it from the injected properties bean in three places:

- `acceptEncoding()`, for duplex `download`
- `requestExchangeEntity()`, for simple `exchange` and duplex `upload`
- the `min-request-size` threshold check in `requestExchangeEntity()`

`Soht2Client` is a singleton `@Service` and its methods receive only a connection id. It does not
know which `HostProperties` entry a session belongs to. `ConnectionService` does know: every
`SessionState` holds `final HostProperties host`.

The server needs no change. It encodes each response from that request's `Accept-Encoding` and
decodes each request body from its `Content-Encoding`. See proposal.md (Why and Assumptions).

## Goals / Non-Goals

**Goals:**
- Give each `connections[]` entry its own compression settings.
- Keep the wire protocol and server behavior unchanged.
- Fail fast on an invalid per-connection compression config, the same way `local-host` validation
  does.

**Non-Goals:**
- A global default that entries inherit (see Decision 2).
- Detecting the old top-level key at startup.

## Decisions

### 1. Nest `CompressionProperties` inside `HostProperties`

Add `private CompressionProperties compression = new CompressionProperties();` to `HostProperties`,
and remove the top-level `compression` field from `Soht2ClientProperties`. Keep
`CompressionProperties` as a static nested class of `Soht2ClientProperties`, with its current
fields, defaults (`NONE`, `2KB`), and Javadoc. Spring binds
`soht2.client.connections[n].compression.type` and `.min-request-size` without extra code. The
configuration processor already in `soht2-client/build.gradle` regenerates the metadata.

*Alternative:* flat `compression-type` and `compression-min-request-size` fields on `HostProperties`.
This was rejected because it breaks the existing `compression.*` shape users already know, and it
duplicates the Javadoc.

### 2. No global fallback

The request is to *move* the setting. A global default plus per-entry overrides would need
nullable fields and merge logic, and it would hide which setting applies to a tunnel. Each entry
stands alone. A missing block means `none`, which is today's default. The old top-level key is
ignored because `@ConfigurationProperties` ignores unknown fields by default, so the client still
starts. Users find out about the change from the README breaking-change note.

### 3. Pass compression settings into `Soht2Client` per call

Change the signatures:

- `exchange(UUID connectionId, CompressionProperties compression, byte[] data)`
- `upload(UUID connectionId, long seq, CompressionProperties compression, byte[] data)`
- `download(UUID connectionId, long seq, CompressionProperties compression)`

`acceptEncoding(...)` and `requestExchangeEntity(...)` take the `CompressionProperties` argument
and no longer read `soht2ClientProperties.getCompression()`. `open` and `close` are unchanged
because they send no tunnel payload. `ConnectionService` passes `state.host.getCompression()` from
the simple `exchange` loop, the duplex `upload` loop, and the duplex `download` loop.

*Alternatives:*
- Pass `HostProperties`. This was rejected because `Soht2Client` needs only the compression
  settings, and a narrower parameter is easier to test.
- Keep a `connectionId → compression` map inside `Soht2Client`, filled on `open`. This was rejected
  because it adds lifecycle state and cleanup to a stateless client.
- Create one `Soht2Client` per connection entry. This was rejected because it changes bean wiring
  for no benefit.

### 4. Validate per-entry compression at startup

In `Soht2ClientProperties.afterPropertiesSet`, for each connection:

- Assert that `compression` is not null. This rejects an explicit `compression:` with no value.
  Use the message `soht2.client.connections[].compression must not be null`.
- Assert that `compression.type` is not null and `compression.minRequestSize` is not null and not
  negative. Use messages that name `soht2.client.connections[].compression.*`.

This matches the existing `validateLocalHost` style.

### 5. `HostProperties` equality

`HostProperties` uses Lombok `@Data` and lives in a `HashSet`. Its `equals` and `hashCode` now
include `compression`, and `CompressionProperties` is `@Data` too, so the values are well-defined.
Two entries with the same local port but different compression are still distinct set members.
Binding the port twice fails at bind time, as it does today, so no extra handling is needed.

## Risks / Trade-offs

- [A user keeps `soht2.client.compression` and silently loses compression.] → The tunnel still
  works because only bandwidth changes. Add a **Breaking change** note and a before/after example
  to `README.md` and `doc/tips-client.md`.
- [Public method signatures of `Soht2Client` change.] → It is used only inside `soht2-client`:
  `ConnectionService` and tests. Update all call sites and the Mockito stubs in
  `ConnectionServiceTest` (`any()` matchers) in the same task.
- [A long `connections` list makes the YAML longer.] → Defaults stay `none`/`2KB`, so only entries
  that need compression add a block.

## Migration Plan

1. Move the old `soht2.client.compression` block under each connection entry that should use
   compression, for example HTTP targets, and remove the old block. Leave SSH and other encrypted
   targets without a block.
2. Rollback: run the previous client JAR with the previous config. The server is unchanged, so
   either client version works against it.
