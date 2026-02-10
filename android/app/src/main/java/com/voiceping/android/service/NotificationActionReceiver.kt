package com.voiceping.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for handling notification action button taps.
 *
 * Receives actions from ChannelMonitoringService notification buttons
 * (Mute and Disconnect) and relays them to the service via startService.
 *
 * Pattern: Notification buttons use PendingIntent.getBroadcast() pointing
 * to this receiver. Receiver creates service intents and calls startService()
 * to trigger service action handlers.
 *
 * Actions:
 * - ACTION_TOGGLE_MUTE: Relay mute toggle to ChannelMonitoringService
 * - ACTION_DISCONNECT: Relay stop command to ChannelMonitoringService
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent")
            return
        }

        when (intent.action) {
            ACTION_TOGGLE_MUTE -> {
                Log.d(TAG, "Notification action: Toggle mute")
                val serviceIntent = Intent(context, ChannelMonitoringService::class.java).apply {
                    action = ChannelMonitoringService.ACTION_TOGGLE_MUTE
                }
                context.startService(serviceIntent)
            }

            ACTION_DISCONNECT -> {
                Log.d(TAG, "Notification action: Disconnect")
                val serviceIntent = Intent(context, ChannelMonitoringService::class.java).apply {
                    action = ChannelMonitoringService.ACTION_STOP
                }
                context.startService(serviceIntent)
            }

            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"

        // Actions sent from notification buttons
        const val ACTION_TOGGLE_MUTE = "com.voiceping.TOGGLE_MUTE"
        const val ACTION_DISCONNECT = "com.voiceping.DISCONNECT"
    }
}
