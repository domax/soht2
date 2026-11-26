<!--suppress MarkdownUnresolvedLinkLabel -->
# Design

## Context

The client configures Apache HttpClient 5 in `HttpClientConfig`:

- `authSchemeRegistry()` registers Basic, Digest, and the deprecated NTLM scheme.
- The default request config prefers `[BASIC, NTLM]` for the proxy.
- `proxyAuth` is computed as `proxy.getAuth()` when a host and username are set. It is logged,
  and it gates the credentials provider.
- `proxyCredentials()` creates `NTCredentials` (domain plus local host name) when
  `auth == NTLM`, and `UsernamePasswordCredentials` otherwise.

`Soht2ClientProperties.ProxyAuth` has the values `BASIC` and `NTLM`. `ProxyProperties` has `auth`
(default `BASIC`) and `domain`, which only NTLM uses. Both `@SuppressWarnings("deprecation")`
annotations exist only because of NTLM.

## Goals / Non-Goals

**Goals:**
- No deprecated NTLM API and no `ProxyAuth` type remain in the client source or tests.
- Proxy authentication is driven only by whether `username` is set.

**Non-Goals:**
- Changing how proxy credentials are supplied (`username`, `password`, or
  `SOHT2_CLIENT_PROXY_PASSWORD`).
- Changing timeout, pool, or SSL behavior in proxy mode.

## Decisions

1. **Delete `ProxyAuth`, `ProxyProperties.auth`, and `ProxyProperties.domain`.**
   - With one scheme, a selector is configuration with no choice.
   - Spring Boot ignores unknown keys under `@ConfigurationProperties` by default, so leftover
     `auth:` or `domain:` keys do not break startup.
   - Alternative: keep `auth` with `BASIC` only, so that `ntlm` fails at binding. Rejected at
     the user's request, because the property has no purpose.
   - Alternative: a startup check that rejects the removed keys. Rejected as extra code for a
     one-time migration. A `407` from the NTLM proxy plus the README note is enough.
2. **Gate Basic credentials on `username` alone.**
   - Replace the `ProxyAuth proxyAuth` variable with a `boolean proxyAuth` (host and username
     set).
   - `configureProxy` registers the credentials provider when `proxyAuth` is true.
   - The startup log shows `proxyAuth=basic` or `none`.
3. **Register only the Basic scheme for the proxy.**
   - `authSchemeRegistry()` registers only `BASIC`, and the preferred proxy schemes become
     `List.of(BASIC)`.
   - Digest is dropped too, so the client offers exactly what the spec allows: Basic only.
     Digest was never documented.
4. **Always create proxy credentials with `UsernamePasswordCredentials`.**
   - `proxyCredentials()` builds `UsernamePasswordCredentials` unconditionally.
   - The `InetAddress` and `Try` host-name lookup goes away, along with both
     `@SuppressWarnings("deprecation")` annotations.
5. **Tests.**
   - Change the `proxyProperties(ProxyAuth)` fixture to `proxyProperties()`, which sets only the
     username and password.
   - Replace `proxyMode_registersBasicAndNtlmAuthSchemes` with a test that asserts Basic is
     registered and NTLM and Digest are not.
   - Delete `proxyMode_ntlmAuth_usesNtCredentials`.
   - Add a test for a proxy with no username, which asserts that no proxy credentials are
     registered.
   - Add a binding test (`ApplicationContextRunner` with `soht2.client.proxy.auth=ntlm` and
     `soht2.client.proxy.domain=X`) that asserts the context starts.
6. **Documentation.**
   - README: drop the `auth` and `domain` lines from the configuration block, and replace the
     deprecation note with a breaking-change note that the keys are removed and Basic is implied
     by `username`. Add a JNTLM/CNTLM pointer.
   - `doc/tips-client.md`: replace the "NTLM proxies" bullet with a short section that explains
     the setup:
     1. Run JNTLM or CNTLM locally against the corporate proxy.
     2. Set `soht2.client.proxy.host: 127.0.0.1` and `port` to its listen port.
     3. Leave out `username` and `password`, unless the local proxy itself requires Basic.
   - Include a YAML example.

## Risks / Trade-offs

- [Users upgrade with `auth: ntlm`] → Startup succeeds, but the NTLM proxy answers `407`. The
  README breaking-change note and the tips section show the migration.
- [Digest proxies stop working] → They were never documented or offered in the configuration.
  Accepted.
- [Extra local process for NTLM users] → JNTLM and CNTLM are mature and serve every application on
  the host.

## Migration Plan

1. Install and start JNTLM or CNTLM with the NTLM proxy as its upstream.
2. Change `soht2.client.proxy` to point to the local proxy.
3. Remove the `auth` and `domain` keys. They are ignored if left in place.
4. Restart the client.

To roll back, redeploy the previous client JAR with the old configuration.
