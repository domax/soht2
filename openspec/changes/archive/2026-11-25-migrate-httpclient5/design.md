<!--suppress MarkdownUnresolvedLinkLabel -->
# Design

## Context

- **Spring Boot 4.1.2** manages `httpclient5` 5.6.5. When
  `spring.http.clients.imperative.factory: http-components` is set, or HC5 is on the classpath,
  `ImperativeHttpClientAutoConfiguration` builds a `ClientHttpRequestFactory` from
  `HttpComponentsClientHttpRequestFactoryBuilder` and `HttpClientSettings`
  (`spring.http.clients.connect-timeout` and `read-timeout`). It applies any
  `ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder>` beans.
- **The builder offers customization hooks:** `withConnectionManagerCustomizer`,
  `withHttpClientCustomizer`, `withDefaultRequestConfigCustomizer`, and
  `withTlsSocketStrategyFactory`.
- **HC5 defaults that conflict with SOHT2:**
  - The pool allows 25 connections in total and 5 per route. Duplex sessions use two requests
    each, so only about 2 sessions would run concurrently.
  - Content compression is enabled. HC5 then adds `Accept-Encoding: gzip, x-gzip, deflate` when
    the header is absent, and transparently decompresses responses.
  - NTLM is not in the default auth scheme registry in 5.6.x. Only Basic, Digest, Bearer, and
    SCRAM are registered, as verified from the `HttpClientBuilder` bytecode.
- **Current SOHT2 behavior:**
  - `Soht2Client` always sets `Accept-Encoding` explicitly and decodes `Content-Encoding` itself.
  - `Soht2ClientConfig.restClient` installs an optional `ClientHttpRequestFactory` bean on the
    `RestClient.Builder`.
  - `ProxyConfig` and `TrustingSSLConfig` each define such a bean today.

## Goals / Non-Goals

**Goals:**

- One HC5-based transport for all modes, configured through Boot's builder so
  `spring.http.clients.*` keeps working.
- A pool size equal to `max-http-connections`.
- NTLM and Basic proxy authentication.
- Trust-all TLS when verification is disabled.
- No double decompression.

**Non-Goals:**

- New properties, async clients, or connection eviction tuning beyond what keep-alive needs.

## Decisions

1. **A single `HttpClientConfig` replaces `ProxyConfig` and `TrustingSSLConfig`.** It
   contributes one
   `ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder>`
   that applies:
   - **Pool:** `withConnectionManagerCustomizer(b -> b.setMaxConnTotal(n).setMaxConnPerRoute(n))`,
     where `n = max-http-connections`.
   - **No double decompression:** `withHttpClientCustomizer(b -> b.disableContentCompression())`.
   - **Proxy, when `soht2.client.proxy.host` is set:**
     - `b.setProxy(new HttpHost(host, port))`.
     - When a username is set, `b.setDefaultCredentialsProvider(...)` with `AuthScope(proxy)` and
       either `UsernamePasswordCredentials` or `NTCredentials(username, password, workstation,
       domain)`.
     - `b.setDefaultAuthSchemeRegistry(...)` registering `BASIC`, `DIGEST`, and `NTLM`
       (`NTLMSchemeFactory.INSTANCE`).
     - `withDefaultRequestConfigCustomizer(rc -> rc.setProxyPreferredAuthSchemes(List.of(BASIC, NTLM)))`.
   - **SSL verification disabled:** `withTlsSocketStrategyFactory(bundle -> new
     DefaultClientTlsStrategy(trustAllContext, NoopHostnameVerifier.INSTANCE))`.

   The Boot-built factory then flows through the existing
   `@Autowired(required = false) ClientHttpRequestFactory`, or through the `RestClient.Builder`
   defaults.
   - Alternative: build `CloseableHttpClient` manually and wrap it in
     `HttpComponentsClientHttpRequestFactory`. Rejected because it would bypass
     `spring.http.clients.*` and need extra code to apply timeouts.
2. **Force the factory type.** Set `spring.http.clients.imperative.factory: http-components` in
   the client `application.yaml`. Detection would otherwise depend on classpath order (Jetty or
   Reactor on the test classpath).
3. **Delete the `HC*` adapters.** Spring's `HttpComponentsClientHttpRequestFactory` supports
   HC5 natively. Before deleting, check `HCClientHttpRequest` (for example, how it sets the
   `Content-Encoding` header around line 142) for behavior that the Spring factory does not
   provide. Port any such behavior into `Soht2Client` request construction instead.
4. **Dependency.** Replace the `httpclient:4.5.14` line with
   `implementation 'org.apache.httpcomponents.client5:httpclient5'` (version from the Boot BOM).
   Keep the `commons-logging` exclusion only if HC5 pulls it in; HC5 uses SLF4J.
5. **Log the effective transport once at startup.** Show proxy host, auth type (never the
   password), pool size, and whether SSL verification is disabled. This replaces the current
   per-bean INFO logs.

## Risks / Trade-offs

- [NTLM is deprecated in HC5 and may be removed in a later 5.x or 6.0] → Registering the scheme
  explicitly keeps it working now. A Javadoc note on `ProxyAuth.NTLM` and the README record the
  deprecation. A future change can add SPNEGO or Kerberos if needed.
- [A read timeout now applies in proxy mode, where there was none] → The default of 60 s is above
  the 25 s hold. This matches the documented requirement in `AGENTS.md` and the README that the
  read timeout exceeds `download-hold-timeout`.
- [Stale pooled connections behind proxies that silently drop idle connections] → Rely on HC5's
  default stale-connection validation for leased connections, and set
  `ConnectionConfig.setValidateAfterInactivity(2s)` through `withConnectionConfigCustomizer`.
- [Behavior differences in the default mode (JDK → HC5)] → Existing `Soht2ClientTest`
  (MockRestServiceServer) and `ConnectionServiceTest` echo tests cover the request and response
  contract. Add a direct-mode pool test.

## Migration Plan

This is transparent to users, because no configuration changes. Rollback means redeploying the
previous client JAR.
