Tips & Tricks for SOHT2 Client
==============================

<!-- TOC -->
* [Tips & Tricks for SOHT2 Client](#tips--tricks-for-soht2-client)
  * [Prerequisites](#prerequisites)
  * [Create a Client Launcher](#create-a-client-launcher)
  * [Duplex Transport](#duplex-transport)
  * [Local Bind Address](#local-bind-address)
  * [NTLM Proxies](#ntlm-proxies)
  * [See Also](#see-also)
<!-- TOC -->

Prerequisites
-------------

All the instructions below assume that you have some of the POSIX-like terminal (Linux, macOS,
etc.). Or, in the case of Windows, you can use:

* [Git Bash](https://git-scm.com/downloads)
* [Cygwin](https://www.cygwin.com/install.html)
* [Windows Subsystem for Linux (WSL)](https://docs.microsoft.com/en-us/windows/wsl/install)

Create a Client Launcher
------------------------

To simplify the process of starting, stopping and status checking of the SOHT2 client, here you can
find the instructions on how to create a simple shell script that can be used as a launcher for the
SOHT2 client.

Suppose you put the `soht2-client-X.X.X.jar` file in the dedicated folder - e.g. `~/soht2-client/`
directory and created the `application-client.yaml` file in the same directory.

Since your `application-client.yaml` file contains credentials for the SOHT2 server, you should
make sure that this file is only accessible by you:

```shell
chmod 600 ~/soht2-client/application-client.yaml
```

I'd recommend making a symlink to the JAR file, so you can update it later without changing the
service script:

```shell
cd ~/soht2-client/
ln -sf soht2-client-X.X.X.jar soht2-client.jar
```

Then copy the script [soht2-client](soht2-client) into the `~/soht2-client/` folder and make it
executable:

```shell
chmod +x ~/soht2-client/soht2-client
```

Make this script available in your `PATH` by creating a symlink to it in your `~/bin/`
directory or any other directory that is in your `PATH`:

```shell
ln -s ~/soht2-client/soht2-client ~/bin/soht2-client
```

Now you can use the `soht2-client` command to start, stop or check the status of the SOHT2 client:

```shell
soht2-client        # Show the script help message
soht2-client start  # Start the SOHT2 client in background
soht2-client status # Show the status of the SOHT2 client
soht2-client stop   # Stop the running SOHT2 client
```

Once the client is started, you can leave it running in the background. It doesn't produce any
traffic unless some connection is established through it.

Logs are stored in the `~/soht2-client/soht2-client.log` file, and you can check them for any
issues.

Duplex Transport
----------------

By default, the client uses the duplex transport (`soht2.client.transport: duplex`, see
[README](../README.md#transport)): every tunneled session has its own upload and download HTTP
requests in flight, so data flows in both directions without polling delays.

* **Connection budget.** Each active session uses up to two concurrent HTTP requests to the server,
  one of them held open while the remote host is silent. Keep `soht2.client.max-http-connections`
  (default `100`) at least twice the number of sessions you expect at the same time. The limit
  applies in every mode: direct, through an HTTP proxy, and with SSL verification disabled.
* **Timeouts.** The server holds a download for up to `soht2.server.download-hold-timeout` (`25s` by
  default). The client's HTTP read timeout (`spring.http.clients.read-timeout`, `60s` by default)
  and any HTTP proxy on the way must allow requests to last longer than that. The read timeout
  applies with `soht2.client.proxy` too.
* **Rollback.** If a proxy drops long requests, or anything else goes wrong, set
  `soht2.client.transport: simple` in `application-client.yaml` to use the original
  request-per-round-trip transport. The client also falls back to `simple` automatically when the
  server is older and does not support duplex.

Local Bind Address
------------------

Each connection's local port is bound to `127.0.0.1` by default
(`soht2.client.connections[].local-host`), so only applications on the same machine can use the
tunnel with your SOHT2 credentials.

* **Sharing a tunnel with other hosts or containers.** To let other machines, Docker containers,
  or WSL reach the tunnel, bind that connection to all interfaces:
  ```yaml
  soht2.client.connections:
  - local-host: 0.0.0.0             # or :: for both IPv4 and IPv6 on dual-stack systems
    local-port: 2222
    remote-host: ${REMOTE_HOST}
    remote-port: 22
  ```
  The client logs a warning for every port that accepts connections from other hosts. Anyone who
  can reach that port uses the tunnel with your credentials, so restrict it with a firewall.
* **IPv6-only `localhost`.** Some applications resolve `localhost` to `::1` only and then get
  "connection refused". Either connect to `127.0.0.1` explicitly, or set `local-host: ::1` for
  that connection.
* **Invalid address.** The client fails to start if `local-host` cannot be resolved.

NTLM Proxies
------------
The client supports only Basic proxy authentication. When `soht2.client.proxy.username` is set,
the client sends Basic credentials to the proxy; otherwise, it uses the proxy without
authentication.

To go through a corporate proxy that requires NTLM, run a local NTLM proxy, such as
[JNTLM](https://github.com/domax/jntlm) or [CNTLM](https://cntlm.sourceforge.net/), on the
client machine. It authenticates to the upstream NTLM proxy on your behalf and exposes a plain
HTTP proxy on a local port:

1. Install JNTLM or CNTLM and configure it with the corporate proxy as its upstream, plus your
   domain, username, and password (see the project's documentation).
2. Start it so that it listens on a local port, for example `127.0.0.1:3128`.
3. Point the SOHT2 client to the local proxy in `application-client.yaml`:
   ```yaml
   soht2.client:
     proxy:
       host: 127.0.0.1
       port: 3128
       # username/password only if the local proxy itself requires Basic authentication
   ```
4. Remove the `auth: ntlm` and `domain` keys left over from older client versions; they are
   ignored.

See Also
--------

- [README](../README.md)
- [SOHT2 Server Tips & Tricks](tips-server.md)
