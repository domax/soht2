# Proposal

## Why

The client binds each configured local port with `new ServerSocket(port)`, which listens on all
network interfaces. Any host that can reach the user's machine can then use the tunnel with the
user's SOHT2 credentials and reach internal targets. Binding to loopback by default makes the
tunnel available only to applications on the same machine, which is the documented use case
(`telnet localhost ${LOCAL_PORT}`, `ssh -p ${LOCAL_PORT} user@localhost`).

## What Changes

- Add an optional per-connection `local-host` setting
  (`soht2.client.connections[].local-host`) that selects the interface address the local port is
  bound to.
- **BREAKING:** The default bind address changes from all interfaces to `127.0.0.1`. Users who
  intentionally share a tunnel with other machines or containers must set `local-host: 0.0.0.0`
  (or `::`) explicitly.
- The client fails to start when `local-host` cannot be resolved to a local address.
- The client logs the effective bind address for each listener, and a warning when a listener is
  bound to a wildcard (all-interfaces) address.

Assumptions (decided without user input; revisit in review if needed):

- Non-loopback binding remains supported through explicit configuration. Rejecting it entirely
  would break legitimate shared-gateway setups.
- The default is IPv4 loopback (`127.0.0.1`), not `localhost`. Name resolution of `localhost` is
  platform-dependent and returns a single address.

Out of scope:

- Authenticating local applications to the client.
- Binding one connection entry to several addresses (for example, both `127.0.0.1` and `::1`).

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `client-tunneling`: Local listeners bind to a configurable address that defaults to loopback.
  The address is validated at startup.

## Impact

- **Client:** `Soht2ClientProperties.HostProperties` (new `localHost` field), its validation in
  `afterPropertiesSet`, the bind call in `ConnectionService.connect`, and the related tests.
- **Docs:** `README.md` (client config block, plus a breaking-change note), `doc/tips-client.md`
  (sharing a tunnel with other hosts or containers), and the `doc/soht2-client` launcher if it
  documents local ports.
- **Compatibility:** Existing configs keep working for same-machine use. Remote, LAN, or Docker
  bridge users must add `local-host: 0.0.0.0`.
