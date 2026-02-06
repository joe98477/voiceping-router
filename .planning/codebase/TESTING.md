# Testing Patterns

**Analysis Date:** 2026-02-06

## Test Framework

**Runner:**
- Mocha 5.0.5
- Config: `test/mocha.opts`

**Assertion Library:**
- Chai 2.3.0 with `expect` syntax

**Run Commands:**
```bash
npm test                  # Run all tests (mocha --exit with xunit reporter)
npm run test:watch       # Watch mode (mocha --reporter nyan --watch)
```

**Mocha Configuration (test/mocha.opts):**
```
--exit
--reporter xunit
--reporter-options output=./test-results/mocha/results.xml
--timeout 5000
```

## Test File Organization

**Location:**
- All tests in `test/` directory at project root
- No co-located test files with source
- Test files separate from source code (`src/lib/` vs `test/`)

**Naming:**
- Pattern: `[feature].test.js`
- Examples: `group.test.js`, `private.test.js`, `connection.test.js`, `self.test.js`
- Test helpers without `.test` suffix: `client.js`, `server.js`

**File Structure:**
```
test/
├── *.test.js              # Test suites (describe/it blocks)
├── client.js              # Connection helper (setup utilities)
├── client-promise.js      # Promise wrapper for client
├── server.js              # Server setup helper
├── server-promise.js      # Promise wrapper for server
├── mocha.opts             # Mocha configuration
└── test.js                # (Exists but purpose unclear from analysis)
```

## Test Structure

**Suite Organization:**
```javascript
describe("router group messaging flows", function() {
  var server = null;
  var userId1 = "1";
  var userId2 = "2";
  var userId3 = "3";
  var groupId = "18";
  var ws1 = null;
  var ws2 = null;
  var ws3 = null;

  before(function(done) {
    runServerPromisified(port)
      .then(function(server1) {
        server = server1;
        // setup connections...
      }).catch((err) => {
        done(err);
      });
  });

  beforeEach(function(done) {
    done();
  });

  it("should receive acknowledge start for sender and start talking for receiver", function(done) {
    // test body
  });

  after(function() {
    // cleanup
  });
});
```

**Lifecycle Patterns:**
- `before()` - Single setup for entire suite; creates server and connects WebSocket clients
- `beforeEach()` - Minimal usage; often empty
- `it()` - Test cases with `done` callback for async
- `after()` - Cleanup; closes WebSocket connections and server

**State Sharing:**
- Suite-level variables store test state: `var server = null`, `var ws1 = null`
- Test data hardcoded: `var userId1 = "1"`, `var groupId = "18"`, `var port = 3334`
- Each test mutates suite state or works independently

## Test Structure - Detailed Example

**Basic Connection Test (connection.test.js):**
```javascript
describe("server connection", function() {
  var server = null;

  before(function(done) {
    server = runServer(port, done);
  });

  after(function(done) {
    server.close();
    done();
  });

  it("should connect", function(done) {
    var ws = connectClient(port);

    ws.on("open", function() {
      done();
    });

    ws.on("error", function(error) {
      done(new Error(error.message));
    });
  });
});
```

**Messaging Test with Dual Listeners (group.test.js):**
```javascript
it("should receive acknowledge start for sender and start talking for receiver", function(done) {
  var from = userId1;
  var to = groupId;

  var timestamp = Date.now();
  var message = notepack.encode([ChannelType.GROUP, MessageType.START, from, to, timestamp]);
  ws1.send(message);

  var acknowledged = false;
  var received = false;

  ws1.once("message", function(data) {
    expect(data).to.not.be.empty;

    var decoded = notepack.decode(data);
    var channelType = decoded[0];
    expect(channelType).to.be.equal(ChannelType.GROUP);

    var messageType = decoded[1];
    expect(messageType).to.be.equal(MessageType.START_ACK);

    var payload = decoded[4];
    expect(payload).to.not.be.empty;
    expect(payload).to.be.equal(`${timestamp}`);

    acknowledged = true;
    if (received) { done(); }
  });

  ws2.once("message", function(data) {
    expect(data).to.not.be.empty;

    var decoded = notepack.decode(data);
    var channelType = decoded[0];
    expect(channelType).to.be.equal(ChannelType.GROUP);

    var messageType = decoded[1];
    expect(messageType).to.be.equal(MessageType.START);

    var payload = decoded[4];
    expect(payload).to.not.be.empty;
    expect(payload).to.be.equal(timestamp);

    received = true;
    if (acknowledged) { done(); }
  });
});
```

## Mocking

**Framework:** No mocking library used (no sinon or jest mocks)

**Approach:**
- Real server and client instances used in tests
- WebSocket connections are real connections to test server
- No stubbing or spying on methods
- Uses real `notepack` encoding/decoding

**What's Used Instead of Mocks:**
- Test helpers (`client.js`, `server.js`) provide controlled setup
- In-memory state (no external database dependencies)
- Real WebSocket server started for integration testing

## Test Data

**Fixtures and Factories:**
- No fixture files or factory pattern
- Test data hardcoded in each test:
  ```javascript
  var userId1 = "1";
  var userId2 = "2";
  var userId3 = "3";
  var groupId = "18";
  var port = 3334;
  ```

**Message Construction:**
```javascript
var timestamp = Date.now();
var message = notepack.encode([ChannelType.GROUP, MessageType.START, from, to, timestamp]);
ws1.send(message);
```

