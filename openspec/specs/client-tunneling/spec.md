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
the SOHT2 server (default 100) in every transport mode: direct, through a proxy, and with SSL
verification disabled.

#### Scenario: More sessions than the former limit
- **GIVEN** the client is configured with a proxy and default `max-http-connections`
- **WHEN** three tunneled sessions exchange data at the same time
- **THEN** all three exchanges are sent to the server concurrently without waiting for a free
  HTTP connection

#### Scenario: Custom limit
- **GIVEN** `soht2.client.max-http-connections` is set to 4
- **WHEN** the client creates its HTTP transport
- **THEN** at most 4 concurrent HTTP connections to the server are used

#### Scenario: Direct mode honors the limit
- **GIVEN** no proxy is configured, SSL verification is enabled, and
  `soht2.client.max-http-connections` is 10
- **WHEN** 5 duplex sessions are open at the same time
- **THEN** all 10 upload and download requests are in flight concurrently
- **AND** no more than 10 HTTP connections to the server are open

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

### Requirement: HTTP connections are reused
The client SHALL reuse idle HTTP connections to the server for later tunnel requests in every
transport mode, instead of opening a new connection per request.

#### Scenario: Sequential requests
- **GIVEN** a duplex session that has completed several download requests
- **WHEN** the client sends the next download request
- **THEN** the request uses an existing connection to the server when one is idle

### Requirement: Read timeout applies in every mode
The client SHALL apply `spring.http.clients.read-timeout` to tunnel requests whether or not a
proxy is configured or SSL verification is disabled.

#### Scenario: Proxy mode with a held download
- **GIVEN** a proxy is configured and `spring.http.clients.read-timeout` is `PT60S`
- **WHEN** the server holds a download request for 25 s and then answers
- **THEN** the client receives the response without a timeout

#### Scenario: Unresponsive server through a proxy
- **GIVEN** a proxy is configured and `spring.http.clients.read-timeout` is `PT5S`
- **WHEN** the server does not answer a request for 10 s
- **THEN** the client request fails with a timeout after about 5 s

### Requirement: Proxy authentication is Basic only
When `soht2.client.proxy.username` is set, the client SHALL authenticate to the configured
proxy with Basic authentication, using the configured username and password. No
authentication-type setting is required or read. The client SHALL NOT answer NTLM or other
non-Basic proxy authentication challenges.

#### Scenario: Basic proxy authentication
- **GIVEN** a proxy username and password are set
- **WHEN** the proxy answers the first request with a Basic authentication challenge
- **THEN** the client retries with Basic proxy credentials and the tunnel request succeeds

#### Scenario: Proxy without credentials
- **GIVEN** a proxy host is set and no proxy username is set
- **WHEN** the client sends a tunnel request through a proxy that does not require
  authentication
- **THEN** the request succeeds without any proxy credentials

#### Scenario: Legacy auth keys are ignored
- **GIVEN** a configuration that still contains `soht2.client.proxy.auth` or
  `soht2.client.proxy.domain`
- **WHEN** the client starts
- **THEN** startup succeeds and those keys have no effect on proxy authentication

#### Scenario: NTLM-only proxy
- **GIVEN** a proxy that offers only NTLM authentication
- **WHEN** the client sends a tunnel request through it
- **THEN** the client does not attempt an NTLM handshake and the request fails with the proxy's
  `407 Proxy Authentication Required` response

#### Scenario: NTLM proxy through a local NTLM proxy
- **GIVEN** a local NTLM proxy (for example JNTLM or CNTLM) that authenticates to the upstream
  NTLM proxy
- **AND** `soht2.client.proxy` points to the local proxy, without credentials or with Basic
  credentials
- **WHEN** the client opens a tunnel
- **THEN** the tunnel request succeeds

### Requirement: Disabled SSL verification trusts any server certificate
When `soht2.client.disable-ssl-verification` is `true`, the client SHALL accept the server's TLS
certificate regardless of issuer or host name.

#### Scenario: Self-signed server certificate
- **GIVEN** the server uses a self-signed certificate for a host name not in the certificate
- **AND** `soht2.client.disable-ssl-verification` is `true`
- **WHEN** the client opens a tunnel
- **THEN** the TLS handshake succeeds and the tunnel opens

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
