# Design Decisions

A running list of the choices behind BeatGame that aren't obvious from just reading the code — what was decided, and why.

| Decision | Why |
|---|---|
| Round timer is enforced server-side | The client shows a countdown for feedback, but the server decides when a round actually closes — a manipulated client clock can't extend a player's own time. |
| `playerToken` is a signed JWT, not a bare UUID | An unsigned identifier could be forged or replayed for any player. The JWT carries the player's identity and the room it belongs to as claims, verified when the WebSocket connection is established, and again on the REST endpoints that need room-scoped authorization. |
| Redis for live game state, Postgres for durable records | Round-by-round state (current round, scores, readiness, disconnect timers) is short-lived and rewritten constantly during a game; rooms, players, and the track catalog need to survive backend restarts. Splitting them avoids paying Postgres write-durability costs for data that's supposed to disappear when the game ends. |
| Circuit breaker + retry around the Deezer client | Deezer is a free, unauthenticated third-party API — rate limits and transient failures happen. Resilience4j handles retry/backoff and trips a circuit breaker instead of hand-rolled retry logic scattered through the provider code. |
| iTunes as a fallback source, not the primary | Deezer has richer genre/chart/pagination support; iTunes fills in when a Deezer lookup comes up short or fails, rather than being queried first. |
| Redis `SETNX`-based idempotency guards on round start/close/answer | A round can be closed by either the last player answering or a server-side timeout firing around the same moment — the guard makes whichever happens first win, cleanly, without a duplicate broadcast. |
| Weighted track selection (least-played, longest-unused first) | A plain random pick would resurface the same handful of popular tracks constantly. Weighting by play count and last-used time spreads plays across the catalog. |
| 6-character, `SecureRandom`-generated room codes | Short enough to read aloud or type from memory, with a uniqueness retry loop on generation to avoid collisions. |
| Deploys go through AWS SSM Session Manager, not SSH | CI needs a way to run commands on the EC2 host without keeping an inbound SSH port open just for deployments. |
| GitHub Actions authenticates via OIDC, not stored AWS keys | Removes an entire class of credential-leak risk — there's no long-lived AWS access key sitting in repository secrets to rotate or accidentally expose. |
| Guest identity only, no accounts | The game is meant to be pick-up-and-play with a friend — a display name is enough to start a room, and there's nothing to sign up for. |
| A separate `Genre` enum, not the provider's own genre IDs | Deezer and iTunes each model genres differently (numeric IDs vs. name strings). Owning the vocabulary means the app doesn't change if a provider's genre model does — only the mapping layer would. |
| STOMP over WebSocket instead of polling | Both players need to see the round timer, the opponent's readiness, and results within the same second they happen. A single persistent connection pushes updates instantly; polling would either lag or add constant request overhead for no benefit. |
| Nginx as the only container with a published port in production | The backend, Postgres, and Redis are never directly reachable from outside the Docker network — everything external goes through the reverse proxy. |
| Docker Compose, not Kubernetes/ECS, for this stage | Right-sized for a single-instance deployment. The state model (Postgres for durable data, Redis for ephemeral game state) doesn't assume single-instance, so a move to an orchestrated, multi-instance setup later is additive rather than a rewrite. |
