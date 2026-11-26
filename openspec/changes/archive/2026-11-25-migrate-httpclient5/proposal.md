# Proposal

## Why

The client depends on Apache HttpClient 4.5.14. That line receives no feature development, and
Spring Framework 7 no longer supports it. The project keeps it working through three hand-written
adapter classes (`HCClientHttpRequestFactory`, `HCClientHttpRequest`, `HCClientHttpResponse`).

The HTTP transport also behaves differently depending on configuration:

- **Proxy or SSL-verification-disabled modes:** use HttpClient 4 with a pool sized by
  `max-http-connections`, but no read timeout.
- **Default mode:** uses the JDK client, which ignores `max-http-connections`, keeps its own
  connection reuse policy, and honors `spring.http.clients.read-timeout`.

Moving to HttpClient 5 removes the unmaintained dependency and the custom adapters, and gives
every mode the same pooled, keep-alive transport.

## What Changes

- Replace `org.apache.httpcomponents:httpclient:4.5.14` with the Spring Boot–managed
  `org.apache.httpcomponents.client5:httpclient5`.
- Delete `HCClientHttpRequestFactory`, `HCClientHttpRequest`, and `HCClientHttpResponse`, and use
  Spring's `HttpComponentsClientHttpRequestFactory` built by Spring Boot.
- Use one HTTP client configuration for all modes (direct, proxy, SSL verification disabled):
  - The pool is limited by `soht2.client.max-http-connections`, both total and per route.
  - `spring.http.clients.read-timeout` and `connect-timeout` apply in every mode.
  - Proxy Basic and NTLM authentication keep working with the existing `soht2.client.proxy.*`
    properties.
  - Disabling SSL verification keeps trusting all certificates and host names.
- Automatic response decompression by the HTTP library is disabled. Tunnel compression stays
  negotiated and decoded by SOHT2 itself, exactly as today.
- No configuration property is added, renamed, or removed.

Assumptions:

- `spring.http.clients.read-timeout` (default `PT60S` in the client `application.yaml`) is the
  single read-timeout setting for all modes. Applying it in proxy mode, which today has no read
  timeout, is intended.

Out of scope:

- Async/HTTP/2 transports, SSL bundles, or new proxy authentication schemes.
- Changing the tunnel protocol or poll behavior.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `client-tunneling`:
  - The concurrent HTTP connection limit applies in every transport mode, not only in proxy or
    SSL-verification-disabled modes.
  - Proxy authentication, read timeouts, and tunnel compression are specified as preserved.

## Impact

- **Client:** `build.gradle`, `ProxyConfig`, `TrustingSSLConfig` (merged into one HTTP client
  configuration), `Soht2ClientConfig.restClient`, `application.yaml`
  (`spring.http.clients.imperative.factory`), and the tests `HttpConnectionPoolTest`,
  `HttpReadTimeoutTest`, and `Soht2ClientTest`.
- **Dependencies:** removes HttpClient 4 and its transitive `httpcore`. `commons-codec` stays
  pinned under the existing vulnerability-fix block if httpclient5 still needs it.
- **Docs:** `README.md` and `doc/tips-client.md` statements that limit `max-http-connections` to
  proxy or SSL-disabled modes.
