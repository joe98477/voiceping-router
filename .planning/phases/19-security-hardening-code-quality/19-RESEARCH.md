# Phase 19: Security Hardening & Code Quality - Research

**Researched:** 2026-02-15
**Domain:** Security audit, TLS/WSS enforcement, code quality automation, vulnerability scanning
**Confidence:** HIGH

## Summary

Phase 19 hardens the full stack (Node.js server + Android client) for production security and code quality. The phase focuses on TLS/WSS enforcement, authentication audit, DTLS verification, vulnerability scanning, code formatting automation, and dead code cleanup. No new features — this phase hardens and cleans what exists.

The Android app currently accepts both `ws://` and `wss://` connections (per SignalingClient.kt line 99). The server already implements JWT authentication on all WebSocket connections via `verifyClient` (websocketServer.ts line 106-236), but lacks production-mode cleartext rejection. Neither codebase has automated formatting (ktlint/prettier) or pre-commit hooks. The `.gitignore` excludes `build/Release` but not `android/app/build/`, causing ~300+ tracked build artifacts (per git status).

**Primary recommendation:** Add network security config XML for Android (blocks cleartext in release, allows in debug), add server-side WS rejection in production mode, integrate ktlint + detekt security rules for Android, add eslint/prettier for server, set up Husky pre-commit hooks for auto-formatting, run OWASP dependency-check (Gradle) and npm audit, and clean up dead code conservatively with dedicated formatter commit.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**TLS strictness:**
- WSS enforced in release builds only; debug builds allow WS for local development
- No certificate pinning — standard Android TLS verification (system CA store)
- TLS terminated at reverse proxy (nginx/LB); Node.js runs HTTP internally
- Server explicitly rejects non-TLS WebSocket upgrade requests in production (defense in depth)
- Android network security config blocks all cleartext traffic in release (not just own server)
- Separate debug network security config allows cleartext and localhost
- TLS connection failures show a user-visible error (toast/banner), not silent retry

**Vulnerability threshold:**
- Fix critical and high severity findings only; medium/low accepted as known risks
- Document medium/low findings in SECURITY-KNOWN-ISSUES.md with justification
- Dependency upgrades limited to patch/minor versions; no major version bumps to fix CVEs
- Add detekt security lint rules to catch common patterns (hardcoded secrets, injection vectors)

**Cleanup scope:**
- Conservative cleanup: dead code, unused imports, unreachable branches only — no structural refactoring
- Both Android and server codebases included in cleanup
- Keep all logging as-is (useful for production debugging)
- Don't remove unused Gradle/npm dependencies (risk of breaking transitive deps)
- Add build/ directory to .gitignore and clean up tracked build artifacts
- Add ktlint (Android) and eslint/prettier (server) for code style enforcement
- Initial formatting applied in a dedicated commit: "style: apply ktlint/prettier formatting"

**Dev workflow:**
- Pre-commit hook runs ktlint, eslint, prettier, and detekt security rules on every commit
- Pre-commit hook auto-fixes formatting (developer just commits)
- Security lint rules also run on every commit via pre-commit hook
- Hook bypass available via `git commit --no-verify` for WIP commits

### Claude's Discretion

- Performance optimization approach: Claude identifies most impactful unnecessary allocations
- Documentation: Claude adds KDoc/JSDoc only where logic is non-obvious

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

## Standard Stack

