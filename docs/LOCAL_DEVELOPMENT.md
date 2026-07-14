# Local Development

## Docker Compose

The simplest way to run the project is the root Compose file:

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

The frontend is available at `http://localhost`. Grafana is bound to loopback only at `http://127.0.0.1:3000`.

## Backend Tests

```powershell
cd beatgame-backend
mvn test
```

Backend tests use Spring Boot Test and Testcontainers. Docker must be running for tests that require PostgreSQL containers.

## Frontend Build

```powershell
cd beatgame-frontend
npm ci
npm run build
```

## Track Catalog Population

The app can populate track data from Deezer and iTunes public APIs. This requires internet access and should not be triggered implicitly on every test run. Keep test profiles deterministic and use mocked providers or existing seeded test data where possible.

## Useful Compose Commands

```powershell
# See service state
docker compose ps

# Backend logs
docker compose logs -f backend

# Frontend logs
docker compose logs -f frontend

# Reset all local state
docker compose down -v
```
