# Stack Research - v4.0 Production Hardening + Dispatch Map View

**Domain:** Enterprise PTT Android App - Production Features + Interactive Dispatch Map
**Researched:** 2026-02-16 (Dispatch Map additions)
**Confidence:** HIGH for map libraries, MEDIUM for version numbers

## Executive Summary

This research covers stack for **two feature sets**:
1. **v4.0 Production Features** (from 2026-02-15): Location tracking, audio hardening, security
2. **NEW: Dispatch Map View** (2026-02-16): Leaflet-based real-time user tracking map in web UI

**Key Finding for Dispatch Map:** Leaflet + react-leaflet 4.x is the optimal choice. Project uses React 18.3.1, so react-leaflet 5.x (requires React 19) is incompatible. Esri World Imagery tiles work without API key. No new Android dependencies needed for battery telemetry (BatteryManager built-in).

---

## Dispatch Map View Stack (NEW - 2026-02-16)

### Core Technologies (Web UI)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **react-leaflet** | 4.2.1 | React bindings for Leaflet maps | Official React integration for Leaflet, supports React 18 (v5.x requires React 19). Provides declarative components (MapContainer, Marker, Popup) that fit React paradigm. Well-maintained with 7k+ GitHub stars. |
| **leaflet** | 1.9.4 | Core mapping library | Industry-standard open-source map library. Lightweight (42KB gzipped), mobile-friendly, extensive plugin ecosystem. Mature (v1.9.4 stable), no breaking changes expected. |
| **leaflet/dist/leaflet.css** | 1.9.4 | Leaflet base styles | REQUIRED stylesheet for all Leaflet maps. Include in index.html via CDN or import in root component. Controls default marker/popup/zoom control styling. |

### Tile Providers (Web UI)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Esri World Imagery (direct TileLayer)** | N/A | Satellite imagery tiles | Free for non-revenue apps with Esri developer account. No API key required for raster tiles. Use direct L.TileLayer URL, no plugin needed. |
| **esri-leaflet** (optional) | 3.0.19 | Esri ArcGIS service integration | OPTIONAL: Only if you later need Esri feature layers, geocoding, or vector basemaps. For just World Imagery, direct TileLayer is simpler. |

### Supporting Libraries (Web UI)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **@types/leaflet** | Latest | TypeScript definitions for Leaflet | OPTIONAL: Only if migrating web-ui to TypeScript. Currently JavaScript only. |
| **react-leaflet-div-icon** | Latest | Custom JSX markers as DivIcon | OPTIONAL: Only if markers need React state/lifecycle (live-updating UI in marker). For static custom icons, use Leaflet's native L.divIcon with SVG strings. |

### Android Battery Telemetry (No new dependencies)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **android.os.BatteryManager** | Built-in (API 21+) | Battery level monitoring | Built into Android SDK. Use `getSystemService(BATTERY_SERVICE)` then `getIntProperty(BATTERY_PROPERTY_CAPACITY)`. NO additional dependencies needed. Project minSdk=26. |

## Installation

### Web UI (web-ui/package.json)

```bash
# Core mapping libraries (install peer dependencies first)
npm install leaflet@1.9.4
npm install react-leaflet@4.2.1

# OPTIONAL: Esri plugin (only if you need feature layers/geocoding later)
# npm install esri-leaflet@3.0.19

# OPTIONAL: TypeScript support
# npm install -D @types/leaflet
```

### index.html (add Leaflet CSS)

```html
<!-- In web-ui/index.html <head> -->
<link
  rel="stylesheet"
  href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
  integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
  crossorigin=""
/>
```

### Android (No new dependencies)

Battery monitoring uses built-in Android APIs:
```kotlin
// In your LocationService or similar
val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
```

NO permissions required for `BatteryManager.getIntProperty()`.

## Alternatives Considered (Dispatch Map)

