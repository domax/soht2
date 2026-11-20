# Spec Delta

## ADDED Requirements

### Requirement: Client tunnel requests use the non-browser contract

The SOHT2 client SHALL identify its tunnel HTTP requests using the server-supported non-browser
contract and SHALL continue sending its configured Basic authentication credentials.

#### Scenario: Client starts a tunnel session

- **GIVEN** the client is configured with a server URL and valid credentials
- **WHEN** it opens, exchanges, and closes a tunnel
- **THEN** each request uses the non-browser contract
- **AND** the complete tunnel session succeeds without a CSRF cookie or token

### Requirement: Client behavior is unchanged by browser CSRF protection

Enabling browser CSRF protection SHALL NOT require the client to persist browser cookies, fetch a
CSRF token, or change the tunnel payload and response formats.

#### Scenario: Existing tunnel data exchange

- **GIVEN** the client exchanges compressed or uncompressed tunnel data
- **WHEN** the server has browser CSRF protection enabled
- **THEN** the client receives and decodes the same response bytes as before
- **AND** tunnel connection concurrency and lifecycle behavior remain unchanged
