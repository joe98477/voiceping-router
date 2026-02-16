# ConnectVoice

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Node.js](https://img.shields.io/badge/Node.js-%3E%3D20.0.0-green.svg)](https://nodejs.org/)

Real-time push-to-talk communication platform for coordinating distributed teams during large-scale events.

## Overview

ConnectVoice is a WebRTC-based PTT comms system with event, team, and channel management. Built on mediasoup with Opus codec, it delivers low-latency voice communication through a Node.js + TypeScript server, React dispatch console, and native Android PTT client. The architecture is designed to scale to 1000+ concurrent users across multiple channels.

**Key Technologies:**
- mediasoup WebRTC SFU (Selective Forwarding Unit)
- Opus audio codec
- Node.js 20+ with TypeScript
- Redis for state management and pub/sub
- PostgreSQL for persistent data
- React dispatch console
- Android client (Jetpack Compose + mediasoup-android)

## Architecture

```
┌─────────────────┐     ┌─────────────────┐
│  Android Client │     │   Web Browser   │
│   (PTT App)     │     │ (Dispatch UI)   │
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     │
              ┌──────▼──────┐
              │    nginx    │  Port 3000
              │   (proxy)   │
              └──────┬──────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
   ┌────▼────┐  ┌───▼────┐  ┌───▼────┐
   │ audio-  │  │control-│  │ web-ui │
   │ server  │  │ plane  │  │        │
   │(mediasoup)│ │ (API)  │  │ (React)│
   └────┬────┘  └───┬────┘  └────────┘
        │           │
   ┌────┴───────────┴────┐
   │                     │
┌──▼──┐            ┌─────▼──┐
│redis│            │postgres│
└─────┘            └────────┘
```

### Docker Compose Services

| Service | Role | Port |
|---------|------|------|
| **audio-server** | mediasoup WebRTC audio routing, PTT session management | internal (via nginx) |
| **control-plane** | REST API: authentication, events, teams, channels | 4000 |
| **web-ui** | React dispatch console for event coordination | 8080 |
| **nginx** | Reverse proxy, WebSocket upgrade, single entry point | 3000 |
| **redis** | State management, pub/sub for real-time sync | internal |
| **postgres** | Persistent storage for users, events, channels | internal |

## Quick Start

**Prerequisites:** Docker and Docker Compose

```bash
git clone https://github.com/joe98477/voiceping-router.git
cd voiceping-router
cp .env.example .env
# IMPORTANT: Edit .env and set MEDIASOUP_ANNOUNCED_IP to your host's IP address
docker compose up -d --build
```

**Verification:**
```bash
docker compose ps  # All 6 services should be running
curl http://localhost:3000  # Should return welcome message
```

**Access Points:**
- Dispatch console: http://localhost:8080
- Control-plane API: http://localhost:4000
- Audio router WebSocket: ws://localhost:3000

**Note:** On first startup, Prisma migrations run automatically to initialize the database schema.

## Environment Configuration

The `.env` file controls all aspects of deployment. Copy `.env.example` as a starting point and configure the following:

### Core Settings
- **`NODE_ENV`**: `production` or `development`
- **`PORT`**: Audio server port (default: 3000)
- **`SECRET_KEY`**: Legacy JWT secret (change in production)
- **`ROUTER_JWT_SECRET`**: JWT secret for router tokens (MUST change in production)
- **`SESSION_SECRET`**: Session cookie secret (MUST change in production)
- **`LEGACY_JOIN_ENABLED`**: Enable legacy company/user-id join flow (default: false)

### Database
- **`POSTGRES_USER`**, **`POSTGRES_PASSWORD`**, **`POSTGRES_DB`**: PostgreSQL credentials
- **`DATABASE_URL`**: Full Postgres connection string (e.g., `postgres://user:pass@postgres:5432/voiceping`)
- **`REDIS_HOST`**, **`REDIS_PORT`**, **`REDIS_PASSWORD`**: Redis connection details

### mediasoup (WebRTC)
- **`MEDIASOUP_ANNOUNCED_IP`**: **CRITICAL** — Set to your host's public or LAN IP address. Without this, remote WebRTC clients cannot connect. Example: `192.168.1.100` for LAN or your public IP for internet access.
- **`MEDIASOUP_LISTEN_IP`**: IP to bind mediasoup workers (default: `0.0.0.0`)
- **`MEDIASOUP_MIN_PORT`**, **`MEDIASOUP_MAX_PORT`**: UDP port range for RTP (default: 40000-49999). Each user requires ~2 ports. Adjust for scale.
- **`MEDIASOUP_LOG_LEVEL`**: mediasoup logging (`debug`, `warn`, `error`)

### STUN/TURN (NAT Traversal)
- **`STUN_SERVER`**: STUN server URL (default: `stun:stun.l.google.com:19302`)
- **`TURN_SERVER`**, **`TURN_USERNAME`**, **`TURN_PASSWORD`**: Optional TURN server for restrictive NATs

### Web UI
- **`WEB_BASE_URL`**: Public URL of the dispatch console (e.g., `https://dispatch.example.com`)
- **`VITE_API_BASE`**: Control-plane API base URL (e.g., `https://api.example.com`)
- **`VITE_ROUTER_WS`**: WebSocket URL for the audio router (e.g., `wss://router.example.com`)

### SMTP (Optional)
- **`SMTP_HOST`**, **`SMTP_PORT`**, **`SMTP_USER`**, **`SMTP_PASS`**, **`SMTP_FROM`**: Office 365-compatible SMTP for password resets and invites

### Bootstrap (Optional)
- **`BOOTSTRAP_ADMIN_EMAIL`**, **`BOOTSTRAP_ADMIN_PASSWORD`**: Create an admin user on first startup

**Most Important Variable:** `MEDIASOUP_ANNOUNCED_IP` must be set correctly or WebRTC connections will fail for any client not running on localhost. Set it to your host's IP address (use `ip addr` or `ifconfig` to find it).

## Android Client

The Android PTT client is in the `android/` directory. It uses Jetpack Compose, Hilt, and mediasoup-android for native WebRTC.

**Prerequisites:**
- Android Studio (latest)
- JDK 21
- Android SDK 35 (compile target)

**Build:**
```bash
cd android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
```

**Output:** `android/app/build/outputs/apk/debug/app-debug.apk`

**Requirements:**
- Minimum SDK: 26 (Android 8.0)
- Target SDK: 35 (Android 15)
- AGP: 9.0.0 (bundles Kotlin 2.2)

**Testing:** Install the APK on a physical device (emulators have limited WebRTC support). Configure the server URL in the app settings to point to your MEDIASOUP_ANNOUNCED_IP.

## Development

For local development without Docker:

**Prerequisites:** Node.js 20+, Redis running locally, PostgreSQL instance

**Audio Server:**
```bash
npm install
npm run dev
```

**Control-plane:**
```bash
cd control-plane
npm install
npm run prisma:migrate  # Run database migrations
npm start
```

**Web UI:**
```bash
cd web-ui
npm install
npm run dev
```

**Tests:**
```bash
npm test          # Run vitest unit tests
npm run lint      # ESLint + Prettier
```

**Note:** Local development requires setting up Redis and PostgreSQL separately. The easiest path is to use `docker compose` for dependencies and run only the Node.js services locally if needed.

## Documentation

Detailed guides are available in the `docs/` directory:

- **[Architecture](docs/architecture.md)** - Control-plane design, authentication flow, Redis state sync
- **[Deployment Guide](docs/deployment-compose.md)** - Production Docker Compose walkthrough, nginx config, SSL setup
- **[API Reference](docs/api.md)** - Control-plane REST endpoints for events, teams, channels, users
- **[User Manual](docs/user-manual.md)** - Dispatch console usage guide for coordinators
- **[Opus Implementation](docs/opus-implementation.md)** - Audio codec details, browser compatibility, Opus parameters

## License

MIT License - see [LICENSE](LICENSE) file for details.

Copyright © 2024-2026 Smart Walkie Pte Ltd
