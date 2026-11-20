# Design

## Context

The server currently disables CSRF in `SecurityConfig`. The UI uses a shared `fetch` wrapper and
HTTP Basic credentials, while `soht2-client` uses Spring `RestClient` for all tunnel requests and
does not maintain browser cookies. Tunnel open, exchange, and close are all state-changing API
operations, so enabling the default CSRF filter without a separate client contract would break the
client.

## Goals / Non-Goals

**Goals:**

- Enforce CSRF validation for browser state-changing API requests.
- Make the token available to the same-origin React UI and attach it consistently.
- Give the non-browser tunnel client an explicit request identity that can be handled without
  browser token state.
- Preserve Basic authentication, tunnel payloads, compression, and connection lifecycle behavior.

**Non-Goals:**

- Replacing Basic authentication with session or bearer authentication.
- Making tunnel endpoints accessible without authentication.
- Changing CORS policy or adding a general-purpose public CSRF bypass.

## Decisions

### Use a cookie-to-header CSRF contract for the UI

Configure Spring Security's cookie-backed CSRF token repository with a browser-readable token
cookie. The UI fetch wrapper will read that cookie and send its value in the corresponding CSRF
request header for `POST`, `PUT`, and `DELETE` calls. The login bootstrap `GET` will establish the
cookie before the first UI mutation.

This matches the existing same-origin SPA architecture and avoids exposing a token in API DTOs.
An in-memory token fetched by a separate endpoint was rejected because it adds a bootstrap
endpoint and synchronization state that the current UI does not need.

### Identify the trusted tunnel client with a dedicated header

The client will add a fixed, documented non-browser marker header to every tunnel request. The
server's CSRF matcher will skip token validation only when that marker is present, while ordinary
authenticated browser requests remain protected. The UI will never emit the marker.

A path-only exemption was rejected because the UI also uses connection mutations and would then
leave those browser operations unprotected. Requiring the tunnel client to obtain and persist a
cookie token was rejected because it is not a browser and would add state to every client process.
The marker is safe for the CSRF threat model because a cross-site browser cannot send an arbitrary
custom header without a permitted preflight, and it does not grant authentication or authorization.

### Test at the security-filter and transport boundaries

Server MockMvc tests will prove missing-token rejection, valid-token acceptance, and the explicit
client-marker path. Client request tests will assert the marker and existing Basic header are
present without cookies. UI validation will exercise the shared request wrapper through its build
and lint checks, with the token-cookie parsing and mutation-header logic kept in a small,
testable helper if the existing UI test setup permits it.

## Risks / Trade-offs

- **[Risk]** A deployment with custom cross-origin UI hosting may not send the CSRF cookie or
  header. → Keep the existing same-origin behavior as the supported contract and verify CORS/cookie
  attributes during integration testing; do not silently fall back to unprotected requests.
- **[Risk]** A caller could copy the tunnel marker. → Continue requiring Basic authentication and
  endpoint authorization; treat the marker only as CSRF classification, not as an authentication
  credential.
- **[Risk]** Existing server tests that issue mutating requests without CSRF tokens may fail. →
  Update security-focused tests to use Spring Security's CSRF test request post-processor, while
  retaining explicit negative tests for missing tokens.
- **[Risk]** The UI may attempt a mutation before receiving a token. → Have the shared wrapper
  fail visibly rather than send a success-shaped request, and ensure the authenticated bootstrap
  request runs before protected screens are reached.

## Migration Plan

1. Deploy the server with cookie/header CSRF support and the non-browser marker matcher.
2. Deploy the UI and client changes together, or deploy the server first while the marker path
   remains compatible with existing authenticated clients during rollout.
3. Verify browser login, user mutations, connection close, and a real client tunnel.
4. Roll back by restoring the previous security configuration and UI/client versions if an
   integration cannot establish its token contract; no database or tunnel-data migration is
   required.
