# Docker Compose deployment

This repository ships a multi-container stack (router, control-plane API, web UI, Redis, Postgres) that can be started with a single Docker Compose command and a small env file.

## Test setup (example)

1) Copy the test env template:

```
cp .env.test.example .env.test
```

2) Start the stack:

```
docker compose --env-file .env.test up -d --build
```

3) Open the services:

- Router: http://localhost:3000
- Control-plane API: http://localhost:4000
- Dispatch UI: http://localhost:8080

Dispatch audio uses WebCodecs for low-latency Opus in the browser. Chrome is the primary target for audio PTT.

Note: The dispatch UI proxies `/api/*` to the control-plane service inside the Docker network, so the browser always calls the same origin. Leave `VITE_API_BASE` empty to use the proxy.
Set `VITE_ROUTER_WS` if the router is not reachable on `ws(s)://<host>:3000`.

4) Log in with the bootstrap admin credentials from `.env.test`:

- Email: `BOOTSTRAP_ADMIN_EMAIL`
- Password: `BOOTSTRAP_ADMIN_PASSWORD`

### Test seed (optional)

The test env template enables a removable test event/team/channel. You can disable auto-seed in the env with
`TEST_SEED_ENABLED=false` or remove it in the dispatch UI under **System Settings → Test seed**.

### Email in test mode

SMTP is optional. If `SMTP_HOST` is empty, emails are skipped and the system still runs normally.
You can configure SMTP later in the dispatch UI under **System Settings**.

## Production notes

1) Copy the main env template and set strong secrets:

```
cp .env.example .env
```

2) Update these values in `.env`:

- `ROUTER_JWT_SECRET`, `SESSION_SECRET`, `SECRET_KEY`
- `WEB_BASE_URL` (HTTPS URL)
- `SESSION_COOKIE_SECURE=true` and `TRUST_PROXY=true` when running behind TLS

If you are serving the UI and API from the same domain, keep `VITE_API_BASE` empty and the UI will call `/api/*` via the built-in proxy. If you want the UI to call a different API host, set `VITE_API_BASE` to the full HTTPS URL.

3) Start the stack:

```
docker compose up -d --build
```

## Stopping the stack

```
docker compose down
```
