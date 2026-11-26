# Tasks

## 1. Dependency switch

- [x] 1.1 In `soht2-client/build.gradle`, replace `org.apache.httpcomponents:httpclient:4.5.14`
  with `org.apache.httpcomponents.client5:httpclient5` (BOM-managed). Check with
  `./gradlew :soht2-client:dependencies --configuration runtimeClasspath | grep -i httpcomponents`
  that no `org.apache.httpcomponents:httpclient` 4.x remains. The module will not compile until
  group 2 is done; that is expected.

## 2. Unified HTTP client configuration

- [x] 2.1 Read `HCClientHttpRequest`, `HCClientHttpResponse`, and `HCClientHttpRequestFactory`,
  and list any behavior beyond what Spring's `HttpComponentsClientHttpRequestFactory` does (for
  example, how headers, `Content-Encoding`, and the body are handled). Port each such behavior to
  `Soht2Client` or the customizer in 2.2. Record the findings in the PR description.
- [x] 2.2 Create `net.soht2.client.config.HttpClientConfig` with the
  `ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder>` bean
  described in design decision 1: pool size, `disableContentCompression`, proxy with Basic or
  NTLM credentials and an explicit auth scheme registry, trust-all TLS when disabled, and
  `validateAfterInactivity`. Add one startup INFO log line with no secrets.
- [x] 2.3 Delete `ProxyConfig`, `TrustingSSLConfig`, `HCClientHttpRequestFactory`,
  `HCClientHttpRequest`, and `HCClientHttpResponse`. Add
  `spring.http.clients.imperative.factory: http-components` to the client `application.yaml`.
  Verify `./gradlew :soht2-client:compileJava` succeeds.
- [x] 2.4 Rewrite `HttpConnectionPoolTest` with `ApplicationContextRunner` and Boot's HTTP client
  auto-configurations plus `HttpClientConfig`. Assert that the resulting
  `HttpComponentsClientHttpRequestFactory`'s connection manager has max total and per-route equal
  to `max-http-connections` in all three modes: direct, proxy, and SSL disabled. Update
  `HttpReadTimeoutTest` to assert the read timeout is applied to the HC5 factory in each mode.
  Verify `./gradlew :soht2-client:test --tests '*Http*'`.
- [x] 2.5 Add tests for proxy authentication and TLS:
  - The proxy-mode client has an auth scheme registry that resolves `NTLM` and `Basic`.
  - Its credentials provider returns `NTCredentials` or `UsernamePasswordCredentials` by
    `proxy.auth`.
  - With SSL disabled, a request to a local HTTPS stub with a self-signed certificate succeeds
    (for example, via `com.sun.net.httpserver.HttpsServer`).

  Verify the client tests pass.
- [x] 2.6 Add a `Soht2ClientTest` or `ConnectionServiceTest` case with compression `none` that
  asserts the outgoing download request's `Accept-Encoding` is `identity` (not
  `gzip, x-gzip, deflate`). Add one with `gzip` asserting a gzip-encoded body is decoded exactly
  once. Verify the client tests pass.

## 3. Documentation

- [x] 3.1 Update `README.md` (the `max-http-connections` comment and paragraph near "Transport")
  and `doc/tips-client.md` to say the pool limit applies in every mode. Note that
  `spring.http.clients.read-timeout` also applies with a proxy, and that NTLM proxy auth is
  deprecated upstream. Verify there is no remaining text limiting `max-http-connections` to
  proxy or SSL-disabled modes.

## 4. Integration check

- [x] 4.1 Run `./gradlew :soht2-client:spotlessCheck build`. Start the server and the client in
  direct mode and confirm a duplex `ssh`/`telnet` tunnel works. If a proxy is available, for
  example `docker run -p 3128:3128 ubuntu/squid`, confirm proxy mode with Basic auth also works.
