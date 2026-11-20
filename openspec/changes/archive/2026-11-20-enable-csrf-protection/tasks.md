# Tasks

## 1. Server CSRF enforcement

- [x] 1.1 Configure the server security chain with cookie-backed CSRF token generation and the
  documented header contract; verify the server issues a browser-readable token and rejects
  authenticated state-changing requests without a matching token.
- [x] 1.2 Add the explicit non-browser tunnel request matcher and document the marker header in the
  server/client API guidance; verify requests carrying the marker still require Basic
  authentication and bypass only CSRF validation.
- [x] 1.3 Update server MockMvc security tests for valid-token, missing-token, invalid-token, and
  marked-client requests; verify `./gradlew :soht2-server:test` passes and protected controller
  methods are not invoked on rejected requests.

## 2. UI CSRF integration

- [x] 2.1 Extend the shared UI HTTP client to read the server's CSRF cookie and attach the matching
  header to every `POST`, `PUT`, and `DELETE` request while preserving Basic authentication; verify
  GET requests do not add a stale or fabricated token.
- [x] 2.2 Ensure the login/bootstrap flow performs the authenticated request that establishes the
  CSRF cookie before protected screens can issue mutations; verify failed login clears credentials
  and does not leave a usable mutation path.
- [x] 2.3 Run `cd soht2-ui && npm run lint && npm run build`; verify the generated UI retains the
  existing router/API context behavior and deploy output.

## 3. Client compatibility

- [x] 3.1 Add the server-supported non-browser marker to the `soht2-client` RestClient defaults
  without adding cookie or CSRF-token state; verify Basic authentication remains present.
- [x] 3.2 Extend client request tests to assert the marker on tunnel open, exchange, and close and
  to cover compressed and uncompressed exchanges; verify `./gradlew :soht2-client:test` passes.

## 4. Cross-module verification

- [x] 4.1 Run the server, client, and UI targeted checks together and exercise a real browser
  mutation plus a client tunnel against the protected server; verify the browser receives 403 for
  missing/invalid tokens while the client opens, exchanges, and closes successfully.
