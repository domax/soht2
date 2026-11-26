# Spec Delta

## REMOVED Requirements

### Requirement: Proxy authentication is preserved
**Reason**: NTLM proxy authentication and the `soht2.client.proxy.auth` selector are removed.
Basic-only authentication is specified by "Proxy authentication is Basic only".
**Migration**: Run a local NTLM proxy (JNTLM or CNTLM) and point `soht2.client.proxy` to it.
Remove the `auth` and `domain` keys.

## ADDED Requirements

### Requirement: Proxy authentication is Basic only
When `soht2.client.proxy.username` is set, the client SHALL authenticate to the configured
proxy with Basic authentication, using the configured username and password. No
authentication-type setting is required or read. The client SHALL NOT answer NTLM or other
non-Basic proxy authentication challenges.

#### Scenario: Basic proxy authentication
- **GIVEN** a proxy username and password are set
- **WHEN** the proxy answers the first request with a Basic authentication challenge
- **THEN** the client retries with Basic proxy credentials and the tunnel request succeeds

#### Scenario: Proxy without credentials
- **GIVEN** a proxy host is set and no proxy username is set
- **WHEN** the client sends a tunnel request through a proxy that does not require
  authentication
- **THEN** the request succeeds without any proxy credentials

#### Scenario: Legacy auth keys are ignored
- **GIVEN** a configuration that still contains `soht2.client.proxy.auth` or
  `soht2.client.proxy.domain`
- **WHEN** the client starts
- **THEN** startup succeeds and those keys have no effect on proxy authentication

#### Scenario: NTLM-only proxy
- **GIVEN** a proxy that offers only NTLM authentication
- **WHEN** the client sends a tunnel request through it
- **THEN** the client does not attempt an NTLM handshake and the request fails with the proxy's
  `407 Proxy Authentication Required` response

#### Scenario: NTLM proxy through a local NTLM proxy
- **GIVEN** a local NTLM proxy (for example JNTLM or CNTLM) that authenticates to the upstream
  NTLM proxy
- **AND** `soht2.client.proxy` points to the local proxy, without credentials or with Basic
  credentials
- **WHEN** the client opens a tunnel
- **THEN** the tunnel request succeeds
