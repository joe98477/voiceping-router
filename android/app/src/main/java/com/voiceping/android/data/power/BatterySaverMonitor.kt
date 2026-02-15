package com.voiceping.android.data.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors battery saver mode changes via BroadcastReceiver.
 *
 * Event-driven (broadcast), NOT polling. Zero CPU when state unchanged.
 *
 * Used to trigger location tracking interval quadrupling (4x multiplier)
 * when battery saver is active.
 */
@Singleton
class BatterySaverMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _isBatterySaverEnabled = MutableStateFlow(false)
    val isBatterySaverEnabled: StateFlow<Boolean> = _isBatterySaverEnabled.asStateFlow()

    /**
     * Callback invoked when battery saver state changes.
     * Used by LocationManager to adjust location tracking interval.
     */
    var onBatterySaverChanged: ((Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                val enabled = powerManager.isPowerSaveMode
                _isBatterySaverEnabled.value = enabled
                onBatterySaverChanged?.invoke(enabled)
                Log.d(TAG, "Battery saver mode: ${if (enabled) "ENABLED" else "DISABLED"}")
            }
        }
    }

    /**
     * Start monitoring battery saver mode.
     * Registers broadcast receiver and checks initial state.
     */
    fun start() {
        // Check initial state
        val initialState = powerManager.isPowerSaveMode
        _isBatterySaverEnabled.value = initialState
        Log.d(TAG, "Battery saver monitor started (initial state: ${if (initialState) "ENABLED" else "DISABLED"})")

        // Register receiver for state changes
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    /**
     * Stop monitoring battery saver mode.
     * Unregisters broadcast receiver.
     */
    fun stop() {
        try {
            context.unregisterReceiver(receiver)
            Log.d(TAG, "Battery saver monitor stopped")
        } catch (e: IllegalArgumentException) {
            // Receiver not registered (stop called multiple times) - safe to ignore
            Log.d(TAG, "Receiver already unregistered")
        }
    }

    companion object {
        private const val TAG = "BatterySaverMonitor"
    }
}
