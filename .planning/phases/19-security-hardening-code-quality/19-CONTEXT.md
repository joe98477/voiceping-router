# Phase 19: Security Hardening & Code Quality - Context

**Gathered:** 2026-02-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Audit and harden the full stack (server + Android) for TLS/WSS enforcement, authentication completeness, and DTLS verification. Clean up both codebases with dead code removal, formatter enforcement, and security lint rules. No new features — this phase hardens and cleans what exists.

</domain>

<decisions>
## Implementation Decisions

### TLS strictness
- WSS enforced in release builds only; debug builds allow WS for local development
- No certificate pinning — standard Android TLS verification (system CA store)
- TLS terminated at reverse proxy (nginx/LB); Node.js runs HTTP internally
- Server explicitly rejects non-TLS WebSocket upgrade requests in production (defense in depth)
- Android network security config blocks all cleartext traffic in release (not just own server)
- Separate debug network security config allows cleartext and localhost
- TLS connection failures show a user-visible error (toast/banner), not silent retry

### Vulnerability threshold
- Fix critical and high severity findings only; medium/low accepted as known risks
- Document medium/low findings in SECURITY-KNOWN-ISSUES.md with justification
- Dependency upgrades limited to patch/minor versions; no major version bumps to fix CVEs
- Add detekt security lint rules to catch common patterns (hardcoded secrets, injection vectors)

### Cleanup scope
- Conservative cleanup: dead code, unused imports, unreachable branches only — no structural refactoring
- Both Android and server codebases included in cleanup
- Keep all logging as-is (useful for production debugging)
- Don't remove unused Gradle/npm dependencies (risk of breaking transitive deps)
- Add build/ directory to .gitignore and clean up tracked build artifacts
- Add ktlint (Android) and eslint/prettier (server) for code style enforcement
- Initial formatting applied in a dedicated commit: "style: apply ktlint/prettier formatting"

### Dev workflow
- Pre-commit hook runs ktlint, eslint, prettier, and detekt security rules on every commit
- Pre-commit hook auto-fixes formatting (developer just commits)
- Security lint rules also run on every commit via pre-commit hook
- Hook bypass available via `git commit --no-verify` for WIP commits

### Claude's Discretion
- Performance optimization approach: Claude identifies most impactful unnecessary allocations
- Documentation: Claude adds KDoc/JSDoc only where logic is non-obvious

</decisions>

<specifics>
## Specific Ideas

- Server should reject WS connections in production even behind a reverse proxy — defense in depth
- User-visible TLS error preferred over silent retry — transparency when secure connection fails
- First formatter run gets its own dedicated commit to keep git history clean
- Pre-commit auto-fix, not report-only — reduce friction for developers

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 19-security-hardening-code-quality*
*Context gathered: 2026-02-15*