| Category | Recommended | Alternative | When to Use Alternative |
|----------|-------------|-------------|-------------------------|
| Map Library | **Leaflet** | Mapbox GL JS | Use Mapbox GL JS if you need 3D buildings, smooth zoom/rotation, or vector tile rendering with custom styles. Requires Mapbox API key (pay-per-request). More complex API than Leaflet. |
| Map Library | **Leaflet** | Google Maps | Use Google Maps if users expect Google Maps UI/branding or if using other Google services (Places API, Directions). Requires API key, commercial licensing fees for production. |
| Map Library | **Leaflet** | OpenLayers | Use OpenLayers if you need advanced GIS features (projections, complex spatial operations). Steeper learning curve, larger bundle size. |
| React Integration | **react-leaflet 4.x** | Direct Leaflet imperative API | Use direct Leaflet if you need fine-grained control over map lifecycle or integrating complex Leaflet plugins not wrapped by react-leaflet. Breaks React declarative paradigm. |
| Tile Provider | **Esri World Imagery** | OpenStreetMap tiles | Use OSM tiles if you need free street map tiles or want community-maintained data. NO satellite imagery. No attribution restrictions. |
| Tile Provider | **Esri World Imagery** | Mapbox Satellite | Use Mapbox Satellite if you need higher resolution imagery or more frequent updates. Requires Mapbox API key, pay-per-request pricing. |
| Custom Markers | **L.divIcon with SVG** | react-leaflet-div-icon | Use react-leaflet-div-icon if markers need React state/lifecycle (e.g., live-updating counters, animations). Adds complexity. For static custom icons, SVG strings in L.divIcon are simpler and consistent with existing @mdi/js icons. |

## What NOT to Use (Dispatch Map)

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **react-leaflet 5.x** | Requires React 19 (project uses React 18.3.1). Upgrading React 18→19 is major migration affecting entire web-ui. | **react-leaflet 4.2.1** (latest compatible with React 18) |
| **Leaflet.awesome-markers** | Depends on Font Awesome or Bootstrap icons (external dependencies). You already use @mdi/js for Material Design Icons. Icon library inconsistency adds bundle size. | **L.divIcon with inline SVG** from @mdi/js icons (consistent with existing UI) |
| **Continuous BatteryManager polling** | Official Android docs warn: "Monitoring battery level continuously drains more battery than normal app behavior." Defeats purpose of tracking. | **One-shot reads**: Capture battery level when sending location updates (already on a schedule), not on separate interval. |
| **esri-leaflet-vector** | Requires API key for vector basemaps. Adds ArcGIS authentication complexity. World Imagery raster tiles work without API key (free developer tier). | **Direct L.TileLayer** with Esri World Imagery URL (no plugin needed) |
| **google-map-react** | Wrong library (Google Maps, not Leaflet). Requires Google Maps API key. | **react-leaflet** |

## Stack Patterns by Variant (Dispatch Map)

### For Static Custom Markers (Recommended)

**Approach:** Use Leaflet's native `L.divIcon` with inline SVG strings from @mdi/js
```javascript
import { mdiAccountCircle } from '@mdi/js';

// Example: User marker with MDI icon
const userIcon = L.divIcon({
  html: `<svg viewBox="0 0 24 24" width="32" height="32">
           <path fill="#2196F3" d="${mdiAccountCircle}"/>
         </svg>`,
  className: 'user-marker',
  iconSize: [32, 32],
  iconAnchor: [16, 32], // Bottom center of icon
  popupAnchor: [0, -32]  // Above icon
});

<Marker position={[lat, lng]} icon={userIcon}>
  <Popup>
    <div>{username}</div>
    <div>Battery: {batteryLevel}%</div>
  </Popup>
</Marker>
```

**Why:**
- Consistent with existing @mdi/js icon usage in UI
- No additional dependencies
- Full CSS control via className
- Works with react-leaflet declarative components
- SVG scales for retina displays

### For Real-Time Marker Updates

