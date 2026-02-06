# Coding Conventions

**Analysis Date:** 2026-02-06

## Naming Patterns

**Files:**
- Lowercase with camelCase for file names: `voiceping.ts`, `channeltype.ts`, `messagetype.ts`
- Classes in TypeScript: `Client`, `Connection`, `Server`, `States`, `VPError`
- Test files use dot notation: `group.test.js`, `connection.test.js`, `private.test.js`
- Test helpers are separate files without test suffix: `client.js`, `server.js`, `client-promise.js`

**Functions:**
- camelCase for function names and methods
- Arrow functions used for event handlers: `handleSocketMessage = (data: any) => { ... }`
- Static methods on classes for utility functions: `States.getUserFromToken()`, `States.addUserToGroup()`
- Debug function pattern: `const dbug1 = dbug("vp:module"); function debug(msg: string) { ... }`

**Variables:**
- UPPERCASE with UNDERSCORE for constants: `MAXIMUM_IDLE_DURATION`, `PING_INTERVAL`, `GROUPS_BUSY_TIMEOUT`, `SECRET`
- camelCase for local variables and parameters: `userId`, `deviceId`, `groupId`, `connections`
- Private properties prefixed with nothing, but convention is `private` keyword: `private user: any`, `private connections: IConnections`
- Unused or "any" types: `private user: any` - widely used when type is uncertain

**Types:**
- Interfaces prefixed with `I`: `IMessage`, `IConnection`, `IConnections`, `IServer`
- Enums PascalCase: `ErrorType`, `ChannelType` (actually enum values, not exported as interface)
- Generic parameter names: `<T>` when needed
- Type aliases use PascalCase: `numberOrString = number|string`

**Message Structure Pattern:**
- Array-based encoding using notepack: `[ChannelType.GROUP, MessageType.START, from, to, timestamp]`
- Indices stored as constants: `CHANNEL_TYPE: 0`, `MESSAGE_TYPE: 1`, `FROM_ID: 2`, `TO_ID: 3`, `MESSAGE_ID: 4`, `BUFFER: 5`

## Code Style

**Formatting:**
- Indentation: 2 spaces (enforced by ESLint)
- Line endings: Not explicitly configured
- Quotes: Double quotes enforced by ESLint rule: `"quotes": [2, "double", "avoid-escape"]`
- Semicolons: Used throughout (standard)

**Linting:**
- Framework: ESLint with Airbnb ES5 base config
- TSlint: Used alongside ESLint for TypeScript linting
- Prettier: Not configured (not in devDependencies)

**Key ESLint Rules:**
- `comma-dangle: 1` - Warning for trailing commas
- `consistent-return: 1` - Warning for inconsistent returns
- `handle-callback-err: 2` - Error if callback errors not handled
- `indent: 1` - Warning for indentation
- `quotes: [2, "double", "avoid-escape"]` - Enforce double quotes
- `space-before-function-paren: [2, "never"]` - No space before function parentheses
- `valid-jsdoc: 1` - Warning for invalid JSDoc

## Import Organization

**Order:**
1. Node.js built-in modules: `import * as cluster from "cluster"`, `import * as http from "http"`
2. Third-party packages: `import * as Q from "q"`, `import * as WebSocket from "ws"`, `import * as dbug from "debug"`
3. Local module imports: `import ChannelType = require("./channeltype")`, `import Connection from "./connection"`
4. Module exports: `import { IMessage } from "./types"`

**Path Aliases:**
- No path aliases configured; uses relative paths: `"./client"`, `"./lib/voiceping"`

**Import Styles:**
- CommonJS require in some modules: `const ChannelType = require("./channeltype")`
- ES6 imports with asterisk: `import * as Q from "q"`
- Default imports: `import Connection from "./connection"`
- Destructured imports: `import { IMessage, numberOrString } from "./types"`

**TSLint Disables:**
- Used selectively: `/* tslint:disable-line:no-var-requires */` for require statements
- Module-level disables: `/* tslint:disable:object-literal-shorthand */` and `/* tslint:enable:object-literal-shorthand */`

## Error Handling

**Patterns:**
- Callback-based error handling: `callback: (err: Error, data?: T) => void`
- Try-catch blocks for synchronous operations: Used in `States.getUserFromToken()`, `Connection.ping()`, `Connection.send()`
- Error propagation through callbacks: `if (err) { ... }`
- Silent error returns: Some errors only logged via `debug()`, not propagated
- Custom error class: `VPError extends Error` with error types enum

