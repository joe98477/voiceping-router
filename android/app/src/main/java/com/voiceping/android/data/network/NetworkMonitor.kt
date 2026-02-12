package com.voiceping.android.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.voiceping.android.domain.model.NetworkType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network connectivity state and type.
 *
 * Wraps ConnectivityManager.NetworkCallback to expose network availability
 * and network type as StateFlows for reactive UI and reconnection logic.
 *
 * Usage:
 * - Call start() when app starts (typically in Application or ViewModel)
 * - Call stop() when monitoring no longer needed (typically in onDestroy)
 * - Collect isNetworkAvailable and networkType StateFlows for reactive behavior
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _networkType = MutableStateFlow(NetworkType.NONE)
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network available: $network")
            _isNetworkAvailable.value = true
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "Network lost: $network")
            _isNetworkAvailable.value = false
            _networkType.value = NetworkType.NONE
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            val type = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                else -> NetworkType.OTHER
            }
            if (_networkType.value != type) {
                Log.i(TAG, "Network type changed: ${_networkType.value} -> $type")
                _networkType.value = type
            }
        }
    }

    /**
     * Start monitoring network state.
     *
     * Registers NetworkCallback and checks initial state.
     */
    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(request, networkCallback)

        // Check initial state
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager?.getNetworkCapabilities(it) }

        if (capabilities != null) {
            _isNetworkAvailable.value = true
            _networkType.value = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                else -> NetworkType.OTHER
            }
            Log.i(TAG, "Initial network state: available=${_isNetworkAvailable.value}, type=${_networkType.value}")
        } else {
            _isNetworkAvailable.value = false
            _networkType.value = NetworkType.NONE
            Log.i(TAG, "Initial network state: no network")
        }
    }

    /**
     * Stop monitoring network state.
     *
     * Unregisters NetworkCallback.
     */
    fun stop() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "NetworkMonitor stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