**Audio Buffer Generation (in-test):**
```javascript
function whitenoise() {
  var bufferSize = 4096;
  var out = [[], []];
  for (var i = 0; i < bufferSize; i++) {
    out[0][i] = [1][i] = Math.random() * 0.25;
  }
  return new Buffer(out);
}
```

**Location:**
- All test data defined within test files
- No shared fixtures directory
- Helper files in `test/` directory next to tests

## Coverage

**Requirements:** No coverage enforcement visible in configuration

**View Coverage:**
- No coverage script in package.json
- No coverage tool configured (no istanbul, nyc, or c8)

**Current Coverage:** Unknown (no .nyc_output or coverage reports in repository)

## Test Types

**Integration Tests (Primary Type):**
- Tests use real WebSocket server and client connections
- Full message round-trip testing (send/receive/acknowledge)
- Multiple clients in single test: group messaging with 2-3 concurrent connections
- Actual binary encoding/decoding with notepack
- Tests verify message flow across multiple clients

**Examples:**
- `group.test.js` - Group messaging flows (broadcast)
- `private.test.js` - Private messaging between users
- `self.test.js` - Self-messaging (same user as sender and receiver)
- `ack.group.test.js` - Acknowledgment patterns
- `connection.test.js` - Connection establishment
- `maximum.group.test.js` - Load/stress testing (many groups)
- `maximum.idle.test.js` - Idle timeout behavior

**Unit Tests:** Not found; all tests are integration tests

**E2E Tests:** Not separate; integration tests serve this purpose

## Async Testing

**Pattern - Callback-based with Manual State:**
```javascript
var acknowledged = false;
var received = false;

ws1.once("message", function(data) {
  expect(data).to.not.be.empty;
  // ... assertions ...
  acknowledged = true;
  if (received) { done(); }
});

ws2.once("message", function(data) {
  expect(data).to.not.be.empty;
  // ... assertions ...
  received = true;
  if (acknowledged) { done(); }
});
```

**Pattern - Sequential Async with Done:**
```javascript
it("should connect", function(done) {
  var ws = connectClient(port);

  ws.on("open", function() {
    done();
  });

  ws.on("error", function(error) {
    done(new Error(error.message));
  });
});
```

**Pattern - Promise Chaining in Setup:**
```javascript
before(function(done) {
  runServerPromisified(port)
    .then(function(server1) {
      server = server1;
      return Q.all([
        connectClientPromisified(port, userId1),
        connectClientPromisified(port, userId2),
        connectClientPromisified(port, userId3)
      ]).then((wss) => {
        // assign websockets
      }).catch((err) => {
        done(err);
      });
    }).catch((err) => {
      done(err);
    });
});
```

## Error Testing

**Pattern - Error Propagation:**
```javascript
ws.on("error", function(error) {
  done(new Error(error.message));
});
```

**Pattern - Assertion Without Try-Catch:**
```javascript
ws1.once("message", function(data) {
  expect(data).to.not.be.empty;  // Chai throws on assertion failure
  expect(messageType).to.be.equal(MessageType.START_ACK);
});
```

**No Explicit Error Testing:**
- No separate tests for error conditions
- No testing of error messages or error types
- Focus is on happy path flows

## Test Helpers

**Client Connection Helper (test/client.js):**
```javascript
function connectClient(port, userId, groupId) {
  var user = {
    uid: userId
  };

  if (groupId) { user.channelIds = [ groupId ]; }

  var token = jwt.encode(user, secret);

  return new Ws(`ws://localhost:${port}`, {
    headers: { token }
  });
}
```

**Promise Wrapper (test/client-promise.js):**
```javascript
function connectClientPromisified(port, userId, groupId) {
  var ws = connectClient(port, userId, groupId);

  var deferred = Q.defer();

  ws.on("open", function() {
    deferred.resolve(ws);
  });

  ws.on("error", function(err) {
    deferred.reject(err);
  });

  return deferred.promise;
}
```

**Server Setup Helper (test/server.js):**
```javascript
function runServer(port, callback) {
  var server = http.createServer();
  server.listen(port, function() {
    new VoicePing.Server({ server });
    callback();
  });
  return server;
}
```

**Server Promise Wrapper (test/server-promise.js):**
- Wraps `runServer` in Promise for easier async test setup

## Linting in Tests

**ESLint Disables:**
```javascript
/* eslint-disable func-names */
/* eslint no-console: 0 */
```

**Rationale:**
- `func-names` disabled because test suites use `function()` syntax
- `no-console` disabled for potential debugging output

## Common Test Patterns

**Port Management:**
- Each test file uses different port to avoid conflicts
- Hardcoded: `3334`, `8000`, `8001`, `3333`

**Timing/Delays:**
```javascript
setTimeout(function() {
  done();
}, 1000);
```
- 1000ms delay after client connections established to ensure server setup

**Message Validation Pattern:**
```javascript
var decoded = notepack.decode(data);
var channelType = decoded[0];
expect(channelType).to.be.equal(ChannelType.GROUP);

var messageType = decoded[1];
expect(messageType).to.be.equal(MessageType.START_ACK);

var payload = decoded[4];
expect(payload).to.not.be.empty;
```

**Multiple Client Coordination:**
- Tests with N clients use N separate WebSocket instances
- Synchronize completion with manual flag variables
- Use `.once()` for single-response expectations
- Use `.on()` for multiple-response expectations (e.g., `self.test.js`)

---

*Testing analysis: 2026-02-06*
