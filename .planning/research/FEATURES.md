# Features Research: Android PTT Client

**Domain:** Android Push-to-Talk (PTT) application for field workers
**Researched:** 2026-02-08
**Confidence:** MEDIUM (verified with multiple PTT app sources, official Android documentation, and competitor analysis)

## Table Stakes

Features users expect from any professional Android PTT app. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Dependencies | Notes |
|---------|--------------|------------|--------------|-------|
| **Hardware PTT button mapping** | Industry standard - volume keys, dedicated PTT buttons (Sonim/Kyocera rugged phones), Bluetooth headset buttons | Medium | Android accessibility services for volume key capture; Bluetooth HID protocol for headset PTT | Volume keys only work when app in foreground unless using accessibility service. Bluetooth PTT requires per-device mapping. |
| **Lock screen PTT operation** | Field workers need hands-free, screen-off operation (device in pocket) | High | Foreground service with WAKE_LOCK, lock screen notification with custom actions, audio focus management | Must handle Android 12+ locked action constraints. Critical for "pocket radio" use case. |
| **Foreground service for background audio** | PTT audio must play with screen off, app backgrounded | Medium | MediaSession service with `mediaPlayback` foreground service type (Android 14+) | Required for background audio playback. Must declare in manifest and request FOREGROUND_SERVICE permission. |
| **Channel list with activity indicators** | Users need to see which channels are active, who's talking | Low | Server already provides channel state via WebSocket | Visual indicators: talking (animated), active (recent transmission), idle. Color-coded or icon-based. |
| **Audio routing control** | Earpiece (privacy) vs speaker (loud environment) | Low | Android AudioManager routing APIs | Default to speaker for PTT (walkie-talkie convention), earpiece for privacy mode. Auto-switch to Bluetooth when connected. |
| **Push notifications for incoming transmissions** | Alert users when channel becomes active, especially when app not in foreground | Medium | FCM integration + server-side notification triggers | Must work with Do Not Disturb - some PTT apps override DND for broadcast talkgroups if admin-configured. |
| **Auto-reconnect with session recovery** | Cellular network drops are common in field environments | Low | Server already supports reconnection via JWT session refresh | ReconnectingSignalingClient exists. Android-specific: handle network changes (WiFi <-> cellular), background restrictions. |
| **Per-channel volume control** | Users need different volume for different channels (noisy vs quiet teams) | Low | Android AudioManager per-stream volume | Store per-channel volume preferences locally. Apply when channel becomes active. |
| **Busy state indicators** | Visual feedback when channel is busy (someone else talking) | Low | Server already provides PTT busy state | Show "busy" overlay on PTT button, disable transmit, show who's talking. |
| **Login with event picker** | Multi-tenant system - users select event before accessing channels | Low | Server already supports JWT auth with event scope | Material 3 design for login flow. Remember last event. |
| **Team-grouped channel list** | Channels organized by team for easier navigation | Low | Server provides team metadata via channel list | Collapsible team sections. Material 3 expansion panels. |
| **Transmission history (recent)** | Users need to see recent transmissions per channel | Low | Local storage of recent messages | Show last 10-20 transmissions per channel. Timestamp, user name, duration. Not persistent across app restarts (table stakes). |

## Differentiators

Features that set VoicePing apart from competitors. Not expected, but highly valued.

