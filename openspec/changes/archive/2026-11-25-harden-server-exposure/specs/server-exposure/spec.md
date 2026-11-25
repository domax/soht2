# Spec Delta

## Purpose

Defines which server endpoints, response headers, error details, and cross-origin permissions are
exposed to unauthenticated or untrusted callers, so operational and diagnostic data stays private
by default.

## ADDED Requirements

### Requirement: Only the health endpoint is public by default
With default configuration, the server SHALL expose the actuator `health` endpoint without
authentication, with the overall status and no component details. It SHALL NOT serve any other
actuator endpoint.

#### Scenario: Anonymous health check
- **GIVEN** the server runs with default configuration
- **WHEN** an unauthenticated client requests `/actuator/health`
- **THEN** the server responds with HTTP 200 and a body containing the overall status
- **AND** the body contains no component details

#### Scenario: Heap dump not available
- **GIVEN** the server runs with default configuration
- **WHEN** any client, authenticated or not, requests `/actuator/heapdump` or `/actuator/env`
- **THEN** the server does not return a heap dump or environment data

### Requirement: Additional actuator endpoints require the admin role
When an operator exposes additional actuator endpoints, the server SHALL require an authenticated
user with the `ADMIN` role for every actuator endpoint except `health`.

#### Scenario: Exposed endpoint, anonymous caller
- **GIVEN** the operator has exposed the `env` endpoint
- **WHEN** an unauthenticated client requests `/actuator/env`
- **THEN** the server responds with HTTP 401

#### Scenario: Exposed endpoint, non-admin user
- **GIVEN** the operator has exposed the `env` endpoint
- **WHEN** a user with the `USER` role requests `/actuator/env`
- **THEN** the server responds with HTTP 403

#### Scenario: Exposed endpoint, admin user
- **GIVEN** the operator has exposed the `env` endpoint
- **WHEN** a user with the `ADMIN` role requests `/actuator/env`
- **THEN** the server responds with HTTP 200

### Requirement: Error responses hide internal details
Error responses SHALL NOT include a stack trace or the exception class name, whatever request
parameters are sent. They SHALL keep the human-readable `message` field.

#### Scenario: Trace requested on a failing request
- **WHEN** a client sends a request that fails, with the query parameter `trace=true`
- **THEN** the error response contains no `trace` field and no `exception` field
- **AND** the error response contains the `status` and `message` fields

### Requirement: Pages cannot be framed by other sites
The server SHALL send `X-Frame-Options: DENY` on its responses.

#### Scenario: UI page response
- **WHEN** a browser loads the UI index page
- **THEN** the response includes the header `X-Frame-Options: DENY`

### Requirement: Cross-origin access is opt-in
The server SHALL NOT grant cross-origin access unless `soht2.server.cors.allowed-origins` lists
the requesting origin, and it SHALL then grant access only to the listed origins.

#### Scenario: Default configuration
- **GIVEN** `soht2.server.cors.allowed-origins` is not set
- **WHEN** a browser sends a CORS preflight to `/api/connection` from `https://evil.example`
- **THEN** the response does not contain an `Access-Control-Allow-Origin` header

#### Scenario: Configured development origin
- **GIVEN** `soht2.server.cors.allowed-origins` contains `http://localhost:5173`
- **WHEN** a browser sends a CORS preflight to `/api/user` from `http://localhost:5173`
- **THEN** the response contains `Access-Control-Allow-Origin: http://localhost:5173`

#### Scenario: Unlisted origin with CORS configured
- **GIVEN** `soht2.server.cors.allowed-origins` contains only `http://localhost:5173`
- **WHEN** a browser sends a CORS preflight from `https://evil.example`
- **THEN** the response does not contain an `Access-Control-Allow-Origin` header
