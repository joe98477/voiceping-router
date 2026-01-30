# Dispatch UI Plan

## Goals
- Admin and dispatch share a single console layout.
- Admin settings drawer exposes full configuration (events, teams, channels, users, invites, system).
- Dispatch settings drawer exposes layout controls only.
- First login with bootstrap admin requires profile setup (display name + strong password).

## Console layout
- Top bar: event name, event ID, pending count, settings cog, logout.
- Body: three main panels (Roster, Teams, Channels) with resource-card styling.
- Panel visibility and density are configurable in View settings.

## Settings drawer (role-based)
- View (all): show/hide roster/teams/channels, density, alert sounds.
- Event (admin): name, requires approval, limits.
- Teams (admin): list + create.
- Channels (admin): list + create admin/team channels.
- Users (admin): create user, assign to event, toggle password reset requirement.
- Invites (admin): generate invite tokens, optional email, team/channel defaults.
- System (admin): SMTP settings.

## First-run flow
- Bootstrap admin logs in with .env.test.example credentials.
- API flags mustChangePassword=true.
- UI routes to /first-run and blocks other pages.
- User sets display name and strong password.
- UI returns to /events.

## Error handling
- API timeout after 10 seconds; UI shows "Request timed out."
- 412 Profile setup required -> UI redirects to /first-run.
- 401/403 show inline errors in the current view.

## Data flow
- All admin settings operate through control-plane API endpoints.
- Overview pulls roster/teams/channels in one call:
  - GET /api/events/:eventId/overview
- Event updates via PATCH /api/events/:eventId
- Teams and channels creation via POST /api/events/:eventId/teams, /api/teams/:teamId/channels, /api/events/:eventId/channels
- User assignment via POST /api/events/:eventId/users
- Invites via POST /api/events/:eventId/invites
- System settings via GET/PATCH /api/admin/settings

## UX notes
- Resource tiles mimic modern dispatch consoles: dense, quick, and role-driven.
- Settings drawer is a single control center for all admin actions.
