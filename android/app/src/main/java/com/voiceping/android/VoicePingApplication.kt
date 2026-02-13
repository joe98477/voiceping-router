package com.voiceping.android

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.webrtc.PeerConnectionFactory

@HiltAndroidApp
class VoicePingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize WebRTC native libraries
        // Required before creating any Device or PeerConnection objects
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions()
        )
        Log.d(TAG, "WebRTC subsystem initialized")
    }

    companion object {
        private const val TAG = "VoicePingApp"
    }
}
