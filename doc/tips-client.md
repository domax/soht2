Tips & Tricks for SOHT2 Client
==============================

<!-- TOC -->
* [Tips & Tricks for SOHT2 Client](#tips--tricks-for-soht2-client)
  * [Prerequisites](#prerequisites)
  * [Create a Client Launcher](#create-a-client-launcher)
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

See Also
--------

- [README](../README.md)
- [SOHT2 Server Tips & Tricks](tips-server.md)
