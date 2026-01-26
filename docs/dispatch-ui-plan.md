# Dispatch Web UI and Control Plane Plan

## Repository review (current baseline)
- The router is a Node.js websocket server intended to relay PTT audio streams, with Redis used for temporary state storage. See `README.md` for runtime expectations and supported auth mode, and `docker-compose.yml` for the current two-service stack (router + Redis).【F:README.md†L1-L147】【F:docker-compose.yml†L1-L27】
- The existing Docker Compose stack runs a single router container (`vp-router`) and a Redis container, and exposes the router on port 3000.【F:docker-compose.yml†L1-L27】

## Recommendation: UI/control-plane deployment topology
**Keep the dispatch web server separate from the voice router container, and connect via APIs.**

Reasons:
1. **Isolation and scaling**: the router is latency-sensitive for audio. Serving web UI assets, authentication flows, and API traffic in the same container couples resource spikes to PTT latency.
2. **Security boundaries**: the control-plane surface (user admin, roles, incidents) can enforce strict RBAC and audit logging without touching the audio plane runtime.
3. **Deployment agility**: web UI can iterate frequently without disrupting the voice routing service.
4. **Operational clarity**: separate logs, metrics, and rollout procedures are easier for dispatch/ops teams.

Implementation pattern:
- **Data plane**: VoicePing Router (existing websocket/audio transport).
- **Control plane**: New web server + API (user/org model, channels, RBAC, presence, incidents).
- **Real-time events**: WebSocket/SSE from the control plane to the UI; control plane can subscribe to router events or emit its own derived presence/telemetry.

## Proposed development structure (phases)

### Phase 0 — Product & workflow definition
**Outputs**
- Dispatcher workflows and states (monitoring, talking, patching, emergency).
- Role definitions (Dispatcher, Supervisor, Admin, Observer, Field User).
- Channel taxonomy (team, incident, cross-team, ad-hoc).

### Phase 1 — Baseline stack and repo scaffolding
**Actions**
- Add a Docker Compose service for the **control-plane API** (Node/Express, Fastify, or NestJS) plus Postgres.
- Add a **web UI** service that builds a static bundle (React/Vue) and is served via a reverse proxy (Nginx/Caddy/Traefik).
- Keep the existing router service as-is.

**Deliverables**
- `docker compose up` starts: router, Redis, control-plane API, Postgres, UI.

### Phase 2 — Auth, identity, and RBAC
**Actions**
- Integrate OIDC (Keycloak or Auth0-style self-hosted equivalent) for the web console.
- Issue device tokens for Android SDK clients.
- Enforce RBAC for channel join/talk/monitor and admin operations.

### Phase 3 — Control-plane data model + API
**Data model (minimal)**
- Tenants, Events, Teams, Channels, Users, Devices, Memberships, Roles, Incidents.

**API endpoints**
- CRUD for events, teams, channels.
- Assign users/devices to channels/roles.
- Incident channel creation and workflow state changes.

### Phase 4 — Presence and real-time dispatch events
**Actions**
- Track user/device presence (online/offline, last seen, channel set).
- Emit PTT events (talk start/end, active speaker, channel activity).
- Broadcast to the web UI via WebSocket/SSE.

### Phase 5 — Dispatcher UI MVP
**Layout inspired by Motorola WAVE PTX / ESChat / TASSTA / Kodiak**
- **Left rail**: talkgroup list + monitor toggles + active speaker state.
- **Center**: live activity feed, alerts, and incident queue.
- **Right rail**: roster with presence and quick call actions.
- **Bottom**: large PTT panel with active channel selector.

**Key behaviors**
- Monitor multiple channels while talking to one.
- Keyboard shortcuts for channel switching and PTT.
- Supervisor override and emergency alert handling (visual + audible).

### Phase 6 — Advanced dispatch features
- Channel patching/bridging.
- Emergency override and priority lanes.
- Ad-hoc 1:1 and incident groups.
- Optional recording hooks + audit log trails.

### Phase 7 — Field client integration
- Android SDK integration for BYOD and dedicated devices.
- Accessory PTT support (Bluetooth/USB-C) and audio routing.
- Device telemetry (battery, network quality, app version).

### Phase 8 — Monitoring & system health
- Metrics: active connections, talk bursts, channel throughput, latency proxies.
- Logs/alerts via Prometheus/Grafana + Loki/ELK.
- Admin dashboard in the UI with system health views.

## UI standards and visual patterns to emulate
- **Radio console metaphors**: talkgroup scanning, priority lanes, PTT panel.
- **High-contrast ops layout**: legible status indicators, minimal navigation depth.
- **Dispatch flows**: quick actions for incidents, emergency, supervisor override.
- **Map pane** (optional early): presence/location combined with talkgroup context.

## Next steps (practical build plan)
1. Introduce a `control-plane` service in Compose and define the API skeleton.
2. Add a `web-ui` project with a design system aligned to dispatch consoles (dark mode, high contrast).
3. Create an event bus between router and control plane for presence + activity.
4. Deliver MVP console: multi-channel monitor, PTT talk target, live activity list.
