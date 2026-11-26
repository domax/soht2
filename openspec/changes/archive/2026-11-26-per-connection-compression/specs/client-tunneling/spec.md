# Spec Delta

## MODIFIED Requirements

### Requirement: Tunnel compression is handled only by SOHT2
For each tunnel request, the client SHALL send only the `Accept-Encoding` value derived from the
`compression.type` of the session's connection entry (`soht2.client.connections[].compression`). It
SHALL decode compressed responses once, according to their `Content-Encoding`, so the bytes written
to the local connection equal those the target sent.

#### Scenario: Compression disabled
- **GIVEN** a connection entry whose `compression.type` is `none`
- **WHEN** the client sends a download request for a session of that entry
- **THEN** the request does not advertise `gzip` or `deflate` in `Accept-Encoding`

#### Scenario: GZIP compression
- **GIVEN** a connection entry whose `compression.type` is `gzip`
- **WHEN** the server answers a download request for a session of that entry with a gzip-encoded
  body
- **THEN** the local application receives exactly the original target bytes

## ADDED Requirements

### Requirement: Compression is configurable per connection
Each `soht2.client.connections[]` entry SHALL accept its own `compression.type` (`none`, `gzip`,
`deflate`) and `compression.min-request-size`. The client SHALL apply an entry's settings only to
sessions accepted on that entry's local port.

#### Scenario: Different compression for different entries
- **GIVEN** one entry for an SSH target with `compression.type: none`
- **AND** another entry for an HTTP target with `compression.type: gzip`
- **WHEN** an application opens a session on each entry's local port and sends data
- **THEN** requests for the SSH session advertise `identity` only and carry no `Content-Encoding`
- **AND** requests for the HTTP session advertise `gzip`

#### Scenario: Request body compressed at or above the threshold
- **GIVEN** an entry with `compression.type: gzip` and `compression.min-request-size: 2KB`
- **WHEN** the client sends an upload or simple exchange request with a 4 KB body for that entry
- **THEN** the request body is gzip-encoded and has `Content-Encoding: gzip`

#### Scenario: Request body below the threshold
- **GIVEN** an entry with `compression.type: gzip` and `compression.min-request-size: 2KB`
- **WHEN** the client sends an upload or simple exchange request with a 1 KB body for that entry
- **THEN** the request body is sent unencoded and without `Content-Encoding`

### Requirement: Connection compression defaults to none
When a connection entry has no `compression` block, or its block omits a field, the client SHALL use
`type: none` and `min-request-size: 2KB` for the missing values.

#### Scenario: Entry without compression settings
- **GIVEN** a connection entry with no `compression` block
- **WHEN** the client tunnels a session for that entry
- **THEN** no request for that session advertises `gzip` or `deflate` or carries
  `Content-Encoding`

#### Scenario: Partial compression settings
- **GIVEN** a connection entry with `compression.type: gzip` and no `min-request-size`
- **WHEN** the client loads its configuration
- **THEN** that entry's `min-request-size` is `2KB`

### Requirement: Global compression setting is not used
The client SHALL NOT apply a top-level `soht2.client.compression` setting to any connection. Such a
key SHALL NOT prevent the client from starting.

#### Scenario: Legacy top-level compression key
- **GIVEN** a configuration with `soht2.client.compression.type: gzip`
- **AND** a connection entry with no `compression` block
- **WHEN** the client starts and tunnels a session for that entry
- **THEN** the client starts successfully
- **AND** no request for that session advertises `gzip` or `deflate`
