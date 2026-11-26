# Proposal

## Why

The client applies one `soht2.client.compression` setting to every connection. Traffic differs
by connection. SSH, TLS, and other already-encrypted or compressed streams gain nothing from
compression and waste CPU on both ends. Plain-text protocols such as HTTP, SQL, or LDAP can benefit
from it. Today users must choose one setting for all tunnels, or run several client instances.

## What Changes

- Add a `compression` block (`type`, `min-request-size`) to each `soht2.client.connections[]`
  entry. It has the same defaults as today: `type: none`, `min-request-size: 2KB`.
- Use the session's connection compression settings for every tunnel request in that session:
  simple `exchange`, duplex `upload`, and duplex `download` (`Accept-Encoding`, request
  `Content-Encoding`, and the minimum-size threshold).
- **BREAKING**: Remove the top-level `soht2.client.compression` property. Existing configs that set
  it no longer get compression. Spring ignores the unknown key, so the client still starts. Each
  connection runs with `type: none` until the user moves the block into the connection entries.
- Update `README.md`, the client defaults in `application.yaml`, and `doc/tips-client.md` to show
  per-connection compression and the migration step.

## Assumptions

- The user asked to *move* the settings, so the plan does not keep a global fallback or default.
  Each connection is configured on its own, and an omitted block means `none`.
- The server needs no change. It already negotiates response compression per request from
  `Accept-Encoding` and decodes request bodies per request from `Content-Encoding`.
- The `CompressionType` values (`none`, `gzip`, `deflate`) and their meanings are unchanged.

## Out of Scope

- Server-side compression settings or behavior.
- Automatic detection of whether a stream is compressible.
- A warning or startup failure when the old top-level `soht2.client.compression` key is still
  present. The breaking change is documented instead.
- Changing the set of compression algorithms.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client-tunneling`: The "Tunnel compression is handled only by SOHT2" requirement is now driven by
  per-connection `soht2.client.connections[].compression` instead of the global
  `soht2.client.compression`. A new requirement states that connections in one client can use
  different compression settings.

## Impact

- `soht2-client`: `Soht2ClientProperties` (`CompressionProperties` moves under `HostProperties`;
  the top-level field is removed), `Soht2Client` (the compression settings come from the caller for
  each request), and `ConnectionService` (passes the session's host compression).
- Tests: `HttpCompressionTest`, `Soht2ClientTest`, `Soht2ClientPropertiesTest`, and
  `ConnectionServiceTest` where signatures change.
- Config and docs: `soht2-client/src/main/resources/application.yaml`, `README.md` client example,
  and `doc/tips-client.md`.
- Users: existing `application-client.yaml` files that use `soht2.client.compression` must be
  migrated.
