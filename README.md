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

TODO List
---------

- [X] Implement support for client side compression
- [ ] Add proxy support for the client side
- [ ] Finalize authentication and authorization mechanisms
- [ ] Update sequence diagram
- [ ] Update README.md with more details
- [ ] Add more tests
- [ ] Implement UI
