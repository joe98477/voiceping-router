# VoicePing Control Plane Architecture

## Overview
The control-plane manages events, teams, channels, and user access. The router remains focused on low-latency audio routing, while Redis provides fast membership lookup and pub/sub updates.

## Core model
- Event: top-level container for teams and channels.
- Team: groups users within an event.
- Channel: either team-scoped or event admin/management.
- Memberships: event/team/channel assignments with status (pending/active).

## Auth
- Admin/Dispatch/User authenticate via the control-plane.
- Router access uses a JWT signed by the control-plane.
- Legacy company/user-id join is controlled by `LEGACY_JOIN_ENABLED`.

## Redis sync
- `u.{userId}.g` stores channel IDs for a user.
- `g.{channelId}.u` stores users in a channel.
- `vp:membership_updates` publishes updates for live routing.

## Limits
Limits are enforced at the control-plane and adjustable per event:
- maxUsers, maxTeams, maxChannels, maxChannelsPerTeam, maxDispatch.