| Feature | Value Proposition | Complexity | Dependencies | Notes |
|---------|-------------------|------------|--------------|-------|
| **Scan mode with auto-switch** | Monitor up to 5 channels simultaneously, auto-switch to active channel, drop back to primary when transmission ends | High | Server supports multi-channel monitoring. Client needs channel priority logic, audio stream switching | Like two-way radio scan mode. Priority channel = primary. Auto-switch on activity. Visual indicator of which channel is "hot". This is the killer feature for dispatch/field coordination. |
| **Scan mode visual bottom bar** | Bottom bar shows all monitored channels with real-time activity indicators, tap to switch manually | Medium | Scan mode feature above | Persistent UI element showing 1-5 monitored channels. Color-coded activity (idle/talking/recent). One-tap manual override. Material 3 chip-style design. |
| **Bluetooth headset PTT with dual-button support** | Map primary/secondary PTT buttons to different channels or functions | High | Bluetooth HID protocol, per-device button mapping | Some Bluetooth headsets have 2 PTT buttons (e.g., E2 headset). Primary = talk on active channel, Secondary = switch to priority channel. Competitive advantage over Zello. |
| **Instant channel monitoring (no manual join)** | Add channel to monitoring with one tap - no explicit join flow | Medium | Server already supports join flow; client needs streamlined UX | Unlike Zello which requires explicit channel join, VoicePing can add to scan list immediately. Reduces friction for dispatch users. |
| **Network quality indicator** | Real-time display of connection quality, latency, jitter | Low | WebRTC stats API for RTT, packet loss, jitter | Show in status bar or per-channel. Warn when quality degraded (>300ms latency, >5% packet loss). Helps field workers troubleshoot. |
| **Offline mode with cached channel list** | View channel list, team structure when offline; notify when online | Low | Local storage of channel metadata | Doesn't allow PTT when offline, but users can see structure. Auto-reconnect when network returns. |
| **Haptic feedback for PTT events** | Vibrate on PTT press, release, transmission start, busy state | Low | Android Vibrator API | Short pulse on press (confirm), different pattern on busy (warn), pulse on transmission end. Improves tactile UX for lock-screen use. |
| **Emergency broadcast override** | Dispatch can force-transmit to all monitored channels simultaneously | Medium | Server already supports emergency broadcast via dispatchHandlers.ts | Client shows distinct visual + audio alert for emergency transmission. Separate from normal PTT. Admin-triggered only. |

## Anti-Features

Features to explicitly NOT build. Common mistakes or feature creep in PTT apps.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Persistent transmission history** | Storage bloat, privacy concerns, not core PTT use case | Keep only recent (last 10-20) transmissions in memory. Clear on app restart. PTT is ephemeral, not archival. |
| **In-app messaging/chat** | Feature creep - VoicePing is voice-first | If needed, link to external chat (Teams, Slack). Don't reinvent messaging. Focus on voice. |
| **Video streaming** | Bandwidth intensive, battery drain, not PTT use case | Audio-only. If visual needed, suggest separate video call app. |
| **GPS tracking / location sharing** | Privacy nightmare, battery drain, regulatory complexity | If dispatch needs location, integrate with external MDM or GPS tracker. Don't build into PTT app. |
| **Complex admin controls in mobile app** | Mobile is for field workers, not admins | Admin functions stay in web dashboard. Mobile app is view-only for settings. |
| **Offline message queuing** | PTT is real-time; queued messages break mental model | Show "offline" state clearly. Don't queue - it creates confusion about whether message was heard. |
| **Custom notification sounds per channel** | Cacophony in multi-channel monitoring | Single, clear PTT notification sound. Visual indicators differentiate channels. |
| **Recording/playback of transmissions** | Legal liability, storage complexity, privacy concerns | Ephemeral only. If recording needed, server-side with compliance framework. |

## UX Patterns

Standard Android PTT interaction patterns from competitor analysis (Zello, WAVE PTX, Voxer).

### PTT Button Interaction Model

**Standard Mode (screen on, app in foreground):**
- Large central PTT button (Material 3 FAB style, 56dp minimum)
- Press and hold to transmit (visual feedback: button grows, color shifts, waveform animation)
- Release to end transmission (button returns to rest state)
- Busy state: button disabled, overlay shows "Busy - [User Name] speaking"
- Transmitting state: button shows waveform or pulsing animation, timer shows duration

