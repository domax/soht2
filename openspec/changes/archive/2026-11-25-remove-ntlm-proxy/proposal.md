# Proposal

## Why

Apache HttpClient 5 deprecates NTLM, and the client keeps it alive with deprecated APIs
(`NTCredentials`, `NTLMSchemeFactory`). It is also hard to test without a real Windows proxy.
Local NTLM proxies such as [JNTLM](https://github.com/domax/jntlm) and
[CNTLM](https://cntlm.sourceforge.net/) already cover NTLM networks better. They handle NTLM
once, for every application on the machine, and give clients a plain proxy without
authentication or with Basic. The client can therefore drop NTLM and support only Basic proxy
authentication. With a single scheme left, the `auth` selector has nothing to choose, so it
goes too.

## What Changes

- **BREAKING:** NTLM proxy authentication is removed from the client.
- **BREAKING:** `soht2.client.proxy.auth` and `soht2.client.proxy.domain` are removed. When
  `soht2.client.proxy.username` is set, the client always uses Basic proxy authentication.
  Leftover `auth` or `domain` keys are ignored, like any unknown property.
- The proxy offers only the Basic authentication scheme. Digest and NTLM challenges from the
  proxy are no longer answered.
- Documentation (`README.md`, `doc/tips-client.md`) drops the `auth`, `domain`, and NTLM options.
  It explains how to reach an NTLM proxy through a local NTLM proxy, such as
  [JNTLM](https://github.com/domax/jntlm) or [CNTLM](https://cntlm.sourceforge.net/), with an
  example client configuration.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client-tunneling`: "Proxy authentication is preserved" is replaced by "Proxy authentication
  is Basic only", which has no authentication-type setting.

## Impact

- **Code:** `soht2-client`:
  - `Soht2ClientProperties`: the `ProxyAuth` enum is deleted, and `ProxyProperties.auth` and
    `domain` are removed.
  - `HttpClientConfig`: the auth scheme registry, credentials, preferred schemes, and startup log.
- **Tests:** `HttpClientConfigTest` (the NTLM tests and the `ProxyAuth` fixture are replaced).
- **Docs:** `README.md` client configuration block and proxy notes, and `doc/tips-client.md`.
- **Operators:** clients behind NTLM-only proxies must run a local NTLM proxy and point
  `soht2.client.proxy` to it.
- **Unaffected:**
  - The server and the wire protocol.
  - Direct mode.
  - Proxies without authentication.
  - Basic proxies, including existing `auth: basic` configurations, where the key is now ignored.

Assumptions:

- Silently ignoring a leftover `auth: ntlm` is acceptable. The client sends Basic credentials,
  and an NTLM-only proxy rejects them with `407`, which is the visible symptom. The README
  breaking-change note explains the migration. No dedicated startup check for removed keys is
  added.

Out of scope:

- Bundling, installing, or configuring JNTLM or CNTLM.
- Other proxy authentication schemes (Digest, Kerberos/SPNEGO).
- Local untracked files such as `dist/soht2-client/application-ntlm.yaml`. Operators update
  their own configurations.
