# client-tunneling Specification

## Purpose

Defines how the SOHT2 client accepts connections on configured local ports and relays each one
through the server, including listener lifetime, session independence, and transport capacity.

## Requirements

### Requirement: Local listener stays bound
The client SHALL bind each configured local port once at startup and keep accepting new
connections on it until the client is stopped, without closing and re-binding the port between
connections.

#### Scenario: Connections arriving while a tunnel is being opened
- **GIVEN** the client is running with a configured local port
- **WHEN** a second application connection arrives on that port while the tunnel for the first
  connection is still being opened on the server
- **THEN** the second connection is accepted and gets its own tunnel
- **AND** neither connection is reset or refused

#### Scenario: Port released on stop
- **GIVEN** the client is running with a configured local port
- **WHEN** the client connections are stopped
- **THEN** the local port is released and can be bound again by a new client start

### Requirement: Sessions on the same port are independent
The client SHALL relay data for each accepted connection independently, so that a connection
waiting for data or for a server response does not delay data transfer of other connections on
the same local port.

#### Scenario: Concurrent sessions on one port
- **GIVEN** two application connections are open on the same local port
- **WHEN** the first connection is idle and the second connection sends data
- **THEN** the second connection's data is relayed to the server without waiting for the first
  connection's read timeout or server exchange to complete

### Requirement: Failed tunnel open closes the local connection
The client SHALL close an accepted local connection when the server tunnel for it cannot be
opened, and SHALL continue accepting further connections on that port.

#### Scenario: Server rejects tunnel open
- **GIVEN** the client is running with a configured local port
- **WHEN** an application connects and the server rejects or fails the tunnel open request
- **THEN** the application's connection is closed by the client
- **AND** a subsequent application connection on the same port is accepted

### Requirement: Configurable concurrent HTTP connections
The client SHALL allow up to `soht2.client.max-http-connections` concurrent HTTP connections to
the SOHT2 server (default 100) when using its proxy or SSL-verification-disabled transport.

#### Scenario: More sessions than the former limit
- **GIVEN** the client is configured with a proxy and default `max-http-connections`
- **WHEN** three tunneled sessions exchange data at the same time
- **THEN** all three exchanges are sent to the server concurrently without waiting for a free
  HTTP connection

#### Scenario: Custom limit
- **GIVEN** `soht2.client.max-http-connections` is set to 4
- **WHEN** the client creates its HTTP transport
- **THEN** at most 4 concurrent HTTP connections to the server are used

### Requirement: Session count not bound by CPU count
The client SHALL run each listener and tunneled session on its own thread of execution, so that
the number of concurrently served sessions is not limited by the number of CPU cores, whether or
not virtual threads are enabled.

#### Scenario: Platform threads with many sessions
- **GIVEN** virtual threads are disabled and the host has fewer CPU cores than the number of
  configured ports plus open sessions
- **WHEN** applications open connections on all configured ports
- **THEN** every connection is accepted and relays data

### Requirement: Client tunnel requests use the non-browser contract

The SOHT2 client SHALL identify its tunnel HTTP requests using the server-supported non-browser
contract and SHALL continue sending its configured Basic authentication credentials.

#### Scenario: Client starts a tunnel session

- **GIVEN** the client is configured with a server URL and valid credentials
- **WHEN** it opens, exchanges, and closes a tunnel
- **THEN** each request uses the non-browser contract
- **AND** the complete tunnel session succeeds without a CSRF cookie or token

### Requirement: Client behavior is unchanged by browser CSRF protection

Enabling browser CSRF protection SHALL NOT require the client to persist browser cookies, fetch a
CSRF token, or change the tunnel payload and response formats.

#### Scenario: Existing tunnel data exchange

- **GIVEN** the client exchanges compressed or uncompressed tunnel data
- **WHEN** the server has browser CSRF protection enabled
- **THEN** the client receives and decodes the same response bytes as before
- **AND** tunnel connection concurrency and lifecycle behavior remain unchanged

### Requirement: Local listeners bind to loopback by default
When a connection entry has no `local-host`, the client SHALL bind its local port on `127.0.0.1`
only, so connections from other hosts are not accepted.

#### Scenario: Same-machine application
- **GIVEN** a connection entry with `local-port: 2222` and no `local-host`
- **WHEN** an application on the same machine connects to `127.0.0.1:2222`
- **THEN** the connection is accepted and tunneled

#### Scenario: Connection from a non-loopback address
- **GIVEN** a connection entry with `local-port: 2222` and no `local-host`
- **WHEN** a client connects to port 2222 on one of the machine's non-loopback addresses
- **THEN** the connection is refused

### Requirement: Bind address is configurable per connection
The client SHALL bind a connection entry's local port on the address given by its `local-host`
setting, which may be a specific interface address or a wildcard address.

#### Scenario: Explicit wildcard bind
- **GIVEN** a connection entry with `local-port: 2222` and `local-host: 0.0.0.0`
- **WHEN** a client connects to port 2222 on a non-loopback address of the machine
- **THEN** the connection is accepted and tunneled

#### Scenario: Different addresses for different entries
- **GIVEN** one entry with `local-host: 127.0.0.1` and `local-port: 2222`
- **AND** another entry with `local-host: 0.0.0.0` and `local-port: 3333`
- **WHEN** the client starts
- **THEN** port 2222 accepts only loopback connections and port 3333 accepts connections on all
  interfaces

### Requirement: Invalid bind address fails startup
The client SHALL fail to start with a configuration error naming
`soht2.client.connections[].local-host` when a `local-host` value cannot be resolved to an
address.

#### Scenario: Unresolvable bind address
- **WHEN** the client starts with a connection entry whose `local-host` is `no-such-host.invalid`
- **THEN** startup fails with an error naming `local-host` and the offending value

### Requirement: Wildcard binding is reported
The client SHALL log a warning naming the port when a local listener is bound to a wildcard
address.

#### Scenario: Wildcard warning
- **GIVEN** a connection entry with `local-host: 0.0.0.0`
- **WHEN** its listener is bound
- **THEN** the client logs a warning that the port accepts connections from other hosts