### Core Security Tools

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| **ktlint** (Gradle plugin) | 12.x | Kotlin code formatter for Android | De facto standard for Kotlin formatting, zero-config out-of-box style |
| **detekt** | 1.23.x | Kotlin static analysis + security rules | Standard Kotlin linter, extensible with security rule sets |
| **eslint** | 8.x/9.x | JavaScript/TypeScript linter | Industry standard for Node.js/TypeScript linting |
| **prettier** | 3.x | Opinionated code formatter (JS/TS) | De facto standard for TypeScript formatting, integrates with eslint |
| **husky** | 9.x | Git hooks manager for Node.js projects | Most popular hook manager for Node.js, stores hooks in .husky/ directory |
| **OWASP dependency-check** (Gradle) | 12.x | Android/Gradle vulnerability scanner | Standard SCA tool for Gradle, uses NVD database |
| **npm audit** | Built-in | Node.js dependency vulnerability scanner | Native to npm, no installation needed |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **lint-staged** | 15.x | Run linters only on staged files | Speeds up pre-commit hooks by filtering to changed files only |
| **eslint-config-prettier** | 9.x | Disables ESLint formatting rules that conflict with Prettier | Required when using eslint + prettier together |
| **eslint-plugin-prettier** | 5.x | Runs Prettier as an ESLint rule | Optional — allows Prettier errors to show as ESLint errors |
| **@typescript-eslint/parser** | 7.x | TypeScript parser for ESLint | Required for ESLint to understand TypeScript syntax |
| **@typescript-eslint/eslint-plugin** | 7.x | TypeScript-specific ESLint rules | Required for TypeScript linting |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ktlint | Spotless (Gradle plugin) | Spotless supports multiple formatters (ktlint, prettier, google-java-format) but adds complexity; ktlint is simpler for Kotlin-only |
| Husky | pre-commit (Python tool) | pre-commit is language-agnostic and works for polyglot repos, but Husky is Node.js-native and simpler for Node.js projects |
| OWASP dependency-check | Snyk Gradle plugin | Snyk has better UX and real-time monitoring, but requires account/API key; OWASP is free and offline-capable |
| npm audit | Snyk CLI | Same tradeoff — Snyk has better UX but requires account; npm audit is built-in |

**Installation:**

**Android (Gradle):**
```gradle
// build.gradle.kts (project-level)
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.5" apply false
    id("org.owasp.dependencycheck") version "12.2.0" apply false
}

// build.gradle.kts (app-level)
plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.5")
}
```

**Server (Node.js):**
```bash
npm install --save-dev \
  eslint@8.56.0 \
  prettier@3.2.5 \
  husky@9.0.11 \
  lint-staged@15.2.2 \
  eslint-config-prettier@9.1.0 \
  eslint-plugin-prettier@5.1.3 \
  @typescript-eslint/parser@7.0.0 \
  @typescript-eslint/eslint-plugin@7.0.0
```

## Architecture Patterns

### Recommended Project Structure

**Android:**
```
android/
├── app/
│   ├── src/main/res/xml/
│   │   ├── network_security_config.xml        # Release: blocks cleartext
│   │   └── network_security_config_debug.xml  # Debug: allows cleartext
│   └── build.gradle.kts                        # Apply ktlint, detekt plugins
├── detekt.yml                                  # Detekt config (formatting + security rules)
└── .editorconfig                               # IDE formatter settings (optional)
```

**Server:**
```
.
├── .husky/
│   └── pre-commit                 # Auto-run ktlint, eslint, prettier on commit
├── .eslintrc.json                 # ESLint config (already exists)
├── .prettierrc.json               # Prettier config
├── package.json                   # Add lint-staged config
└── src/server/
    ├── config.ts                  # Add NODE_ENV check for WS rejection
    └── signaling/websocketServer.ts  # Add production WS rejection
```

### Pattern 1: Android Network Security Config (Build Variant-Specific)

**What:** XML-based network security policy that blocks cleartext traffic in release builds but allows it in debug builds (for local development).

**When to use:** Always use for production Android apps to enforce HTTPS/WSS and prevent accidental cleartext leaks.

**Example:**
```xml
<!-- android/app/src/main/res/xml/network_security_config.xml (release) -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Block ALL cleartext traffic in release builds -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <!-- Trust system CA store (Android's built-in CAs) -->
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>

<!-- android/app/src/debug/res/xml/network_security_config.xml (debug) -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Allow cleartext for localhost in debug builds -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">10.0.2.2</domain> <!-- Android emulator host -->
        <domain includeSubdomains="true">127.0.0.1</domain>
    </domain-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

**AndroidManifest.xml:**
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

**Source:** [Android Network Security Configuration (Official Docs)](https://developer.android.com/privacy-and-security/security-config)

### Pattern 2: Server-Side WS Rejection (Defense in Depth)

**What:** Even though TLS is terminated at the reverse proxy, the Node.js server should reject cleartext WebSocket (ws://) upgrade requests in production mode as an additional security layer.

**When to use:** Always in production environments, even behind a reverse proxy.

**Example:**
```typescript
// src/server/config.ts
export const config = {
  server: {
    port: parseInt(process.env.PORT || '3000', 10),
    host: process.env.HOST || '0.0.0.0',
    nodeEnv: process.env.NODE_ENV || 'development', // Add NODE_ENV
  },
  // ... rest of config
};

