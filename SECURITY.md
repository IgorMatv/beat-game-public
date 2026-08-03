# Security

## Scope

BeatGame is a portfolio-scale application with guest identity rather than user accounts. A player receives a signed, short-lived JWT when creating or joining a room. The token is used to authenticate the WebSocket connection and scope gameplay commands to that room.

This is not an account, identity, or privacy system. There is no password-based player login, logout endpoint, token revocation list, persistent player profile, or token refresh flow. Some room-information REST endpoints are intentionally unauthenticated and should not be treated as private data stores.

The live demo's shared password gate is only a casual access filter. It is embedded in the frontend bundle and must not be treated as a secret or an authorization boundary.

## Deployment expectations

- Replace all development defaults for JWT signing, database, Grafana, and admin credentials in production.
- Keep runtime secrets in deployment configuration, never in source files or committed `.env` files.
- Use HTTPS/WSS in deployed environments.
- The admin track-population endpoints must be protected with a strong, deployment-specific token.

## Reporting a vulnerability

Please do not disclose an exploitable vulnerability in a public issue. Use GitHub's private vulnerability reporting for this repository if it is enabled. Otherwise, contact the maintainer privately through the GitHub profile and include:

- the affected component or endpoint;
- a concise description of the impact;
- reproduction steps or a minimal proof of concept;
- any suggested mitigation.

You may redact tokens, credentials, personal data, and other sensitive values from the report. We will acknowledge reports when possible and coordinate disclosure after a fix or mitigation is available.
