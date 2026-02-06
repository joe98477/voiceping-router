# Codebase Structure

**Analysis Date:** 2026-02-06

## Directory Layout

```
voiceping-router/
├── src/                           # Main TypeScript router server source
│   ├── app.ts                     # Entry point - HTTP server setup
│   └── lib/                       # Core library modules
│       ├── server.ts              # WebSocket server and routing logic
│       ├── client.ts              # Client connection management
│       ├── connection.ts          # WebSocket connection wrapper
│       ├── states.ts              # In-memory state management
│       ├── redis.ts               # Redis persistence layer
│       ├── packer.ts              # Binary message serialization
│       ├── voiceping.ts           # Main module exports
│       ├── config.ts              # Environment configuration
│       ├── logger.ts              # Winston logging setup
│       ├── types.ts               # TypeScript interfaces
│       ├── messagetype.ts         # Message type enums
│       ├── channeltype.ts         # Channel type enums
│       ├── keys.ts                # Redis key generation
│       ├── recorder.ts            # Audio message handling
│       └── vperror.ts             # Custom error types
├── control-plane/                 # Express API and database layer
│   ├── src/
│   │   └── index.js               # Express API server
│   ├── prisma/
│   │   ├── schema.prisma          # Database schema
│   │   └── migrations/            # Database migration history
│   └── package.json
├── web-ui/                        # React frontend UI
│   ├── src/
│   │   ├── components/            # React components
│   │   ├── pages/                 # Page components
│   │   ├── theme/                 # Styling
│   │   └── utils/                 # Utilities
│   └── package.json
├── test/                          # Mocha test files (root router)
│   ├── *.test.js                  # Test suites
│   └── mocha.opts                 # Mocha configuration
├── dist/                          # Compiled JavaScript (generated)
├── docs/                          # Documentation
├── tsconfig.json                  # TypeScript compiler config
├── tslint.json                    # TSLint linter config
├── package.json                   # Root router dependencies
└── README.md                       # Project documentation
```

## Directory Purposes

**src/:**
- Purpose: Router server implementation in TypeScript
- Contains: Core WebSocket server, client management, message routing, state management
- Key files: `src/app.ts` (entry point), `src/lib/server.ts` (main logic)

**src/lib/:**
- Purpose: Modular library components
- Contains: Transport, routing, serialization, persistence, configuration
- Key files: `server.ts`, `client.ts`, `connection.ts`, `states.ts`, `redis.ts`, `packer.ts`

**control-plane/src/:**
- Purpose: Management API for user, team, and channel administration
- Contains: Express endpoints, Prisma database operations, JWT token generation
- Key files: `control-plane/src/index.js` (API server, 1000+ lines)

**control-plane/prisma/:**
- Purpose: Database schema and migrations
- Contains: User, Team, Channel, SystemSettings models
- Key files: `control-plane/prisma/schema.prisma`, `migrations/`

**web-ui/src/:**
- Purpose: React dispatch console UI for operators
- Contains: Components (Console, SettingsTabs, etc.), pages, theme styling
- Key files: `web-ui/src/components/`, `web-ui/src/pages/`

**test/:**
- Purpose: Mocha test suite for router
- Contains: Unit and integration tests for messaging, groups, connections
- Test files: `test/*.test.js` (e.g., `group.test.js`, `private.test.js`)

**dist/:**
- Purpose: Compiled JavaScript output (generated on build)
- Generated: `npm run build` compiles TypeScript to JavaScript
- Committed: No (in .gitignore)

**docs/:**
- Purpose: Architecture documentation and deployment guides
- Contains: API specs, architecture diagrams, deployment instructions
- Key files: `docs/architecture.md`, `docs/deployment-compose.md`

## Key File Locations

**Entry Points:**
- `src/app.ts`: Main server startup - creates HTTP server and WebSocket listener
- `control-plane/src/index.js`: API server startup - Express with Prisma and Redis
- `web-ui/src/`: React Vite application entry

**Configuration:**
- `src/lib/config.ts`: All router environment variables and defaults
- `control-plane/src/index.js`: API server config (hardcoded at top, lines 1-60)
- `tsconfig.json`: TypeScript compilation targets (es5, commonjs)

**Core Logic:**
- `src/lib/server.ts`: WebSocket server, message routing, group management (336 lines)
- `src/lib/client.ts`: Connection pooling, message handling, acknowledgments (620 lines)
- `src/lib/connection.ts`: WebSocket wrapper, message packing/unpacking (165 lines)
- `src/lib/states.ts`: In-memory state store with optional Redis backup (440 lines)
- `src/lib/redis.ts`: Redis operations for persistence and subscriptions (300+ lines)

**Message Definition:**
- `src/lib/types.ts`: IMessage interface with channelType, messageType, fromId, toId, payload
- `src/lib/messagetype.ts`: Enum with 27 message types (START=1, AUDIO=3, TEXT=17, etc.)
- `src/lib/channeltype.ts`: Enum with 2 channel types (GROUP=0, PRIVATE=1)

**Serialization:**
- `src/lib/packer.ts`: Binary message encoding/decoding using notepack
- `src/lib/keys.ts`: Helper to generate Redis key names

**Utilities & Support:**
- `src/lib/logger.ts`: Winston logger configuration
- `src/lib/config.ts`: Centralized configuration with env var defaults
- `src/lib/voiceping.ts`: Module export wrapper (minimal, exports Server, MessageType, ChannelType)

