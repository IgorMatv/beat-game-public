# API Reference

BeatGame uses REST for room and catalog operations and STOMP over SockJS for real-time gameplay.

## REST API

The local API base is `/api`. In production it is served on the same HTTPS origin as the frontend.

### Rooms

| Method | Endpoint | Request body | Response |
|---|---|---|---|
| `POST` | `/api/rooms` | `{ "playerName": "Alice" }` | `201` with `roomCode`, `playerToken`, and `playerId` |
| `POST` | `/api/rooms/solo` | `{ "playerName": "Alice" }` | `201` with `roomCode`, `playerToken`, and `playerId` |
| `POST` | `/api/rooms/{code}/join` | `{ "playerName": "Bob" }` | `200` with `playerToken`, `playerId`, and current `players` |
| `GET` | `/api/rooms/{code}` | — | Room code, status, player count, and maximum players |
| `GET` | `/api/rooms/{code}/players` | — | Current player list |
| `POST` | `/api/rooms/{code}/reset` | — | `204`; requires `X-Player-Token` for that room |

Example create response:

```json
{
  "roomCode": "AB12CD",
  "playerToken": "<signed-jwt>",
  "playerId": 1
}
```

### Catalog

- `GET /api/genres` returns the supported genre names.
- `GET /api/decades` returns the supported decade values.

Track population is an administrative operation under `/api/admin/tracks/populate*` and requires the deployment-specific `X-Admin-Token` header. It is not needed for the normal game flow when the catalog has already been seeded.

## WebSocket / STOMP

Connect to `/ws` using SockJS. The STOMP `CONNECT` frame must include the signed JWT in the native `playerToken` header:

```text
CONNECT
accept-version:1.2
playerToken:<signed-jwt>

\0
```

After connecting, subscribe to both room topics:

- `/topic/room.{roomCode}` — room state, configuration updates, and start errors;
- `/topic/game.{roomCode}` — round state, results, game-over, and pause events.

The client normally publishes `game.subscribed` and `game.sync` immediately after subscribing. The first acknowledges the subscription; the second requests the current game snapshot after a reconnect or page reload.

### Client-to-server destinations

| Destination | Typical payload | Purpose |
|---|---|---|
| `/app/game.start` | `{ roomCode, rounds, category, categoryType }` | Host starts a game |
| `/app/game.answer` | `{ roomCode, trackId, answerIndex, timeMs }` | Submit the selected answer |
| `/app/game.ready` | `{ roomCode }` | Ready for the next round |
| `/app/game.subscribed` | `{ roomCode }` | Confirm topic subscription |
| `/app/game.sync` | `{ roomCode }` | Request the current game snapshot |
| `/app/room.config` | `{ roomCode, rounds, category, categoryType }` | Host changes game configuration |
| `/app/game.pause` | `{ roomCode, paused }` | Pause or resume music |

The server takes the authenticated room and player identity from the WebSocket session. Payload `roomCode` values are not trusted for authorization. `room.config` is host-only; answers and ready messages are available to authenticated room members.

### Server-to-client messages

Room topic messages include:

```json
{ "players": [{ "id": 1, "name": "Alice", "isHost": true }], "status": "WAITING" }
```

```json
{ "rounds": 5, "category": "ROCK", "categoryType": "GENRE" }
```

Game topic messages are identified by their fields rather than a shared `type` field. The main shapes are:

```json
{
  "roundNumber": 1,
  "totalRounds": 5,
  "trackId": 42,
  "previewUrl": "https://...",
  "options": ["Track A", "Track B", "Track C", "Track D"],
  "remainingSeconds": 30,
  "scores": { "1": 0, "2": 0 }
}
```

```json
{
  "roundNumber": 1,
  "correctTrackId": 42,
  "correctAnswer": "Track A — Artist",
  "scores": { "1": 100, "2": 0 }
}
```

Game-over messages contain `scores` and `winnerPlayerId`. Pause messages contain only `{ "paused": true }` or `{ "paused": false }`.

The server is authoritative for answer validity, scoring, round closure, and timers. Clients should treat WebSocket broadcasts as the source of truth for gameplay state.
