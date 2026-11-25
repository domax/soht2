# Spec Delta

## ADDED Requirements

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
