# Proposal

## Why

The server currently disables CSRF protection for its browser-served administration UI, leaving
state-changing authenticated requests without a browser-side request-origin check. Enabling CSRF
for the UI while preserving the non-browser tunnel client's Basic-auth behavior will protect the
web application without disrupting configured socket tunneling.

## What Changes

- Enable server-side CSRF protection for authenticated state-changing API requests.
- Expose a CSRF token to the browser UI through the server's cookie/header integration.
- Make the UI retain and send the token on every state-changing API request, while continuing to
  use Basic authentication.
- Ensure the `soht2-client` HTTP request flow remains compatible with the protected server without
  requiring browser cookies or CSRF tokens.
- Add focused server, UI, and client verification for the protected and non-browser flows.

Assumptions:

- The existing same-origin deployment is the primary UI deployment; configured CORS behavior remains
  unchanged unless required for the token exchange.
- CSRF protection applies to browser API interactions, while the tunnel client is treated as a
  trusted non-browser integration using its existing Basic credentials.

Out of scope:

- Replacing HTTP Basic authentication or changing user roles.
- Introducing a separate token-based authentication system.
- Changing tunnel endpoints, payloads, compression, or connection semantics.

## Capabilities

### New Capabilities

- `csrf-protection`: Protect browser-originated state-changing API requests with a server-issued
  CSRF token and provide a client integration for sending it.

### Modified Capabilities

- `server-authentication`: Authenticated server requests must additionally satisfy CSRF
  requirements when they are browser state changes, while the non-browser tunnel client remains
  compatible.
- `client-tunneling`: The client continues to open, exchange, and close tunnels through the
  protected server without browser-specific CSRF state.

## Impact

- **Server:** `SecurityConfig`, CSRF cookie/header configuration, security integration tests, and
  potentially API/CORS configuration.
- **UI:** The shared fetch wrapper and login/bootstrap flow in `soht2-ui`, plus focused request
  tests or testable helpers.
- **Client:** `soht2-client` REST request configuration and tests if Spring Security's protected
  endpoints require an explicit non-browser exemption or compatible request behavior.
- **Documentation/configuration:** Any API or deployment guidance needed to explain the browser
  token contract, without changing credentials or tunnel protocol configuration.
