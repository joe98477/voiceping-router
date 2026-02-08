package com.voiceping.android.presentation.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voiceping.android.domain.model.ConnectionState

@Composable
fun ConnectionBanner(
    connectionState: ConnectionState
) {
    AnimatedVisibility(
        visible = connectionState != ConnectionState.CONNECTED
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when (connectionState) {
                        ConnectionState.CONNECTING -> Color(0xFFFFA726) // Amber
                        ConnectionState.FAILED -> MaterialTheme.colorScheme.error
                        else -> Color.Transparent
                    }
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.FAILED -> "Connection failed. Retrying..."
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}
