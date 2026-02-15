package com.voiceping.android.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FusedLocationProviderClient wrapper for location tracking.
 *
 * Provides startTracking/stopTracking with configurable intervals and priority.
 * Caller (LocationManager) is responsible for permission checks.
 */
@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LocationTracker"
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    /**
     * Start location tracking with specified interval and priority.
     *
     * @param intervalMs Update interval in milliseconds
     * @param priority Priority constant from com.google.android.gms.location.Priority
     * @param onLocationUpdate Callback invoked with new location
     */
    @SuppressLint("MissingPermission")
    fun startTracking(
        intervalMs: Long,
        priority: Int,
        onLocationUpdate: (Location) -> Unit
    ) {
        Log.d(TAG, "Starting tracking: interval=${intervalMs}ms, priority=$priority")

        // Stop any existing tracking first
        stopTracking()

        val locationRequest = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(TAG, "Location update: lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}m")
                    onLocationUpdate(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    /**
     * Stop location tracking and clean up callback.
     */
    fun stopTracking() {
        locationCallback?.let {
            Log.d(TAG, "Stopping tracking")
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }
}
