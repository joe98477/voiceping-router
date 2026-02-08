package com.voiceping.android.presentation.loading

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceping.android.BuildConfig
import com.voiceping.android.data.network.MediasoupClient
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.repository.AuthRepository
import com.voiceping.android.data.storage.PreferencesManager
import com.voiceping.android.data.storage.TokenManager
import com.voiceping.android.domain.model.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

sealed class LoadingUiState {
    object Connecting : LoadingUiState()
    data class Connected(val savedEventId: String?) : LoadingUiState()
    data class Failed(val message: String) : LoadingUiState()
}

@HiltViewModel
class LoadingViewModel @Inject constructor(
    private val signalingClient: SignalingClient,
    private val mediasoupClient: MediasoupClient,
    private val preferencesManager: PreferencesManager,
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoadingUiState>(LoadingUiState.Connecting)
    val uiState: StateFlow<LoadingUiState> = _uiState.asStateFlow()

    init {
        connectToServer()
    }

    private fun connectToServer() {
        viewModelScope.launch {
            try {
                // 1. Check if token needs refresh
                if (tokenManager.needsRefresh()) {
                    Log.d("LoadingViewModel", "Token needs refresh, attempting silent refresh")
                    val refreshResult = authRepository.refreshTokenWithRetry()
                    if (refreshResult.isFailure) {
                        _uiState.value = LoadingUiState.Failed("Session expired. Please login again.")
                        return@launch
                    }
                }

                // 2. Get server URL from BuildConfig
                val serverUrl = BuildConfig.SERVER_URL

                // 3. Connect to WebSocket server
                Log.d("LoadingViewModel", "Connecting to WebSocket: $serverUrl")
                signalingClient.connect(serverUrl)

                // 4. Wait for connection to be established (with 15-second timeout)
                withTimeout(15_000) {
                    signalingClient.connectionState
                        .first { it == ConnectionState.CONNECTED }
                }

                Log.d("LoadingViewModel", "WebSocket connected, initializing mediasoup")

                // 5. Initialize mediasoup Device
                try {
                    mediasoupClient.initialize()
                    Log.d("LoadingViewModel", "Mediasoup initialized successfully")
                } catch (e: Exception) {
                    // Log warning but continue - initialize can be retried on channel join
                    Log.w("LoadingViewModel", "Mediasoup initialization failed, will retry on channel join", e)
                }

                // 6. Emit connected state with saved event ID
                val savedEventId = preferencesManager.getLastEventId()
                _uiState.value = LoadingUiState.Connected(savedEventId)

            } catch (e: Exception) {
                Log.e("LoadingViewModel", "Connection failed", e)
                _uiState.value = LoadingUiState.Failed(e.message ?: "Connection failed")
            }
        }
    }

    fun retry() {
        _uiState.value = LoadingUiState.Connecting
        connectToServer()
    }
}
