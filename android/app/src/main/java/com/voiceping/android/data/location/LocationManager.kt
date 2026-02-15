package com.voiceping.android.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Priority
import com.google.gson.JsonObject
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.network.dto.SignalingType
import com.voiceping.android.domain.model.ConnectionState
import com.voiceping.android.domain.model.MotionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinator singleton managing location tracking and motion detection.
 *
 * Features:
 * - Adaptive intervals based on motion state (STILL=5min, WALKING=60s, DRIVING=30s)
 * - Battery adaptation (< 20% → LOW_POWER + 5min interval)
 * - 50m deduplication (skip updates if moved < 50m)
 * - GPS displacement fallback if Activity Recognition unavailable
 * - PTT-triggered location on demand
 *
 * Emits LocationUpdate to SharedFlow for server transmission (plan 03).
 */
@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationTracker: LocationTracker,
    private val motionDetector: MotionDetector,
    private val signalingClient: SignalingClient
) {
    companion object {
        private const val TAG = "LocationManager"
        private const val DEDUPLICATION_DISTANCE_METERS = 50f
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val PTT_TRIGGERED_MIN_INTERVAL_MS = 120_000L // 2 minutes

        // Motion-based intervals
        private const val INTERVAL_STILL_MS = 5 * 60 * 1000L      // 5 minutes
        private const val INTERVAL_WALKING_MS = 60 * 1000L         // 60 seconds
        private const val INTERVAL_DRIVING_MS = 30 * 1000L         // 30 seconds
        private const val INTERVAL_UNKNOWN_MS = 60 * 1000L         // 60 seconds
    }

    private var isTracking = false
    private var lastSentLocation: Location? = null
    private var lastSentTime: Long = 0
    private var activityRecognitionAvailable = true

    // Power multipliers for location tracking interval
    var wakeLockMultiplier: Int = 1  // 1 = wake lock active, 2 = wake lock released
        private set
    var batterySaverMultiplier: Int = 1  // 1 = normal, 4 = battery saver active
        private set
    private var wakeLockRecoveryCycles = 0  // Gradual ramp-up counter

    private val _currentMultiplier = MutableStateFlow(1)
    val currentMultiplier: StateFlow<Int> = _currentMultiplier.asStateFlow()

    private val _locationUpdates = MutableSharedFlow<LocationUpdate>()
    val locationUpdates: SharedFlow<LocationUpdate> = _locationUpdates

    private val _currentLocation = MutableStateFlow<LocationUpdate?>(null)
    val currentLocation: StateFlow<LocationUpdate?> = _currentLocation.asStateFlow()

    private val offlineQueue = ArrayDeque<LocationUpdate>(50)
    private val maxQueueSize = 50

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Observe motion state changes and adjust tracking intervals
        scope.launch {
            motionDetector.motionState.collect { motionState ->
                if (isTracking) {
                    Log.d(TAG, "Motion state changed to $motionState, restarting tracker with adaptive interval")
                    startTrackingWithAdaptiveInterval(motionState)
                }
            }
        }

        // Observe connection state changes and flush offline queue on reconnect
        scope.launch {
            signalingClient.connectionState.collect { connectionState ->
                if (connectionState == ConnectionState.CONNECTED && offlineQueue.isNotEmpty()) {
                    Log.d(TAG, "WebSocket reconnected, flushing ${offlineQueue.size} queued locations")
                    flushOfflineQueue()
                }
            }
        }
    }

    /**
     * Start location tracking.
     *
     * Checks permissions, starts motion detection, and begins location updates
     * with initial interval (60s for UNKNOWN state).
     *
     * @throws SecurityException if location permission not granted
     */
    fun startTracking() {
        if (isTracking) {
            Log.w(TAG, "Already tracking")
            return
        }

        // Check location permission
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            Log.e(TAG, "Location permission not granted")
            throw SecurityException("Location permission required for tracking")
        }

        Log.d(TAG, "Starting location tracking")
        isTracking = true

        // Start motion detector (may fail if Activity Recognition unavailable)
        try {
            motionDetector.startMonitoring()
        } catch (e: Exception) {
            Log.w(TAG, "Activity Recognition unavailable, using GPS displacement fallback", e)
            activityRecognitionAvailable = false
        }

        // Start location tracker with initial interval for UNKNOWN state
        startTrackingWithAdaptiveInterval(motionDetector.motionState.value)
    }

    /**
     * Stop location tracking and clean up.
     */
    fun stopTracking() {
        if (!isTracking) {
            return
        }

        Log.d(TAG, "Stopping location tracking")
        isTracking = false
        locationTracker.stopTracking()
        motionDetector.stopMonitoring()
    }

    /**
     * Called when wake lock is released after timeout.
     * Doubles location tracking interval (2x multiplier).
     */
    fun onWakeLockReleased() {
        wakeLockMultiplier = 2
        _currentMultiplier.value = wakeLockMultiplier * batterySaverMultiplier
        Log.d(TAG, "Wake lock released, location multiplier now ${_currentMultiplier.value}x")

        if (isTracking) {
            startTrackingWithAdaptiveInterval(motionDetector.motionState.value)
        }
    }

    /**
     * Called when wake lock is acquired.
     * Gradually ramps up location interval over 1-2 cycles.
     */
    fun onWakeLockAcquired() {
        wakeLockRecoveryCycles = 2
        _currentMultiplier.value = wakeLockMultiplier * batterySaverMultiplier
        Log.d(TAG, "Wake lock acquired, location recovery in $wakeLockRecoveryCycles cycles")
    }

    /**
     * Called when battery saver state changes.
     * Applies 4x multiplier when enabled, immediate snap-back when disabled.
     */
    fun onBatterySaverChanged(enabled: Boolean) {
        batterySaverMultiplier = if (enabled) 4 else 1
        _currentMultiplier.value = wakeLockMultiplier * batterySaverMultiplier
        Log.d(TAG, "Battery saver changed to $enabled, location multiplier now ${_currentMultiplier.value}x")

        if (isTracking) {
            startTrackingWithAdaptiveInterval(motionDetector.motionState.value)
        }
    }

    /**
     * Request PTT-triggered location send.
     *
     * Immediately sends current location if:
     * - More than 2 minutes since last send
     * - Battery >= 20%
     *
     * Otherwise skips to preserve battery.
     */
    fun requestPttTriggeredLocation() {
        val now = System.currentTimeMillis()
        val timeSinceLastSend = now - lastSentTime

        if (timeSinceLastSend < PTT_TRIGGERED_MIN_INTERVAL_MS) {
            Log.d(TAG, "PTT location skipped: last send ${timeSinceLastSend}ms ago (< ${PTT_TRIGGERED_MIN_INTERVAL_MS}ms threshold)")
            return
        }

        val batteryLevel = getBatteryLevel()
        if (batteryLevel < LOW_BATTERY_THRESHOLD) {
            Log.d(TAG, "PTT location skipped: battery $batteryLevel% < $LOW_BATTERY_THRESHOLD%")
            return
        }

        lastSentLocation?.let { location ->
            Log.d(TAG, "PTT triggered location send")
            emitLocationUpdate(location)
        }
    }

    /**
     * Start tracking with interval and priority adapted to current motion state and battery level.
     *
     * @param motionState Current motion state
     */
    private fun startTrackingWithAdaptiveInterval(motionState: MotionState) {
        val batteryLevel = getBatteryLevel()
        val lowBattery = batteryLevel < LOW_BATTERY_THRESHOLD

        // Determine interval based on motion state
        var interval = when (motionState) {
            MotionState.STILL -> INTERVAL_STILL_MS
            MotionState.WALKING -> INTERVAL_WALKING_MS
            MotionState.DRIVING -> INTERVAL_DRIVING_MS
            MotionState.UNKNOWN -> INTERVAL_UNKNOWN_MS
        }

        // Override interval if low battery
        if (lowBattery) {
            interval = INTERVAL_STILL_MS
            Log.d(TAG, "Low battery ($batteryLevel%), forcing 5min interval")
        }

        // Apply power multipliers
        val effectiveMultiplier = wakeLockMultiplier * batterySaverMultiplier
        interval *= effectiveMultiplier

        // Determine priority
        val priority = if (lowBattery) {
            Priority.PRIORITY_LOW_POWER
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        Log.d(TAG, "Starting tracker: motionState=$motionState, interval=${interval}ms, multiplier=${effectiveMultiplier}x, priority=$priority, battery=$batteryLevel%")

        locationTracker.startTracking(interval, priority) { location ->
            onLocationUpdate(location)
        }
    }

    /**
     * Handle location update from LocationTracker.
     *
     * Applies deduplication, GPS displacement fallback, and emits to SharedFlow.
     *
     * @param location New location from FusedLocationProviderClient
     */
    private fun onLocationUpdate(location: Location) {
        // If Activity Recognition unavailable, use GPS displacement fallback
        if (!activityRecognitionAvailable) {
            motionDetector.checkDisplacementFallback(location)
        }

        // Check if we should send this update (deduplication)
        if (!shouldSendLocation(location)) {
            Log.d(TAG, "Location update skipped (< ${DEDUPLICATION_DISTANCE_METERS}m displacement)")
            return
        }

        emitLocationUpdate(location)

        // Handle wake lock recovery cycle countdown
        if (wakeLockRecoveryCycles > 0) {
            wakeLockRecoveryCycles--
            if (wakeLockRecoveryCycles == 0) {
                wakeLockMultiplier = 1
                _currentMultiplier.value = wakeLockMultiplier * batterySaverMultiplier
                Log.d(TAG, "Wake lock recovery complete, location multiplier now ${_currentMultiplier.value}x")
                startTrackingWithAdaptiveInterval(motionDetector.motionState.value)
            }
        }
    }

    /**
     * Emit LocationUpdate to SharedFlow for server transmission.
     *
     * @param location Location to emit
     */
    private fun emitLocationUpdate(location: Location) {
        val locationUpdate = LocationUpdate(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            speed = if (location.hasSpeed()) location.speed else null,
            heading = if (location.hasBearing()) location.bearing else null,
            motionState = motionDetector.motionState.value,
            timestamp = Instant.now().toString()
        )

        // Update current location for debug screen (before deduplication)
        _currentLocation.value = locationUpdate

        scope.launch {
            _locationUpdates.emit(locationUpdate)
        }

        // Send via WebSocket (with offline queue fallback)
        sendLocationUpdate(locationUpdate)

        lastSentLocation = location
        lastSentTime = System.currentTimeMillis()

        Log.d(TAG, "Location update emitted: lat=${locationUpdate.latitude}, lng=${locationUpdate.longitude}, motionState=${locationUpdate.motionState}")
    }

    /**
     * Send location update via WebSocket, or queue if offline.
     *
     * @param update Location update to send
     */
    private fun sendLocationUpdate(update: LocationUpdate) {
        if (signalingClient.connectionState.value == ConnectionState.CONNECTED) {
            // Flush queue first if not empty
            if (offlineQueue.isNotEmpty()) {
                flushOfflineQueue()
            }

            // Send individual update
            signalingClient.send(SignalingType.LOCATION_UPDATE, update.toJsonObject())
            Log.d(TAG, "Location sent via WebSocket: lat=${update.latitude}, lng=${update.longitude}")
        } else {
            // Queue for offline transmission
            if (offlineQueue.size >= maxQueueSize) {
                offlineQueue.removeFirst()
                Log.d(TAG, "Offline queue full, dropping oldest update")
            }
            offlineQueue.addLast(update)
            Log.d(TAG, "Location queued offline (queue size: ${offlineQueue.size})")
        }
    }

    /**
     * Flush offline queue as batch message.
     */
    private fun flushOfflineQueue() {
        if (offlineQueue.isEmpty()) return

        val batch = offlineQueue.toList()
        offlineQueue.clear()

        val batchJson = JsonObject().apply {
            val updatesArray = com.google.gson.JsonArray()
            batch.forEach { update ->
                updatesArray.add(update.toJsonObject())
            }
            add("updates", updatesArray)
        }

        signalingClient.send(SignalingType.LOCATION_BATCH, batchJson)
        Log.d(TAG, "Flushed ${batch.size} queued locations as batch")
    }

    /**
     * Determine if location update should be sent based on deduplication logic.
     *
     * @param newLocation New location to evaluate
     * @return true if should send, false if should skip
     */
    private fun shouldSendLocation(newLocation: Location): Boolean {
        val lastLocation = lastSentLocation ?: return true // First send

        val results = FloatArray(1)
        Location.distanceBetween(
            lastLocation.latitude,
            lastLocation.longitude,
            newLocation.latitude,
            newLocation.longitude,
            results
        )

        val distance = results[0]
        return distance >= DEDUPLICATION_DISTANCE_METERS
    }

    /**
     * Get current battery level as percentage (0-100).
     *
     * @return Battery level percentage
     */
    private fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