**Approach:** Store marker data in React state, render Marker components from state
```javascript
import { MapContainer, TileLayer, Marker, Tooltip } from 'react-leaflet';

const [userPositions, setUserPositions] = useState({});

useEffect(() => {
  socket.on('LOCATION_BROADCAST', (data) => {
    setUserPositions(prev => ({
      ...prev,
      [data.userId]: {
        lat: data.lat,
        lng: data.lng,
        username: data.username,
        batteryLevel: data.batteryLevel,
        timestamp: data.timestamp
      }
    }));
  });

  return () => socket.off('LOCATION_BROADCAST');
}, []);

return (
  <MapContainer center={[0, 0]} zoom={13}>
    <TileLayer
      url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
      attribution='Tiles &copy; Esri'
      maxZoom={19}
    />
    {Object.entries(userPositions).map(([userId, pos]) => (
      <Marker key={userId} position={[pos.lat, pos.lng]} icon={getUserIcon(pos)}>
        <Tooltip permanent={false} direction="top">
          <div className="user-tooltip">
            <strong>{pos.username}</strong><br/>
            Battery: {pos.batteryLevel}%<br/>
            {new Date(pos.timestamp).toLocaleTimeString()}
          </div>
        </Tooltip>
      </Marker>
    ))}
  </MapContainer>
);
```

**Why:**
- React manages marker lifecycle automatically
- Tooltip (hover) vs Popup (click) built into react-leaflet
- Efficient re-renders (React tracks keys, only updates changed markers)
- WebSocket updates trigger state change → automatic re-render
- Tooltip permanent={false} = shows on hover only

### For Esri World Imagery Tiles

**Approach:** Use direct TileLayer (simplest, no plugin)

```javascript
import { TileLayer } from 'react-leaflet';

<TileLayer
  url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
  attribution='Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community'
  maxZoom={19}
/>
```

**Why direct TileLayer instead of esri-leaflet plugin:**
- Esri World Imagery raster tiles are public REST API (no auth needed)
- No plugin dependency (smaller bundle)
- Simpler code (declarative TileLayer vs imperative L.esri API)
- Only need plugin if using Esri feature layers, geocoding, or dynamic map services

**If you later need esri-leaflet:**
```javascript
import L from 'leaflet';
import 'esri-leaflet';

useEffect(() => {
  if (map) {
    L.esri.basemapLayer('Imagery').addTo(map);
    L.esri.basemapLayer('ImageryLabels').addTo(map); // Optional labels overlay
  }
}, [map]);
```

### Battery Level Collection (Android)

**Approach:** Read battery level in existing LocationService when preparing location updates

```kotlin
// In LocationService.kt (where location updates are sent)
private fun getBatteryLevel(): Int {
    val batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
}

private fun sendLocationUpdate(location: Location) {
    val batteryLevel = getBatteryLevel()

    webSocketManager.send(LocationUpdate(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracy = location.accuracy,
        timestamp = location.time,
        batteryLevel = batteryLevel  // NEW field
    ))
}
```

**Why:**
- No continuous monitoring (efficient, respects Android best practices)
- Battery level captured at same frequency as location (already optimized)
- No additional permissions required (BatteryManager.getIntProperty() is unrestricted)
- Fails gracefully if BatteryManager unavailable (returns -1)
- getSystemService() safe to call on main thread (cached system service)

---

## v4.0 Production Features Stack (From 2026-02-15)

### Location & Motion Detection

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| com.google.android.gms:play-services-location | 21.3.0 | FusedLocationProviderClient, GPS tracking | Industry standard, battery-efficient location API with adaptive throttling built-in |
| com.google.android.gms:play-services-location | 21.3.0 | ActivityRecognitionClient | Stationary/motion detection using device sensors, enables motion-aware location throttling |
| androidx.work:work-runtime-ktx | 2.10.1+ | Periodic background location updates | Battery-efficient periodic tasks (15min minimum interval), survives app restarts |

**Implementation Notes:**
- `FusedLocationProviderClient` already handles adaptive GPS/WiFi/cellular fusion
- `ActivityRecognitionClient` provides STILL, WALKING, IN_VEHICLE states for throttling logic
- WorkManager PeriodicWorkRequest has **15-minute minimum** interval (API limitation)
- For sub-15min updates, use FusedLocationProviderClient with LocationRequest intervals

