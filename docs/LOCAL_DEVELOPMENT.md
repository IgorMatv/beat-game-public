# 🛠️ Local Development

> From a fresh clone to a running BeatGame stack.

[← Back to README](../README.md) · [Architecture](ARCHITECTURE.md) · [Observability](OBSERVABILITY.md)

## Quick start

### Prerequisites

- Docker with Docker Compose
- Internet access on first catalog population
- Java 21 and Maven only when running the backend outside Docker
- Node.js and npm only when running the frontend outside Docker

### Start the full stack

```powershell
Copy-Item .env.example .env
# Set local passwords and optional tokens in .env
docker compose up -d --build
docker compose ps
```

| Service | Address | Notes |
|---|---|---|
| BeatGame | http://localhost | React app through Nginx |
| Grafana | http://127.0.0.1:3000 | Available only from the local machine |
| Backend | Internal port `8080` | Reached through the frontend proxy |

> **First startup:** PostgreSQL, Redis, and the backend must become healthy before dependent services start. A clean build can therefore take a little longer than subsequent starts.

## Development loop

### Backend

```powershell
cd beatgame-backend
mvn test
```

The test suite uses JUnit, Spring Boot Test, and Testcontainers. Docker must be running for integration tests that create PostgreSQL containers.

### Frontend

```powershell
cd beatgame-frontend
npm ci
npm run build
```

For the Vite development server, `/api` and `/ws` are proxied according to `vite.config.ts`. The optional `VITE_API_BASE_URL` can point the client at a separately running backend.

## Configuration

The root `.env.example` documents the Compose configuration. Copy it to `.env`; never commit the resulting file.

| Variable | Purpose | Local behavior |
|---|---|---|
| `POSTGRES_*` | Database name and credentials | Required by Compose |
| `ALLOWED_ORIGINS` | Browser origins accepted by the backend | Usually `http://localhost` |
| `ADMIN_TOKEN` | Protects catalog population endpoints | Blank disables those endpoints |
| `GRAFANA_ADMIN_*` | Grafana login | Password must be set |
| `VITE_ACCESS_PASSWORD` | Optional demo UI gate | Blank disables the gate |

> `VITE_*` variables are embedded in the frontend bundle. They are public configuration, not a safe place for secrets.

## Track catalog

Track metadata and previews are populated through the public Deezer and iTunes APIs. This operation needs internet access and should remain explicit rather than running during every test.

For deterministic tests, use mocked providers or controlled seeded data. If `ADMIN_TOKEN` is blank, the administrative population endpoints are disabled.

## Compose cheat sheet

| Goal | Command |
|---|---|
| Show service health | `docker compose ps` |
| Follow backend logs | `docker compose logs -f backend` |
| Follow frontend logs | `docker compose logs -f frontend` |
| Rebuild one service | `docker compose up -d --build backend` |
| Stop the stack | `docker compose down` |
| Remove containers and local volumes | `docker compose down -v` |

> ⚠️ `docker compose down -v` removes the local database, Redis state, Prometheus data, and Grafana data.

## Common checks

- **Backend never becomes healthy:** inspect `docker compose logs backend` and verify the database values in `.env`.
- **Browser cannot connect:** confirm that the frontend is on port 80 and that `ALLOWED_ORIGINS` includes the browser origin.
- **Grafana rejects the login:** verify `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD`, then recreate its volume if credentials were changed after first startup.
- **Testcontainers tests fail:** confirm Docker is running and accessible to Maven.
