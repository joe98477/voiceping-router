package com.voiceping.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoicePingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Additional initialization will be added in future plans
    }
}
