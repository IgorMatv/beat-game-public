# Architecture

## High-Level Flow

BeatGame is split into a browser frontend, a Spring Boot backend, PostgreSQL, Redis, and an observability stack.

```text
Browser
  | HTTP / WebSocket
Nginx frontend container
  | /api and /ws proxy
Spring Boot backend
  | JPA              | runtime state
PostgreSQL          Redis

Prometheus -> Spring Boot Actuator /actuator/prometheus
Grafana    -> Prometheus datasource
```

## Backend

The backend owns room lifecycle, player registration, game rounds, scoring, track catalog access, and WebSocket event handling.

Important packages:

- `com.beatgame.room`: room creation, joining, room status, cleanup.
- `com.beatgame.player`: player records and player tokens.
- `com.beatgame.game`: game state, round orchestration, Redis-backed runtime state.
- `com.beatgame.track`: track catalog, genres, decades, providers, preview URL resolution.
- `com.beatgame.websocket`: STOMP endpoint, inbound auth interceptor, game message handlers, disconnect handling.
- `com.beatgame.config`: Redis, REST clients, rate limiting, app configuration.

PostgreSQL stores durable entities such as rooms, players, and tracks. Redis stores active game state, scores, readiness, and disconnected-player markers.

## Frontend

The frontend is a React/Vite app. It handles room creation/joining, lobby configuration, gameplay, audio playback, round result overlays, and game-over state.

Important areas:

- `src/pages`: page-level flows for home, lobby, game, solo config, and game over.
- `src/hooks/useWebSocket.ts`: STOMP/SockJS connection, subscriptions, publishing game events.
- `src/store/useGameStore.ts`: client-side game state.
- `src/types`: shared DTO shapes used by WebSocket and API flows.

## WebSocket Model

The backend exposes `/ws` using SockJS with STOMP destinations:

- Client publishes commands under `/app`, for example `/app/game.start`, `/app/game.answer`, `/app/game.ready`, `/app/game.rejoin`, `/app/room.config`, `/app/game.pause`.
- Server broadcasts room state under `/topic/room.{roomCode}`.
- Server broadcasts game events under `/topic/game.{roomCode}`.

The frontend subscribes to both topics after connecting.

## Runtime Infrastructure

Docker Compose starts:

- `postgres`: durable relational data.
- `redis`: active game state and transient markers.
- `backend`: Spring Boot app.
- `frontend`: Nginx serving the built React app and proxying API/WebSocket traffic.
- `prometheus`: scrapes backend Actuator metrics.
- `grafana`: provisioned dashboards and Prometheus datasource.
