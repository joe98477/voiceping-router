# External Integrations

**Analysis Date:** 2026-02-06

## APIs & External Services

**Router WebSocket API:**
- Service: Custom Voice Ping Protocol
  - What it's used for: Real-time push-to-talk (PTT) audio routing and message delivery
  - Port: 3000 (default)
  - Protocol: WebSocket (ws:// or wss://)
  - Client: `src/lib/client.ts`, `src/lib/server.ts`, `src/lib/connection.ts`

**Control-Plane REST API:**
- Service: Express.js HTTP API
  - What it's used for: Dispatch console backend, user management, group/channel management, invites
  - Port: 4000 (default)
  - Protocol: REST with JSON payloads
  - Server: `control-plane/src/index.js`

**Web UI to Control-Plane:**
- Service: Internal HTTP/HTTPS to Control-Plane
  - SDK/Client: Fetch API via `web-ui/src/api.js`
  - Base URL: `VITE_API_BASE` environment variable
  - Auth: Session cookies (credentials: "include")
  - Endpoints: User auth, event management, team/channel settings, invites

**Web UI to Router:**
- Service: WebSocket real-time connection
  - What it's used for: Live dispatch console, group communication, audio streaming
  - URL: `VITE_ROUTER_WS` environment variable (defaults to `ws(s)://<host>:3000`)
  - SDK/Client: Native WebSocket

## Data Storage

**Databases:**
- **PostgreSQL 14+** (control-plane)
  - Connection: `DATABASE_URL` env var (format: `postgres://user:pass@host:port/db`)
  - Client: Prisma Client (`@prisma/client` 5.18.0)
  - Schema: `control-plane/prisma/schema.prisma`
  - Tables: Users, Events, Teams, Channels, Memberships, Invites, PasswordResets, AuditLogs, SystemSettings

**Redis**
- **Router Server (v2.8.0):**
  - Connection: `REDIS_HOST` (default: 127.0.0.1), `REDIS_PORT` (default: 6379), `REDIS_PASSWORD` (optional)
  - Use: Group state, message queuing, audio stream buffering
  - Client: `src/lib/redis.ts` (nodepack binary packing)

- **Control-Plane (v4.7.0):**
  - Connection: Same Redis instance (via environment)
  - Use: Session storage via connect-redis, pub/sub for router communication
  - Client: `connect-redis 7.1.0` with native Redis client

**File Storage:**
- Local filesystem only - no cloud storage integration detected

**Caching:**
- Redis (implicit caching via state storage)

## Authentication & Identity

**Auth Provider:**
- Custom implementation
  - User auth: Email/password with bcryptjs hashing (`control-plane/src/index.js`)
  - Session: Express sessions with Redis store (control-plane)
  - Cookie: `express-session` with secure flag controlled by `SESSION_COOKIE_SECURE` env var

**JWT Tokens:**
- `ROUTER_JWT_SECRET` or `SECRET_KEY` - Tokens for router authentication
  - Library: jsonwebtoken 9.0.2 (control-plane), jwt-simple 0.5.6 (router legacy)
  - Scope: Control-plane signs tokens for router API calls
  - Usage: Device/client authentication with router WebSocket

**Legacy Auth:**
- `LEGACY_JOIN_ENABLED` flag - Supports old company/user-id join flow (default: false)
- Token generation: `src/lib/` (router side)

**Password Management:**
- Password reset tokens: `PasswordReset` model with expiration (`control-plane/prisma/schema.prisma`)
- Email delivery: Nodemailer via SMTP

## Monitoring & Observability

**Error Tracking:**
- Not detected - No external error tracking service (Sentry, etc.)

**Logs:**
- Docker JSON-file driver for containers (configured in `docker-compose.yml`)
- Winston logger (0.8.3) for router (`src/lib/logger.ts`)
- Debug module (wildcard version) with `DEBUG=vp:*` in `.env.test.example`
- Console logging in control-plane

**Activity Tracking:**
- `AuditLog` model in database (`control-plane/prisma/schema.prisma`)
- Endpoint logging: Request logging in control-plane

## CI/CD & Deployment

**Hosting:**
- Docker containers (primary deployment method)
- Docker Compose for multi-service orchestration (`docker-compose.yml`)
- Bare metal Node.js supported (secondary)

**CI Pipeline:**
- Not detected - No GitHub Actions, GitLab CI, or other CI service configured

**Containers:**
- `voiceping-router:local` - Router service (`Dockerfile`)
- `control-plane` - API service (`control-plane/Dockerfile`)
- `web-ui` - React SPA with nginx (`web-ui/Dockerfile`)
- `redis` - Official Redis image
- `postgres:14-alpine` - PostgreSQL database

**Reverse Proxy:**
- Not configured in compose - Assumes external proxy (nginx/traefik)

## Environment Configuration

**Required env vars:**

**Core:**
- `PORT` - Router listen port (default 3000)
- `NODE_ENV` - production/development (required in prod)
- `ROUTER_JWT_SECRET` or `SECRET_KEY` - Auth secret (required in production)
- `SESSION_SECRET` - Session encryption key

**Redis:**
- `REDIS_HOST` (default: 127.0.0.1)
- `REDIS_PORT` (default: 6379)
- `REDIS_PASSWORD` (optional)
- `REDIS_URL` (optional, overrides host/port)

**Database (Control-Plane):**
- `DATABASE_URL` - PostgreSQL connection string
- `POSTGRES_USER` - DB user
- `POSTGRES_PASSWORD` - DB password
- `POSTGRES_DB` - Database name

**SMTP (Optional):**
- `SMTP_HOST` - Mail server hostname
- `SMTP_PORT` - Mail server port (default 587)
- `SMTP_USER` - SMTP username
- `SMTP_PASS` - SMTP password
- `SMTP_FROM` - From address (default: no-reply@voiceping.local)

**Web UI:**
- `WEB_BASE_URL` - Dispatch console base URL
- `VITE_API_BASE` - Control-plane API endpoint for browser
- `VITE_ROUTER_WS` - Router WebSocket URL for browser
- `VITE_STARTUP_STATUS` - Show startup status page (optional)

**Bootstrap & Testing:**
- `BOOTSTRAP_ADMIN_EMAIL` - Initial admin email (optional, first run only)
- `BOOTSTRAP_ADMIN_PASSWORD` - Initial admin password (optional)
- `TEST_SEED_ENABLED` - Create test event/team/channel (default false)
- `TEST_SEED_EVENT_NAME` - Test event name (default: "VoicePing Test Event")
- `TEST_SEED_TEAM_NAME` - Test team name (default: "Test Team")
- `TEST_SEED_CHANNEL_NAME` - Test channel name (default: "Test Channel")

**Optional Advanced:**
- `TRUST_PROXY` - Enable X-Forwarded-* headers (default: false)
- `SESSION_COOKIE_SECURE` - Enforce secure cookies (default: true in prod)
- `LEGACY_JOIN_ENABLED` - Support old join flow (default false)
- `GROUP_BUSY_TIMEOUT` - Group busy state timeout ms (default: 95000)
- `MAXIMUM_AUDIO_DURATION` - Max audio message ms (default: 90000)
- `MAXIMUM_IDLE_DURATION` - Max idle pause ms (default: 3000)
- `PING_INTERVAL` - WebSocket ping interval ms (default: 120000)
- `DEBUG` - Debug module pattern (e.g., "vp:*")

**Secrets location:**
- `.env` file (not committed) - Production/development secrets
- `.env.test` - Test environment (in docker-compose setup)
- Environment variables passed at container runtime (preferred for Docker)

## Webhooks & Callbacks

**Incoming:**
- Not detected - No webhook endpoints

**Outgoing:**
- SMTP email callbacks (implicit via Nodemailer) - Password resets, invites
- Control-plane to Router communication via TCP socket (`ROUTER_HOST:ROUTER_PORT`)
  - Port: 3000 (default `ROUTER_PORT`)
  - For: Router status queries, JWT token validation

**Router-to-Control-Plane:**
- TCP socket connection from control-plane (`control-plane/src/index.js`)
- Queries: Router availability, connection count, group status

## Service-to-Service Communication

**Control-Plane to Router:**
- Protocol: TCP socket
- Endpoint: `ROUTER_HOST:ROUTER_PORT` (default: localhost:3000)
- Timeout: `ROUTER_STATUS_TIMEOUT_MS` (default: 500ms)
- Purpose: Status checking, JWT validation

**Control-Plane to Redis:**
- For: Session storage, pub/sub (router events)

**Router to Redis:**
- For: Group state, message buffering, cleanup operations

---

*Integration audit: 2026-02-06*