**Lock Screen Mode:**
- Notification with custom PTT action (requires Android unlock for security on Android 12+)
- Alternative: accessibility overlay with PTT button (works without unlock, but requires special permission)
- Hardware button mapping (volume down = PTT) - most common pattern for lock screen use

**Hardware Button Patterns:**
- Volume Down = PTT (most common default)
- Volume Up = secondary function (switch to priority channel, toggle scan mode)
- Camera button = alternative PTT (some apps support)
- Dedicated PTT button (rugged phones like Sonim XP8, Kyocera DuraForce) = instant PTT without app launch

### Channel List Patterns

**Zello Pattern:**
- Tab-based navigation: Contacts, Channels, Recents
- Channels tab shows joined channels with status indicators (online/offline)
- FAB (+) button for adding channels/contacts
- Swipe actions for channel options

**WAVE PTX Pattern:**
- Two modes: PTT Radio (radio emulation - minimal UI) and Standard Mode (full features)
- Talkgroup list with scan list indicators
- Drawer navigation for settings, favorites, map

**VoicePing Recommendation:**
- Single screen: Team-grouped channel list (Material 3 expansion panels)
- Each channel shows: name, current speaker (if active), activity indicator, monitoring status (scan mode)
- Bottom bar: Scan mode controls + active channel indicator
- Top bar: Event name, network quality, settings
- No tabs - simpler for field workers

### Scan Mode Visual Patterns

**Traditional Two-Way Radio Pattern:**
- Primary channel (priority 1) = default transmit channel
- Scan list (2-5 additional channels) = listen-only unless manually switched
- Auto-switch: when scan channel becomes active, radio switches audio to that channel
- Drop-back: when transmission ends, radio returns to primary channel after 5-second delay
- Manual override: press PTT on non-primary channel to transmit there, or press channel selector to lock to that channel

**VoicePing Implementation:**
- Bottom bar shows 1-5 monitored channels as chips/cards
- Each chip shows: channel name, activity indicator (idle/talking/recent), speaker name (if talking)
- Active channel (currently playing audio) highlighted with distinct color/border
- Primary channel marked with star/pin icon
- Tap chip to manually switch to that channel
- Auto-switch behavior:
  - When any monitored channel becomes active → switch audio to that channel (visual highlight)
  - When transmission ends → 5-second delay → return to primary channel (unless user manually locked)
  - If multiple channels active simultaneously → priority order (primary > manually selected > first active)
- Visual feedback: animated pulse on active channel, waveform on speaking channel

### Audio Routing Patterns

**Default Behavior:**
- PTT incoming audio → speaker (walkie-talkie convention: loud for field use)
- Phone calls → earpiece (standard Android behavior)
- Priority: Bluetooth headset > wired headset > speaker/earpiece

**User Controls:**
- Toggle: earpiece mode (privacy) vs speaker mode (loud)
- Setting persists per session, not per channel (KISS principle)
- Auto-switch to Bluetooth when connected (with visual indicator)
- Volume: per-channel volume adjustment (slider in channel long-press menu)

**Edge Case Handling:**
- Phone call incoming → pause PTT audio, resume after call ends
- Multiple Bluetooth devices → last connected wins (or user selects in Android Bluetooth settings)
- Wired headset plugged in mid-transmission → auto-switch to wired, continue transmission

### Settings Patterns (Typical PTT Apps)

**Audio Settings:**
- Speaker vs earpiece default
- PTT call volume (per-channel saved separately)
- Notification sound (single sound, not per-channel)
- Vibration patterns (on/off, intensity)

**PTT Button Settings:**
- Select PTT button (volume down/up, screen button, Bluetooth)
- Map hardware buttons to channels (advanced)
- PTT lock (require hold vs toggle mode) - most apps default to hold

**Network Settings:**
- Prefer WiFi vs cellular (usually auto-detect with WiFi priority)
- Auto-reconnect on network change (usually always on)
- Low bandwidth mode (reduce audio quality to save data)

