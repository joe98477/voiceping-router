# Location Tracking WebSocket API

This document describes the WebSocket location tracking API for VoicePing Router. These message types enable real-time location tracking for Android clients and location monitoring for dispatch web UI.

## Overview

The location tracking system provides:
- Real-time location updates from Android clients
- SQLite storage with 24-hour retention
- Broadcast to dispatch users in real time
- Batch upload for offline/reconnect scenarios
- Latest position query with stale indicator

## Message Types

### 1. `location-update` (Client → Server)

Single location update from Android client. Fire-and-forget - no response expected.

**Direction:** Client → Server
**Auth:** Any authenticated user
**Response:** None (fire-and-forget)

**Message Format:**
```json
{
  "type": "location-update",
  "data": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "accuracy": 15.5,
    "speed": 2.3,
    "heading": 180.0,
    "motionState": "walking",
    "timestamp": "2026-02-15T10:30:45.123Z"
  }
}
```

**Field Constraints:**
- `latitude`: number, range -90 to 90 (degrees)
- `longitude`: number, range -180 to 180 (degrees)
- `accuracy`: number, >= 0 (meters)
- `speed`: number | null, >= 0 (m/s), null if unavailable
- `heading`: number | null, range 0 to 360 (degrees), null if unavailable
- `motionState`: enum, one of: `'still'`, `'walking'`, `'driving'`, `'unknown'`
- `timestamp`: ISO8601 string from client's clock

**Behavior:**
- Server validates lat/lng range, rejects invalid coordinates with error
- Server validates motionState enum, rejects invalid values
- Server stores in SQLite with userId from auth context
- Server broadcasts to all connected dispatch users
- No response sent to client (fire-and-forget for performance)

---

### 2. `location-batch` (Client → Server)

Batch of location updates, typically sent after reconnect when queued updates were accumulated offline.

**Direction:** Client → Server
**Auth:** Any authenticated user
**Response:** None (fire-and-forget)

**Message Format:**
```json
{
  "type": "location-batch",
  "data": {
    "updates": [
      {
        "latitude": 37.7749,
        "longitude": -122.4194,
        "accuracy": 15.5,
        "speed": 2.3,
        "heading": 180.0,
        "motionState": "walking",
        "timestamp": "2026-02-15T10:30:45.123Z"
      },
      {
        "latitude": 37.7750,
        "longitude": -122.4195,
        "accuracy": 12.0,
        "speed": 2.5,
        "heading": 185.0,
        "motionState": "walking",
        "timestamp": "2026-02-15T10:31:45.456Z"
      }
    ]
  }
}
```

**Field Constraints:**
- `updates`: array of location objects (same format as location-update)
- Array must not be empty
- Each update validated individually (invalid entries skipped, not rejected)

**Behavior:**
- Server validates each update, skips invalid entries (logs warnings)
- Server stores all valid updates in a single transaction
- Server broadcasts only the LATEST update (last in array) to dispatch users
- Assumption: Client sorts updates by timestamp before sending

---

### 3. `location-query` (Dispatch Client → Server)

Get all latest positions for all users. Dispatch-only permission.

**Direction:** Dispatch Client → Server
**Auth:** DISPATCH role only
**Response:** Array of latest positions with stale indicator

**Request Format:**
```json
{
  "type": "location-query",
  "id": "client-request-id-123"
}
```

**Response Format:**
```json
{
  "type": "channel-state",
  "id": "client-request-id-123",
  "data": {
    "positions": [
      {
        "userId": "user-123",
        "latitude": 37.7749,
        "longitude": -122.4194,
        "accuracy": 15.5,
        "speed": 2.3,
        "heading": 180.0,
        "motionState": "walking",
        "timestamp": "2026-02-15T10:30:45.123Z",
        "isStale": false
      },
      {
        "userId": "user-456",
        "latitude": 37.7800,
        "longitude": -122.4200,
        "accuracy": 20.0,
        "speed": null,
        "heading": null,
        "motionState": "still",
        "timestamp": "2026-02-15T10:20:00.000Z",
        "isStale": true
      }
    ],
    "count": 2
  }
}
```

**Stale Indicator Logic:**
- `isStale`: boolean, true if position timestamp > 5 minutes old
- Calculation: `(Date.now() - new Date(timestamp).getTime()) > 5 * 60 * 1000`
- Stale positions still returned (shows last known position for offline users)

**Permission:**
- If non-dispatch user requests: returns error `Permission denied: Location queries are only available to Dispatch users`

---

### 4. `location-broadcast` (Server → Dispatch Clients)

Real-time location update broadcast to all dispatch users. Sent automatically when server receives location-update or location-batch.

**Direction:** Server → All Dispatch Clients
**Auth:** Broadcast to all DISPATCH role connections
**Trigger:** Automatic when location-update or location-batch received

**Message Format:**
```json
{
  "type": "location-broadcast",
  "data": {
    "userId": "user-123",
    "latitude": 37.7749,
    "longitude": -122.4194,
    "accuracy": 15.5,
    "speed": 2.3,
    "heading": 180.0,
    "motionState": "walking",
    "timestamp": "2026-02-15T10:30:45.123Z"
  }
}
```

