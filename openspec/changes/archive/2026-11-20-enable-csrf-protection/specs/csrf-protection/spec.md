# Spec Delta

## Purpose

Protects browser-originated state-changing requests to the SOHT2 API with a server-issued CSRF
token while preserving the existing authenticated administration experience.

## ADDED Requirements

### Requirement: Browser receives a CSRF token

The server SHALL issue a CSRF token to the browser UI through a same-origin cookie or equivalent
browser-readable response mechanism.

#### Scenario: Authenticated UI bootstraps protection

- **GIVEN** the browser loads the SOHT2 UI and makes an authenticated API request
- **WHEN** the server responds successfully
- **THEN** the browser has access to the current CSRF token

### Requirement: UI sends CSRF tokens on state changes

The UI SHALL include the current CSRF token in the server-defined request header for every
authenticated state-changing API request.

#### Scenario: UI creates or updates data

- **GIVEN** the UI has received a CSRF token
- **WHEN** it sends an authenticated `POST`, `PUT`, or `DELETE` API request
- **THEN** the request includes the matching CSRF header
- **AND** the request is accepted by the server

### Requirement: Missing or invalid browser tokens are rejected

The server SHALL reject authenticated browser-originated state-changing API requests that omit or
send an invalid CSRF token.

#### Scenario: Cross-site state-changing request

- **GIVEN** an authenticated browser request targets a state-changing API endpoint
- **WHEN** the request has no valid CSRF token
- **THEN** the server responds with HTTP 403
- **AND** the state change is not performed