**Notification Settings:**
- Lock screen notifications (on/off)
- Show content on lock screen (for secure devices, may hide channel names)
- Override Do Not Disturb (admin-configurable per channel/talkgroup)

**Scan Mode Settings (if feature exists):**
- Max monitored channels (1-5)
- Auto-switch behavior (on/off)
- Drop-back delay (0-10 seconds)
- Priority channel selection

**Other:**
- Auto-start on boot (for always-on field workers)
- Battery optimization exclusion (request user to disable for app)
- Remember last event (for multi-tenant)

**VoicePing Principle:** Minimal settings. Smart defaults. Field workers should not need to configure - it should just work.

## Edge Cases

Important edge cases and expected behavior for Android PTT apps.

### Phone Calls

**Incoming phone call during PTT reception:**
- Pause PTT audio immediately (Android system audio focus loss)
- Show notification: "PTT paused - incoming call"
- After call ends: auto-resume PTT audio if still connected to channel
- Do NOT resume mid-transmission if transmission ended during call

**Incoming phone call during PTT transmission:**
- End PTT transmission immediately (send PTT release to server)
- Answer phone call (Android system takes audio focus)
- After call ends: return to channel, but do NOT auto-resume transmission

**Outgoing phone call while monitoring channels:**
- Pause PTT monitoring (stop all audio playback)
- After call ends: auto-resume monitoring

**Expected behavior:** Phone calls always win. PTT is secondary to cellular voice.

### Do Not Disturb (DND)

**User has DND enabled:**
- PTT audio should still play (if app in foreground or monitoring mode active)
- PTT notifications should be suppressed UNLESS channel is marked as "emergency broadcast" (admin override)
- Setting: "Allow PTT audio during DND" (default: ON for field worker use case)

**Broadcast talkgroup override:**
- Admin can configure talkgroups to override DND (emergency use case)
- Client shows distinct visual alert (red banner, different notification)
- Plays emergency sound even in DND

**Expected behavior:** DND suppresses normal notifications, but active PTT monitoring continues (field workers need to hear transmissions even in DND).

### Battery Saver Mode

**Android Battery Saver enabled:**
- Reduces CPU performance, limits background sync
- Foreground service should continue running (not killed by battery saver)
- Network reconnection may be delayed if app backgrounded
- Visual effects (animations) may be reduced by system

**App-specific battery optimization:**
- Request user to exclude VoicePing from battery optimization (via settings deep link)
- Show warning when battery optimization detected: "For best performance, disable battery optimization for VoicePing"

**Expected behavior:** Foreground service keeps app alive, but warn users about battery optimization settings that could impact performance.

### Split Screen / Multitasking

**App in split screen mode:**
- Continue PTT operation normally (audio playback, transmission)
- UI may be condensed - ensure PTT button remains accessible
- Bottom bar (scan mode) should remain visible in compact layout

**App in picture-in-picture (PiP):**
- Not applicable - PTT apps don't typically support PiP (no video)

**Task switcher / recent apps:**
- Foreground service keeps app alive
- Switching to another app = app moves to background but audio continues
- PTT button in notification (MediaSession controls) for quick access

**Expected behavior:** PTT audio continues regardless of app visibility state. Foreground service is key.

### Network Changes

**WiFi to cellular handoff:**
- WebRTC may drop connection briefly (ICE restart)
- Auto-reconnect with exponential backoff (ReconnectingSignalingClient)
- Show "reconnecting" indicator during handoff
- Resume monitoring after reconnection

**Network completely lost:**
- Show "offline" state immediately (WebSocket disconnect)
- Disable PTT button (prevent failed transmit attempts)
- Keep channel list visible (cached)
- Auto-reconnect when network returns

**Airplane mode toggled:**
- On: immediate disconnect, show offline
- Off: auto-reconnect when network available

**Expected behavior:** Seamless handoff when possible. Clear offline indicators when not possible. Auto-reconnect always.

