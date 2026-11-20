# server-authentication Specification

## Purpose

Defines how the SOHT2 server authenticates HTTP Basic API requests efficiently while keeping
credential changes and revocations effective immediately.

## Requirements

### Requirement: Recently verified credentials are reused
The server SHALL remember a successfully verified username/password pair for at most
`soht2.server.user-cache-ttl` and SHALL authenticate repeated requests carrying the same pair
without repeating the password-hash verification.

#### Scenario: Repeated exchange requests
- **GIVEN** a user has made an authenticated request with valid credentials
- **WHEN** the same user sends further requests with the same credentials within the TTL
- **THEN** the requests are authenticated with the user's current role
- **AND** the password-hash verification is not performed again for those requests

#### Scenario: Remembered credentials expire
- **GIVEN** a user's credentials were verified and remembered
- **WHEN** a request with the same credentials arrives after the TTL has elapsed
- **THEN** the password-hash verification is performed again

### Requirement: Invalid credentials are never remembered
The server SHALL perform full verification for any credentials that do not exactly match a
remembered verified pair, and SHALL NOT remember failed authentication attempts.

#### Scenario: Wrong password after a successful login
- **GIVEN** a user's valid credentials were verified and remembered
- **WHEN** a request arrives with the same username and a different password
- **THEN** the request is rejected with HTTP 401

#### Scenario: Repeated wrong password
- **GIVEN** a request with an invalid password was rejected
- **WHEN** the same invalid credentials are sent again
- **THEN** the request is rejected with HTTP 401

### Requirement: Credential changes take effect immediately
The server SHALL stop accepting remembered credentials as soon as the user's password is changed,
the user is updated, or the user is deleted.

#### Scenario: Password changed
- **GIVEN** a user's credentials were verified and remembered
- **WHEN** the user's password is changed through the API
- **THEN** a request with the old password is rejected with HTTP 401
- **AND** a request with the new password is authenticated

#### Scenario: User deleted
- **GIVEN** a user's credentials were verified and remembered
- **WHEN** an admin deletes the user
- **THEN** a request with that user's credentials is rejected with HTTP 401

#### Scenario: Role changed
- **GIVEN** a user's credentials were verified and remembered
- **WHEN** an admin changes the user's role
- **THEN** the next request with the same credentials is authorized according to the new role

### Requirement: Browser state changes require CSRF validation

The server SHALL require a valid CSRF token for authenticated browser-originated `POST`, `PUT`,
and `DELETE` requests that can change application state.

#### Scenario: Authenticated mutation without a token

- **GIVEN** a user has valid HTTP Basic credentials
- **WHEN** the user sends a state-changing API request without a valid CSRF token
- **THEN** the server responds with HTTP 403
- **AND** the controller operation is not invoked

### Requirement: Non-browser tunnel requests remain compatible

The server SHALL provide an explicit, non-browser request path for the configured SOHT2 client to
perform tunnel open, exchange, and close operations without browser cookie or CSRF-token state.

#### Scenario: Client opens and exchanges a tunnel

- **GIVEN** the SOHT2 client has valid configured credentials
- **WHEN** it opens a connection and exchanges data using its non-browser request contract
- **THEN** the server authenticates and processes both requests successfully
- **AND** no browser CSRF cookie or token is required
