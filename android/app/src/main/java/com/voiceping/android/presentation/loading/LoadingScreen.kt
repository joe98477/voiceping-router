package com.voiceping.android.presentation.loading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voiceping.android.R

@Composable
fun LoadingScreen(
    viewModel: LoadingViewModel = hiltViewModel(),
    onConnected: (savedEventId: String?) -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate on successful connection
    LaunchedEffect(uiState) {
        if (uiState is LoadingUiState.Connected) {
            val savedEventId = (uiState as LoadingUiState.Connected).savedEventId
            onConnected(savedEventId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "VoicePing Logo",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (uiState) {
            is LoadingUiState.Connecting -> {
                Text(
                    text = "Connecting...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            is LoadingUiState.Failed -> {
                val errorMessage = (uiState as LoadingUiState.Failed).message

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(onClick = { viewModel.retry() }) {
                        Text("Retry")
                    }

                    Button(onClick = onLogout) {
                        Text("Logout")
                    }
                }
            }

            is LoadingUiState.Connected -> {
                // Navigating... (handled by LaunchedEffect)
            }
        }
    }
}
