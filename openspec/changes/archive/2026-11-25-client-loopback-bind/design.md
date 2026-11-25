<!--suppress MarkdownUnresolvedLinkLabel -->
# Design

## Context

- `ConnectionService.connect` creates `new ServerSocket(host.getLocalPort())`. The JDK binds this
  to the wildcard address with a default backlog of 50.
- `HostProperties` has `localPort`, `remoteHost`, and `remotePort`. It is a Lombok `@Data` class
  stored in a `Set`, so a new field takes part in `equals` and `hashCode`.
- Client tests connect with `new Socket("localhost", port)`. On the JDK this resolves to
  `127.0.0.1` first unless `java.net.preferIPv6Addresses=true` is set.

## Goals / Non-Goals

**Goals:** a loopback-only default, explicit opt-in for wider exposure, and early failure for a
bad address.

**Non-Goals:** dual-stack loopback listeners (`127.0.0.1` and `::1` together), or a global
default bind address property.

## Decisions

1. **Per-connection `localHost` (String, default `"127.0.0.1"`).** The name mirrors the existing
   `remoteHost`. Per-entry configuration lets one client expose one port and keep the others
   private.
   - Alternative: a global `soht2.client.local-host`. Rejected because the per-entry form covers
     the global case at little cost, and adding a global default later is non-breaking.
2. **Bind call.** Use `new ServerSocket()` followed by
   `bind(new InetSocketAddress(InetAddress.getByName(localHost), localPort))`, leaving the socket
   options at their current defaults. This form keeps the address explicit and lets the effective
   address be logged.
3. **Validation.** In `Soht2ClientProperties.afterPropertiesSet`, check each connection's
   `localHost` with `InetAddress.getByName`. Literal IPs resolve without DNS. On failure, throw
   `IllegalArgumentException` with the message
   `soht2.client.connections[].local-host '<value>' cannot be resolved`.
   - Startup DNS for hostnames is acceptable because it happens once.
   - The check does not require the address to belong to a local interface. A non-local address
     fails at bind with the existing `connect` error logging.
4. **Wildcard warning.** After binding, if `serverSocket.getInetAddress().isAnyLocalAddress()`,
   log `WARN connect: port {} accepts connections from other hosts (local-host={})`.

## Risks / Trade-offs

- [Applications that resolve `localhost` to `::1` only (for example, some Node.js versions) get
  "connection refused"] → Document setting `local-host: ::1` (or `::` for both families on
  dual-stack systems) in `doc/tips-client.md`.
- [Docker or WSL setups reaching the client from another network namespace break silently after
  upgrade] → Add a breaking-change note in the README, and a wildcard-bind example in
  `doc/tips-client.md`.
- [Lombok `equals` now includes `localHost`] → Two entries with the same port and different hosts
  are now distinct. That is the intended behavior. If both are wildcard or overlapping, binding
  fails with the existing "Address already in use" handling.

## Migration Plan

Users who share tunnels with other hosts add `local-host: 0.0.0.0` to each such entry before
upgrading. Rolling back the client JAR restores the old behavior. Older versions ignore the extra
`local-host` key, because `@ConfigurationProperties` binding ignores unknown fields by default.
