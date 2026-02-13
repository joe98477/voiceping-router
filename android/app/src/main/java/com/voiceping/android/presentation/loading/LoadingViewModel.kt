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
                // 1. Re-establish session with stored credentials (cookies are in-memory,
                //    so we need to re-login on each app start / Loading screen entry)
                val credentials = tokenManager.getStoredCredentials()
                if (credentials != null) {
                    Log.d(TAG, "Re-establishing session with stored credentials")
                    val loginResult = authRepository.login(credentials.email, credentials.password)
                    if (loginResult.isFailure) {
                        _uiState.value = LoadingUiState.Failed("Login failed. Please sign in again.")
                        return@launch
                    }
                }

                // 2. Check for saved event ID
                val savedEventId = preferencesManager.getLastEventId()

                if (savedEventId == null) {
                    // No saved event — navigate to event picker (no WS needed yet)
                    Log.d(TAG, "No saved event, navigating to event picker")
                    _uiState.value = LoadingUiState.Connected(null)
                    return@launch
                }

                // 3. Get router token for the saved event (JWT for WebSocket auth)
                Log.d(TAG, "Getting router token for event: $savedEventId")
                val tokenResult = authRepository.getRouterToken(savedEventId)
                if (tokenResult.isFailure) {
                    Log.w(TAG, "Failed to get router token, clearing saved event", tokenResult.exceptionOrNull())
                    // Event might have been removed or user lost access — go to event picker
                    preferencesManager.clearLastEventId()
                    _uiState.value = LoadingUiState.Connected(null)
                    return@launch
                }

                // 4. Connect to WebSocket server with JWT token
                val serverUrl = BuildConfig.SERVER_URL
                val token = tokenResult.getOrThrow()
                Log.d(TAG, "Connecting to WebSocket: $serverUrl")
                signalingClient.connect(serverUrl, token)

                // 5. Wait for connection to be established (with 15-second timeout)
                withTimeout(15_000) {
                    signalingClient.connectionState
                        .first { it == ConnectionState.CONNECTED }
                }

                Log.d(TAG, "WebSocket connected, initializing mediasoup")

                // 6. Initialize mediasoup Device
                mediasoupClient.initialize()
                Log.d(TAG, "Mediasoup initialized successfully")

                // 7. Emit connected state with saved event ID
                _uiState.value = LoadingUiState.Connected(savedEventId)

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                _uiState.value = LoadingUiState.Failed(e.message ?: "Connection failed")
            }
        }
    }

    fun retry() {
        _uiState.value = LoadingUiState.Connecting
        connectToServer()
    }

    companion object {
        private const val TAG = "LoadingViewModel"
    }
}
