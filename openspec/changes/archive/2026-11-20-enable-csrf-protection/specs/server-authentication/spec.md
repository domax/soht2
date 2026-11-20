# Spec Delta

## ADDED Requirements

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
