# WebSocket Reliability

This document describes the current WebSocket behavior. It is intentionally written as an implementation note, including current limits and recommended improvements.

## Current Implementation

Frontend:

- Uses SockJS and STOMP.js in `beatgame-frontend/src/hooks/useWebSocket.ts`.
- Connects to `${VITE_API_BASE_URL}/ws`, or `/ws` when `VITE_API_BASE_URL` is empty.
- Sends `playerToken` and `roomCode` as STOMP connect headers.
- Subscribes to `/topic/room.{roomCode}` and `/topic/game.{roomCode}`.
- Uses `reconnectDelay: 5000`, so reconnect attempts happen every 5 seconds.
- Does not currently configure exponential backoff or jitter.
- Does not explicitly configure STOMP heartbeat intervals in the client.

Backend:

- Exposes `/ws` with SockJS in `WebSocketConfig`.
- Uses Spring's simple in-memory STOMP broker for `/topic` destinations.
- Accepts client commands on `/app/*` destinations.
- Uses `AuthChannelInterceptor` to associate connection headers with the WebSocket session.
- Handles disconnects with `DisconnectEventListener`.

## Client Disconnect During a Multiplayer Game

When one player disconnects during an active multiplayer game:

1. Spring emits `SessionDisconnectEvent`.
2. `DisconnectEventListener` loads the room and game state.
3. The player is marked as disconnected in Redis.
4. A 60-second rejoin timer starts.
5. If the player is still marked disconnected after 60 seconds, the game ends and the remaining player is declared winner.
6. Runtime game data for the room is cleared from Redis.

If the player reconnects within the window and publishes `/app/game.rejoin`, the backend attempts to restore state through `gameService.handleRejoin`.

## Solo Disconnect

For solo rooms, a disconnect clears Redis game data immediately. A game-over WebSocket message is not useful in that case because the only client connection is already gone.

## Page Reload or Browser Reopen

The client stores game-related state in the frontend store. On WebSocket reconnect, the hook publishes `/app/game.rejoin` only when the local phase is neither `home` nor `lobby`.

Practical impact:

- A short network drop while the page remains open has a recovery path.
- A full reload can lose in-memory frontend state unless route/local storage state is enough to reconnect with the room code and player token.
- The backend-side room/game state can still exist in PostgreSQL/Redis, but the client needs a reliable way to rediscover and replay it.

## Server Restart

Server restart recovery is limited in the current implementation.

What survives:

- PostgreSQL data survives because it is stored in the database volume.
- Redis data survives if the Redis container and volume remain available.

What does not fully survive:

- Active Java timers and scheduled tasks are in process memory.
- Spring's simple STOMP broker is in memory.
- WebSocket subscriptions disappear and clients must reconnect.

Practical impact: after a backend restart, clients may reconnect, but active round timing and full game continuation are not guaranteed without additional recovery logic.

## Heartbeat

No explicit STOMP heartbeat configuration is present in the frontend hook or backend WebSocket config. SockJS/STOMP and underlying transports may still detect broken connections eventually, but the application does not currently define a clear heartbeat policy.

## Recommendations

1. Add explicit STOMP heartbeat settings on both client and server.
2. Replace fixed 5-second reconnect with exponential backoff plus jitter.
3. Persist enough round state to resume after backend restart: current round id, start time, deadline, selected track, options, submitted answers, and scores.
4. Add a REST or WebSocket snapshot endpoint that lets a reconnecting client fetch the authoritative room/game state.
5. Store player token and room code in a deliberate client persistence layer if reload recovery is required.
6. Add integration tests for disconnect, reconnect within 60 seconds, reconnect after timeout, and backend restart behavior.
7. Consider an external broker if this grows beyond a single backend instance.
