package com.voiceping.android.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceping.android.data.location.LocationManager
import com.voiceping.android.data.network.ChannelStatsPoller
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.power.BatterySaverMonitor
import com.voiceping.android.data.power.WakeLockManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Hidden developer stats screen showing real-time audio quality metrics.
 *
 * Displays:
 * - Signaling latency (PING RTT from SignalingClient)
 * - Consumer network stats (jitter, packet loss) — stub until crow-misia stats parsing validated
 *
 * Only accessible in debug builds via Settings > Audio Stats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevStatsScreen(
    signalingClient: SignalingClient,
    locationManager: LocationManager,
    wakeLockManager: WakeLockManager,
    batterySaverMonitor: BatterySaverMonitor,
    channelStatsPoller: ChannelStatsPoller,
    onBack: () -> Unit
) {
    val latency by signalingClient.latency.collectAsStateWithLifecycle()
    val currentLocation by locationManager.currentLocation.collectAsStateWithLifecycle()
    val wakeLockActive by wakeLockManager.wakeLockActive.collectAsStateWithLifecycle()
    val batterySaverEnabled by batterySaverMonitor.isBatterySaverEnabled.collectAsStateWithLifecycle()
    val channelIntervals by channelStatsPoller.channelIntervals.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Network", style = MaterialTheme.typography.titleMedium)

            StatRow("Signaling RTT", if (latency != null) "${latency}ms" else "N/A")

            Spacer(modifier = Modifier.height(16.dp))

            Text("Audio Quality", style = MaterialTheme.typography.titleMedium)
            Text(
                "Consumer stats parsing not yet validated on device. " +
                "See crow-misia Consumer.stats API (17-RESEARCH.md open question #1).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            StatRow("Jitter", "Pending device validation")
            StatRow("Packet Loss", "Pending device validation")
            StatRow("Packets Received", "Pending device validation")
            StatRow("Quality Indicator", "Pending device validation")

            Spacer(modifier = Modifier.height(16.dp))

            Text("Power Management", style = MaterialTheme.typography.titleMedium)
            StatRow("Wake Lock", if (wakeLockActive) "Active" else "Released")
            StatRow("Wake Lock Timeout", "${wakeLockManager.wakeLockTimeoutMs / 1000}s")
            StatRow("Battery Saver", if (batterySaverEnabled) "Active" else "Inactive")
            val locationMultiplier = when {
                batterySaverEnabled -> 4
                !wakeLockActive -> 2
                else -> 1
            }
            StatRow("Location Multiplier", "${locationMultiplier}x")

            Spacer(modifier = Modifier.height(16.dp))

            Text("Channel Polling", style = MaterialTheme.typography.titleMedium)
            if (channelIntervals.isEmpty()) {
                Text(
                    "No channels being polled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                channelIntervals.forEach { (channelId, interval) ->
                    StatRow("Channel ${channelId.take(8)}", "${interval / 1000}s")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Location Tracking", style = MaterialTheme.typography.titleMedium)

            if (currentLocation != null) {
                val loc = currentLocation!!
                StatRow("Latitude", String.format("%.6f", loc.latitude))
                StatRow("Longitude", String.format("%.6f", loc.longitude))
                StatRow("Accuracy", "${String.format("%.1f", loc.accuracy)}m")
                StatRow("Speed", loc.speed?.let { "${String.format("%.1f", it)} m/s" } ?: "N/A")
                StatRow("Heading", loc.heading?.let { "${String.format("%.0f", it)}°" } ?: "N/A")
                StatRow("Motion State", loc.motionState.toString().lowercase().replaceFirstChar { it.uppercase() })

                // Format timestamp
                val timestamp = try {
                    val instant = Instant.parse(loc.timestamp)
                    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                    formatter.format(instant)
                } catch (e: Exception) {
                    loc.timestamp
                }
                StatRow("Last Update", timestamp)
                StatRow("Tracking Status", "Active")
            } else {
                Text(
                    "Location: Not available (waiting for first fix)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