### Bluetooth Headset Edge Cases

**Bluetooth headset disconnects mid-transmission:**
- Continue transmission, but switch audio to speaker (don't drop transmission)
- Show notification: "Bluetooth disconnected - using speaker"

**Bluetooth headset connects mid-transmission:**
- Switch audio to Bluetooth immediately (if user preference allows)
- Don't interrupt transmission

**Multiple Bluetooth devices paired:**
- Use last connected device (Android default behavior)
- If user wants to switch, must do so via Android Bluetooth settings (don't reinvent in-app)

**Bluetooth PTT button not responding:**
- Show troubleshooting hint: "Ensure Bluetooth device is mapped to VoicePing in Settings > PTT Button"
- Provide link to pairing guide

**Expected behavior:** Audio routing switches seamlessly. PTT button mapping requires user configuration (per-device).

### Screen Lock Transitions

**Screen locks during transmission:**
- Continue transmission (don't release PTT)
- Show lock screen notification with waveform animation
- Allow release via hardware button or lock screen action

**Screen locks during reception:**
- Continue playing audio
- Show lock screen notification with speaker name

**App moves to background (home button pressed):**
- Continue monitoring/transmission via foreground service
- MediaSession notification shows active channel, PTT controls

**Expected behavior:** Screen state does not affect PTT operation. Foreground service + MediaSession handle all background cases.

### Permission Edge Cases

**User revokes microphone permission mid-session:**
- Immediately disable PTT button
- Show alert: "Microphone permission required for PTT"
- Provide button to re-request permission

**User revokes notification permission:**
- PTT audio continues (doesn't depend on notifications)
- Lock screen controls unavailable (no notification to show actions)
- Show in-app warning: "Enable notifications for lock screen PTT"

**User revokes foreground service permission (Android 14+):**
- App cannot run in background
- Show critical alert: "Foreground service required for background PTT"
- Deep link to app settings

**Expected behavior:** Graceful degradation when permissions revoked. Clear messaging about what's broken and how to fix.

### Multi-Instance Prevention

**User tries to open app on multiple devices simultaneously:**
- Server should enforce single-session-per-user (JWT invalidation)
- Show alert on older device: "Session started on another device"
- Gracefully disconnect older session

**User has multiple Android devices logged in:**
- Each device = separate WebSocket session
- Server broadcasts PTT to all sessions
- Both devices receive audio (not a problem - walkie-talkie behavior)

**Expected behavior:** Allow multi-device (field worker might have phone + tablet). Server prevents duplicate transmissions.

## Feature Dependencies on Server

VoicePing Android app builds on existing server features. No new server features required for MVP.

| Android Feature | Server Dependency | Status |
|-----------------|-------------------|--------|
| Channel monitoring | `/joinChannel` handler, WebSocket channel membership | EXISTS |
| Multi-channel scan mode | Multiple simultaneous `/joinChannel` calls, multi-consumer support | EXISTS (dispatch web already does this) |
| PTT transmission | `/pttRequest` handler with busy state management | EXISTS |
| Hardware PTT button | Client-side only (no server changes) | N/A |
| Lock screen PTT | Client-side foreground service + MediaSession | N/A |
| Emergency broadcast | `/emergencyBroadcast` handler in dispatchHandlers.ts | EXISTS (admin/dispatch role only) |
| Network quality indicator | WebRTC stats (client-side), no server changes | N/A |
| Per-channel volume | Client-side setting storage | N/A |
| Auto-reconnect | JWT session refresh, existing reconnection logic | EXISTS |
| Offline mode | Client-side cache, no server changes | N/A |

**Key insight:** Android app is a new client for existing server. No server-side feature work required for MVP. Focus on Android-native UX patterns.

## Implementation Complexity Notes

**Low Complexity (1-3 days per feature):**
- Channel list UI, team grouping
- Per-channel volume control
- Audio routing (earpiece/speaker toggle)
- Network quality indicator
- Haptic feedback
- Offline mode with cached channels

**Medium Complexity (3-7 days per feature):**
- Foreground service with MediaSession
- Lock screen notifications with PTT actions
- Hardware volume button mapping (accessibility service approach)
- Push notifications (FCM integration)
- Scan mode bottom bar UI
- Auto-reconnect with network change handling

**High Complexity (1-2 weeks per feature):**
- Lock screen PTT operation (foreground service + wake lock + audio focus + notification actions + Android 12+ constraints)
- Scan mode auto-switch logic (multi-channel audio stream management, priority resolution)
- Bluetooth headset PTT button mapping (HID protocol, per-device configuration, dual-button support)

**Critical path for MVP:**
1. Lock screen PTT operation (highest complexity, highest value for field workers)
2. Scan mode with auto-switch (differentiator feature)
3. Hardware button mapping (table stakes for "pocket radio" use case)

## Sources

- [Zello PTT Walkie Talkie - Apps on Google Play](https://play.google.com/store/apps/details?id=com.loudtalks&hl=en_US)
- [Zello Support: Using Volume/Screen Button for PTT (Android)](https://support.zello.com/hc/en-us/articles/230745107-Using-Volume-Screen-Button-for-PTT-Android)
- [Zello Support: Android Options Guide](https://support.zello.com/hc/en-us/articles/230749107-Android-Options-Guide)
- [Motorola WAVE PTX Mobile App](https://www.airwavecommunication.com/wave-ptx-ptt/wave-ptx-mobile-app.htm)
- [Android Developers: Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android Developers: Background playback with MediaSessionService](https://developer.android.com/media/media3/session/background-playback)
- [Android Developers: About notifications](https://developer.android.com/develop/ui/views/notifications)
- [Android Developers: Configure audio policies](https://source.android.com/docs/core/audio/implement-policy)
- [Sonim XP8 PTT Capabilities](https://blog.sonimtech.com/blog/the-push-to-talk-over-cellular-capabilities-of-the-sonim-xp8)
- [Sonim XP5plus Rugged PTT Phone](https://www.sonimtech.com/products/phones/xp5plus)
- [Pairing a Bluetooth PTT Button (Android) - Zello Support](https://support.zello.com/hc/en-us/articles/230745407-Pairing-a-Bluetooth-PTT-Button-Android)
- [Zebra: Enable PTT Button Support for Bluetooth Headsets](https://docs.zebra.com/us/en/solutions/wcc/wcc-pttpro-android-cg/ptt-pro-for-android-json-configuration-elements/enable-ptt-button-support-for-bluetooth-headsets.html)
- [VoicePing: Integrated Bluetooth PTT Headset](https://www.voicepingapp.com/blog/integrated-bluetooth-ptt-headset)
- [Google Support: Limit interruptions with Do Not Disturb](https://support.google.com/android/answer/9069335?hl=en)
- [Tait Radio Academy: How Scanning Works](https://www.taitradioacademy.com/topic/how-scanning-works-1/)
- [Herda Radio: Mastering Scanning - The Ultimate Guide](https://herdaradio.com/blog/radioknowledge/scanning/)
- [PTT over Cellular Network Latency Analysis (2026)](https://www.telecomgurukul.com/post/analyzing-latency-in-4g-and-5g-networks-updated-in-2024)
- [Mobile Tornado: Factors that have driven the increase in reliability of PTT](https://mobiletornado.com/blog/what-has-driven-the-increase-in-reliability-of-ptt/)
- [Talker: 5 Best Push-to-Talk Walkie Talkie Apps (2025)](https://talker.network/5-best-push-to-talk-apps-for-ios-and-android/)
- [Push-to-Talk Functions with Locked Screen](https://www.smartwalkie.com/blog/push-to-talk-functions-with-locked-screen)
