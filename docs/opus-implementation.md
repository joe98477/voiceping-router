# Opus implementation specification (dispatch UI)

This document is the **source of truth** for how the dispatch web UI captures, encodes, transports, and plays audio when interoperating with the VoicePing router.
It is specific to this repository and should be kept in sync with any audio pipeline changes.

## Summary (normative)
- **Encoder (browser):** WebCodecs `AudioEncoder` with `codec: "opus"`, mono, **48 kHz**, **24 kbps** target bitrate.
- **Transport:** Opus packets are sent as **raw binary payloads** inside VoicePing msgpack frames.
- **Decoder (browser):** `opus-decoder` (WASM) converts Opus packets to PCM for playback.
- **Router behavior:** The router treats audio payloads as **opaque bytes** and forwards them to channel members.
- **Browser priority:** **Chrome first** (WebCodecs required). Other browsers must support WebCodecs to transmit.

## Message framing (normative)
The router uses msgpack (notepack) frames with the following layout:

```
[channelType, messageType, fromId, toId, payload]
```

For audio:
- `channelType = 0` (GROUP)
- `messageType = 3` (AUDIO)
- `payload` is a **raw Opus packet** (Uint8Array)

Dispatch PTT sends:
1) `START` (messageType 1)
2) Many `AUDIO` packets
3) `STOP` (messageType 2)

The router does not inspect payloads; it forwards them as-is to group members.

## Identifiers (normative)
- `fromId`: dispatcher user ID from control-plane.
- `toId`: channel ID (group) from control-plane.
- `channelType`: currently only `GROUP (0)` used for dispatch audio.

## Audio encoding (normative)
File: `web-ui/src/utils/voicepingAudio.js`
- Codec: **Opus** via WebCodecs.
- Sample rate: **48 kHz** (required for interop; encoder probes and enforces).
- Channels: **1 (mono)**.
- Bitrate: **24 kbps** target (`DEFAULT_BITRATE`).
- Packetization: **WebCodecs-controlled**, not fixed frame size. The browser determines frame duration/packet size.

### Implications
- There is **no fixed ptime** specified in code. If interoperability requires a specific frame size (e.g., 20ms), the encoder must be reconfigured or audio frames buffered accordingly.
- Opus packets are emitted by the encoder and sent immediately to the router without additional framing or headers.

## Audio decoding & playback (normative)
File: `web-ui/src/utils/voicepingAudio.js`
- Decoder: `opus-decoder` WASM.
- Output: Web Audio `AudioContext` with a playhead to schedule buffers sequentially.
- Playback is gated by:
  - Channel listen toggles (`listenerChannelIds`).
  - Dispatch listen toggles (team/channel).
- Empty or invalid Opus frames are dropped before decode and before `createBuffer` to avoid playback errors.
- A small jitter buffer (~100ms target) is used to smooth network jitter; long gaps reset the playhead.

### Playback timing
- Packets are scheduled in order with a simple playhead.
- There is no jitter buffer beyond sequential scheduling. If network jitter becomes an issue, a jitter buffer should be added here.

## Loopback test (normative)
Loopback is **dispatch UI only**:
- Captures outgoing Opus packets for each transmission.
- Replays locally **2 seconds** after transmit ends.
- Triggered per-channel via the loopback button.
- No server-side changes.

## Permissions & browser requirements (normative)
- WebCodecs (`AudioEncoder` + `MediaStreamTrackProcessor`) is required for transmit.
- Microphone permission is required and prompted via "Enable audio".
- Microphone access requires HTTPS or localhost; HTTP on a LAN IP will not prompt for access.
- Browsers do not prompt for speaker access; only microphone permission is expected.
- Chrome is the primary supported browser.

## Error handling (normative)
- Transmit attempts fail fast if:
  - Microphone permission is denied.
  - WebCodecs is unavailable.
  - Router WebSocket is not open.
  - No valid target channels resolved for a team.
- `START_FAILED` or `UNAUTHORIZED_GROUP` responses surface a UI error.
- Encoder output frames of zero length are dropped and logged when debug is enabled.

## Router expectations (normative)
- Router treats payload as opaque bytes and forwards to group members.
- Router does not enforce Opus configuration; compatibility is the client’s responsibility.

## Non-normative notes (implementation details)
- The dispatch UI uses `getRouterWsUrl()` which defaults to `ws(s)://<host>:3000` unless `VITE_ROUTER_WS` is set.
- The loopback uses already-encoded Opus packets to avoid extra re-encoding.

## Future changes checklist
If modifying the audio pipeline, update:
1) `web-ui/src/utils/voicepingAudio.js`
2) This document
3) Any interop testing notes in `docs/deployment-compose.md`

## Test checklist (manual)
- Chrome: enable mic, connect audio, PTT to a channel with another client listening.
- Listen/mute toggles control playback as expected.
- Loopback test plays audio ~2s after transmission ends.
- Android SDK client receives audio from dispatch UI (if applicable).
- No console errors about invalid audio buffers during playback.

## Audio capture (dispatch UI)
`web-ui/src/utils/voicepingAudio.js`
- Uses `MediaStreamTrackProcessor` + `AudioEncoder` to capture and encode Opus.
- Sample rate is configured at **48kHz** (fallbacks handled by WebCodecs support probing).
- Bitrate is set to ~24kbps (`DEFAULT_BITRATE`).
- Mono channel only.

If WebCodecs is unavailable, transmit is blocked and the UI surfaces an error.

## Audio playback (dispatch UI)
`web-ui/src/utils/voicepingAudio.js`
- Uses `opus-decoder` (WASM) to decode Opus packets to PCM.
- Uses `AudioContext` to schedule playback with a small playhead buffer.
- Playback respects listen/mute targets and channel toggles in the Console UI.

## Loopback test
Loopback is implemented in the dispatch UI only:
- After the PTT transmission ends, the captured Opus packets are replayed locally **2 seconds later**.
- No server-side changes required.

## Compatibility notes
- **Chrome is required** for transmit due to WebCodecs + MediaStreamTrackProcessor.
- If the Android SDK uses a different Opus packetization profile, update the encoder config here and re-test interoperability.

## Where to change
- Encoding/decoding pipeline: `web-ui/src/utils/voicepingAudio.js`
- Console UI controls: `web-ui/src/pages/Console.jsx`