### Audio Reliability & Debugging

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **NO NEW LIBRARIES** | - | Audio debugging | Use existing Android NBLOG APIs, AudioTrack.getUnderrunCount(), mediasoup stats |
| androidx.media3:media3-exoplayer | 1.5.0 (optional) | Advanced audio buffer management | Only if switching from direct AudioTrack, provides sophisticated jitter handling |

**Implementation Notes:**
- **Do NOT add new audio libraries** - work within existing libmediasoup-android 0.21.0
- Intermittent silence often caused by:
  - Network issues → TURN server required for mobile reliability
  - AEC ducking → adjust WebRTC audio processing settings in PeerConnectionFactory
  - Buffer underruns → increase AudioTrack buffer size, monitor with getUnderrunCount()
- Android 15 specific issue: audio capture stops after periods
- Use mediasoup Consumer stats for jitter monitoring (already available in 0.21.0)

### Security Audit & Testing

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| com.squareup.okhttp3:okhttp | 5.3.0 | TLS 1.3, CertificatePinner | Upgrade from 4.12.0 for improved TLS support, certificate pinning for MITM prevention |
| **Network Security Config** | Native (XML) | Certificate Transparency (Android 16+) | Native Android support for CT, no library needed |
| Nogotofail | Testing tool | Network security testing | Google's automated network security issue detection across all devices |

**Implementation Notes:**
- **Certificate pinning NOT recommended** for production Android apps - CA changes break app without updates
- Use **Network Security Config XML** instead for declarative TLS policies
- Certificate Transparency officially supported Android 16+
- For OkHttp CT enforcement on Android <16, use appmattus/certificatetransparency interceptor
- TLS 1.3 supported in OkHttp 5.x

### Runtime Permissions

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| com.google.accompanist:accompanist-permissions | 0.37.0 | Compose permissions handling | Still experimental but no official replacement, standard for Jetpack Compose permission flows |

**Implementation Notes:**
- Accompanist Permissions **not deprecated** - remains experimental
- Use `rememberPermissionState()` for reactive permission tracking in Compose
- Best practice: Request permissions **late in flow**
- Android blocks permission dialog after repeated denials - provide rationale UI
- For v4.0: Implement upfront educational UI + just-in-time re-prompts

### Power Optimization

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **NO NEW LIBRARIES** | - | Doze mode exemption | Foreground service already exempt from Doze mode |
| **PARTIAL_WAKE_LOCK** | Native | Audio processing during sleep | Already using wake locks, validate configuration |

**Implementation Notes:**
- Foreground services **exempt from Doze**
- Audio playback exempt from app standby
- WorkManager respects Doze constraints automatically
- Battery optimization: Use FusedLocationProviderClient PRIORITY_BALANCED_POWER_ACCURACY (not PRIORITY_HIGH_ACCURACY) for non-PTT location updates

## Full Installation (v4.0 + Dispatch Map)

### Web UI
```bash
cd web-ui

# NEW - Dispatch map
npm install leaflet@1.9.4
npm install react-leaflet@4.2.1

# Add Leaflet CSS to index.html (see above)
```

### Android
```gradle
// android/app/build.gradle.kts

dependencies {
    // EXISTING - keep versions
    implementation("io.github.crow-misia.libmediasoup-android:libmediasoup-android:0.21.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // UPGRADE to 5.3.0 recommended
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // NEW - Location & Motion (v4.0)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // NEW - Background Tasks (v4.0)
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // NEW - Permissions UI (v4.0)
    implementation("com.google.accompanist:accompanist-permissions:0.37.0")

    // OPTIONAL - Certificate Transparency for Android <16 (v4.0)
    implementation("com.github.appmattus.certificatetransparency:certificatetransparency-android:1.1.3")

    // NO NEW DEPENDENCIES for battery telemetry (built-in BatteryManager)
}
```

### Server (Protocol Extension)
```typescript
// Extend existing LocationUpdate interface
interface LocationUpdate {
  userId: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  timestamp: number;
  batteryLevel?: number;  // NEW: optional for backward compatibility
}
```

