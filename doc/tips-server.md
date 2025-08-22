Tips & Tricks for SOHT2 Server
==============================

<!-- TOC -->
* [Tips & Tricks for SOHT2 Server](#tips--tricks-for-soht2-server)
  * [Create a Systemd Service](#create-a-systemd-service)
  * [SOHT2 Server Port](#soht2-server-port)
  * [Change Context Path](#change-context-path)
  * [Fast Reset](#fast-reset)
  * [Alternative Database](#alternative-database)
  * [Web Consoles](#web-consoles)
  * [See Also](#see-also)
<!-- TOC -->

Create a Systemd Service
------------------------

In case if you start the SOHT2 server on a Linux (e.g., EC2 instance), you can create a systemd
service to manage it. Suppose you put the `soht2-server-X.X.X.jar` file in the `/opt/soht2`
directory and created the `application-server.yaml` file in the same directory.

I'd recommend making a symlink to the JAR file, so you can update it later without changing the
service configuration:

```shell
cd /opt/soht2
ln -sf soht2-server-X.X.X.jar soht2-server.jar
```

Create an environment file `/opt/soht2/soht2-server.env` with the following content,
where you need to replace the `SOHT2_SERVER`, `SOHT2_USR`, and `SOHT2_PWD` with your actual values:

```shell
JAVA_OPTS="-Xmx256m -server"      # Java options for the SOHT2 server
SOHT2_SERVER="soht2.example.com"  # Public domain name of the SOHT2 server
SOHT2_USR="admin"                 # Username for the admin user
SOHT2_PWD="password"              # Password for the admin user
```

Make sure the environment file is available to the service user only (e.g., `ec2-user`):

```shell
chown ec2-user:ec2-user /opt/soht2/soht2-server.env
chmod 600 /opt/soht2/soht2-server.env
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

SOHT2 Server Port
-----------------

By default, the SOHT2 server listens on port `8080`. If you need to change this port, you can add
the following lines to the `application-server.yaml` file:

```yaml
server:
  port: 8080 # Change this to your desired port number
```

Change Context Path
-------------------

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

Fast Reset
----------

If you need to reset the SOHT2 server quickly, you can delete the database file specified in the
`soht2.server.database-path` property in the `application-server.yaml` file (it has the `.mv.db`
extension - e.g. `soht2.mv.db`) and restart service. This will remove all users and connections,
allowing you to start fresh. However, be cautious as this action is irreversible and will delete all
stored data.

Alternative Database
--------------------

If you want to use a different database instead of the default H2DB, you can configure it like that:

1. Make sure your database has a JDBC driver, and a database is available from the SOHT2 server.
2. Download the corresponding JDBC driver and put it into the directory where SOHT2 running user can
   reach it — e.g., to the same folder as the SOHT2 server JAR file.
3. Update the `application-server.yaml` file with the following content:
    ```yaml
    spring:
      datasource:
        url: jdbc:${MY_DATABASE_URL} # e.g., jdbc:mysql://localhost:3306/my_db
        username: ${MY_DATABASE_USERNAME}
        password: ${MY_DATABASE_PASSWORD}
    ```
4. Create a `loader.properties` file in the same directory where SOHT2 server JAR is placed, and put
   there the following content:
    ```properties
    loader.path=path/to/your/jdbc/jars/
    ```
   The `path/to/your/jdbc/jars/` may be a relative path in case if you're sure that you run the
   SOHT2 server from correct working folder.
5. Run the SOHT2 server as usual.

Web Consoles
------------

In addition to the API, SOHT2 server provides the web consoles for managing users and connections:

1. SOHT2 UI
    - URL: `https://${SOHT2_SERVER}/`
    - This console allows you to manage users and connections and view connection history.
2. Swagger UI
    - URL: `https://${SOHT2_SERVER}/swagger-ui`
    - This console provides an interactive interface for the SOHT2 server API, allowing you to test
      endpoints and view API documentation.

See Also
--------

- [README](../README.md)
- [SOHT2 Client Tips & Tricks](tips-client.md)
