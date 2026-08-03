<div align="center">

# 🎵 BeatGame

**Real-time multiplayer music guessing game.**
Create a room, invite a friend, race against the clock to name the track before they do.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](#major-technologies)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)](#major-technologies)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)](#major-technologies)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)](#major-technologies)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](#major-technologies)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](#major-technologies)
[![Docker Compose](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](#quick-start)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

> 🎮 **Live demo:** [beatgame.app](https://beatgame.app) — password: `pass12345` (a casual access filter, not an account system).

## Purpose

BeatGame is a full-stack, real-time multiplayer web game built as a portfolio project to demonstrate production-style service composition: a Spring Boot backend, a React SPA, WebSocket-driven game state, a persistence/cache layer, and a self-hosted observability stack for local development — deployed to a real domain over HTTPS.

## Main User Flow

1. A player opens the site, enters a display name, and creates a room (or plays **solo** without inviting anyone).
2. The room owner shares the generated room code; a second player joins with it.
3. Once both players are ready, the game runs a fixed number of rounds: each round plays a short track preview and both players race to pick the correct title from a set of options before the round timer expires.
4. Each round shows a result screen (who answered correctly, updated scores); after the final round, a game-over screen declares the winner.

## Major Technologies

| Layer | Stack |
|---|---|
| **Backend** | Java 21 · Spring Boot 3.2 · Spring WebSocket · Spring Data JPA · Redis · Flyway · Micrometer |
| **Frontend** | React 18 · TypeScript · Vite · Zustand · STOMP.js · SockJS · Howler |
| **Infrastructure** | Docker Compose · PostgreSQL 16 · Redis 7 · Nginx · Prometheus · Grafana OSS |
| **Testing** | JUnit 5 · Spring Boot Test · Testcontainers · Vitest |

## Repository Structure

```
beatgame-backend/     Spring Boot API + WebSocket server (Java 21, Maven)
beatgame-frontend/    React + TypeScript SPA (Vite)
docs/                 Architecture, deployment, and design-decision write-ups
observability/        Prometheus scrape config + provisioned Grafana dashboard
docker-compose.yml    Full local stack — all services in containers
```

## Prerequisites

- Docker Desktop / Docker Engine with Compose v2 (`docker compose`, not the standalone `docker-compose`)
- Internet access on first run — the backend seeds its track catalog from the public Deezer and iTunes APIs
- To run tests outside Docker: Java 21 + Maven on `PATH` (no Maven wrapper is checked in), and Node.js ≥ 20 + npm

## Quick Start

```powershell
Copy-Item .env.example .env
```

Edit `.env` and set `GRAFANA_ADMIN_PASSWORD` — Compose fails fast without it (`GF_SECURITY_ADMIN_PASSWORD` is a required variable in `docker-compose.yml`). Every other value in `.env.example` has a working default or safely disables an optional feature when left blank (e.g. `ADMIN_TOKEN`, `VITE_ACCESS_PASSWORD`).

```powershell
docker compose up -d --build
```

Open **http://localhost**. Grafana is reachable at **http://127.0.0.1:3000**.

```powershell
docker compose down
```

## Running Tests

```powershell
cd beatgame-backend
mvn test
```

Requires Docker to be running — one test (`TrackRepositoryTest`) uses Testcontainers to start a real Postgres instance.

```powershell
cd beatgame-frontend
npm ci
npm test
```

## Documentation

- **[Architecture](docs/ARCHITECTURE.md)** — components, data flow, and why state is split between Postgres and Redis.
- **[Deployment](docs/DEPLOYMENT.md)** — how this runs in AWS, and the CI/CD pipeline that ships it.
- **[Design Decisions](docs/DESIGN_DECISIONS.md)** — a running list of notable choices and the reasoning behind them.
- **[API](docs/API.md)** — REST endpoints and the STOMP/WebSocket message flow.
- **[Contributing](CONTRIBUTING.md)** — local workflow, tests, and pull-request expectations.
- **[Security](SECURITY.md)** — authentication scope and vulnerability reporting.

## Current Limitations

- **Single backend instance only.** The WebSocket broker and round timers are in-process (Spring's simple broker, a JVM-local scheduler) — there's no coordination across processes, so the backend cannot be horizontally scaled as-is. Round timers are re-armed after a restart from Redis state, but a restart can still interrupt or delay an in-progress round.
- **No automated end-to-end coverage.** The full create-room → play → game-over journey is verified by a human following a manual test checklist, not by an automated test.
- **Frontend test coverage is narrow.** A handful of source files have any test at all; most page components are untested.
- **Limited provider fallback.** On-demand catalog population falls back from Deezer to iTunes when Deezer fails, but provider fallback is not universal and does not guarantee complete catalog or preview availability.
- **The live demo's password gate is a casual filter, not authentication** — it's a shared static password baked into the client bundle, not an account or session system.
- **Single-instance deployment.** Production runs on one EC2 host with no managed database, load balancer, or staging environment.

### Security scope

BeatGame uses short-lived guest JWTs to authenticate WebSocket gameplay, but it has no user accounts, logout, token revocation, or persistent identity. Some room-information REST endpoints are intentionally lightweight and do not require a player token. Production secrets must be supplied through deployment configuration; the defaults in the repository are for local development only.

## License

Released under the [MIT License](LICENSE).