**Storage:**
- Add `batteryLevel INTEGER` column to existing location table (nullable)
- Update LOCATION_BROADCAST handler to include batteryLevel in payload

**No breaking changes:** batteryLevel is optional, backward compatible with existing Android clients.

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| react-leaflet@4.2.1 | react@18.x, leaflet@1.7.0+ | v4.x supports React 18. v5.x requires React 19 (breaking change). |
| leaflet@1.9.4 | All modern browsers, IE11 with polyfills | Stable API since 1.0. v1.9.4 is latest stable (2023). |
| esri-leaflet@3.0.19 | leaflet@1.x | Requires WebMercator projection (WKID 3857). Not compatible with other projections. |
| react-leaflet@4.x | React 18, React 19 | React 19 compatible despite peer dependency listing (tested by community). |
| OkHttp 5.3.0 | Retrofit 2.11.0 | Fully compatible |
| play-services-location 21.3.0 | compileSdk 35 | Requires Google Play Services on device |
| WorkManager 2.10.1 | Hilt 2.59.1 | Use hilt-work for DI integration |
| Accompanist 0.37.0 | Compose BOM 2026.01.00 | Experimental API, stable in practice |
| libmediasoup-android 0.21.0 | WebRTC M124 | Bundled, no separate WebRTC dependency needed |

**CRITICAL:** Do NOT upgrade to react-leaflet 5.x until upgrading React 18→19 (major migration affecting entire web-ui).

## Integration with Existing Stack

### Web UI Additions
Current: React 18.3.1, Vite 5.4.8, @mdi/js icons, custom CSS (no CSS frameworks)

**Fits seamlessly:**
- react-leaflet uses React 18 patterns (hooks, functional components)
- Leaflet CSS is standalone (no conflicts with custom CSS)
- Custom markers can use @mdi/js icons (consistency)
- Vite handles Leaflet's ES modules and CSS imports natively

**Build considerations:**
- Leaflet CSS must be loaded globally (index.html or root import)
- Leaflet's default marker icons use `require.resolve()` which Vite handles automatically
- No additional Vite config needed

### Server Changes (Dispatch Map)
Current: Node.js v24, TypeScript, WebSocket signaling, SQLite location storage

**Protocol extension:**
```typescript
// Extend existing LocationUpdate interface
interface LocationUpdate {
  userId: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  timestamp: number;
  batteryLevel?: number;  // NEW: optional for backward compatibility
}
```

**Storage:**
- Add `batteryLevel INTEGER` column to existing location table (nullable)
- Update LOCATION_BROADCAST handler to include batteryLevel in payload

**No breaking changes:** batteryLevel is optional, backward compatible with existing Android clients.

### Android Changes (Dispatch Map)
Current: Kotlin, Jetpack Compose, Hilt DI, play-services-location 21.3.0

**No new dependencies needed:**
- BatteryManager is Android SDK built-in (API 21+, project minSdk=26)
- Integrate into existing LocationService (already has context access)

**Hilt injection pattern:**
```kotlin
// LocationService already @Inject constructor-injected, just add:
private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
```

## Upgrade Recommendations

| Current | Upgrade To | Priority | Reason |
|---------|------------|----------|--------|
| OkHttp 4.12.0 | OkHttp 5.3.0 | MEDIUM | TLS 1.3 support, separate Android optimizations, DNS over HTTPS stable |
| Retrofit 2.11.0 | Keep | N/A | Current version compatible with OkHttp 5.x |
| Gson 2.11.0 | Moshi 1.15.x | LOW | Better Kotlin null safety, but Gson works fine |

**OkHttp 5.0 Migration Notes:**
- Separate JVM/Android artifacts for platform optimizations
- MockWebServer moved to new coordinate (testing only)
- DNS over HTTPS now stable (not experimental)

## Sources

