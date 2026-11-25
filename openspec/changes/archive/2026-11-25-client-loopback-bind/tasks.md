# Tasks

## 1. Configuration

- [x] 1.1 Add `private String localHost = "127.0.0.1";` with Javadoc to
  `Soht2ClientProperties.HostProperties`. Also fix the existing `localPort` Javadoc, which wrongly
  says "host name or IP address". In `afterPropertiesSet`, validate each entry's `localHost` with
  `InetAddress.getByName` per design decision 3. Extend `Soht2ClientPropertiesTest`:
  - The default is `127.0.0.1`.
  - Binding `local-host: 0.0.0.0` from YAML works.
  - `no-such-host.invalid` fails with a message naming `local-host` and the value.

  Verify `./gradlew :soht2-client:test --tests '*Soht2ClientPropertiesTest'`.

## 2. Listener binding

- [x] 2.1 In `ConnectionService.connect`, bind via
  `new ServerSocket()` + `bind(new InetSocketAddress(InetAddress.getByName(host.getLocalHost()), host.getLocalPort()))`.
  Log the effective bind address at INFO, and log the wildcard warning from design decision 4.
  Keep the existing `BindException` handling unchanged. Verify the existing `ConnectionServiceTest`
  cases still pass.
- [x] 2.2 Add `ConnectionServiceTest` cases:
  - With the default `localHost`, the server socket's `getInetAddress()` is loopback, and a
    connection to a non-loopback local address (from `NetworkInterface`, skipped via
    `assumeTrue` when none exists) is refused.
  - With `localHost = "0.0.0.0"`, the bound address is the wildcard and the warning is logged.

  Verify `./gradlew :soht2-client:test`.

## 3. Documentation

- [x] 3.1 Add `local-host: 127.0.0.1` to the connection example in the `README.md` client config
  block, with a comment, and add a breaking-change note about the new loopback default. In
  `doc/tips-client.md`, add a section on sharing a tunnel with other hosts or containers
  (`local-host: 0.0.0.0`) and on IPv6-only `localhost` clients (`local-host: ::1`). Verify the
  property names match `HostProperties`.

## 4. Integration check

- [x] 4.1 Run `./gradlew :soht2-client:spotlessCheck :soht2-client:build`. Then start the client
  against a local server and confirm:
  - `lsof -nP -iTCP:<port> -sTCP:LISTEN` shows `127.0.0.1:<port>`.
  - `telnet localhost <port>` tunnels successfully.
