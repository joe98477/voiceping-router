package com.voiceping.android.presentation.channels

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voiceping.android.data.ptt.PttState
import com.voiceping.android.domain.model.ConnectionState
import com.voiceping.android.presentation.channels.components.BottomBar
import com.voiceping.android.presentation.channels.components.ChannelRow
import com.voiceping.android.presentation.channels.components.TeamHeader
import com.voiceping.android.presentation.shell.ConnectionBanner
import com.voiceping.android.presentation.shell.ProfileDrawer
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    viewModel: ChannelListViewModel = hiltViewModel(),
    onSwitchEvent: () -> Unit = {},
    onSettings: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val channels by viewModel.channels.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val joinedChannel by viewModel.joinedChannel.collectAsState()
    val currentSpeaker by viewModel.currentSpeaker.collectAsState()
    val lastSpeaker by viewModel.lastSpeaker.collectAsState()
    val pttState by viewModel.pttState.collectAsState()
    val pttMode by viewModel.pttMode.collectAsState()
    val audioRoute by viewModel.audioRoute.collectAsState()
    val toggleMaxDuration by viewModel.toggleMaxDuration.collectAsState()
    val pttStartToneEnabled by viewModel.pttStartToneEnabled.collectAsState()
    val rogerBeepEnabled by viewModel.rogerBeepEnabled.collectAsState()
    val rxSquelchEnabled by viewModel.rxSquelchEnabled.collectAsState()
    val needsMicPermission by viewModel.needsMicPermission.collectAsState()
    val showBatteryPrompt by viewModel.showBatteryOptimizationPrompt.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()

    val context = LocalContext.current
    var drawerOpen by remember { mutableStateOf(false) }

    // Transmission duration ticker (updates every second during transmission)
    var transmissionDuration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pttState) {
        if (pttState is PttState.Transmitting) {
            while (true) {
                transmissionDuration = viewModel.getTransmissionDuration()
                delay(1000)
            }
        } else {
            transmissionDuration = 0L
        }
    }

    // Battery optimization launcher
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Result doesn't matter — user either allowed or denied. Dismiss either way.
        viewModel.dismissBatteryOptimizationPrompt()
    }

    // Launch battery optimization dialog when prompted
    LaunchedEffect(showBatteryPrompt) {
        if (showBatteryPrompt) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            batteryOptimizationLauncher.launch(intent)
        }
    }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMicPermissionResult(granted)
    }

    // Launch permission request when needed
    LaunchedEffect(needsMicPermission) {
        if (needsMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    ProfileDrawer(
        isOpen = drawerOpen,
        onDismiss = { drawerOpen = false },
        userName = "User Name",
        userEmail = "user@example.com",
        appVersion = "1.0.0",
        pttMode = pttMode,
        audioRoute = audioRoute,
        toggleMaxDuration = toggleMaxDuration,
        pttStartToneEnabled = pttStartToneEnabled,
        rogerBeepEnabled = rogerBeepEnabled,
        rxSquelchEnabled = rxSquelchEnabled,
        onPttModeChanged = { viewModel.setPttMode(it) },
        onAudioRouteChanged = { viewModel.setAudioRoute(it) },
        onToggleMaxDurationChanged = { viewModel.setToggleMaxDuration(it) },
        onPttStartToneChanged = { viewModel.setPttStartToneEnabled(it) },
        onRogerBeepChanged = { viewModel.setRogerBeepEnabled(it) },
        onRxSquelchChanged = { viewModel.setRxSquelchEnabled(it) },
        onSwitchEvent = {
            drawerOpen = false
            onSwitchEvent()
        },
        onSettings = {
            drawerOpen = false
            onSettings()
        },
        onLogout = {
            drawerOpen = false
            onLogout()
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Channels") },
                    actions = {
                        // Connection status dot
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = when (connectionState) {
                                        ConnectionState.CONNECTED -> androidx.compose.ui.graphics.Color.Green
                                        ConnectionState.CONNECTING -> androidx.compose.ui.graphics.Color.Yellow
                                        ConnectionState.FAILED -> androidx.compose.ui.graphics.Color.Red
                                        else -> androidx.compose.ui.graphics.Color.Gray
                                    },
                                    shape = CircleShape
                                )
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Profile icon
                        IconButton(onClick = { drawerOpen = true }) {
                            Icon(Icons.Default.Person, contentDescription = "Profile")
                        }
                    }
                )
            },
            bottomBar = {
                BottomBar(
                    joinedChannel = joinedChannel,
                    currentSpeaker = currentSpeaker,
                    pttState = pttState,
                    pttMode = pttMode,
                    transmissionDuration = transmissionDuration,
                    onPttPressed = { viewModel.onPttPressed() },
                    onPttReleased = { viewModel.onPttReleased() }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Connection banner at top
                ConnectionBanner(connectionState = connectionState)

                // Channel list grouped by team
                val channelsByTeam = channels.groupBy { it.teamName }

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    channelsByTeam.forEach { (teamName, teamChannels) ->
                        // Team header
                        item {
                            TeamHeader(teamName = teamName)
                        }

                        // Channels in team
                        items(teamChannels) { channel ->
                            ChannelRow(
                                channel = channel,
                                isJoined = channel.id == joinedChannel?.id,
                                lastSpeaker = if (channel.id == joinedChannel?.id) lastSpeaker else null,
                                lastSpeakerVisible = channel.id == joinedChannel?.id && lastSpeaker != null,
                                onToggle = { viewModel.toggleChannel(channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}
