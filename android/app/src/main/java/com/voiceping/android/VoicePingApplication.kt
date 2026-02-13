package com.voiceping.android

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import io.github.crow_misia.mediasoup.MediasoupClient
import io.github.crow_misia.webrtc.log.DefaultLogHandler

@HiltAndroidApp
class VoicePingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize mediasoup native library (loads libmediasoupclient_so.so + WebRTC init)
        // Required before creating any Device or PeerConnection objects
        MediasoupClient.initialize(this, DefaultLogHandler)
        Log.d(TAG, "mediasoup + WebRTC subsystem initialized")
    }

    companion object {
        private const val TAG = "VoicePingApp"
    }
}
