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

Disclaimer
----------

This project is not affiliated with or endorsed by the original SOHT project or its authors. It is
an independent implementation that aims to provide similar functionality with modern Java practices.

Also, this project is not a legal advice or a security solution. It is provided "as is" without any
warranties or guarantees. Use it at your own risk.

Please review the code and documentation carefully before using it in production environments.
For any legal or security concerns, consult with a qualified professional.

This project is intended for educational and informational purposes only.

By using this project, you agree to the terms of the [MIT License](LICENSE.txt) and then merge it
into the site-policy repo. This is to ensure that I can review and iterate on the changes before
they are made public. I appreciate your understanding and cooperation in this process.

How It Works
------------

SOHT2 establishes a tunnel between a client and a server over HTTP. The client sends socket
connection requests to the server, which then forwards these requests to the appropriate remote
hosts. The server acts as a bridge, allowing the client to communicate with remote hosts without
exposing them directly to the client.

![components.png](doc/components.png)

Below is a simplified sequence diagram illustrating the flow of a connection request from the client
to the server and how the server processes this request to establish a tunnel to a remote host:

![sequence.png](doc/sequence.png)

How to Configure And Run
------------------------

Both the client and server components can be run using Java, so on both sides you need to have Java
21 or later installed.

### Server Part

1. Get the latest release, e.g. `soht2-server-X.X.X.jar`
   from [packages](https://github.com/domax/soht2/packages).
2. Create/Edit the `application-server.yaml` file to configure the server settings, and
   place it in the same directory as the JAR file. Here is an example configuration:
    ```yaml
    soht2.server:
      socket-read-timeout: PT0.1S                  # Timeout for socket read operations
      read-buffer-size: 1MB                        # Size of the read buffer for socket connections
      user-cache-ttl: PT10M                        # Time-to-live for user cache entries
      database-path: ./soht2                       # Path to the database file
      admin-username: "${SOHT2_USR}"               # Username for the admin user
      default-admin-password: "${SOHT2_PWD}"       # Default password for the admin user
      open-api-server-url: https://${SOHT2_SERVER} # Public URL of the OpenAPI server
      abandoned-connections:                       # Settings for abandoned connections
        timeout: PT1M                              # Timeout for abandoned connections
        check-interval: PT5S                       # Interval for checking abandoned connections
    ```
   All settings are optional, but you have to define `soht2.server.database-path`,
   `soht2.server.admin-username`, and `soht2.server.default-admin-password` to create database and
   admin user for the server.
3. Run the server with the following command:
    ```shell
    java -jar soht2-server-X.X.X.jar
    ```

### Client Part

1. Get the latest release, e.g. `soht2-client-X.X.X.jar`
   from [packages](https://github.com/domax/soht2/packages).
2. Create/Edit the `application-client.yaml` file to configure the client settings, and
   place it in the same directory as the JAR file. Here is an example configuration:
    ```yaml
    soht2.client:
      url: https://${SOHT2_SERVER}/api/connection # URL of the SOHT2 server API endpoint
      socket-read-timeout: PT0.1S     # Timeout for reading from socket connections
      read-buffer-size: 1MB           # Size of the read buffer for socket connections
      username: "${SOHT2_USR}"        # Username for authentication on SOHT2 server
      password: "${SOHT2_PWD}"        # Password for authentication on SOHT2 server
      connections:                    # List of connections to establish - at least 1 item required
        - local-port: 2022            # Local port to listen on
          remote-host: ${REMOTE_HOST} # Remote host to connect to
          remote-port: 22             # Port on a remote host to connect to
      compression:                    # Compression settings for the connections
        type: none                    # Compression type for the connections (none, gzip, deflate)
        min-request-size: 2KB         # Minimum request size to apply compression
      poll:                           # Polling settings for the connections
        strategy: exponent            # Polling strategy for connections (exponent, linear, fixed)
        initial-delay: PT0.1S         # Initial delay before the first poll retry
        max-delay: PT1S               # Maximum delay between retries
        factor: 5                     # Factor for exponential backoff
      proxy:                          # HTTP or NTLM proxy configuration
        host: ${PROXY_HOST}           # If defined, the client sets up an HTTP proxy to this host
        port: ${PROXY_PORT}           # Port of the HTTP proxy host
        username: "${PROXY_USR}"      # Optional, only for authenticated HTTP or NTLM proxy
        password: "${PROXY_PWD}"      # Optional, only for authenticated HTTP or NTLM proxy
        domain: "MYORG"               # Optional, only for NTLM proxy
      disable-ssl-verification: false # Disable SSL verification for the connections
    ```
   You have to define `soht2.client.url` and at least one connection in the
   `soht2.client.connections` list. All the rest of properties are optional.<br>
   If you want to use an HTTP proxy, you can define the `soht2.client.proxy` section with
   appropriate values.<br>
   If you want to use SOCKS5 proxy, just add `-DsocksProxyHost=localhost -DsocksProxyPort=1080` to
   the list of JVM options (see next step) - but set the correct host and port, of course.<br>
   _Be aware that this implementation does not support authentication for SOCKS5 proxy._
3. Run the client with the following command:
    ```shell
    java -jar soht2-client-X.X.X.jar
    ```
   Or, with SOCKS5 proxy:
    ```shell
    java -DsocksProxyHost=localhost -DsocksProxyPort=1080 -jar soht2-client-X.X.X.jar
    ```
4. If you don't have errors, you should be able to connect to the remote host through the tunnel.
   You can test it by running `telnet localhost 2022` (or whatever local port you defined in the
   configuration) and then trying to connect to the remote host, e.g. `ssh -p 2022 user@localhost`
   for this example configuration.

Tips & Tricks
-------------

### SOHT2 Server

#### Create a Systemd Service

In case if you start the SOHT2 server on a Linux (e.g., EC2 instance), you can create a systemd
service to manage it. Suppose you put the `soht2-server-X.X.X.jar` file in the `/opt/soht2`
directory and created the `application-server.yaml` file in the same directory.

I'd recommend making a symlink to the JAR file, so you can update it later without changing the
service configuration:

```shell
cd /opt/soht2
ln -sf soht2-server-X.X.X.jar soht2-server.jar
```

Then login as a root and create the file `/etc/systemd/system/soht2-server.service` with the
following content:

```properties
[Unit]
Description=SOHT2 Server Service
After=syslog.target network.target

[Service]
EnvironmentFile=/opt/soht2/soht2-server.env
User=ec2-user
WorkingDirectory=/opt/soht2
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/soht2/soht2-server.jar
SuccessExitStatus=143
TimeoutStopSec=10
Restart=on-failure
RestartSec=5
SyslogIdentifier=soht2-server

[Install]
WantedBy=multi-user.target
```

Then enable and run the service:

```shell
sudo systemctl enable soht2-server.service
sudo systemctl start soht2-server.service
sudo systemctl daemon-reload
```

To get the service logs, you can use the following command:

```shell
journalctl -u soht2-server -f
```

#### SOHT2 Server Port

By default, the SOHT2 server listens on port `8080`. If you need to change this port, you can add
the following lines to the `application-server.yaml` file:

```yaml
server:
  port: 8080 # Change this to your desired port number
```

#### Change Context Path

In case if you want to access to your SOHT2 server from a different context path (e.g., `/soht2`) to
make it publicly accessible by URL like `https://example.com/soht2`, you may do two things:

1. Change the context path in the `application-server.yaml` file:
   ```yaml
   server:
     servlet:
       context-path: /soht2
   ```
2. If you are using a reverse proxy (like Nginx or Apache), you can configure it to forward requests
   from the desired context path to the SOHT2 server.
   E.g., for Nginx add the following configuration:
    ```nginx configuration
    location /soht2/ {
      proxy_pass http://localhost:8080/soht2/;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-Proto "https";
    }
    ```

That way, you can access the SOHT2 server at `https://example.com/soht2/api/connection`, and it
allows all the UI components to work correctly.

#### Fast Reset

If you need to reset the SOHT2 server quickly, you can delete the database file specified in the
`soht2.server.database-path` property in the `application-server.yaml` file (it has the `.mv.db`
extension - e.g. `soht2.mv.db`) and restart service. This will remove all users and connections,
allowing you to start fresh. However, be cautious as this action is irreversible and will delete all
stored data.

#### Web Consoles

In addition to the API, SOHT2 server provides the web consoles for managing users and connections:

1. SOHT2 UI (_work is in progress_)
    - URL: `https://${SOHT2_SERVER}/`
    - This console allows you to manage users and view connection history.
2. Swagger UI
    - URL: `https://${SOHT2_SERVER}/swagger-ui`
    - This console provides an interactive interface for the SOHT2 server API, allowing you to test
      endpoints and view API documentation.

TODO List
---------

- [X] Implement support for client side compression
- [X] Add proxy support for the client side
- [X] Add removal of abandoned connections on the server side
- [X] Update sequence diagram
- [X] Update README.md with more details
- [X] Finalize authentication and authorization mechanisms
- [X] Add user controller for managing users
- [X] Add OpenAPI documentation for the server API
- [ ] Add allowedTargets per user on the server side (`*:*`, `localhost:*`, `192.168.0.*:22`, etc.)
- [ ] Add connection history on the server side
- [ ] Add more tests
- [ ] Implement UI
