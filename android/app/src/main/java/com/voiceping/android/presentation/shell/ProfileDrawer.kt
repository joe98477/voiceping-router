package com.voiceping.android.presentation.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ProfileDrawer(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    userName: String,
    userEmail: String,
    appVersion: String = "1.0.0",
    onSwitchEvent: () -> Unit = {},
    onSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        content()

        // Drawer overlay (right-to-left slide)
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Dim background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = onDismiss)
                )

                // Drawer panel (right side)
                Surface(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxSize()
                        .align(Alignment.CenterEnd),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // User info
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()

                        // Menu items
                        Spacer(modifier = Modifier.height(8.dp))
                        ListItem(
                            headlineContent = { Text("Switch Event") },
                            leadingContent = { Icon(Icons.Default.Event, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onSwitchEvent)
                        )

                        ListItem(
                            headlineContent = { Text("Settings") },
                            leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onSettings)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        HorizontalDivider()

                        ListItem(
                            headlineContent = { Text("Logout") },
                            leadingContent = { Icon(Icons.Default.Logout, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onLogout)
                        )

                        // App version
                        Text(
                            text = "Version $appVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
