---
phase: quick-6
plan: 01
type: execute
wave: 1
depends_on: []
files_modified: [README.md]
autonomous: true

must_haves:
  truths:
    - "README uses 'ConnectVoice' branding throughout, no 'VoicePing' references remain in prose"
    - "A first-time user can get the full stack running in under 5 minutes by following the Quick Start"
    - "Architecture section gives a clear mental model of all 6 Docker services and how they connect"
    - "Android build instructions are present and accurate"
    - "Links to detailed docs (architecture, deployment, API, user manual, opus) are included"
  artifacts:
    - path: "README.md"
      provides: "Complete project README with ConnectVoice branding"
      min_lines: 120
  key_links:
    - from: "README.md quick start"
      to: ".env.example"
      via: "cp command"
      pattern: "cp .env.example .env"
    - from: "README.md docs section"
      to: "docs/*.md"
      via: "relative links"
      pattern: "docs/"
---

<objective>
Rewrite README.md from scratch for the ConnectVoice rebrand, replacing the outdated VoicePing-era content with an industry-standard README that covers the current architecture (mediasoup WebRTC, Docker Compose, control-plane, web dispatch UI, Android PTT client).

Purpose: Give first-time users and contributors a clear, accurate entry point to understand, deploy, and develop the project.
Output: A single polished README.md
</objective>

<execution_context>
@/home/earthworm/.claude/get-shit-done/workflows/execute-plan.md
@/home/earthworm/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@README.md
@docker-compose.yml
@.env.example
@package.json
@docs/architecture.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Rewrite README.md with ConnectVoice branding</name>
  <files>README.md</files>
  <action>
Replace the entire contents of README.md with a professionally structured document. Use "ConnectVoice" as the project name throughout. Do NOT reference "VoicePing" in prose (the repo URL and legacy code references like package.json `name` field are fine to mention once in a "Legacy" or "Repository" note if needed, but the README itself should read as a ConnectVoice document).

Structure the README with these sections in order:

1. **Title and badges area**
   - `# ConnectVoice` as heading
   - One-line tagline: real-time push-to-talk communication platform for coordinating distributed teams during large-scale events
   - License badge (MIT), Node.js >=20 badge

2. **Overview** (2-3 sentences)
   - What it does: WebRTC-based PTT audio routing with event/team/channel management
   - Key tech: mediasoup (Opus codec), Node.js + TypeScript server, React dispatch console, Android PTT client
   - Scale mention: designed for 1000+ users across multiple channels

3. **Architecture** (brief visual + table)
   - ASCII diagram showing: Android Client / Web Browser --> nginx (port 3000) --> audio-server (mediasoup WebRTC) + control-plane (REST API, port 4000), web-ui (dispatch console, port 8080), redis, postgres
   - Table listing all 6 Docker Compose services with their role and port:
     | Service | Role | Port |
     |---------|------|------|
     | audio-server | mediasoup WebRTC audio routing | internal (via nginx) |
     | control-plane | REST API: auth, events, teams, channels | 4000 |
     | web-ui | React dispatch console | 8080 |
     | nginx | Reverse proxy + WebSocket upgrade | 3000 |
     | redis | State management + pub/sub | internal |
     | postgres | User, event, and channel data | internal |

4. **Quick Start** (numbered steps, copy-paste ready)
   - Prerequisites: Docker and Docker Compose
   - Steps:
     ```
     git clone https://github.com/SmartWalkieOrg/voiceping-router.git
     cd voiceping-router
     cp .env.example .env
     # Edit .env — at minimum set MEDIASOUP_ANNOUNCED_IP to your host IP
     docker compose up -d --build
     ```
   - Verification: `docker compose ps` (all 6 services running), open http://localhost:8080 for dispatch console
   - Note: first start runs Prisma migrations automatically