**Error Logging:**
- Errors logged with context: `debug(\`PACK ERR ${err} ${JSON.stringify(msg)}\`)`
- Logger instance used for system-level errors: `logger.error(msg, ...meta)`
- Full JSON stringification of error context in debug messages

## Logging

**Framework:** Winston logger (custom wrapper in `src/lib/logger.ts`)

**Logger Instance:**
- Single logger instance exported: `export = new Lgger()` (note: class named `Lgger` not `Logger`)
- Methods: `info(msg: string, ...meta: any[])`, `error(msg: string, ...meta: any[])`

**Patterns:**
- Worker identification prepended: `(cluster.worker ? \`worker ${cluster.worker.id} \` : "") + msg`
- Debug module for detailed tracing: `import * as dbug from "debug"` with namespace `vp:module-name`
- Info-level logging for connection events: `logger.info(\`id ${this.id} key ${key} ...\`)`
- Error-level logging for exceptions: `logger.error(...)`
- Debug function wraps debug module with worker context

**When to Log:**
- Connection lifecycle events (register, close, duplicate login)
- Message handling (receive, pack, unpack, send)
- State changes (user added to group, group busy timeout)
- Errors and exceptions
- Ping/pong heartbeat (debug level only)

## Comments

**When to Comment:**
- Rarely used; code is generally self-documenting
- Section headers for handler groups: `// (WEB)SOCKET (WS) EVENT HANDLERS`
- Implementation notes: `// timestamp on stop talking is not implemented on mobile clients, this is just a test payload data`
- Commented-out code is minimal

**JSDoc/TSDoc:**
- Minimal usage; found in `States` class
- Format example: `/** * Decode user token from JWT format into user uuid * @param { string } token * @param { function } callback * @private */`
- Not comprehensive; only on some public methods

## Function Design

**Size:**
- Most functions 20-60 lines
- Larger functions in state management (`States.ts`: methods 30-100 lines)
- Private handler functions tend to be smaller (10-30 lines)

**Parameters:**
- Limited parameters (typically 2-4)
- `this: ClassName` used for class method context
- Callbacks as final parameters: `callback?: (err: Error, data?: T) => void`
- Optional callbacks increasingly used (recent commits show `callback?` optional parameter)

**Return Values:**
- Most methods return void when using callbacks
- Some return values for promises (using Q library)
- Silent returns on error in callbacks: `return;` without explicit value
- Type annotations on all return values

## Module Design

**Exports:**
- Default exports for classes: `export default class Client`, `export default class Connection`
- Named exports for interfaces: `export interface IServer`, `export type numberOrString`
- Module exports for constants: `module.exports = VoicePing;`
- Static class methods for singleton-like utilities: `States` class with only static methods

**Barrel Files:**
- `voiceping.ts` acts as main entry point, exports `ChannelType`, `MessageType`, `Server`, `decoder`, `mediaRecord`
- Not a typical barrel file pattern; more of a facade

**Private/Public:**
- Consistent use of access modifiers: `public`, `private`
- Properties explicitly declared: `public id: numberOrString`, `private connections: IConnections = {}`
- Arrow functions for handlers ensure `this` binding: `private handleSocketClose = (code, reason) => { ... }`

## TypeScript Configuration

**Compiler Settings (tsconfig.json):**
- Target: ES5
- Module: CommonJS
- `noImplicitReturns: true` - All code paths must return
- `noImplicitThis: true` - `this` must be explicitly typed
- `noImplicitAny: false` - `any` type allowed (seen in `user: any`)
- `strictNullChecks: false` - Null/undefined checks not enforced
- `removeComments: true` - Comments stripped from output
- Source maps enabled

## Notable Patterns

**Worker Context Pattern:**
- Cluster identification built into debug and logger messages
- Enables tracking which worker processed each operation
- Pattern: `(cluster.worker ? \`worker ${cluster.worker.id} \` : "") + msg`

**Promise-based Testing:**
- Uses Q library for Promise handling
- Test helpers wrap callbacks in promises for cleaner test code
- `Q.defer()` and `Q.all()` patterns in test setup

**Binary Data Handling:**
- Multiple utility functions for normalizing binary payloads
- Handles Buffer, Uint8Array, ArrayBuffer, ArrayBuffer views
- Pattern: Check each type and convert to Buffer uniformly

---

*Convention analysis: 2026-02-06*
