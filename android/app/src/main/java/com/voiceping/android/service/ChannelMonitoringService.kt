package com.voiceping.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voiceping.android.R
import com.voiceping.android.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service for channel monitoring with persistent notification.
 *
 * Keeps app alive as a "pocket radio" when screen is off, maintaining WebSocket
 * connection and audio playback. Provides minimal controls via notification:
 * Mute and Disconnect buttons.
 *
 * Service type: mediaPlayback (declares intent to play audio in background)
 * Notification: IMPORTANCE_LOW (unobtrusive like a music player)
 * Restart policy: START_NOT_STICKY (no auto-restart after force-kill)
 *
 * Actions:
 * - ACTION_START: Start foreground service with initial channel name
 * - ACTION_UPDATE_CHANNEL: Update notification with new channel name
 * - ACTION_TOGGLE_MUTE: Toggle mute state, update notification button label
 * - ACTION_STOP: Stop foreground service
 */
@AndroidEntryPoint
class ChannelMonitoringService : Service() {

    private var currentChannelName: String? = null
    private var isMuted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting channel monitoring foreground service")
                val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
                currentChannelName = channelName
                createNotificationChannel()
                val notification = buildNotification(channelName, isMuted)
                startForegroundService(notification)
            }

            ACTION_UPDATE_CHANNEL -> {
                val newChannelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME)
                // Only update if channel name changed (per user decision: minimal updates)
                if (newChannelName != null && newChannelName != currentChannelName) {
                    Log.d(TAG, "Updating channel notification: $currentChannelName -> $newChannelName")
                    currentChannelName = newChannelName
                    updateNotification(newChannelName)
                }
            }

            ACTION_TOGGLE_MUTE -> {
                Log.d(TAG, "Toggling mute: $isMuted -> ${!isMuted}")
                isMuted = !isMuted
                _isMutedFlow.value = isMuted
                // Update notification to reflect new mute state (button label changes)
                updateNotification(currentChannelName)
            }

            ACTION_STOP -> {
                Log.d(TAG, "Stopping channel monitoring foreground service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        // START_NOT_STICKY: Don't restart if killed (user decision)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Start foreground service with API 34+ compatibility.
     * Uses FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK for Android 14+.
     */
    private fun startForegroundService(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires explicit service type
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Create notification channel for channel monitoring.
     *
     * IMPORTANCE_LOW: No sound from notification (user hears squelch + audio)
     * VISIBILITY_PUBLIC: Show on lock screen
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Channel Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for active channel monitoring"
            setShowBadge(false) // Pocket radio doesn't need badge
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Build notification with channel name and action buttons.
     *
     * @param channelName Current channel name (null falls back to "VoicePing")
     * @param isMuted Current mute state (affects Mute button label)
     */
    private fun buildNotification(channelName: String?, isMuted: Boolean): Notification {
        // Content intent: tap notification to open app
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Mute action: toggle mute via NotificationActionReceiver
        val muteIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            muteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Disconnect action: stop service via NotificationActionReceiver
        val disconnectIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(channelName ?: "VoicePing")
            .setContentText("Monitoring")
            .setSmallIcon(R.drawable.ic_logo)
            .setOngoing(true) // Persistent, cannot swipe away
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_logo, // Placeholder icon
                if (isMuted) "Unmute" else "Mute",
                mutePendingIntent
            )
            .addAction(
                R.drawable.ic_logo, // Placeholder icon
                "Disconnect",
                disconnectPendingIntent
            )
            .build()
    }

    /**
     * Update notification with new channel name.
     * Only called when channel name actually changes.
     */
    private fun updateNotification(channelName: String?) {
        val notification = buildNotification(channelName, isMuted)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "ChannelMonitoringService"

        // Actions
        const val ACTION_START = "com.voiceping.START_MONITORING"
        const val ACTION_UPDATE_CHANNEL = "com.voiceping.UPDATE_CHANNEL"
        const val ACTION_TOGGLE_MUTE = "com.voiceping.TOGGLE_MUTE_SERVICE"
        const val ACTION_STOP = "com.voiceping.STOP_MONITORING"

        // Intent extras
        const val EXTRA_CHANNEL_NAME = "channel_name"

        // Notification
        private const val CHANNEL_ID = "channel_monitoring"
        private const val NOTIFICATION_ID = 1000 // Different from AudioCaptureService (1001)

        // Mute state exposed for ChannelRepository to observe
        private val _isMutedFlow = MutableStateFlow(false)
        val isMutedFlow: StateFlow<Boolean> = _isMutedFlow.asStateFlow()
    }
}