### HIGH Confidence (Official Documentation)
- [React Leaflet Official Docs](https://react-leaflet.js.org/docs/start-installation/) — Installation requirements, React 18 compatibility verified
- [Leaflet Download Page](https://leafletjs.com/download.html) — CDN URLs for Leaflet CSS v1.9.4
- [Leaflet Custom Icons Guide](https://leafletjs.com/examples/custom-icons/) — Official L.divIcon documentation
- [Android BatteryManager API Reference](https://developer.android.com/reference/kotlin/android/os/BatteryManager) — Official Android documentation for battery monitoring
- [Android Battery Monitoring Guide](https://developer.android.com/training/monitoring-device-state/battery-monitoring) — Official best practices, no continuous monitoring warning
- [Esri Leaflet Developer Docs](https://developers.arcgis.com/esri-leaflet/) — Installation and basemap layer usage
- [FusedLocationProviderClient API](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient) - Google Play Services
- [Activity Recognition API](https://developers.google.com/location-context/activity-recognition) - Google official
- [Android Doze Mode](https://developer.android.com/training/monitoring-device-state/doze-standby) - Android official
- [Request Runtime Permissions](https://developer.android.com/training/permissions/requesting) - Android official
- [WorkManager Releases](https://developer.android.com/jetpack/androidx/releases/work) - AndroidX official
- [OkHttp Changelog](https://square.github.io/okhttp/changelogs/changelog/) - Square official
- [Network Security Config](https://developer.android.com/privacy-and-security/security-config) - Android official

### MEDIUM Confidence (WebSearch Verified)
- [react-leaflet npm](https://www.npmjs.com/package/react-leaflet) — Version 5.0.0 requires React 19 (WebSearch verified)
- [esri-leaflet npm](https://www.npmjs.com/package/esri-leaflet) — Version 3.0.19 published 5 months ago (WebSearch)
- [GitHub: react-leaflet releases](https://github.com/PaulLeCam/react-leaflet/releases) — React 18 compatibility in v4.x
- [Leaflet CDN Options](https://cdnjs.com/libraries/leaflet) — unpkg, cdnjs, jsDelivr CDN URLs
- [Crystal Clear Certificates - Certificate Transparency](https://www.spght.dev/articles/21-04-2025/crystal-clear-certs) - Android GDE article
- [OkHttp 5.0 Migration Guide](https://medium.com/@hiren6997/okhttp-5-0-what-changed-and-how-to-upgrade-without-breaking-everything-1e2dfb255848) - Community guide
- [Accompanist Permissions](https://google.github.io/accompanist/permissions/) - Google experimental library
- [Advanced Location Tracking Battery Efficiency](https://www.oneclickitsolution.com/centerofexcellence/android/advanced-location-tracking-with-battery-efficiency-in-android-app) - Implementation patterns

### LOW Confidence (Community Sources)
- [GitHub: react-leaflet-div-icon](https://github.com/jgimbel/react-leaflet-div-icon) — Community plugin for JSX markers (optional)
- [Medium: Creating dynamic JSX markers](https://medium.com/@nikjohn/creating-a-dynamic-jsx-marker-with-react-leaflet-f75fff2ddb9) — Alternative pattern examples
- [Gist: Leaflet with Esri World Imagery](https://gist.github.com/d3noob/8663620) — Community example of direct TileLayer usage
- [Why WebRTC Calls Fail on Mobile Data](https://www.softpagecms.com/2026/01/06/why-webrtc-calls-fail-mobile-data-fix-2026/) - Issue diagnosis, validate with testing
- [Flutter WebRTC Android 15 Issue](https://github.com/flutter-webrtc/flutter-webrtc/issues/1759) - Unresolved bug report
- [Audio Debugging AOSP](https://source.android.com/docs/core/audio/debugging) - AOSP docs (device-specific)

---

*Stack research for: VoicePing Router v4.0 + Dispatch Map View*
*Researched: 2026-02-16 (Dispatch Map additions)*
*Confidence: HIGH for core libraries (official docs), MEDIUM for version numbers (WebSearch only, npm blocked), HIGH for Android APIs (official docs)*
