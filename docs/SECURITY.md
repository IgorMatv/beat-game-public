# Security Notes

This repository is intended as a portfolio/demo project. It should still be treated like a real app when run outside a local machine.

## Secrets

Do not commit `.env` files. Use `.env.example` as a template and set real values locally or in deployment secrets.

Values that must be changed outside local development:

- `POSTGRES_PASSWORD`
- `ADMIN_TOKEN`
- `GRAFANA_ADMIN_PASSWORD`
- any future API keys or OAuth credentials

## Network Exposure

The default Compose setup exposes:

- Frontend on `http://localhost:80`.
- Grafana on `http://127.0.0.1:3000` only.

Review `docker-compose.yml` before exposing services publicly. PostgreSQL, Redis, Prometheus, and the backend are only exposed on the internal Docker network by default.

## Application Notes

- WebSocket clients authenticate with room/player tokens, not user accounts.
- Rate limiting is implemented at the backend filter layer.
- Public music preview URLs come from third-party public APIs and should be treated as external content.