5. **Environment Configuration**
   - Group variables by concern (not a flat list). Use a table or grouped list:
     - **Core**: NODE_ENV, PORT, SECRET_KEY, ROUTER_JWT_SECRET, SESSION_SECRET, LEGACY_JOIN_ENABLED
     - **Database**: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB, DATABASE_URL, REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
     - **mediasoup**: MEDIASOUP_ANNOUNCED_IP (IMPORTANT: must be set to host's public/LAN IP for WebRTC), MEDIASOUP_LISTEN_IP, MEDIASOUP_MIN_PORT, MEDIASOUP_MAX_PORT, MEDIASOUP_LOG_LEVEL
     - **STUN/TURN**: STUN_SERVER, TURN_SERVER, TURN_USERNAME, TURN_PASSWORD
     - **Web UI**: WEB_BASE_URL, VITE_API_BASE, VITE_ROUTER_WS
     - **SMTP** (optional): SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, SMTP_FROM
     - **Bootstrap** (optional): BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD
   - Call out MEDIASOUP_ANNOUNCED_IP as the most important variable to set correctly (without it, WebRTC will fail for remote clients)

6. **Android Client**
   - Prerequisites: Android Studio, JDK 21, Android SDK 35
   - Build: `cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug`
   - APK location: `android/app/build/outputs/apk/debug/app-debug.apk`
   - Note: min SDK 26, target SDK 35, uses Jetpack Compose + Hilt + mediasoup-android

7. **Development** (local dev without Docker)
   - Server: `npm install && npm run dev` (requires Node.js 20+, Redis running locally)
   - Control-plane: `cd control-plane && npm install && npm run prisma:migrate && npm start`
   - Web UI: `cd web-ui && npm install && npm run dev`
   - Tests: `npm test` (vitest)
   - Linting: `npm run lint`

8. **Documentation**
   - Bulleted list linking to:
     - [Architecture](docs/architecture.md) - Control-plane design, auth flow, Redis sync
     - [Deployment Guide](docs/deployment-compose.md) - Production Docker Compose walkthrough
     - [API Reference](docs/api.md) - Control-plane REST endpoints
     - [User Manual](docs/user-manual.md) - Dispatch console usage guide
     - [Opus Implementation](docs/opus-implementation.md) - Audio codec details

9. **License**
   - MIT -- link to LICENSE file
   - Copyright line: Smart Walkie Pte Ltd

Formatting guidelines:
- Use fenced code blocks with language hints (bash, etc.)
- Keep it scannable: headers, tables, short paragraphs
- No emojis
- Total length: roughly 150-250 lines (comprehensive but not bloated)
  </action>
  <verify>
Verify the README:
1. `grep -c "VoicePing" README.md` should return 0 or at most 1 (only if referencing the repo URL)
2. `grep -c "ConnectVoice" README.md` should return 3+
3. `grep "docker compose" README.md` confirms Docker quick start is present
4. `grep "MEDIASOUP_ANNOUNCED_IP" README.md` confirms the critical env var is documented
5. `grep "assembleDebug" README.md` confirms Android build instructions are present
6. `grep "docs/" README.md` confirms doc links are present
7. Line count is between 120 and 300
  </verify>
  <done>
README.md is a complete, professional, ConnectVoice-branded document that covers: project overview, architecture diagram/table, Docker quick start, environment configuration, Android build, local development, documentation links, and license. No stale VoicePing-era content remains.
  </done>
</task>

</tasks>

<verification>
- README.md reads as a ConnectVoice project document, not a VoicePing one
- A developer unfamiliar with the project can follow Quick Start to get all services running
- All 6 Docker Compose services are documented with their roles and ports
- Environment variables are grouped logically with MEDIASOUP_ANNOUNCED_IP called out
- Android build instructions match the actual Gradle setup (AGP 9, SDK 35, JDK 21)
- All 5 docs/*.md files are linked
</verification>

<success_criteria>
- README.md exists with ConnectVoice branding, zero or minimal VoicePing references
- All major sections present: overview, architecture, quick start, env config, android, dev, docs, license
- Quick start is copy-paste runnable (3-4 commands)
- MEDIASOUP_ANNOUNCED_IP documented as critical for WebRTC
</success_criteria>

<output>
After completion, create `.planning/quick/6-rewrite-readme-for-connect-voice-rebrand/6-SUMMARY.md`
</output>
