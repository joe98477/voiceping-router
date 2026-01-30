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

4) Log in with the bootstrap admin credentials from `.env.test`:

- Email: `BOOTSTRAP_ADMIN_EMAIL`
- Password: `BOOTSTRAP_ADMIN_PASSWORD`

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
- `WEB_BASE_URL` and `VITE_API_BASE` (HTTPS URLs)
- `SESSION_COOKIE_SECURE=true` and `TRUST_PROXY=true` when running behind TLS

3) Start the stack:

```
docker compose up -d --build
```

## Stopping the stack

```
docker compose down
```