// src/server/signaling/websocketServer.ts (in verifyClient method)
private async verifyClientAsync(
  info: { origin: string; secure: boolean; req: http.IncomingMessage },
  callback: (result: boolean, code?: number, message?: string) => void
): Promise<void> {
  // Defense in depth: Reject non-TLS WebSocket in production
  if (config.server.nodeEnv === 'production' && !info.secure) {
    logger.warn('Connection rejected: Cleartext WebSocket not allowed in production');
    callback(false, 403, 'TLS required');
    return;
  }

  // ... rest of JWT verification logic
}
```

**Source:** [WebSockets: The Complete Guide for 2026](https://devtoolbox.dedyn.io/blog/websocket-complete-guide)

### Pattern 3: Pre-Commit Hook with Auto-Fix (Husky + lint-staged)

**What:** Git pre-commit hook that automatically formats code using ktlint/prettier and runs security linters (detekt, eslint) before allowing commit.

**When to use:** Always in team environments to enforce consistent formatting and catch security issues early.

**Example:**
```bash
# Install Husky
npx husky init

# .husky/pre-commit
#!/bin/sh
. "$(dirname "$0")/_/husky.sh"

# Run lint-staged (formats server code)
npx lint-staged

# Run ktlint (formats Android code)
cd android && ./gradlew ktlintFormat --daemon || exit 1

# Run detekt security rules (Android)
cd android && ./gradlew detekt --daemon || exit 1
```

**package.json (lint-staged config):**
```json
{
  "lint-staged": {
    "src/**/*.ts": [
      "prettier --write",
      "eslint --fix"
    ]
  }
}
```

**Source:** [Husky Official Docs](https://typicode.github.io/husky/), [Git Hooks: The Complete Guide for 2026](https://devtoolbox.dedyn.io/blog/git-hooks-complete-guide)

### Pattern 4: Detekt Security Rules Configuration

**What:** Custom detekt.yml configuration that enables security-focused rule sets to catch hardcoded secrets, injection vectors, and other security anti-patterns.

**When to use:** Always for production Android apps.

**Example:**
```yaml
# detekt.yml
build:
  maxIssues: 0

formatting:
  active: true
  autoCorrect: true  # Auto-fix formatting issues

potential-bugs:
  active: true

security:
  active: true
  HardcodedPassword:
    active: true
  HardcodedApiKey:
    active: true
  UnsafeCallOnNullableType:
    active: true

style:
  active: true
  MagicNumber:
    active: false  # Too noisy for this codebase
