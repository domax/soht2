# Spec Delta

## ADDED Requirements

### Requirement: Tunnel payload size is bounded
The server SHALL reject an upload or simple exchange request with HTTP 413 when its body is larger
than `soht2.server.max-request-size` (default 4 MB), and SHALL NOT write any of its data to the
target.

#### Scenario: Oversized upload body
- **GIVEN** `soht2.server.max-request-size` is 1 MB
- **WHEN** a client sends an upload request with a 2 MB body
- **THEN** the server responds with HTTP 413
- **AND** no bytes from that request are written to the target

#### Scenario: Body within the limit
- **GIVEN** the default `soht2.server.max-request-size`
- **WHEN** a client sends an upload request with a 1 MB body
- **THEN** the data is written to the target as before

### Requirement: Decompressed payload size is bounded
The server SHALL reject an upload or simple exchange request with HTTP 413 when its compressed
body would decompress to more than `soht2.server.max-request-size`, and SHALL stop decompressing
once the limit is exceeded.

#### Scenario: Compression bomb
- **GIVEN** the default `soht2.server.max-request-size`
- **WHEN** a client sends a 100 KB gzip-encoded upload that would decompress to 100 MB
- **THEN** the server responds with HTTP 413
- **AND** no bytes from that request are written to the target

### Requirement: Rejected oversized requests keep the session usable
A request rejected for size SHALL NOT close the connection or advance the lane's sequence number,
so the client may send smaller data with the same sequence number.

#### Scenario: Retry after rejection
- **GIVEN** an upload request with sequence number N was rejected with HTTP 413
- **WHEN** the client sends an upload request with sequence number N and a body within the limit
- **THEN** the server writes the data to the target and acknowledges the request

### Requirement: Payload limit is validated
The server SHALL fail to start when `soht2.server.max-request-size` is zero or negative.

#### Scenario: Non-positive limit
- **WHEN** the server starts with `soht2.server.max-request-size` set to `0B`
- **THEN** startup fails with a configuration error naming the property