**Testing:**
- `test/test.js`: Base test utilities and connection helpers
- `test/server.js`: Server setup for tests
- `test/client.js`: Client SDK for tests
- `test/*.test.js`: Test suites (group.test.js, private.test.js, etc.)

**Database:**
- `control-plane/prisma/schema.prisma`: Database schema with User, Team, Channel, SystemSettings, etc.
- `control-plane/prisma/migrations/`: Migration files for schema versions

## Naming Conventions

**Files:**
- TypeScript source: `camelCase.ts` (e.g., `server.ts`, `messagetype.ts`)
- JavaScript source: `camelCase.js` (e.g., `index.js` in control-plane)
- Test files: `descriptive.test.js` or `descriptive.spec.js` (e.g., `group.test.js`)
- Configuration: `tsconfig.json`, `tslint.json`, `mocha.opts`
- Compiled output: `outDir: dist` produces `dist/app.js`, `dist/lib/server.js`, etc.

**Directories:**
- Feature modules: `src/lib/` (lowercase with purpose suffix)
- Components: `web-ui/src/components/` (PascalCase directories for React components)
- Pages: `web-ui/src/pages/` (filename matches route)
- Database: `control-plane/prisma/` (schema + migrations)
- Tests: `test/` (flat structure with descriptive names)

**Functions & Classes:**
- Classes: `PascalCase` (e.g., `Server`, `Client`, `Connection`)
- Functions: `camelCase` (e.g., `sendMessageToUser()`, `handleConnectionMessage()`)
- Private methods: Prefix with underscore or use `private` keyword (e.g., `_handleSocketMessage()`)
- Callbacks: Named with `handle` prefix (e.g., `handleClientMessage()`, `handleSocketPing()`)

**Variables & Constants:**
- Constants: `UPPER_SNAKE_CASE` (e.g., `MAXIMUM_IDLE_DURATION`, `PING_INTERVAL`)
- Configuration values: `camelCase` (e.g., `routerJwtSecret`, `busyTimeout`)
- Maps/Dictionaries: Plural suffix (e.g., `clients`, `connections`, `usersInsideGroupsSet`)

**Types & Interfaces:**
- Interfaces: `IPascalCase` prefix (e.g., `IMessage`, `IClient`, `IServer`)
- Enums: `PascalCase` (e.g., `MessageType`, `ChannelType`)
- Type aliases: `camelCase` or `numberOrString` for union types

## Where to Add New Code

**New Feature (Message Routing):**
- Primary code: `src/lib/server.ts` (add method to Server class) or `src/lib/client.ts` (add handler)
- Message type: Add enum to `src/lib/messagetype.ts`
- Tests: `test/feature.test.js` with mocha syntax matching `test/group.test.js`
- Config: Add to `src/lib/config.ts` if needs env var

**New Component (React UI):**
- Implementation: `web-ui/src/components/ComponentName/ComponentName.tsx` or `.jsx`
- Styling: `web-ui/src/theme/` or inline CSS-in-JS
- Usage: Import in `web-ui/src/pages/` and render
- Build: Vite automatically handles compilation

**Utilities & Helpers:**
- Shared helpers (router): Add to `src/lib/` as new module or extend existing
- Shared helpers (API): Add to `control-plane/src/` or separate utilities file
- String/formatting: Create in same directory or `src/lib/utils.ts`

**Database Model Changes:**
- Schema definition: Edit `control-plane/prisma/schema.prisma`
- Generate client: Run `npm run prisma:generate`
- Create migration: `npx prisma migrate dev --name description`
- Deployed: Run `npm run prisma:migrate` on production

**New Endpoint (Control Plane API):**
- Route definition: Add to `control-plane/src/index.js` (main file, no separation)
- Database ops: Use `prisma` client instance in endpoint
- Auth: Check JWT via `req.session.userId` or token verification
- Response: JSON via `res.json()` or `res.status().json()`

**Environment Variables:**
- Router: Add default to `src/lib/config.ts` and document in README.md
- API: Add to `control-plane/src/index.js` config section (lines 1-60)
- Frontend: Export as `VITE_*` prefix for Vite to expose to browser

## Special Directories

**dist/:**
- Purpose: Compiled JavaScript output from TypeScript
- Generated: `npm run build` (cleans and runs compile)
- Committed: No (in .gitignore)
- Watch: `npm run build:watch` for development

**node_modules/:**
- Purpose: Installed dependencies
- Generated: `npm install`
- Committed: No (in .gitignore)

**.git/:**
- Purpose: Git repository metadata
- Committed: Yes (structure files only)

**migrations/ (control-plane/prisma/):**
- Purpose: Database schema version history
- Generated: `npx prisma migrate dev` creates new files
- Committed: Yes (part of schema version control)
- Applied: `npm run prisma:migrate` on deployment

**.env & .env.*:**
- Purpose: Environment variable configuration
- Generated: Manual creation from `.env.example`
- Committed: No (in .gitignore for secrets)
- Structure: `KEY=value` format, loaded by dotenv in app.ts

**test/:**
- Purpose: Test suite execution
- Config: `test/mocha.opts` specifies test patterns and options
- Run: `npm test` or `npm run test:watch`
- Framework: Mocha with Chai assertions
