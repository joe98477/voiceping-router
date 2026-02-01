# ConnectVoice UI Theme (Agent Guidance)

## Theme intent
- Match the ConnectVoice dispatch control-plane look: dark navy, glow accents, glassy panels.
- Prioritize legibility and operational clarity over decorative effects.
- Keep layouts dense but breathable; avoid large empty areas.

## Layout rules
- Use the control-plane 3-column layout where feasible (left: groups, center: live activity, right: roster).
- Keep a top bar with brand, stats, and time.
- Reserve a bottom footer strip for primary action (PTT) and status badges.

## Typography
- Primary font: Rajdhani.
- Titles use 600-700 weight, body 400-500.
- Uppercase labels for badges and section headers.

## Color usage
- Backgrounds: `--cv-bg`, `--cv-bg-2`.
- Panels: `--cv-surface`, `--cv-surface-2`.
- Accents: `--cv-accent` for focus; `--cv-accent-2` for secondary.
- Status: `--cv-ok`, `--cv-warn`, `--cv-alert` for state pills.

## Components
- Panels use `--cv-radius`/`--cv-radius-lg` and `--cv-shadow`.
- Buttons and toggles should glow on active state.
- Alerts should be noticeable but not neon.

## Rebrand
- Replace "VoicePing" with "ConnectVoice" in all visible UI text.
- Treat "ConnectVoice" as the product name across new surfaces.