**Broadcast Logic:**
- Server broadcasts only if position is NEWER than cached position (compares timestamp)
- Server maintains in-memory cache of latest position per user
- Deduplication: Same or older timestamp skipped (no broadcast)

---

## SQLite Schema

**Table: `locations`**

```sql
CREATE TABLE locations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL,
  latitude REAL NOT NULL CHECK(latitude >= -90 AND latitude <= 90),
  longitude REAL NOT NULL CHECK(longitude >= -180 AND longitude <= 180),
  accuracy REAL NOT NULL CHECK(accuracy >= 0),
  speed REAL CHECK(speed IS NULL OR speed >= 0),
  heading REAL CHECK(heading IS NULL OR (heading >= 0 AND heading < 360)),
  motion_state TEXT NOT NULL CHECK(motion_state IN ('still', 'walking', 'driving', 'unknown')),
  timestamp TEXT NOT NULL,  -- ISO8601 from client
  created_at TEXT NOT NULL DEFAULT (datetime('now'))  -- Server insert time
);

CREATE INDEX idx_locations_user_time ON locations(user_id, timestamp DESC);
CREATE INDEX idx_locations_created ON locations(created_at);
```

**Retention Policy:**
- Records older than 24 hours (by `created_at`) are deleted hourly
- Cleanup runs via `setInterval()` every 60 minutes
- Cleanup SQL: `DELETE FROM locations WHERE created_at < datetime('now', '-24 hours')`

---

## Integration Guide (Future Dispatch Web UI - LOC-06)

### 1. Initial Load

When dispatch web UI loads, send `location-query` to get all latest positions:

```typescript
const queryMessage = {
  type: 'location-query',
  id: generateRequestId(),
};

websocket.send(JSON.stringify(queryMessage));

// Handle response
websocket.addEventListener('message', (event) => {
  const message = JSON.parse(event.data);

  if (message.id === queryRequestId && message.data.positions) {
    const positions = message.data.positions;

    // Render on map
    positions.forEach((pos) => {
      if (pos.isStale) {
        // Render with gray/faded marker (offline user)
      } else {
        // Render with active marker
      }
    });
  }
});
```

### 2. Real-Time Updates

Listen for `location-broadcast` messages to update map in real time:

```typescript
websocket.addEventListener('message', (event) => {
  const message = JSON.parse(event.data);

  if (message.type === 'location-broadcast') {
    const { userId, latitude, longitude, accuracy, motionState, timestamp } = message.data;

    // Update marker on map
    updateUserMarker(userId, {
      lat: latitude,
      lng: longitude,
      accuracy,
      motionState,
      timestamp,
      isStale: false, // Real-time update is never stale
    });
  }
});
```

### 3. Stale Detection (Client-Side)

Implement client-side stale detection to fade markers after 5 minutes:

```typescript
setInterval(() => {
  const now = Date.now();
  const staleThreshold = 5 * 60 * 1000; // 5 minutes

  userMarkers.forEach((marker, userId) => {
    const age = now - new Date(marker.timestamp).getTime();
    const isStale = age > staleThreshold;

    if (isStale && !marker.wasStale) {
      // Fade marker (user went offline)
      marker.setOpacity(0.5);
      marker.wasStale = true;
    }
  });
}, 30000); // Check every 30 seconds
```

### 4. Motion State Visualization

Display motion state with different marker styles:

```typescript
function getMarkerIcon(motionState: string, isStale: boolean) {
  if (isStale) {
    return '/markers/user-offline.png';
  }

  switch (motionState) {
    case 'still': return '/markers/user-still.png';
    case 'walking': return '/markers/user-walking.png';
    case 'driving': return '/markers/user-driving.png';
    case 'unknown': return '/markers/user-unknown.png';
  }
}
```

---

## Error Handling

**Validation Errors:**
- Invalid lat/lng: `"Invalid latitude: 91 (must be -90 to 90)"`
- Invalid motion state: `"Invalid motion state: running"`
- Empty batch: `"Invalid batch: updates must be a non-empty array"`

**Permission Errors:**
- Non-dispatch query: `"Permission denied: Location queries are only available to Dispatch users"`

**Service Errors:**
- Location services not initialized: `"Location services not available"`

All errors returned as SignalingType.ERROR with message.id correlation.

---

## Performance Considerations

- **Fire-and-forget:** `location-update` and `location-batch` have no response to minimize latency
- **Deduplication:** Broadcasts skipped for same/older timestamps (in-memory cache check)
- **WAL mode:** SQLite database uses WAL journal mode for concurrent reads during writes
- **Batch transactions:** `location-batch` uses SQLite transaction for atomic multi-insert
- **Hourly cleanup:** 24-hour retention cleanup runs once per hour (not per-update)

---

## Future Enhancements (Not in Phase 18)

- Geofencing alerts (dispatch notified when user enters/exits zone)
- Location history playback (dispatch can scrub timeline for user movement)
- Speed alerts (dispatch notified when user exceeds speed threshold)
- Battery-optimized update intervals (adaptive based on motion state)
- Location accuracy thresholds (server rejects low-accuracy updates)

---

**Document Version:** 1.0
**Phase:** 18 - Location Tracking
**Last Updated:** 2026-02-15
