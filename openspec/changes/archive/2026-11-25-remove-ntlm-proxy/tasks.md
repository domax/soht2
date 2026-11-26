# Tasks

## 1. Client code

- [x] 1.1 In `Soht2ClientProperties`, delete the `ProxyAuth` enum and its Javadoc, and remove
  `ProxyProperties.auth` and `ProxyProperties.domain`. Verify: `grep -n "ProxyAuth\|domain"
  Soht2ClientProperties.java` returns nothing.
- [x] 1.2 In `HttpClientConfig`:
  - Replace the `ProxyAuth proxyAuth` variable with a `boolean` (host and username set), and log
    `proxyAuth=basic` or `none`.
  - Register only `BASIC` in `authSchemeRegistry()`, and set the preferred proxy schemes to
    `List.of(StandardAuthScheme.BASIC)`.
  - Make `proxyCredentials()` always return `UsernamePasswordCredentials`.
  - Remove the NTLM/Digest/`ProxyAuth`/`InetAddress` imports and the
    `@SuppressWarnings("deprecation")` annotations, and change the Javadoc to say "Basic".

  Verify: `grep -rni "ntlm\|ProxyAuth" soht2-client/src/main` returns nothing, and
  `./gradlew -q :soht2-client:compileJava` passes.

## 2. Tests

- [x] 2.1 In `HttpClientConfigTest`:
  - Change the fixture to `proxyProperties()`, which sets only the username and password.
  - Replace `proxyMode_registersBasicAndNtlmAuthSchemes` with a test asserting that Basic is
    registered and NTLM and Digest are not.
  - Delete `proxyMode_ntlmAuth_usesNtCredentials`.
  - Add a test that a proxy with no username registers no proxy credentials.

  Verify: `./gradlew :soht2-client:test --tests '*HttpClientConfigTest'` passes.
- [x] 2.2 Add a test that binds `soht2.client.proxy.auth=ntlm` and
  `soht2.client.proxy.domain=X` and asserts that the context starts and Basic credentials are
  used. Verify: the new test passes.

## 3. Documentation

- [x] 3.1 In the `README.md` client configuration:
  - Remove the `auth` and `domain` lines, and change the `username` and `password` comments to
    "Optional, enables Basic proxy authentication".
  - Replace the NTLM deprecation note with a **Breaking change** note: NTLM and the `auth` and
    `domain` keys were removed, and NTLM proxies work through
    [JNTLM](https://github.com/domax/jntlm) or [CNTLM](https://cntlm.sourceforge.net/), with a
    link to the tips section.

  Verify: `grep -ni "ntlm\|proxy.auth\|domain" README.md` shows only the new note.
- [x] 3.2 In `doc/tips-client.md`, replace the "NTLM proxies" bullet, which mentions
  `soht2.client.proxy.auth`, with a "NTLM Proxies" section. It explains running JNTLM or CNTLM
  locally, links both projects, and gives a YAML example that points `soht2.client.proxy` at
  `127.0.0.1:<port>` without credentials. Verify: `grep -n "proxy.auth" doc/tips-client.md`
  returns nothing and the README link anchor resolves.

## 4. Verification

- [x] 4.1 Run `./gradlew spotlessCheck build`. Verify: the build succeeds and
  `grep -rni "ntlm\|NTCredentials\|ProxyAuth" soht2-client/src` returns nothing.

## Workflow follow-up

- Archive with `/opsx-archive remove-ntlm-proxy` after apply.
