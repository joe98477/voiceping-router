# Phase 16: Permission Management - Context

**Gathered:** 2026-02-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Upfront permission education and graceful degradation for mic, location, and notification permissions. Users see an education screen on first launch, get contextual rationale dialogs when they attempt actions requiring denied permissions, and the app degrades gracefully without crashing when permissions are revoked mid-use.

</domain>

<decisions>
## Implementation Decisions

### Education flow
- Single screen listing all three permissions with brief explanations, then request all at once
- Direct & minimal tone: short bullet points ("Microphone - for PTT voice"), not paragraphs
- Skippable: user can proceed without granting; app works with reduced functionality
- Appears before login on first launch
- No labels distinguishing required vs optional - list all equally
- Only microphone is truly required; location and notification are optional
- Accessible from in-app settings: user can revisit permission status anytime
- If mic not granted and user taps PTT: PTT button disabled with message, not allowed to attempt

### Rationale dialogs
- Triggered only on action attempt (e.g., user taps PTT without mic permission)
- Standard Material system-style dialog with title, brief explanation, Grant/Cancel buttons
- Permission-specific messaging: different message per permission explaining WHY it's needed in user terms
- After granting permission from rationale dialog: dismiss dialog, user re-triggers action themselves (no auto-retry)
- No "Don't ask again" option in rationale dialog; rely on denial count for Settings redirect
- Detect Android's "Don't ask again" flag: if system won't show dialog, skip rationale and go straight to Settings redirect
- Deep-link directly to app's permission settings page (not general Settings)
- Re-check all permissions on resume (onResume) and update UI state immediately

### Degradation behavior
- Mic revoked: PTT button disabled, persistent banner at top with "Fix" action button
- Listen-only mode: user can still hear others on channels when mic is revoked, just can't transmit
- Mic revoked mid-transmission: immediately stop transmission, show brief error toast
- Location revoked: small on/off icon in top toolbar showing location sharing status (not tappable)
- Notification revoked: one-time toast/snackbar warning about background audio risk
- Multiple permissions missing: combined banner ("Some permissions needed for full functionality") with Fix button that opens in-app settings screen
- Banner auto-dismisses when permission is re-granted (detected on resume)
- In-app settings screen: shows each permission with granted/denied status and action button to request or open Settings

### Denial tracking UX
- After 2 denials: softer Settings redirect - "To use [feature], please enable [permission] in your device settings"
- Denial count resets on app restart (not persisted across sessions)
- Settings redirect dialog has "Open Settings" + "Not now" options
- Denial count tracked per-permission (mic, location, notification independently)
- After "Not now" on Settings redirect: shows again on every subsequent action attempt
- In-app settings screen shows just grant status, no denial count visible

### Claude's Discretion
- Education screen visual layout and icon choices
- Exact wording of permission-specific rationale messages
- Animation/transition for education screen
- Toast duration for mid-transmission error and notification warning
- Exact Material dialog styling and button labels

</decisions>

<specifics>
## Specific Ideas

- PTT button should be visibly disabled (greyed out) when mic permission is missing, not just non-functional
- Location icon in toolbar is purely informational (not tappable) - user manages location permission through settings screen
- Combined banner approach for multiple missing permissions keeps the UI clean rather than stacking banners

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within phase scope

</deferred>

---

*Phase: 16-permission-management*
*Context gathered: 2026-02-15*
