package com.voiceping.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.voiceping.android.R
import com.voiceping.android.data.storage.dataStore
import com.voiceping.android.presentation.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Boot receiver for auto-start on device boot.
 *
 * Android 15 restriction: Cannot launch mediaPlayback foreground service from BOOT_COMPLETED.
 * Instead, show a notification that user can tap to launch the app.
 *
 * User decision: Only show notification if user has enabled boot auto-start in settings.
 *
 * Integration:
 * - Reads boot_auto_start_enabled from DataStore (non-Hilt context via Context.dataStore extension)
 * - Shows notification with MainActivity launch intent
 * - User taps notification to open app and join channels
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {

            Log.d(TAG, "Boot completed, checking auto-start setting")

            // Read auto-start setting from DataStore
            // NOTE: BootReceiver cannot use Hilt injection, so access DataStore directly
            val autoStartEnabled = runBlocking {
                val prefs = context.dataStore.data.first()
                prefs[booleanPreferencesKey("boot_auto_start_enabled")] ?: false
            }

            if (autoStartEnabled) {
                Log.d(TAG, "Auto-start enabled, showing boot notification")
                showBootNotification(context)
            } else {
                Log.d(TAG, "Auto-start disabled, ignoring boot event")
            }
        }
    }

    /**
     * Show notification that launches MainActivity when tapped.
     *
     * CRITICAL per Android 15 research: Cannot launch foreground service from BOOT_COMPLETED.
     * Show notification instead, allowing user to manually launch app.
     */
    private fun showBootNotification(context: Context) {
        // Create notification channel
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            BOOT_CHANNEL_ID,
            "Boot Auto-Start",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification shown on device boot when auto-start is enabled"
        }
        notificationManager.createNotificationChannel(channel)

        // Content intent: launch MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, BOOT_CHANNEL_ID)
            .setContentTitle("VoicePing ready")
            .setContentText("Tap to connect to channels")
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true) // Dismiss when tapped
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Post notification
        notificationManager.notify(BOOT_NOTIFICATION_ID, notification)
        Log.d(TAG, "Boot notification shown")
    }

    companion object {
        private const val TAG = "BootReceiver"
        private const val BOOT_CHANNEL_ID = "boot_start"
        private const val BOOT_NOTIFICATION_ID = 1002
    }
}