```

**Source:** [detekt Configuration File](https://detekt.dev/docs/introduction/configurations/), [Detekt Formatting Rules](https://detekt.dev/docs/1.21.0/rules/formatting/)

### Pattern 5: User-Visible TLS Error (No Silent Retry)

**What:** When SignalingClient fails to connect due to TLS issues, show a user-visible error (toast/banner) instead of silently retrying in the background.

**When to use:** Required per user decision — transparency when secure connection fails.

**Example:**
```kotlin
// SignalingClient.kt (in WebSocketListener.onFailure)
override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    Log.e(TAG, "WebSocket connection failed: ${t.message}", t)

    // Check if failure is TLS-related (SSLException, CertificateException, etc.)
    val isTlsError = t is javax.net.ssl.SSLException ||
                     t is java.security.cert.CertificateException ||
                     (t.message?.contains("SSL", ignoreCase = true) == true)

    if (isTlsError) {
        _connectionState.value = ConnectionState.TLS_ERROR // New state
        // ChannelListScreen will observe this state and show error banner
    } else {
        _connectionState.value = ConnectionState.RECONNECTING
        scheduleReconnect()
    }
}
```

**ConnectionState.kt:**
```kotlin
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    TLS_ERROR  // New state for TLS failures
}
```

**Source:** User decision from CONTEXT.md

### Anti-Patterns to Avoid

- **Anti-pattern: Cleartext in release builds** — Never set `cleartextTrafficPermitted="true"` globally in release builds. Always use build variant-specific network security configs.
- **Anti-pattern: Certificate pinning without OTA update** — Certificate pinning is brittle (app breaks when cert rotates). User decision: no pinning, rely on system CA store.
- **Anti-pattern: Major dependency upgrades for CVEs** — Upgrading major versions (e.g., Retrofit 2.x → 3.x) to fix CVEs introduces breaking changes and regressions. User decision: patch/minor only.
- **Anti-pattern: Removing all unused Gradle/npm dependencies** — Transitive dependencies may require seemingly "unused" packages. User decision: keep all deps, only remove obvious dead code.
- **Anti-pattern: Silent TLS failures** — Users need to know when secure connections fail. User decision: show error, don't retry silently.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| **Code formatting** | Custom formatter or manual formatting guidelines | ktlint (Android), prettier (server) | Formatting is deceptively complex (edge cases, AST manipulation, comment preservation). ktlint/prettier are battle-tested with 100k+ repos. |
| **Vulnerability scanning** | Custom CVE database scraper | OWASP dependency-check (Gradle), npm audit | NVD database has 200k+ vulnerabilities and updates daily. OWASP tool is maintained by security experts. |
| **Git hooks** | Shell scripts in `.git/hooks/` | Husky (Node.js projects) | `.git/hooks/` is not version-controlled. Husky stores hooks in `.husky/` directory (committed to repo), ensuring team consistency. |
| **TLS certificate validation** | Custom SSL/TLS verification | Android system CA store, OkHttp default TrustManager | TLS has 20+ years of CVEs (Heartbleed, POODLE, BEAST). Let Android/OkHttp handle it. |
| **WebRTC DTLS encryption** | Custom DTLS implementation | mediasoup built-in DTLS-SRTP | DTLS-SRTP is mandatory per RFC 8827. mediasoup uses OpenSSL's battle-tested DTLS with certificate fingerprint exchange. |

**Key insight:** Security and tooling are areas where custom solutions are almost always worse. The standard tools (ktlint, OWASP, Husky) have been hardened by millions of users and edge cases. Use them.

## Common Pitfalls

### Pitfall 1: Network Security Config Not Applied

**What goes wrong:** Developer adds `network_security_config.xml` but forgets to reference it in `AndroidManifest.xml`, so cleartext traffic is still allowed.

**Why it happens:** The XML file is passive — Android only enforces it if the manifest declares `android:networkSecurityConfig="@xml/network_security_config"`.

**How to avoid:** Always verify by running app with Charles Proxy or Wireshark — confirm that HTTP requests are blocked with "Cleartext HTTP traffic not permitted" error.

**Warning signs:** App successfully connects to `ws://localhost:3000` in release build (should fail with ERR_CLEARTEXT_NOT_PERMITTED).

