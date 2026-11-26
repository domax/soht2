# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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

### Requirement: Proxy authentication is preserved
The client SHALL authenticate to the configured proxy with Basic or NTLM authentication according
to `soht2.client.proxy.auth`, using the configured username, password, and (for NTLM) domain.

#### Scenario: Basic proxy authentication
- **GIVEN** `soht2.client.proxy.auth` is `basic` with a username and password
- **WHEN** the proxy answers the first request with a Basic authentication challenge
- **THEN** the client retries with Basic proxy credentials and the tunnel request succeeds

#### Scenario: NTLM proxy authentication
- **GIVEN** `soht2.client.proxy.auth` is `ntlm` with a username, password, and domain
- **WHEN** the proxy answers with an NTLM authentication challenge
- **THEN** the client completes the NTLM handshake and the tunnel request succeeds

### Requirement: Disabled SSL verification trusts any server certificate
When `soht2.client.disable-ssl-verification` is `true`, the client SHALL accept the server's TLS
certificate regardless of issuer or host name.

#### Scenario: Self-signed server certificate
- **GIVEN** the server uses a self-signed certificate for a host name not in the certificate
- **AND** `soht2.client.disable-ssl-verification` is `true`
- **WHEN** the client opens a tunnel
- **THEN** the TLS handshake succeeds and the tunnel opens

### Requirement: Tunnel compression is handled only by SOHT2
The client SHALL send only the `Accept-Encoding` value derived from
`soht2.client.compression.type`. It SHALL decode compressed responses once, according to their
`Content-Encoding`, so the bytes written to the local connection equal those the target sent.

#### Scenario: Compression disabled
- **GIVEN** `soht2.client.compression.type` is `none`
- **WHEN** the client sends a download request
- **THEN** the request does not advertise `gzip` or `deflate` in `Accept-Encoding`

#### Scenario: GZIP compression
- **GIVEN** `soht2.client.compression.type` is `gzip`
- **WHEN** the server answers a download request with a gzip-encoded body
- **THEN** the local application receives exactly the original target bytes
