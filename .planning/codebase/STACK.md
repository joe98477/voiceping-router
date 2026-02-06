# Technology Stack

**Analysis Date:** 2026-02-06

## Languages

**Primary:**
- TypeScript 3.5.1 - Router server (`src/` with `.ts` files)
- JavaScript (ES6+) - Control-plane API (`control-plane/src/index.js`)
- JavaScript (JSX) - React web UI (`web-ui/src/` with `.jsx` files)

**Secondary:**
- Node.js native modules for HTTP/networking

## Runtime

**Environment:**
- Node.js 8.16.0 (exact version required per `.nvmrc`)

**Package Manager:**
- npm 6.4.1 (exact version per `package.json` engines)
- Lockfile: `package-lock.json` (present)

## Frameworks

**Core:**
- Express 4.18.2 - Control-plane REST API (`control-plane/src/index.js`)
- React 18.3.1 - Web UI dispatch console (`web-ui/src/`)
- React Router 6.26.2 - Web UI routing (`web-ui/src/pages/`)
- Vite 5.4.8 - Web UI build tool and dev server (`web-ui/vite.config.js`)

**Real-time Communication:**
- ws (WebSocket) 5.2.0 - Router server WebSocket handling (`src/lib/server.ts`, `src/lib/connection.ts`)
- WebSocket (native browser API) - Web UI real-time updates

**Testing:**
- Mocha 5.0.5 - Test runner
- Chai 2.3.0 - Assertion library

**Build/Dev:**
- TypeScript 3.5.1 - TS compilation
- TSLint 5.8.0 - TypeScript linting
- ESLint 3.0.0 - JavaScript linting with Airbnb ES5 config
- tsc-watch 5.0.3 - Watch mode compilation with auto-reload

## Key Dependencies

**Critical:**
- @prisma/client 5.18.0 - ORM and schema management for control-plane database (`control-plane/src/index.js`, `control-plane/prisma/schema.prisma`)
- redis 2.8.0 - Router server in-memory store (`src/lib/redis.ts`)
- redis 4.7.0 - Control-plane session and pub/sub (`control-plane/src/index.js`)
- express-session 1.17.3 - Session management for control-plane (`control-plane/src/index.js`)
- connect-redis 7.1.0 - Redis session store integration (`control-plane/src/index.js`)

**Authentication & Security:**
- jsonwebtoken 9.0.2 - JWT token generation and validation (`control-plane/src/index.js`)
- jwt-simple 0.5.6 - Lightweight JWT for router legacy auth (`src/lib/`)
- bcryptjs 2.4.3 - Password hashing (`control-plane/src/index.js`)

**Communication:**
- nodemailer 6.9.13 - SMTP email (invites, password resets) (`control-plane/src/index.js`)

**Utilities:**
- dotenv 16.0.0 - Environment variable loading (`src/app.ts`)
- lodash 4.17.14 - Utility functions (router)
- debug * - Debug logging (`package.json`)
- notepack 0.0.2 - Binary message packing for router (legacy)
- notepack.io 3.0.1 - Binary message packing for web UI (`web-ui/package.json`)
- opus-decoder 0.7.7 - Audio codec decoding (`web-ui/package.json`)
- @mdi/js 7.4.47 - Material Design Icons (`web-ui/package.json`)
- q 1.4.1 - Promise/deferred library (router)

**Development:**
- @types/node 8.0.47 - TypeScript definitions
- @types/ws 3.2.0 - WebSocket TypeScript definitions
- babel-eslint 8.0.1 - Babel parser for ESLint
- eslint-plugin-react 7.4.0 - React-specific linting
- @vitejs/plugin-react 4.3.1 - Vite React plugin

## Configuration

**Environment:**
- Configured via `.env` file (copied from `.env.example`)
- Multiple environment support: development, test, production
- Test environment config: `.env.test.example` for Docker Compose testing

**Build:**
- TypeScript compilation to `dist/` directory via `tsconfig.json`
- Vite build for web UI (SPA)
- Docker multi-service build via `docker-compose.yml`

**Key Configuration Files:**
- `tsconfig.json` - TS compiler options (target ES5, CommonJS modules)
- `control-plane/package.json` - Control-plane specific dependencies
- `web-ui/package.json` - Web UI specific dependencies
- `web-ui/vite.config.js` - Vite dev/build config

## Platform Requirements

**Development:**
- Ubuntu 24.04 LTS (recommended)
- Node.js 8.16.0 (exact version)
- npm 6.4.1 (exact version)
- Redis (local or remote)
- PostgreSQL 14+ (for control-plane)

**Production:**
- Docker and Docker Compose
- Node.js 8.16.0 runtime
- Redis instance
- PostgreSQL 14+ instance
- SMTP server (optional, for email features)
- Nginx or similar reverse proxy (recommended for TLS)

**Deployment Target:**
- Containerized via Docker (primary)
- Bare Node.js on Linux servers (secondary)
- Browser clients supporting modern JavaScript

---

*Stack analysis: 2026-02-06*
