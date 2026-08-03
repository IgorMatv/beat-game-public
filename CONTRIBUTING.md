# Contributing

Thanks for taking an interest in BeatGame. The project is a small full-stack application, so changes that keep the backend, frontend, and real-time protocol aligned are especially valuable.

## Development setup

The fastest way to run the complete stack is Docker Compose:

```powershell
Copy-Item .env.example .env
# Set GRAFANA_ADMIN_PASSWORD in .env
docker compose up -d --build
```

The game is available at `http://localhost`; Grafana is available at `http://127.0.0.1:3000`.

For focused development, run the backend and frontend using their native toolchains:

```powershell
cd beatgame-backend
mvn spring-boot:run
```

```powershell
cd beatgame-frontend
npm ci
npm run dev
```

See the root README for prerequisites and the Docker-based workflow.

## Tests

Run backend tests from `beatgame-backend`:

```powershell
mvn test
```

The backend test suite uses Testcontainers for at least one PostgreSQL integration test, so Docker must be running.

Run frontend tests from `beatgame-frontend`:

```powershell
npm ci
npm test
```

Before opening a pull request, also run the frontend production build:

```powershell
npm run build
```

## Making changes

- Keep gameplay state and authorization decisions on the server. The client countdown and UI state are not authoritative.
- If a REST or WebSocket payload changes, update `docs/API.md` and the relevant frontend types together with the backend DTO or controller.
- Add or update tests for behavior changes, especially around room membership, reconnects, timers, and round idempotency.
- Do not commit `.env`, credentials, generated frontend output, `node_modules`, or local IDE files.
- Keep production infrastructure and deployment secrets out of application changes unless the change specifically concerns deployment.

## Pull requests

A useful pull request includes:

1. A short explanation of the user-visible or operational change.
2. Tests and build commands that were run.
3. Notes about database migrations, environment variables, or WebSocket protocol changes.
4. Screenshots or a short recording for visible frontend changes.

Please keep unrelated refactors out of feature or bug-fix pull requests. Changes that affect both sides of the WebSocket contract should be reviewed as one change.
