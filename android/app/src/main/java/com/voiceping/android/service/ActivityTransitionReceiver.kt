package com.voiceping.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.voiceping.android.data.location.MotionDetector
import com.voiceping.android.domain.model.MotionState
import dagger.hilt.android.AndroidEntryPoint

/**
 * BroadcastReceiver for ActivityTransition events.
 *
 * Receives activity transition events from Google Play Services Activity Recognition API
 * and delivers motion state changes to MotionDetector singleton.
 */
@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ActivityTransitionRx"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) {
            Log.w(TAG, "Received intent without ActivityTransitionResult")
            return
        }

        val result = ActivityTransitionResult.extractResult(intent)
        if (result == null) {
            Log.w(TAG, "Failed to extract ActivityTransitionResult")
            return
        }

        for (event in result.transitionEvents) {
            Log.d(TAG, "Activity transition: type=${event.activityType}, transition=${event.transitionType}")

            val motionState = mapActivityToMotionState(event)
            if (motionState != null) {
                // Deliver to MotionDetector singleton via companion object reference
                MotionDetector.instance?.updateMotionState(motionState)
            }
        }
    }

    /**
     * Map ActivityTransitionEvent to MotionState.
     *
     * @param event Activity transition event from Google Play Services
     * @return MotionState or null if event should be ignored
     */
    private fun mapActivityToMotionState(event: ActivityTransitionEvent): MotionState? {
        // Only process ENTER transitions
        if (event.transitionType != com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
            return null
        }

        return when (event.activityType) {
            DetectedActivity.STILL -> MotionState.STILL
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE -> MotionState.WALKING
            DetectedActivity.IN_VEHICLE -> MotionState.DRIVING
            else -> null
        }
    }
}
