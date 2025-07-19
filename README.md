SOHT2 - Socket Over HTTP Tunnel 2
=================================

SOHT2 is a Java-based API that provides a secure and efficient way to tunnel socket connections over
HTTP. It is designed to work seamlessly with various applications that require socket communication,
such as web services, IoT devices, and more.

This project was inspired by the [original SOHT project](https://www.ericdaugherty.com/dev/soht/)
but has been rewritten using modern Java to improve performance, reliability, and maintainability.

SOHT2 leverages modern Java features and best practices to deliver a robust and flexible solution
for socket tunneling needs. It is built with a focus on simplicity and ease of use, making it
accessible for users of all skill levels.

It supports both client and server modes, allowing you to create a tunnel for outgoing connections
or to accept incoming connections through an optional HTTP proxy.

How It Works
------------

![components.png](doc/components.png)

How to Run
----------

Both the client and server components can be run using Java, so on both sides you need to have Java
21 or later installed.

### Server Part

1. Get the latest release, e.g. `soht2-server-X.X.X.jar`
2. Create/Edit the `application-server.yaml` file to configure the server settings, and
   place it in the same directory as the JAR file. Here is an example configuration:
    ```yaml
    soht2.server:
      socket-timeout: PT0.1S
      read-buffer-size: 64KB
    ```
   It is an optional file, and if it is not present, the server will use default values.
3. Run the server with the following command:
    ```shell
    java -Dspring.profiles.active=server -jar soht2-server-X.X.X.jar
    ```

### Client Part

1. Get the latest release, e.g. `soht2-client-X.X.X.jar`
2. Create/Edit the `application-client.yaml` file to configure the client settings, and
   place it in the same directory as the JAR file. Here is an example configuration:
    ```yaml
    soht2.client:
      url: https://my-soht2-server/api/connection
      connections:
        - local-port: 2022
          remote-host: my-remote-host
          remote-port: 22
      compression.type: none
      proxy:
        host: my-proxy-host     # Optional, but when defined, the client will initiate an HTTP proxy
        port: my-proxy-port     # Optional, default is 3128
        username: "${USERNAME}" # Optional, only for authenticated HTTP or NTLM proxy
        password: "${PASSWORD}" # Optional, only for authenticated HTTP or NTLM proxy
        domain: "MYORG"         # Optional, only for NTLM proxy
    ```
   You have to define `soht2.client.url` and at least one connection in the
   `soht2.client.connections` list.<br>
   If you want to use an HTTP proxy, you can define the `soht2.client.proxy` section with
   appropriate
   values.<br>
   If you want to use SOCKS5 proxy, just add `-DsocksProxyHost=localhost -DsocksProxyPort=1080` to
   the list of JVM options (see next step) - but set the appropriate host and port, of course. Be
   aware that this implementation does not support authentication for SOCKS5 proxy.
3. Run the client with the following command:
    ```shell
    java -Dspring.profiles.active=client -jar soht2-client-X.X.X.jar
    ```
4. If you don't have errors, you should be able to connect to the remote host through the tunnel.
   You can test it by running `telnet localhost 2022` (or whatever local port you defined in the
   configuration) and then trying to connect to the remote host, e.g. `ssh -p 2022 user@localhost`
   for this example configuration.

TODO List
---------

- [X] Implement support for client side compression
- [X] Add proxy support for the client side
- [ ] Add removal of abandoned connections on the server side
- [ ] Finalize authentication and authorization mechanisms
- [ ] Update sequence diagram
- [ ] Update README.md with more details
- [ ] Add more tests
- [ ] Implement UI
