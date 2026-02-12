package com.voiceping.android.presentation

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.voiceping.android.data.hardware.HardwareKeyHandler
import com.voiceping.android.data.ptt.PttManager
import com.voiceping.android.data.repository.ChannelRepository
import com.voiceping.android.data.storage.PreferencesManager
import com.voiceping.android.presentation.navigation.NavGraph
import com.voiceping.android.presentation.theme.VoicePingTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var hardwareKeyHandler: HardwareKeyHandler

    @Inject
    lateinit var pttManager: PttManager

    @Inject
    lateinit var channelRepository: ChannelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Wire hardware PTT callbacks
        hardwareKeyHandler.onPttPress = {
            val targetChannelId = channelRepository.getHardwarePttTargetChannelId()
            if (targetChannelId != null) {
                pttManager.requestPtt(targetChannelId)
            }
        }
        hardwareKeyHandler.onPttRelease = {
            pttManager.releasePtt()
        }

        setContent {
            VoicePingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        preferencesManager = preferencesManager
                    )
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Intercept volume keys for dual-purpose PTT
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (hardwareKeyHandler.isVolumeKeyPttEnabled(event.keyCode)) {
                val handled = hardwareKeyHandler.handleKeyEvent(event)
                if (handled) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
