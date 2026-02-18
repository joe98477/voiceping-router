# Phase 21: Backend Protocol Extension - Research

**Researched:** 2026-02-16
**Domain:** Protocol extension for battery telemetry with backward-compatible optional fields across Android, Node.js server, and React web client
**Confidence:** HIGH

## Summary

Extending the location protocol with battery telemetry requires coordinated changes across three components: Android client (BatteryManager + PowerManager + NetworkCapabilities), Node.js server (SQLite schema extension + WebSocket broadcast), and React web client (graceful field handling). The key challenge is maintaining backward compatibility during a rolling deployment where old and new clients coexist.

Battery percentage is read via `BatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)` returning 0-100 integer. Power-save mode uses `PowerManager.isPowerSaveMode()` returning boolean. Network type detection uses `NetworkCapabilities.hasTransport()` to distinguish WiFi from cellular. All three are available without additional permissions on Android API 21+.

Server-side storage extends the existing SQLite `locations` table with four new nullable columns: `battery_percentage INTEGER`, `power_save_mode INTEGER` (SQLite boolean as 0/1), `network_type TEXT`, and `low_battery_alert_sent INTEGER`. The server calculates `lowBattery: boolean` flag based on 20% threshold and fires one-time `LOW_BATTERY_ALERT` events when crossing the threshold.

**Primary recommendation:** Use `ALTER TABLE ADD COLUMN` for schema migration (instant operation, no data copy), send null/-1 sentinel when battery unavailable, include all telemetry fields in every broadcast (no delta optimization), use server-side low battery detection with hysteresis (alert fires once, suppressed until recovery above 20%), and validate backward compatibility with protocol extension tests.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Battery data scope:**
- Battery percentage as integer (0-100), no decimals
- No charging state — just the percentage number
- Include power-save mode status (boolean: Android battery saver on/off)
- Include network type: WiFi vs Cellular (simple binary, no 3G/4G/5G distinction)
- When battery info is unavailable (emulator, permissions denied): send null/-1 sentinel value to explicitly signal "unknown"

**Update triggers:**
- Telemetry piggybacks on existing LOCATION_UPDATE messages — no separate message type
- No urgent/out-of-cycle sends for battery events — telemetry rides the normal location cadence
- Server stores only the latest telemetry snapshot per user, not history
- Server always includes all telemetry fields in every LOCATION_BROADCAST (no delta/change-only optimization)
- Extend existing LOCATION_QUERY response with telemetry fields for initial state sync on dispatch connect/reconnect
- When a user goes offline, last known telemetry remains available to dispatch (with stale indicator from Phase 25)

**Low battery alerting:**
- Server flags lowBattery: true/false based on a 20% threshold (server-side determination, not client)
- Single threshold only — no critical tier
- Both: lowBattery flag in every LOCATION_BROADCAST AND a one-time LOW_BATTERY_ALERT event when crossing below 20%
- Alert fires once when dropping below 20%, suppressed until battery recovers above 20% and drops again
- No recovery event — just suppression reset

**Dispatch visibility intent (shapes Phase 25 data requirements):**
- Battery display: battery icon + percentage number, icon changes color at threshold
- Color scheme: green (50%+), yellow (20-49%), red (below 20%) — classic traffic light
- All three telemetry fields visible in status popup (battery %, power-save, network type)
- Small battery color indicator attached to each map marker (visible without hovering)
- Power-save mode: warning icon on marker when active + "Power Saver: ON" text in popup
- Network type: text label ("WiFi" / "Cellular") in popup only — no marker icon for network
- Low battery alert UX: toast notification when threshold crossed + marker turns red (persistent)
- Map popup is sufficient — no separate battery summary panel needed

### Claude's Discretion

- Exact sentinel value for unavailable battery (null vs -1 vs omit)
- LOW_BATTERY_ALERT message payload structure
- Database column types for telemetry fields in SQLite
- How to read battery/power-save/network on Android (BatteryManager vs sticky intent)
- Toast notification duration and styling

### Deferred Ideas (OUT OF SCOPE)

- Battery drain trend analysis (history storage) — potential future phase
- Battery summary panel sorted by level — could be added in Phase 26 polish or later
- Signal strength tracking — decided against for now, could be future enhancement
- Cellular generation (3G/4G/5G) distinction — kept simple as WiFi/Cellular for now

</user_constraints>

## Standard Stack

