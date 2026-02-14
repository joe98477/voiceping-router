# Requirements: VoicePing PTT Platform

**Defined:** 2026-02-15
**Core Value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical

## v4.0 Requirements

Requirements for milestone v4.0 — Production Hardening & Location. Each maps to roadmap phases.

### Audio Reliability

- [ ] **AUDIO-01**: App reliably transmits audio on every PTT press without intermittent silence failures
- [ ] **AUDIO-02**: Producer creation retries up to 3 times with exponential backoff on failure
- [ ] **AUDIO-03**: Transport health monitoring detects orphaned/stale transports and auto-cleans up after 15s disconnected
- [ ] **AUDIO-04**: WebRTC jitter buffer tuned for PTT use case (reduced latency vs default streaming config)
- [ ] **AUDIO-05**: Server sends transmission acknowledgment confirming audio was received by listeners
- [ ] **AUDIO-06**: Audio stream timing hardened with proper Opus FEC configuration for packet loss recovery

### Permissions

- [ ] **PERM-01**: App presents permission education screen on first launch explaining why each permission is needed
- [ ] **PERM-02**: App re-prompts with contextual rationale when user performs an action requiring a denied permission
- [ ] **PERM-03**: App gracefully degrades if permission revoked mid-use (shows error state, not crash)
- [ ] **PERM-04**: Permission denial count tracked to prevent infinite prompt loops (redirect to Settings after 2 denials)

### Location

- [ ] **LOC-01**: App collects precise GPS location (~3m) every 5 minutes
- [ ] **LOC-02**: App collects general location (~10-50m) every 60 seconds
- [ ] **LOC-03**: Motion-aware throttling reduces location frequency when user is stationary
- [ ] **LOC-04**: App skips redundant location sends if recent update already transmitted
- [ ] **LOC-05**: Server API endpoint receives and stores location updates from Android clients
- [ ] **LOC-06**: Location API documented for future dispatch web UI map integration
- [ ] **LOC-07**: Background location works via foreground service with proper Android 14+ service type declaration

### Power & Bandwidth

- [ ] **PWR-01**: Wake lock released when no active audio for >30 seconds, reacquired on speaker activity
- [ ] **PWR-02**: Network quality polling interval adjusts dynamically (15s idle channels, 5s active channels)
- [ ] **PWR-03**: Location updates batched for efficient server transmission
- [ ] **PWR-04**: Battery consumption validated at <6%/hour with all v4.0 features active (screen off)

### Security

- [ ] **SEC-01**: All signaling uses WSS (TLS WebSocket), app rejects WS connections
- [ ] **SEC-02**: All API endpoints verified as authenticated, unauthenticated gaps fixed
- [ ] **SEC-03**: Network security config blocks cleartext traffic in release builds
- [ ] **SEC-04**: Android codebase scanned for security vulnerabilities and issues fixed
- [ ] **SEC-05**: WebRTC media streams verified as using DTLS encryption

### Code Quality

- [ ] **CODE-01**: Android codebase cleaned up — unused code, dead imports, and redundant logic removed
- [ ] **CODE-02**: Code optimized for performance — unnecessary allocations, inefficient patterns addressed

## Future Requirements

Deferred to future milestones. Tracked but not in current roadmap.

### Dispatch Web UI

- **DISP-01**: Dispatch console shows real-time user locations on a map overlay
- **DISP-02**: Dispatch can filter location view by team/channel
- **DISP-03**: Location history trail for individual users

### Advanced Audio

- **ADV-01**: Offline audio queueing — store transmissions during outage, send when reconnected
- **ADV-02**: Bandwidth-aware codec — auto-switch Opus bitrate based on cellular vs WiFi

### Advanced Location

- **ALOC-01**: Geofence triggers — automated workflows when user arrives at/leaves a site
- **ALOC-02**: Power profiling dashboard for fleet battery consumption monitoring

### Security (Advanced)

- **ASEC-01**: End-to-end encryption (MCPTT KMS) for mission-critical compliance
- **ASEC-02**: Server-side security audit logging (who accessed what channel, when)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Dispatch web UI map view | Deferred to future milestone focused on web UI enhancements |
| iOS native app | Android first, iOS in future milestone |
| End-to-end encryption | High complexity (MCPTT KMS), defer to v5.0+ |
| Offline audio queueing | Complex storage/sync, lower priority than reliability fixes |
| Geofence automation | Workflow engine complexity, needs product design first |
| Certificate pinning | Operational risk during certificate rotation outweighs security benefit |
| Google Maps SDK integration | No map UI in v4.0, just location collection/server storage |
| Custom audio codec | Opus is industry standard, tune bitrate/FEC instead |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUDIO-01 | Phase 17 | Pending |
| AUDIO-02 | Phase 17 | Pending |
| AUDIO-03 | Phase 17 | Pending |
| AUDIO-04 | Phase 17 | Pending |
| AUDIO-05 | Phase 17 | Pending |
| AUDIO-06 | Phase 17 | Pending |
| PERM-01 | Phase 16 | Pending |
| PERM-02 | Phase 16 | Pending |
| PERM-03 | Phase 16 | Pending |
| PERM-04 | Phase 16 | Pending |
| LOC-01 | Phase 18 | Pending |
| LOC-02 | Phase 18 | Pending |
| LOC-03 | Phase 18 | Pending |
| LOC-04 | Phase 18 | Pending |
| LOC-05 | Phase 18 | Pending |
| LOC-06 | Phase 18 | Pending |
| LOC-07 | Phase 18 | Pending |
| PWR-01 | Phase 20 | Pending |
| PWR-02 | Phase 20 | Pending |
| PWR-03 | Phase 20 | Pending |
| PWR-04 | Phase 20 | Pending |
| SEC-01 | Phase 19 | Pending |
| SEC-02 | Phase 19 | Pending |
| SEC-03 | Phase 19 | Pending |
| SEC-04 | Phase 19 | Pending |
| SEC-05 | Phase 19 | Pending |
| CODE-01 | Phase 19 | Pending |
| CODE-02 | Phase 19 | Pending |

**Coverage:**
- v4.0 requirements: 28 total
- Mapped to phases: 28
- Unmapped: 0 ✓

**Phase distribution:**
- Phase 16 (Permission Management): 4 requirements
- Phase 17 (Audio Reliability): 6 requirements
- Phase 18 (Location Tracking): 7 requirements
- Phase 19 (Security Hardening & Code Quality): 7 requirements
- Phase 20 (Power Optimization & Validation): 4 requirements

---
*Requirements defined: 2026-02-15*
*Last updated: 2026-02-15 after roadmap creation (100% coverage validated)*
