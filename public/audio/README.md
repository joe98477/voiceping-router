# PTT Audio Feedback Tones

This directory contains audio files used for PTT feedback tones.

## File Naming Convention

Audio files should be named according to their purpose:

- `transmit-start.mp3` - Played when PTT transmission starts (user presses button)
- `transmit-stop.mp3` - Played when PTT transmission stops (user releases button)
- `busy-tone.mp3` - Played when PTT is denied because channel is busy

## Event-Specific Tones

You can override default tones for specific events by creating event-specific folders:

```
/audio/events/{eventId}/transmit-start.mp3
/audio/events/{eventId}/transmit-stop.mp3
/audio/events/{eventId}/busy-tone.mp3
```

The system will attempt to load event-specific tones first, falling back to the default tones if not found.

## Recommendations

- **Format:** MP3, 44.1kHz or 48kHz sample rate
- **Duration:** 100-500ms for quick feedback
- **Volume:** Normalize to consistent levels
- **Channels:** Mono or stereo (system handles both)

## Custom Tones

Administrators can upload custom audio files following the naming convention above. To add a custom tone:

1. Export audio as MP3
2. Name according to convention (e.g., `transmit-start.mp3`)
3. Place in `/audio/` directory (default) or `/audio/events/{eventId}/` (event-specific)
4. System will automatically load on next preload

## Placeholder Files

The following files are placeholders and should be replaced with actual audio:

- `transmit-start.mp3`
- `transmit-stop.mp3`
- `busy-tone.mp3`

To generate proper tones, use audio editing software (Audacity, GarageBand, etc.) or online tone generators.

### Suggested Tone Characteristics

- **Transmit Start:** Short ascending beep (100-200ms), frequency ~800-1200Hz
- **Transmit Stop:** Short descending beep (100-200ms), frequency ~1200-800Hz
- **Busy Tone:** Two-tone alternating beep (300-500ms), frequencies ~400Hz and ~600Hz