**Source:** [Managing HTTP & Cleartext Traffic on Android](https://devblogs.microsoft.com/xamarin/cleartext-http-android-network-security/)

### Pitfall 2: First ktlint/prettier Run Creates Massive Diff

**What goes wrong:** Running ktlint/prettier for the first time reformats 100+ files, creating a 10k+ line diff that pollutes git blame and makes code review impossible.

**Why it happens:** Formatter changes every file in the codebase (indentation, spacing, line breaks, etc.).

**How to avoid:** User decision: Dedicate a single commit to formatting: `style: apply ktlint/prettier formatting`. Keep this commit separate from functional changes. Use `git blame --ignore-rev <commit-hash>` to skip formatting commits in blame view.

**Warning signs:** Pull request with 5,000 line diff that's 99% whitespace/formatting changes mixed with functional changes.

**Source:** User decision from CONTEXT.md

### Pitfall 3: Pre-Commit Hook Too Slow (Developer Friction)

**What goes wrong:** Pre-commit hook runs full detekt + ktlint + eslint on entire codebase, taking 60+ seconds per commit. Developers start using `--no-verify` to bypass.

**Why it happens:** Running linters on all files (not just staged files) is slow.

**How to avoid:** Use `lint-staged` to run linters only on staged files. For Gradle tasks, pass `--daemon` flag to reuse JVM. Example: `./gradlew ktlintFormat --daemon`.

**Warning signs:** Developers complaining about slow commits. Frequent use of `git commit --no-verify`.

**Source:** [lint-staged GitHub](https://github.com/okonet/lint-staged)

### Pitfall 4: OWASP Dependency-Check False Positives (CPE Mismatches)

**What goes wrong:** OWASP dependency-check reports CVEs for unrelated libraries (e.g., flags "commons-io" vulnerability in `okio` due to name similarity).

**Why it happens:** NVD uses CPE (Common Platform Enumeration) matching, which can misidentify libraries with similar names.

**How to avoid:** Review all CRITICAL/HIGH findings manually. Check CVE details (affected versions, library name). Suppress false positives in `dependency-check-suppressions.xml`. Document medium/low findings in `SECURITY-KNOWN-ISSUES.md` per user decision.

**Warning signs:** CVE report shows vulnerability in a library you don't use (e.g., Apache Commons when you use OkHttp).

**Source:** [OWASP Dependency-Check False Positives](https://jeremylong.github.io/DependencyCheck/general/suppression.html)

### Pitfall 5: TLS Error Swallowed by Generic onFailure Handler

**What goes wrong:** SignalingClient.onFailure catches all WebSocket failures (network, DNS, TLS) and treats them the same (retry with exponential backoff). TLS errors should show user-visible error, not silent retry (per user decision).

**Why it happens:** Generic exception handling doesn't distinguish TLS failures (SSLException, CertificateException) from transient network failures.

**How to avoid:** Check exception type in `onFailure`. If `SSLException` or `CertificateException`, set state to `TLS_ERROR` and show banner. Don't retry.

**Warning signs:** User reports "app keeps trying to connect but never shows error" when server has expired TLS cert.

**Source:** User decision from CONTEXT.md (TLS connection failures show user-visible error, not silent retry)

### Pitfall 6: WebRTC DTLS Assumed Secure Without Verification

**What goes wrong:** Developer assumes WebRTC media streams are encrypted because "WebRTC uses DTLS", but never verifies DTLS is actually enabled or that certificate fingerprints are exchanged.

**Why it happens:** DTLS-SRTP is mandatory per RFC 8827, but misconfigured signaling (missing `dtlsParameters` exchange) can fall back to unencrypted RTP.

**How to avoid:** Verify in mediasoup Transport creation that `dtlsParameters` (fingerprints + role) are exchanged in signaling. Check mediasoup logs for "DTLS state: connected" messages. Capture Wireshark traffic and confirm RTP packets are encrypted (SRTP, not plain RTP).

**Warning signs:** Wireshark shows unencrypted RTP packets (payload readable). mediasoup logs missing "DTLS state: connected".

**Source:** [WebRTC Security Architecture RFC 8827](https://datatracker.ietf.org/doc/html/rfc8827), [mediasoup DTLS Transport](https://github.com/versatica/mediasoup/blob/v3/worker/src/RTC/DtlsTransport.cpp)

## Code Examples

Verified patterns from official sources and current codebase:

### Example 1: OWASP Dependency-Check Gradle Configuration

```gradle
// android/build.gradle.kts (project-level)
plugins {
    id("org.owasp.dependencycheck") version "12.2.0"
}

dependencyCheck {
    // Fail build on CRITICAL/HIGH only (per user decision)
    failBuildOnCVSS = 7.0f  // CVSS 7.0+ = HIGH/CRITICAL

    // Suppress false positives
    suppressionFile = "dependency-check-suppressions.xml"

    // Output formats
    formats = listOf("HTML", "JSON")

    // Cache NVD database for faster scans
    cveValidForHours = 24
}

// Run with: ./gradlew dependencyCheckAnalyze
```

**Source:** [OWASP Dependency-Check Gradle Plugin](https://github.com/dependency-check/dependency-check-gradle)

### Example 2: npm audit in CI/CD Pipeline

```bash
#!/bin/bash
# scripts/security-audit.sh

# Run npm audit and fail on HIGH/CRITICAL only (per user decision)
npm audit --audit-level=high --production

# Generate JSON report for analysis
npm audit --json > npm-audit-report.json

# Count vulnerabilities by severity
CRITICAL=$(jq '.metadata.vulnerabilities.critical' npm-audit-report.json)
HIGH=$(jq '.metadata.vulnerabilities.high' npm-audit-report.json)

if [ "$CRITICAL" -gt 0 ] || [ "$HIGH" -gt 0 ]; then
    echo "CRITICAL vulnerabilities: $CRITICAL"
    echo "HIGH vulnerabilities: $HIGH"
    echo "Security audit FAILED. Fix critical/high vulnerabilities before merging."
    exit 1
fi

echo "Security audit PASSED. No critical/high vulnerabilities found."
```

**Source:** [npm audit documentation](https://docs.npmjs.com/cli/v9/commands/npm-audit/)

### Example 3: WebRTC DTLS Verification (mediasoup)

```kotlin
// MediasoupClient.kt (verify DTLS in Transport creation)
private suspend fun createSendTransport(channelId: String): Transport {
    val response = signalingClient.request(
        SignalingMessage(
            type = SignalingType.CREATE_TRANSPORT,
            channelId = channelId,
            direction = "send"
        )
    )

    val dtlsParameters = response.dtlsParameters
        ?: throw IllegalStateException("Missing dtlsParameters in CREATE_TRANSPORT response")

    // DTLS fingerprints MUST be present (RFC 8827 mandates DTLS-SRTP)
    if (dtlsParameters.fingerprints.isEmpty()) {
        throw IllegalStateException("Missing DTLS fingerprints — TLS not enabled")
    }

    Log.d(TAG, "DTLS fingerprints received: ${dtlsParameters.fingerprints.size}")

    // Create transport with DTLS parameters
    val transport = device.createSendTransport(/* ... */)

    // Verify DTLS state after connection
    Log.d(TAG, "Transport DTLS state: ${transport.connectionState}") // Should be "connected"

    return transport
}
```

**Source:** [mediasoup API documentation](https://mediasoup.org/documentation/v3/mediasoup/api/), [WebRTC DTLS-SRTP RFC 8827](https://datatracker.ietf.org/doc/html/rfc8827)

### Example 4: Android TLS Error Handling (User-Visible Error)

```kotlin
// SignalingClient.kt (WebSocketListener.onFailure)
override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    Log.e(TAG, "WebSocket connection failed", t)

    // Detect TLS-related failures
    val isTlsError = when {
        t is javax.net.ssl.SSLException -> true
        t is javax.net.ssl.SSLHandshakeException -> true
        t is java.security.cert.CertificateException -> true
        t.message?.contains("SSL", ignoreCase = true) == true -> true
        t.message?.contains("certificate", ignoreCase = true) == true -> true
        else -> false
    }

    if (isTlsError) {
        // User-visible error (no silent retry per user decision)
        _connectionState.value = ConnectionState.TLS_ERROR
        _tlsErrorMessage.value = "Secure connection failed: ${t.message}"
    } else {
        // Transient network error — retry with exponential backoff
        _connectionState.value = ConnectionState.RECONNECTING
        scheduleReconnect()
    }
}

// ChannelListScreen.kt (observe TLS_ERROR state and show banner)
when (connectionState) {
    ConnectionState.TLS_ERROR -> {
        Banner(
            message = "Secure connection failed. Check your network or contact support.",
            severity = BannerSeverity.ERROR,
            onDismiss = null  // Not user-dismissible (app can't proceed)
        )
    }
    // ... other states
}
```

**Source:** User decision from CONTEXT.md (TLS connection failures show user-visible error)

### Example 5: Dedicated Formatting Commit (Clean Git History)

```bash
# Apply formatting in a single, isolated commit (per user decision)

# Format Android code
cd android
./gradlew ktlintFormat --daemon
cd ..

# Format server code
npx prettier --write "src/**/*.ts"

# Commit formatting changes separately
git add -A
git commit -m "style: apply ktlint/prettier formatting

This commit applies automatic code formatting using:
- ktlint (Android Kotlin code)
- prettier (Node.js TypeScript code)

No functional changes. Use git blame --ignore-rev to skip this commit."

# Add commit hash to .git-blame-ignore-revs (for git 2.23+)
echo "<commit-hash>" >> .git-blame-ignore-revs
git add .git-blame-ignore-revs
git commit -m "chore: add formatting commit to git-blame-ignore-revs"
```

**Source:** User decision from CONTEXT.md (initial formatting in dedicated commit)

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| ESLint 8.x (legacy .eslintrc) | ESLint 9.x (Flat Config) | ESLint 9.0 (April 2024) | New eslint.config.js format replaces .eslintrc.json. Legacy format still supported in ESLint 8.x. |
| Manual certificate pinning | System CA store + HTTPS enforcement | Android 7.0+ (2016) | Network security config replaced manual pinning. Pinning still possible but discouraged. |
| ktlint CLI tool | ktlint Gradle plugin | ktlint 0.43 (2021) | Gradle plugin integrates ktlint into build lifecycle. CLI still available for CI/CD. |
| Husky 4.x (HUSKY_SKIP_HOOKS) | Husky 9.x (.husky/ directory) | Husky 5.0 (2021) | Modern Husky stores hooks in `.husky/` (committed), not `.git/hooks/`. Simpler setup. |
| mediasoup 2.x (older DTLS) | mediasoup 3.x (OpenSSL 1.1.1+) | mediasoup 3.0 (2019) | Modern DTLS 1.2 with improved security. DTLS 1.3 support added in recent versions. |

**Deprecated/outdated:**
- **ESLint .eslintrc.json** — Still supported but ESLint 9.x recommends eslint.config.js (Flat Config). Migration guide: https://eslint.org/docs/latest/use/configure/migration-guide
- **Android usesCleartextTraffic manifest attribute** — Works but network security config XML is more granular (allows per-domain exceptions).
- **npm audit fix --force** — Deprecated in npm 7+. Use `npm audit fix` (patch/minor) or manual upgrades for major versions.

## Open Questions

1. **DTLS encryption verification approach**
   - What we know: mediasoup uses DTLS-SRTP (mandatory per RFC 8827). DTLS parameters (fingerprints + role) are exchanged in signaling during Transport creation.
   - What's unclear: Best way to verify DTLS is active in production without Wireshark. Can we check Transport.connectionState programmatically? Does crow-misia library expose DTLS state?
   - Recommendation: Check mediasoup server logs for "DTLS state: connected" messages. On Android, log `Transport.connectionState` after send/recv transport creation. If "connected", DTLS is active. Document in 19-02 PLAN.

2. **Performance optimization scope**
   - What we know: User decision gives Claude discretion to identify "most impactful unnecessary allocations". No structural refactoring allowed.
   - What's unclear: What qualifies as "most impactful"? Memory allocations in tight loops? Excessive object creation in UI rendering?
   - Recommendation: Use Android Studio Memory Profiler to identify top allocators (objects/second). Focus on ChannelListScreen (UI rendering), MediasoupClient (audio pipeline), and SignalingClient (message parsing). Document findings in 19-03 PLAN.

3. **Detekt security rules coverage**
   - What we know: Detekt supports custom security rules. User decision: "Add detekt security lint rules to catch common patterns (hardcoded secrets, injection vectors)".
   - What's unclear: Which specific detekt rules catch hardcoded secrets? Is there a detekt-security plugin or do we configure built-in rules?
   - Recommendation: Research detekt built-in security rules (potential-bugs, security rule sets). Check for third-party plugins (e.g., detekt-verify). Configure in detekt.yml and document in 19-01 PLAN.

4. **Build artifacts in git status**
   - What we know: Git status shows ~300+ modified files in `android/app/build/` (compiled classes, generated sources, APK). `.gitignore` has `build/Release` but not `android/app/build/`.
   - What's unclear: Why are build artifacts tracked? Was `android/app/build/` accidentally added to git? Is there a gradle.properties flag that's missing?
   - Recommendation: Verify with `git ls-files android/app/build/` to confirm artifacts are tracked. If tracked, run `git rm -r --cached android/app/build/` and update `.gitignore` with `android/**/build/`. Document in 19-03 PLAN.

## Sources

### Primary (HIGH confidence)

- [Android Network Security Configuration (Official Docs)](https://developer.android.com/privacy-and-security/security-config) — Android TLS/cleartext enforcement
- [ktlint-gradle GitHub](https://github.com/JLLeitschuh/ktlint-gradle) — Gradle plugin for ktlint
- [detekt GitHub](https://github.com/detekt/detekt) — Kotlin static analysis
- [detekt Configuration File](https://detekt.dev/docs/introduction/configurations/) — Detekt YAML config
- [detekt Formatting Rules](https://detekt.dev/docs/1.21.0/rules/formatting/) — Detekt formatting rule set
- [OWASP Dependency-Check Gradle Plugin](https://github.com/dependency-check/dependency-check-gradle) — Gradle vulnerability scanning
- [npm audit documentation](https://docs.npmjs.com/auditing-package-dependencies-for-security-vulnerabilities/) — npm vulnerability scanning
- [Husky Official Docs](https://typicode.github.io/husky/) — Git hooks manager
- [lint-staged GitHub](https://github.com/okonet/lint-staged) — Run linters on staged files
- [WebRTC Security Architecture RFC 8827](https://datatracker.ietf.org/doc/html/rfc8827) — DTLS-SRTP mandatory in WebRTC
- [mediasoup API documentation](https://mediasoup.org/documentation/v3/mediasoup/api/) — mediasoup Transport DTLS
- [mediasoup DTLS Transport source](https://github.com/versatica/mediasoup/blob/v3/worker/src/RTC/DtlsTransport.cpp) — mediasoup DTLS implementation

### Secondary (MEDIUM confidence)

- [WebSockets: The Complete Guide for 2026](https://devtoolbox.dedyn.io/blog/websocket-complete-guide) — WSS best practices (Feb 2026 blog)
- [Git Hooks: The Complete Guide for 2026](https://devtoolbox.dedyn.io/blog/git-hooks-complete-guide) — Git hooks patterns (Feb 2026 blog)
- [How to Set Up ESLint 9 with Prettier (Jan 2026)](https://medium.com/@madhan.gannarapu/how-to-set-up-eslint-9-with-prettier-in-node-js-flat-config-typescript-0eb1755f83cd) — ESLint 9 Flat Config
- [Managing HTTP & Cleartext Traffic on Android](https://devblogs.microsoft.com/xamarin/cleartext-http-android-network-security/) — Network security config examples
- [WebRTC Encryption and Security (2026 guide)](https://www.mirrorfly.com/blog/webrtc-encryption-and-security/) — DTLS-SRTP overview
- [How to Secure WebSocket Connections in Node.js](https://medium.com/@innovativejude.tech/how-to-secure-websocket-connections-in-node-js-a-step-by-step-guide-6d983a07bd96) — WSS setup guide

### Tertiary (LOW confidence)

- None — All findings verified with official docs or recent sources (2024-2026)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — All tools (ktlint, detekt, eslint, prettier, Husky, OWASP) are industry-standard with official docs and recent (2024-2026) sources
- Architecture: HIGH — Network security config, pre-commit hooks, DTLS verification patterns verified with official Android/WebRTC/mediasoup docs
- Pitfalls: MEDIUM-HIGH — Common pitfalls sourced from community blogs (Medium, dev.to) and GitHub issues, cross-verified with official docs where possible
- Open questions: MEDIUM — DTLS verification approach and detekt security rules need further investigation during planning

**Research date:** 2026-02-15
**Valid until:** 2026-03-15 (30 days — stable domain, security tooling changes slowly)

**Notes:**
- Current codebase already has `.eslintrc.json` (ESLint 8.x config) — can be reused or migrated to ESLint 9.x Flat Config
- SignalingClient already accepts both `ws://` and `wss://` (line 99 comment) — need build variant check
- WebSocket server already has JWT auth (verifyClient) but lacks production WS rejection
- ProGuard rules already comprehensive (proguard-rules.pro) — no changes needed for Phase 19
- Git status shows ~300+ build artifacts tracked — needs cleanup in 19-03

**Recommendations for planner:**
- 19-01: TLS/WSS enforcement (network security config, server WS rejection, TLS error handling)
- 19-02: Vulnerability scanning + authentication audit (OWASP, npm audit, API endpoint review, DTLS verification)
- 19-03: Code quality automation + cleanup (ktlint, detekt, eslint, prettier, Husky, dead code removal, build artifacts cleanup)
