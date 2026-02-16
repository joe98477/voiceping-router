package com.voiceping.android.data.power

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages partial wake lock for background audio monitoring.
 *
 * Wake lock is:
 * - Acquired when user joins first channel
 * - Released after configurable timeout (default 300s) of no audio activity
 * - Instantly reacquired on PTT press or incoming audio (SPEAKER_CHANGED with speaker)
 * - After reacquisition, held for full timeout period before next release
 *
 * Server provides wakeLockTimeoutSeconds via JWT payload.
 */
@Suppress("MagicNumber") // Power timeout values are well-understood constants
@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "VoicePing::AudioMonitoring"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var releaseRunnable: Runnable? = null

    var wakeLockTimeoutMs: Long = 300_000L
        private set

    private val _wakeLockActive = MutableStateFlow(false)
    val wakeLockActive: StateFlow<Boolean> = _wakeLockActive.asStateFlow()

    /**
     * Callback invoked when wake lock is released after timeout.
     * Used by LocationManager to double location tracking interval.
     */
    var onWakeLockReleased: (() -> Unit)? = null

    /**
     * Callback invoked when wake lock is acquired.
     * Used by LocationManager to restore location tracking interval.
     */
    var onWakeLockAcquired: (() -> Unit)? = null

    val isHeld: Boolean
        get() = wakeLock.isHeld

    /**
     * Acquire wake lock and reset timeout.
     * If already held, resets timeout to full duration (minimum hold pattern).
     */
    fun acquire() {
        if (!wakeLock.isHeld) {
            // 10 minutes max safety timeout per Android docs recommendation
            wakeLock.acquire(10 * 60 * 1000L)
            _wakeLockActive.value = true
            onWakeLockAcquired?.invoke()
            Log.d(TAG, "Wake lock ACQUIRED (timeout: ${wakeLockTimeoutMs / 1000}s)")
        }
        resetTimeout()
    }

    /**
     * Reset timeout: cancel existing delayed release, schedule new one.
     * Called on every audio activity event to keep wake lock alive.
     *
     * If the wake lock was previously released (idle timeout elapsed),
     * re-acquires it so the CPU stays awake for audio processing.
     */
    fun resetTimeout() {
        // Re-acquire if released (e.g., after idle timeout, then audio activity resumes)
        if (!wakeLock.isHeld) {
            wakeLock.acquire(10 * 60 * 1000L)
            _wakeLockActive.value = true
            onWakeLockAcquired?.invoke()
            Log.d(TAG, "Wake lock RE-ACQUIRED on audio activity")
        }

        releaseRunnable?.let { handler.removeCallbacks(it) }

        releaseRunnable = Runnable {
            if (wakeLock.isHeld) {
                wakeLock.release()
                _wakeLockActive.value = false
                onWakeLockReleased?.invoke()
                Log.d(TAG, "Wake lock RELEASED after ${wakeLockTimeoutMs / 1000}s timeout")
            }
            releaseRunnable = null
        }

        handler.postDelayed(releaseRunnable!!, wakeLockTimeoutMs)
    }

    /**
     * Immediately release wake lock and cancel timeout.
     * Used on disconnectAll.
     */
    fun releaseImmediate() {
        releaseRunnable?.let { handler.removeCallbacks(it) }
        releaseRunnable = null

        if (wakeLock.isHeld) {
            wakeLock.release()
            _wakeLockActive.value = false
            Log.d(TAG, "Wake lock RELEASED (immediate)")
        }
    }

    /**
     * Set wake lock timeout from server configuration.
     * Called when auth response received.
     *
     * @param seconds Timeout in seconds. If 0 or negative, defaults to 300s.
     */
    fun setTimeoutFromServer(seconds: Long) {
        wakeLockTimeoutMs = if (seconds > 0) seconds * 1000L else 300_000L
        Log.d(TAG, "Wake lock timeout set to ${wakeLockTimeoutMs / 1000}s from server config")
    }

    companion object {
        private const val TAG = "WakeLockManager"
    }
}
