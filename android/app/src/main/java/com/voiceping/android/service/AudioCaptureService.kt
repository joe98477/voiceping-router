package com.voiceping.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voiceping.android.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service for audio capture during PTT transmission.
 *
 * Provides microphone foreground service type (Android 14+) to enable
 * background audio capture. Started/stopped by PttManager.
 *
 * Actions:
 * - ACTION_START: Start foreground service with microphone notification
 * - ACTION_STOP: Stop foreground service
 */
@AndroidEntryPoint
class AudioCaptureService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting audio capture foreground service")
                createNotificationChannel()
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("VoicePing PTT Active")
                    .setContentText("Transmitting audio...")
                    .setSmallIcon(R.drawable.ic_logo)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                startForeground(NOTIFICATION_ID, notification)
            }

            ACTION_STOP -> {
                Log.d(TAG, "Stopping audio capture foreground service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification shown during PTT audio transmission"
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AudioCaptureService"
        const val ACTION_START = "com.voiceping.START_CAPTURE"
        const val ACTION_STOP = "com.voiceping.STOP_CAPTURE"
        private const val CHANNEL_ID = "audio_capture"
        private const val NOTIFICATION_ID = 1001
    }
}
