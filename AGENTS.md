# AGENTS.md

## Plan Mode
- Make the plan extremely concise. Sacrifice grammer for the sake of concision.
- At the end of each plan, give me a list of unresolved questions to answer, if any.

## Scope
This file applies to the entire repository unless a more specific AGENTS.md is added in a subdirectory.

## Repo overview
This repository contains the VoicePing Router Node.js websocket server and its Docker Compose setup.

## Additional docs
- `docs/opus-implementation.md` documents the current Opus/WebCodecs implementation for the dispatch UI and router payload expectations.

## Working agreements
- Keep changes small and focused.
- Prefer adding new documentation under `docs/` when proposing architecture or workflows.
- Update `README.md` only when user-facing setup steps change.
- Use `npm run build` and `npm test` when practical before committing.

## UI reliability
- Clear stale error messages after successful reloads.
- When data is `null` and an error exists, render an error state instead of a perpetual loading screen.
- Clear errors on retry actions before re-fetching.
- Routes that depend on profile setup should redirect away from `/first-run` once setup is complete.

## Code style
- Follow existing TypeScript/TSLint conventions in `tslint.json` and `tsconfig.json`.
- Avoid try/catch around imports.

## Docker
- Compose services live in `docker-compose.yml`.
- Redis configuration is stored in `redis/redis.conf`.
