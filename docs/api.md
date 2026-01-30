# Control-plane API (v1)

## Auth
- `POST /api/auth/login` { email, password }
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/forgot-password` { email }
- `POST /api/auth/reset-password` { token, password }
- `GET /api/ready`

## Admin
- `POST /api/admin/users` { email, password, displayName?, globalRole? }
- `GET /api/admin/status`

## Events / Teams / Channels
- `POST /api/events` { name, requiresApproval?, limits? }
- `PATCH /api/events/:eventId` { name?, requiresApproval?, limits? }
- `GET /api/events`
- `GET /api/events/:eventId/overview`
- `POST /api/events/:eventId/teams` { name }
- `POST /api/teams/:teamId/channels` { name }
- `POST /api/events/:eventId/channels` { name }

## Membership
- `POST /api/events/:eventId/users` { userId, role, teamIds?, channelIds? }
- `PATCH /api/events/:eventId/users/:userId/approve`
- `PATCH /api/events/:eventId/users/:userId/teams` { teamIds }
- `PATCH /api/events/:eventId/users/:userId/channels` { channelIds }

## Invites
- `POST /api/events/:eventId/invites` { teamId?, channelIds?, expiresInMinutes?, maxUses?, email? }
- `POST /api/invites/:token/join`

## Router token
- `POST /api/router/token` { eventId }