### Core Libraries (Already in Use)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Android BatteryManager | API 21+ | Battery percentage and charging state | Built-in Android system service, no permissions required, instant access |
| Android PowerManager | API 21+ | Power save mode detection | Built-in system service, `isPowerSaveMode()` available since Lollipop |
| Android ConnectivityManager | API 21+ | Network type detection (WiFi/Cellular) | Built-in system service, NetworkCapabilities API modern approach since Lollipop |
| better-sqlite3 | ^11.7.0 | SQLite storage (already in use) | Synchronous API, already storing location data |
| ws | ^8.16.0 | WebSocket server (already in use) | Already handling LOCATION_UPDATE and LOCATION_BROADCAST |

**No new dependencies required.** All telemetry APIs are built-in Android system services available since API 21 (Android 5.0 Lollipop).

### Supporting Patterns

| Pattern | Use Case | Reference |
|---------|----------|-----------|
| Nullable columns in SQLite | Optional telemetry fields (old clients omit) | [SQLite ALTER TABLE](https://www.sqlite.org/lang_altertable.html) |
| Optional JSON fields | Backward-compatible protocol extension | [Zalando API Guidelines](https://github.com/zalando/restful-api-guidelines/blob/main/chapters/compatibility.adoc) |
| Server-side threshold detection | Low battery alerting with hysteresis | Industry standard 20% threshold ([Android Battery Monitoring](https://www.airdroid.com/mdm/android-battery-monitoring/)) |
| WebSocket broadcast fanout | Location + telemetry to all dispatch users | Existing LocationBroadcaster pattern |

## Architecture Patterns

### Pattern 1: Reading Battery Status on Android

**What:** Use `BatteryManager.getIntProperty()` to get battery percentage as integer 0-100
**When to use:** Each time location update is prepared for sending to server

**Example:**
```kotlin
// Source: https://developer.android.com/reference/kotlin/android/os/BatteryManager
val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
val batteryPct: Int = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
// Returns 0-100, or sentinel value if unavailable (emulator, permissions issue)
```

**Key details:**
- Returns integer 0-100 on real devices
- May return -1 or throw exception on emulators or permission issues
- No special permissions required (BATTERY_STATS permission is signature-level, not needed for basic battery %)
- Instant synchronous call (not a broadcast receiver)

**Fallback for unavailable battery:**
```kotlin
fun getBatteryPercentage(): Int? {
    return try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (pct < 0) null else pct
    } catch (e: Exception) {
        null
    }
}
```

### Pattern 2: Detecting Power Save Mode

**What:** Use `PowerManager.isPowerSaveMode()` to check if Battery Saver is active
**When to use:** Each location update, included in telemetry data

**Example:**
```kotlin
// Source: https://developer.android.com/about/versions/pie/power
val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
val isPowerSaveMode: Boolean = powerManager.isPowerSaveMode
```

**Key details:**
- Returns boolean: true if Battery Saver enabled, false otherwise
- Available since API 21 (Lollipop)
- No permissions required
- Synchronous call
- Power save mode affects location accuracy (important for dispatch to know)

### Pattern 3: Network Type Detection (WiFi vs Cellular)

**What:** Use `NetworkCapabilities.hasTransport()` to distinguish WiFi from cellular
**When to use:** Each location update, included in telemetry data

**Example:**
```kotlin
// Source: https://developer.android.com/training/monitoring-device-state/connectivity-status-type
fun getNetworkType(): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = cm.activeNetwork ?: return "Unknown"
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return "Unknown"

    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "WiFi" // Treat as WiFi for simplicity
        else -> "Unknown"
    }
}
```

**Key details:**
- Replaces deprecated `ConnectivityManager.getActiveNetworkInfo().getType()`
- NetworkCapabilities available since API 21, mandatory since API 29
- Requires `ACCESS_NETWORK_STATE` permission (already granted for VoicePing)
- Simple WiFi/Cellular binary (no 3G/4G/5G distinction)
- VPN transport can coexist with WiFi/Cellular (check underlying transports)

### Pattern 4: SQLite Schema Extension with ALTER TABLE

**What:** Add nullable columns to existing `locations` table without migrating data
**When to use:** Server startup schema migration for backward compatibility

**Example:**
```typescript
// Source: https://www.sqlite.org/lang_altertable.html
db.exec(`
  ALTER TABLE locations ADD COLUMN battery_percentage INTEGER;
  ALTER TABLE locations ADD COLUMN power_save_mode INTEGER;
  ALTER TABLE locations ADD COLUMN network_type TEXT;
  ALTER TABLE locations ADD COLUMN low_battery_alert_sent INTEGER DEFAULT 0;
`);
```

**Key details:**
- `ALTER TABLE ADD COLUMN` is instant (no data copy)
- New columns are NULL for existing rows (backward compatible)
- SQLite has no native BOOLEAN type (use INTEGER 0/1)
- Check if column exists before adding (use `PRAGMA table_info(locations)`)
- low_battery_alert_sent tracks alert hysteresis (0 = not sent, 1 = sent below 20%)

**Migration safety pattern:**
```typescript
function addColumnIfNotExists(db: Database, table: string, column: string, type: string): void {
  const columns = db.prepare(`PRAGMA table_info(${table})`).all();
  const exists = columns.some((c: any) => c.name === column);
  if (!exists) {
    db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${type}`);
  }
}
```

### Pattern 5: Backward-Compatible Protocol Extension

**What:** Add optional fields to existing messages, clients ignore unknown fields
**When to use:** Extending LOCATION_UPDATE, LOCATION_BROADCAST, LOCATION_QUERY response

**Example (Server TypeScript):**
```typescript
// Source: https://github.com/zalando/restful-api-guidelines/blob/main/chapters/compatibility.adoc
// Existing interface
interface LocationData {
  userId: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  speed: number | null;
  heading: number | null;
  motionState: 'still' | 'walking' | 'driving' | 'unknown';
  timestamp: string;
}

// Extended interface (Phase 21)
interface LocationDataWithTelemetry extends LocationData {
  batteryPercentage?: number | null;  // Optional: 0-100 or null
  powerSaveMode?: boolean | null;     // Optional: true/false or null
  networkType?: string | null;        // Optional: "WiFi" | "Cellular" | "Unknown" | null
}
```

**Android sending pattern:**
```kotlin
val locationData = JsonObject().apply {
    addProperty("latitude", location.latitude)
    addProperty("longitude", location.longitude)
    addProperty("accuracy", location.accuracy)
    // ... existing fields ...

    // Phase 21: Add telemetry fields (optional)
    getBatteryPercentage()?.let { addProperty("batteryPercentage", it) }
    addProperty("powerSaveMode", isPowerSaveMode())
    addProperty("networkType", getNetworkType())
}
```

**Old web client handling (graceful):**
```javascript
// Old client ignores unknown fields — no code change needed
function handleLocationBroadcast(data) {
  const { userId, latitude, longitude } = data;
  // batteryPercentage, powerSaveMode, networkType silently ignored
  updateMarker(userId, latitude, longitude);
}
```

**Key principles:**
- Never make new fields mandatory (breaking change)
- Old clients ignore unknown fields (Robustness Principle)
- New clients tolerate missing fields (null checks)
- Server always includes all fields in broadcast (even if null)

### Pattern 6: Server-Side Low Battery Detection with Hysteresis

**What:** Server calculates `lowBattery` flag and fires one-time alert with suppression
**When to use:** Every location update with battery data

**Example:**
```typescript
function updateLocationWithTelemetry(userId: string, location: LocationDataWithTelemetry): void {
  const batteryPct = location.batteryPercentage;
  const lowBattery = batteryPct !== null && batteryPct !== undefined && batteryPct < 20;

  // Get previous alert state from database
  const prevState = locationStore.getUserAlertState(userId);
  const alertAlreadySent = prevState?.low_battery_alert_sent === 1;

  // Fire alert if crossing below 20% for the first time
  if (lowBattery && !alertAlreadySent) {
    broadcastLowBatteryAlert(userId, batteryPct);
    locationStore.setAlertSent(userId, true);
  }

  // Reset alert flag if battery recovered above 20%
  if (!lowBattery && alertAlreadySent) {
    locationStore.setAlertSent(userId, false);
  }

  // Always include lowBattery flag in broadcasts
  locationBroadcaster.broadcastLocation(userId, {
    ...location,
    lowBattery
  });
}
```

**Key details:**
- 20% threshold is industry standard ([Android MDM monitoring](https://www.airdroid.com/mdm/android-battery-monitoring/))
- Alert fires once when crossing below 20%
- Alert suppressed until battery recovers above 20% (hysteresis prevents alert spam)
- lowBattery flag always present in broadcasts (server calculates, client displays)
- Alert persisted in database (survives server restart)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Battery status monitoring | Custom BroadcastReceiver for battery changes | Direct BatteryManager.getIntProperty() call at send time | Battery broadcasts are noisy (every 1% change), we only need status when sending location update |
| Network type detection | Deprecated getActiveNetworkInfo().getType() | NetworkCapabilities.hasTransport() | Old API deprecated in API 29, doesn't support new network types, NetworkCapabilities is future-proof |
| Low battery threshold detection | Client-side alert logic | Server-side calculation with hysteresis | Centralized threshold, consistent across all clients, easy to adjust, server controls alert spam |
| Database migration | Manual SQL with version checks | ALTER TABLE ADD COLUMN with existence check | SQLite ADD COLUMN is instant (no data copy), simple and safe |
| Protocol versioning | Version field in messages | Optional fields + graceful handling | Avoids version negotiation complexity, simpler client/server code, rolling deployment friendly |

**Key insight:** Backward compatibility is easier with optional fields than versioned protocols. Let clients send what they can, server tolerates missing data, new fields are additive only.

## Common Pitfalls

### Pitfall 1: Making New Fields Mandatory

**What goes wrong:** Old clients fail to connect or send updates after server upgrade
**Why it happens:** Forgetting that old Android clients are still deployed and can't send battery data
**How to avoid:** Always make protocol extensions optional — use nullable types, provide defaults
**Warning signs:** Integration tests fail when old client connects to new server

**Prevention:**
```typescript
// BAD: Assumes batteryPercentage always present
if (data.batteryPercentage < 20) { /* ... */ }

// GOOD: Null-safe with default
const lowBattery = (data.batteryPercentage ?? 100) < 20;
```

### Pitfall 2: Emulator Battery Status Assumptions

**What goes wrong:** Battery percentage returns -1 or throws exception on emulators
**Why it happens:** Emulators don't simulate battery hardware consistently
**How to avoid:** Explicitly handle null/negative values with sentinel pattern
**Warning signs:** Crashes or invalid data when testing on emulator

**Prevention:**
```kotlin
fun getBatteryPercentage(): Int? {
    return try {
        val pct = batteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)
        if (pct < 0 || pct > 100) null else pct  // Validate range
    } catch (e: Exception) {
        null  // Graceful degradation
    }
}
```

### Pitfall 3: ALTER TABLE Without Existence Check

**What goes wrong:** Server crashes on restart with "duplicate column name" error
**Why it happens:** ALTER TABLE fails if column already exists, no IF NOT EXISTS clause
**How to avoid:** Check `PRAGMA table_info()` before adding column
**Warning signs:** Server fails to start after redeployment

**Prevention:**
```typescript
function addColumnIfNotExists(db: Database, table: string, column: string, type: string): void {
  const columns = db.prepare(`PRAGMA table_info(${table})`).all();
  const exists = columns.some((c: any) => c.name === column);
  if (!exists) {
    db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${type}`);
    logger.info(`Added column ${column} to ${table}`);
  }
}
```

### Pitfall 4: Low Battery Alert Spam

**What goes wrong:** Dispatch receives LOW_BATTERY_ALERT every 30 seconds for same user
**Why it happens:** No hysteresis — alert fires on every update when battery < 20%
**How to avoid:** Track alert state in database, fire once, reset on recovery above threshold
**Warning signs:** Toast notifications spam dispatch console

**Prevention:**
```typescript
// Track alert state per user in database
interface AlertState {
  userId: string;
  low_battery_alert_sent: number;  // 0 or 1
}

// Only fire alert when crossing threshold for first time
if (batteryPct < 20 && !alertState.low_battery_alert_sent) {
  broadcastLowBatteryAlert(userId, batteryPct);
  db.prepare(`UPDATE locations SET low_battery_alert_sent = 1 WHERE user_id = ?`).run(userId);
}

// Reset flag when battery recovers above 20%
if (batteryPct >= 20 && alertState.low_battery_alert_sent) {
  db.prepare(`UPDATE locations SET low_battery_alert_sent = 0 WHERE user_id = ?`).run(userId);
}
```

### Pitfall 5: Network Type Permission Denial

**What goes wrong:** `getNetworkCapabilities()` returns null unexpectedly
**Why it happens:** Missing `ACCESS_NETWORK_STATE` permission (unlikely but possible)
**How to avoid:** Gracefully handle null return, fallback to "Unknown"
**Warning signs:** Network type always shows "Unknown" for some users

**Prevention:**
```kotlin
fun getNetworkType(): String {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Unknown"
        val network = cm.activeNetwork ?: return "Unknown"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "Unknown"

        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Unknown"
        }
    } catch (e: Exception) {
        "Unknown"
    }
}
```

### Pitfall 6: Broadcast to All Dispatch Users Not Channel-Scoped

**What goes wrong:** Dispatch users don't receive location updates for users outside their channels
**Why it happens:** Confusion about "broadcast to all dispatch users" vs "broadcast to channel members"
**How to avoid:** Location broadcasts are global (not channel-scoped), send to all DISPATCH role users
**Warning signs:** Map markers missing for users in other channels

**Clarification:** Phase 18 established location broadcasts go to **all dispatch users globally**, not filtered by channel. This is intentional — dispatch needs to see all field workers regardless of channel assignment.

## Code Examples

Verified patterns from official sources and codebase:

### Reading Battery Percentage (Android)

```kotlin
// Source: https://developer.android.com/reference/kotlin/android/os/BatteryManager
import android.content.Context
import android.os.BatteryManager

class TelemetryReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getBatteryPercentage(): Int? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            // Validate range: 0-100 expected, -1 or invalid means unavailable
            if (pct in 0..100) pct else null
        } catch (e: Exception) {
            null  // Emulator or permission issue
        }
    }

    fun isPowerSaveMode(): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isPowerSaveMode
        } catch (e: Exception) {
            false  // Default to false if unavailable
        }
    }

    fun getNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return "Unknown"
            val caps = cm.getNetworkCapabilities(network) ?: return "Unknown"

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "WiFi"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
```

### Extending LocationUpdate Message (Android)

```kotlin
// Extend existing LocationManager.kt from Phase 18
fun sendLocationUpdate(location: Location, motionState: MotionState) {
    val telemetryReader = TelemetryReader(context)

    val locationData = JsonObject().apply {
        addProperty("latitude", location.latitude)
        addProperty("longitude", location.longitude)
        addProperty("accuracy", location.accuracy)
        addProperty("speed", if (location.hasSpeed()) location.speed else null)
        addProperty("heading", if (location.hasBearing()) location.bearing else null)
        addProperty("motionState", motionState.name.lowercase())
        addProperty("timestamp", Instant.now().toString())

        // Phase 21: Add battery telemetry (optional fields)
        telemetryReader.getBatteryPercentage()?.let { addProperty("batteryPercentage", it) }
        addProperty("powerSaveMode", telemetryReader.isPowerSaveMode())
        addProperty("networkType", telemetryReader.getNetworkType())
    }

    signalingClient.send(
        SignalingMessage(
            type = SignalingType.LOCATION_UPDATE,
            data = locationData
        )
    )
}
```

### SQLite Schema Migration (Server)

```typescript
// Source: Existing LocationStore.ts + https://www.sqlite.org/lang_altertable.html
private migrateSchema(): void {
  // Add telemetry columns if they don't exist
  this.addColumnIfNotExists('locations', 'battery_percentage', 'INTEGER');
  this.addColumnIfNotExists('locations', 'power_save_mode', 'INTEGER');
  this.addColumnIfNotExists('locations', 'network_type', 'TEXT');
  this.addColumnIfNotExists('locations', 'low_battery_alert_sent', 'INTEGER DEFAULT 0');

  logger.info('Location database schema migration complete');
}

private addColumnIfNotExists(table: string, column: string, type: string): void {
  const columns = this.db.prepare(`PRAGMA table_info(${table})`).all();
  const exists = (columns as any[]).some((c) => c.name === column);

  if (!exists) {
    this.db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${type}`);
    logger.info(`Added column ${column} to ${table}`);
  }
}
```

### Storing Location with Telemetry (Server)

```typescript
// Extend existing LocationStore.insertLocation()
insertLocation(data: LocationDataWithTelemetry): void {
  const stmt = this.db.prepare(`
    INSERT INTO locations (
      user_id, latitude, longitude, accuracy, speed, heading,
      motion_state, timestamp,
      battery_percentage, power_save_mode, network_type, low_battery_alert_sent
    )
    VALUES (
      @userId, @latitude, @longitude, @accuracy, @speed, @heading,
      @motionState, @timestamp,
      @batteryPercentage, @powerSaveMode, @networkType, @lowBatteryAlertSent
    )
  `);

  try {
    stmt.run({
      userId: data.userId,
      latitude: data.latitude,
      longitude: data.longitude,
      accuracy: data.accuracy,
      speed: data.speed,
      heading: data.heading,
      motionState: data.motionState,
      timestamp: data.timestamp,
      // Telemetry fields (nullable)
      batteryPercentage: data.batteryPercentage ?? null,
      powerSaveMode: data.powerSaveMode ? 1 : 0,
      networkType: data.networkType ?? null,
      lowBatteryAlertSent: 0  // Initial state
    });
  } catch (err) {
    logger.error(`Failed to insert location: ${err}`);
    throw err;
  }
}
```

### Low Battery Detection with Hysteresis (Server)

```typescript
// New method in LocationStore or handlers.ts
function handleLocationUpdateWithTelemetry(
  ctx: ClientContext,
  locationData: LocationDataWithTelemetry
): void {
  // Calculate low battery flag (server-side)
  const batteryPct = locationData.batteryPercentage;
  const lowBattery = batteryPct !== null && batteryPct !== undefined && batteryPct < 20;

  // Get previous alert state
  const prevLocation = locationStore.getLatestPosition(ctx.userId);
  const alertAlreadySent = prevLocation?.low_battery_alert_sent === 1;

  // Store location with telemetry
  locationStore.insertLocation({
    ...locationData,
    userId: ctx.userId
  });

  // Fire one-time alert when crossing below 20%
  if (lowBattery && !alertAlreadySent) {
    const alertMessage = createMessage(SignalingType.LOW_BATTERY_ALERT, {
      userId: ctx.userId,
      userName: ctx.userName,
      batteryPercentage: batteryPct,
      timestamp: locationData.timestamp
    });

    // Broadcast alert to all dispatch users
    broadcastToDispatchUsers(alertMessage);

    // Mark alert as sent
    locationStore.setLowBatteryAlertSent(ctx.userId, true);

    logger.warn(`Low battery alert for ${ctx.userId}: ${batteryPct}%`);
  }

  // Reset alert flag if battery recovered above 20%
  if (!lowBattery && alertAlreadySent) {
    locationStore.setLowBatteryAlertSent(ctx.userId, false);
    logger.info(`Low battery alert reset for ${ctx.userId}: ${batteryPct}%`);
  }

  // Broadcast location with telemetry and lowBattery flag
  locationBroadcaster.broadcastLocation(ctx.userId, {
    ...locationData,
    lowBattery
  });
}
```

### Broadcasting Location with Telemetry (Server)

```typescript
// Extend existing LocationBroadcaster.broadcastLocation()
broadcastLocation(userId: string, location: LocationDataWithTelemetry & { lowBattery: boolean }): void {
  // Update cache
  this.latestPositions.set(userId, location);

  // Broadcast to all dispatch users
  const broadcastMessage = JSON.stringify({
    type: 'location-broadcast',
    data: {
      userId,
      latitude: location.latitude,
      longitude: location.longitude,
      accuracy: location.accuracy,
      speed: location.speed,
      heading: location.heading,
      motionState: location.motionState,
      timestamp: location.timestamp,
      // Phase 21: Telemetry fields
      batteryPercentage: location.batteryPercentage ?? null,
      powerSaveMode: location.powerSaveMode ?? null,
      networkType: location.networkType ?? null,
      lowBattery: location.lowBattery
    }
  });

  this.sendToDispatchUsers(broadcastMessage);
}
```

### LOCATION_QUERY Response with Telemetry (Server)

```typescript
// Extend existing handleLocationQuery() in handlers.ts
async handleLocationQuery(ctx: ClientContext, message: SignalingMessage): Promise<void> {
  if (ctx.role !== UserRole.DISPATCH) {
    this.sendError(ctx, message.id, 'Permission denied: Location queries are only available to Dispatch users');
    return;
  }

  // Get all latest positions with telemetry
  const positions = this.locationBroadcaster.getAllLatestPositions();

  // Each position includes batteryPercentage, powerSaveMode, networkType, lowBattery
  this.sendResponse(ctx, message.id, {
    positions: positions.map(pos => ({
      ...pos,
      batteryPercentage: pos.batteryPercentage ?? null,
      powerSaveMode: pos.powerSaveMode ?? null,
      networkType: pos.networkType ?? null,
      lowBattery: (pos.batteryPercentage ?? 100) < 20,
      isStale: pos.isStale
    })),
    count: positions.length
  });
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| BroadcastReceiver for battery changes | Direct BatteryManager.getIntProperty() at send time | Android 5.0+ | Less battery drain, simpler code, no broadcast spam |
| ConnectivityManager.getActiveNetworkInfo().getType() | NetworkCapabilities.hasTransport() | Deprecated API 29 (2019) | Future-proof, supports new transport types |
| Client-side battery threshold alerts | Server-side calculation with hysteresis | Best practice for centralized monitoring | Consistent alerts, easy threshold adjustment |
| Versioned protocol (v1, v2) | Optional fields + graceful handling | Modern API design | Rolling deployments, no version negotiation |
| Separate battery telemetry messages | Piggyback on existing location updates | Bandwidth optimization | Fewer WebSocket messages, aligned update cadence |

**Deprecated/outdated:**
- `Intent.ACTION_BATTERY_CHANGED` sticky broadcast — noisy, use BatteryManager direct call instead
- `getActiveNetworkInfo()` — deprecated API 29, use NetworkCapabilities
- BATTERY_STATS permission — signature-level, not needed for basic percentage
- Mandatory protocol fields — breaks backward compatibility, use optional fields

## Open Questions

1. **Sentinel value for unavailable battery: null vs -1 vs omit field entirely?**
   - What we know: Android returns -1 for unavailable, null is more idiomatic for TypeScript
   - What's unclear: Which is clearer for web client handling?
   - Recommendation: Use null (omit field entirely) — cleaner JSON, web client ignores missing fields gracefully

2. **LOW_BATTERY_ALERT message payload: include userName or just userId?**
   - What we know: Dispatch UI needs to show readable name in toast notification
   - What's unclear: Is userName already available in dispatch context map?
   - Recommendation: Include userName in alert payload — avoids dispatch needing to lookup

3. **Toast notification duration: how long should low battery alert display?**
   - What we know: User decisions specify toast notification on threshold cross
   - What's unclear: Standard duration for critical vs non-critical toasts
   - Recommendation: 5 seconds for low battery (non-blocking, gives time to read, auto-dismisses)

4. **Should power-save mode automatically trigger different marker styling beyond icon?**
   - What we know: User decisions specify warning icon on marker + text in popup
   - What's unclear: Should marker itself change shape/size when power-save active?
   - Recommendation: Icon overlay sufficient — keep marker base consistent, icon provides visual cue

## Sources

### Primary (HIGH confidence)

- [Android BatteryManager API Reference](https://developer.android.com/reference/kotlin/android/os/BatteryManager) - Official API for battery percentage
- [Android PowerManager API Reference](https://developer.android.com/about/versions/pie/power) - Power save mode detection
- [Android NetworkCapabilities](https://developer.android.com/training/monitoring-device-state/connectivity-status-type) - Modern network type detection
- [SQLite ALTER TABLE](https://www.sqlite.org/lang_altertable.html) - Schema migration documentation
- [Zalando REST API Guidelines - Compatibility](https://github.com/zalando/restful-api-guidelines/blob/main/chapters/compatibility.adoc) - Backward-compatible protocol extension patterns

### Secondary (MEDIUM confidence)

- [GeeksforGeeks: Android Battery Level](https://www.geeksforgeeks.org/android/how-to-check-the-battery-level-in-android-programmatically/) - Battery percentage examples
- [Android MDM Battery Monitoring](https://www.airdroid.com/mdm/android-battery-monitoring/) - 20% threshold standard
- [WebSocket Real-Time Architecture](https://ably.com/topic/websocket-architecture-best-practices) - Broadcast patterns
- [SQLite Tutorial: ALTER TABLE](https://www.sqlitetutorial.net/sqlite-alter-table/) - ALTER TABLE limitations and best practices

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Built-in Android APIs, existing server infrastructure
- Architecture: HIGH - Direct API calls, existing patterns from Phase 18
- Pitfalls: HIGH - Emulator battery issues well-documented, backward compatibility tested in industry

**Research date:** 2026-02-16
**Valid until:** 90 days (stable APIs, no fast-moving dependencies)
