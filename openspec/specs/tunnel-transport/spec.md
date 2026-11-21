# tunnel-transport Specification

## Purpose

Defines how tunneled bytes travel between the SOHT2 client and server over HTTP: independent
upload and download lanes, prompt server-driven delivery, retry safety, end-of-stream signaling,
and compatibility with the simple single-request exchange.

## Requirements

### Requirement: Upload does not wait for download
In the duplex transport, the client SHALL send bytes read from a local connection to the server
without waiting for a pending download request of the same session to complete.

#### Scenario: Upload while a download is held
- **GIVEN** a duplex session whose download request is being held by the server because the
  target has sent nothing
- **WHEN** the local application writes data to the local connection
- **THEN** the data reaches the target before the held download request completes

### Requirement: Target data is delivered without client polling delay
In the duplex transport, the server SHALL respond to a pending download request as soon as target
data is available, rather than waiting for a client poll interval.

#### Scenario: Server-initiated data after an idle period
- **GIVEN** a duplex session that has been idle for longer than the client's maximum poll delay
- **WHEN** the target sends data
- **THEN** the data is written to the local connection within 100 ms plus one network round trip,
  independent of the poll strategy settings

### Requirement: Idle download requests are bounded
The server SHALL complete a held download request with an empty successful response once
`soht2.server.download-hold-timeout` (default 25 s) elapses without target data, and the client
SHALL then issue the next download request.

#### Scenario: Hold timeout with no data
- **GIVEN** a duplex session whose target sends nothing
- **WHEN** a download request has been held for `download-hold-timeout`
- **THEN** the server responds successfully with an empty body
- **AND** the session stays open and the client issues a new download request

#### Scenario: Custom hold timeout
- **GIVEN** `soht2.server.download-hold-timeout` is set to 5 s
- **WHEN** a download request is held with no target data
- **THEN** the server responds with an empty body after about 5 s

### Requirement: Hold timeout is validated
The server SHALL fail to start when `soht2.server.download-hold-timeout` is zero or negative.

#### Scenario: Non-positive hold timeout
- **WHEN** the server starts with `soht2.server.download-hold-timeout` set to `PT0S`
- **THEN** startup fails with a configuration error naming the property

### Requirement: Idle timeout counts traffic in both lanes
In the duplex transport, the client SHALL apply `disconnect-on-idle-timeout` to a session only
when neither its upload lane nor its download lane has carried data for that duration; empty
download responses SHALL NOT count as traffic.

#### Scenario: One-way download keeps the session alive
- **GIVEN** `disconnect-on-idle-timeout` is 1 minute
- **WHEN** the target streams data to the client for 5 minutes while the local application sends
  nothing
- **THEN** the session stays open

#### Scenario: No traffic in either lane
- **GIVEN** `disconnect-on-idle-timeout` is 1 minute
- **WHEN** neither side sends any data for 1 minute, while download requests keep returning empty
- **THEN** the client closes the session and the local connection

### Requirement: Byte order is preserved per direction
The transport SHALL deliver bytes in each direction in the order they were produced, without
loss or duplication, while the session is open.

#### Scenario: Large bidirectional transfer
- **GIVEN** a duplex session
- **WHEN** the local application and the target each send several megabytes concurrently
- **THEN** each side receives exactly the bytes the other side sent, in the same order

### Requirement: Retried requests are idempotent
The server SHALL treat a repeated upload or download request carrying the same sequence number as
the previous request of that lane as a retry: an upload retry SHALL NOT write data to the target
again, and a download retry SHALL replay the original result exactly (the same bytes, or an empty
response if the original was empty) without reading new data from the target. The client SHALL
use the next sequence number for every new download request, including after an empty response.

#### Scenario: Download response lost in transit
- **GIVEN** the server answered download request number N with some bytes
- **WHEN** the client does not receive the response and repeats download request number N
- **THEN** the server returns the same bytes again
- **AND** no target bytes are lost or duplicated

#### Scenario: Empty download retried after new data arrives
- **GIVEN** the server answered download request number N with an empty response
- **AND** the target has since sent new bytes
- **WHEN** the client repeats download request number N
- **THEN** the server returns an empty response again
- **AND** the new bytes are returned only for download request number N+1

#### Scenario: Next long poll after an empty response
- **GIVEN** the client received an empty response for download request number N
- **WHEN** the client issues its next download request
- **THEN** that request carries sequence number N+1

#### Scenario: Upload retried after a timeout
- **GIVEN** the server applied upload request number N
- **WHEN** the client repeats upload request number N with the same body
- **THEN** the server acknowledges it without writing the bytes to the target a second time

#### Scenario: Out-of-order sequence
- **WHEN** a request arrives whose sequence number is neither the previous nor the next expected
  number for its lane
- **THEN** the server rejects it with HTTP 409 and the session is closed

### Requirement: Target end-of-stream is signaled
When the target closes its connection, the server SHALL deliver any remaining target bytes and
then answer the next download request with an end-of-stream response, and the client SHALL close
the local connection on receiving it.

#### Scenario: Target closes after sending data
- **GIVEN** a duplex session
- **WHEN** the target sends final data and closes its connection
- **THEN** the local application receives all final data
- **AND** the local connection is then closed by the client

### Requirement: Duplex endpoints enforce connection ownership
The server SHALL apply to the upload and download requests the same authentication and
connection-owner authorization as the simple exchange request.

#### Scenario: Another user's connection
- **GIVEN** a duplex session opened by user A
- **WHEN** user B sends an upload or download request for that session
- **THEN** the server responds with HTTP 403 and no data is read from or written to the target

### Requirement: Transport is selectable on the client
The client SHALL use the duplex transport by default and SHALL use the simple single-request
exchange when `soht2.client.transport` is set to `simple`.

#### Scenario: Simple transport forced
- **GIVEN** `soht2.client.transport` is `simple`
- **WHEN** a local connection is tunneled
- **THEN** all data is exchanged through the simple exchange request, with the configured poll
  strategy applied

#### Scenario: Invalid transport value
- **WHEN** `soht2.client.transport` is set to an unsupported value
- **THEN** the client fails to start with a configuration error naming the property

### Requirement: Client falls back to simple for older servers
When the duplex transport is selected and the server does not support the duplex requests, the
client SHALL tunnel the session with the simple exchange instead, without losing data or closing
the local connection.

#### Scenario: Server without duplex support
- **GIVEN** the client uses the default duplex transport
- **AND** the server only supports the simple exchange request
- **WHEN** a local connection is tunneled
- **THEN** data is exchanged successfully through the simple exchange request

### Requirement: Simple exchange remains supported
The server SHALL continue to accept the simple single-request exchange with unchanged request and
response format, so clients of earlier versions keep working.

#### Scenario: Earlier client version
- **GIVEN** a client that only uses the simple exchange request
- **WHEN** it opens a connection and exchanges data with the new server
- **THEN** the data is relayed exactly as before this change
