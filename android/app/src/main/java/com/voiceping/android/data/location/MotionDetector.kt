package com.voiceping.android.data.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.voiceping.android.domain.model.MotionState
import com.voiceping.android.service.ActivityTransitionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ActivityTransition API wrapper for motion detection.
 *
 * Detects user motion state (STILL, WALKING, DRIVING) using Android Activity Recognition API.
 * Falls back to GPS displacement if Activity Recognition is unavailable.
 *
 * Exposes motionState as StateFlow for observers (LocationManager).
 */
@Singleton
class MotionDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MotionDetector"
        private const val DISPLACEMENT_THRESHOLD_METERS = 30f
        private const val NO_MOVEMENT_COUNT_THRESHOLD = 2

        // Singleton instance for ActivityTransitionReceiver callback
        var instance: MotionDetector? = null
    }

    init {
        instance = this
    }

    private val activityRecognitionClient: ActivityRecognitionClient =
        ActivityRecognition.getClient(context)

    private val _motionState = MutableStateFlow(MotionState.UNKNOWN)
    val motionState: StateFlow<MotionState> = _motionState

    // GPS displacement fallback fields
    private var lastDisplacementLocation: Location? = null
    private var consecutiveNoMovementCount = 0

    private var pendingIntent: PendingIntent? = null

    /**
     * Start monitoring activity transitions.
     * Registers for STILL, WALKING, and IN_VEHICLE enter transitions.
     *
     * @throws Exception if Activity Recognition is unavailable (permission denied or API disabled)
     */
    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        Log.d(TAG, "Starting motion detection")

        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )

        val request = ActivityTransitionRequest(transitions)

        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent!!)
            .addOnSuccessListener {
                Log.d(TAG, "Activity transition updates registered successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to register activity transitions", e)
                throw e
            }
    }

    /**
     * Stop monitoring activity transitions and clean up.
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopping motion detection")
        pendingIntent?.let {
            activityRecognitionClient.removeActivityTransitionUpdates(it)
            pendingIntent = null
        }
    }

    /**
     * Update motion state (called from ActivityTransitionReceiver).
     *
     * @param state New motion state detected by Activity Recognition API
     */
    fun updateMotionState(state: MotionState) {
        if (_motionState.value != state) {
            Log.d(TAG, "Motion state changed: ${_motionState.value} -> $state")
            _motionState.value = state
        }
    }

    /**
     * GPS displacement fallback for when Activity Recognition is unavailable.
     *
     * Uses location displacement to infer motion:
     * - If user moves < 30m for 2 consecutive updates -> STILL
     * - If user moves >= 30m -> WALKING
     *
     * @param location Current GPS location
     */
    fun checkDisplacementFallback(location: Location) {
        val lastLocation = lastDisplacementLocation
        lastDisplacementLocation = location

        if (lastLocation == null) {
            // First location, can't calculate displacement yet
            return
        }

        val results = FloatArray(1)
        Location.distanceBetween(
            lastLocation.latitude,
            lastLocation.longitude,
            location.latitude,
            location.longitude,
            results
        )

        val distance = results[0]
        Log.d(TAG, "GPS displacement: ${distance}m")

        if (distance < DISPLACEMENT_THRESHOLD_METERS) {
            consecutiveNoMovementCount++
            if (consecutiveNoMovementCount >= NO_MOVEMENT_COUNT_THRESHOLD) {
                updateMotionState(MotionState.STILL)
            }
        } else {
            consecutiveNoMovementCount = 0
            updateMotionState(MotionState.WALKING)
        }
    }
}
