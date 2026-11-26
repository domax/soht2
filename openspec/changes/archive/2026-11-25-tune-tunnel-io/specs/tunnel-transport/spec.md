# Spec Delta

## ADDED Requirements

### Requirement: Target connect is bounded
The server SHALL abort a target connection attempt that does not complete within
`soht2.server.target-connect-timeout` (default `PT10S`). It SHALL answer the open request with the
same error status it uses for other target connection failures.

#### Scenario: Unreachable target
- **GIVEN** `soht2.server.target-connect-timeout` is `PT2S`
- **AND** an allowed target address that silently drops connection attempts
- **WHEN** a client opens a tunnel to that target
- **THEN** the server answers the open request with an error within about 2 s
- **AND** no connection is registered for it

#### Scenario: Reachable target
- **GIVEN** the default target connect timeout
- **WHEN** a client opens a tunnel to a listening target
- **THEN** the connection opens as before

### Requirement: Held downloads keep the connection alive
The server SHALL NOT close a connection as abandoned while a download request for it is being
held. A held download SHALL still return within `soht2.server.download-hold-timeout` when the
target sends no data.

#### Scenario: Hold longer than the abandoned check interval
- **GIVEN** a duplex connection whose target sends nothing
- **AND** the client keeps re-issuing download requests as each hold expires
- **WHEN** more than the server's abandoned-connection timeout passes
- **THEN** the connection is still open

#### Scenario: Hold expires without data
- **GIVEN** `soht2.server.download-hold-timeout` is `PT2S`
- **WHEN** a download request is issued and the target sends nothing
- **THEN** the server answers with an empty payload after about 2 s

#### Scenario: Data arrives during a hold
- **GIVEN** a download request is being held
- **WHEN** the target sends bytes
- **THEN** the server answers the held request with those bytes without waiting for the hold to
  expire

### Requirement: Available bytes are coalesced
After reading at least one byte from a socket for a payload, server and client SHALL include in
the same payload any further bytes already available without blocking, up to `read-buffer-size`.

#### Scenario: Burst from target
- **GIVEN** the target writes 3 chunks of 10 KB in quick succession before the server reads
- **WHEN** the server answers a download request
- **THEN** the payload contains all 30 KB in order

#### Scenario: Buffer limit respected
- **GIVEN** `read-buffer-size` is 64 KB and 200 KB are available on the socket
- **WHEN** a payload is produced
- **THEN** it contains at most 64 KB and the rest follows in later payloads, in order

### Requirement: Tunnel sockets disable Nagle's algorithm
The server SHALL enable `TCP_NODELAY` on target sockets, and the client SHALL enable it on accepted
local sockets.

#### Scenario: Interactive keystroke
- **GIVEN** an open tunnel to an interactive service
- **WHEN** the local application writes a single byte
- **THEN** the socket carrying it has `TCP_NODELAY` enabled on both tunnel ends

### Requirement: Duplex upload idle waiting is cheap
The client duplex upload loop SHALL NOT wake more often than once per second while the local
application sends nothing. It SHALL still enforce the idle timeout and forward local data as soon
as it arrives.

#### Scenario: Idle duplex session
- **GIVEN** an open duplex session with no local traffic
- **WHEN** 5 s pass
- **THEN** the upload loop has woken at most about 5 times

#### Scenario: Local data during idle wait
- **GIVEN** the upload loop is waiting on an idle local socket
- **WHEN** the local application writes bytes
- **THEN** the bytes are uploaded without waiting for the wake interval
