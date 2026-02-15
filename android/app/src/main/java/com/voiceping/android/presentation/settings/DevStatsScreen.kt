package com.voiceping.android.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceping.android.data.network.SignalingClient

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
    onBack: () -> Unit
) {
    val latency by signalingClient.latency.collectAsStateWithLifecycle()

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
