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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import com.voiceping.android.domain.model.AudioOutputDevice
import com.voiceping.android.presentation.settings.ButtonDetectionDialog
import com.voiceping.android.presentation.settings.keyCodeToName
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voiceping.android.data.ptt.PttState
import com.voiceping.android.domain.model.ConnectionState
import com.voiceping.android.presentation.channels.components.BottomBar
import com.voiceping.android.presentation.channels.components.ChannelRow
import com.voiceping.android.presentation.channels.components.ChannelVolumeDialog
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
    val monitoredChannels by viewModel.monitoredChannels.collectAsState()
    val primaryChannelId by viewModel.primaryChannelId.collectAsState()
    val displayedChannelId by viewModel.displayedChannelId.collectAsState()
    val scanModeEnabled by viewModel.scanModeEnabled.collectAsState()
    val scanModeLocked by viewModel.scanModeLocked.collectAsState()
    val scanReturnDelay by viewModel.scanReturnDelay.collectAsState()
    val pttTargetMode by viewModel.pttTargetMode.collectAsState()
    val audioMixMode by viewModel.audioMixMode.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val pttState by viewModel.pttState.collectAsState()
    val pttMode by viewModel.pttMode.collectAsState()
    val audioRoute by viewModel.audioRoute.collectAsState()
    val toggleMaxDuration by viewModel.toggleMaxDuration.collectAsState()
    val pttStartToneEnabled by viewModel.pttStartToneEnabled.collectAsState()
    val rogerBeepEnabled by viewModel.rogerBeepEnabled.collectAsState()
    val rxSquelchEnabled by viewModel.rxSquelchEnabled.collectAsState()
    val needsMicPermission by viewModel.needsMicPermission.collectAsState()
    val showBatteryPrompt by viewModel.showBatteryOptimizationPrompt.collectAsState()

    // Hardware button settings
    val volumeKeyPttConfig by viewModel.volumeKeyPttConfig.collectAsState()
    val bluetoothPttEnabled by viewModel.bluetoothPttEnabled.collectAsState()
    val bluetoothPttButtonKeycode by viewModel.bluetoothPttButtonKeycode.collectAsState()
    val bootAutoStartEnabled by viewModel.bootAutoStartEnabled.collectAsState()
    val currentOutputDevice by viewModel.currentOutputDevice.collectAsState()
    val showButtonDetection by viewModel.showButtonDetection.collectAsState()
    val detectedKeyCode by viewModel.detectedKeyCode.collectAsState()

    val context = LocalContext.current
    var drawerOpen by remember { mutableStateOf(false) }
    var volumeDialogChannelId by remember { mutableStateOf<String?>(null) }

    // Derive displayed channel state for BottomBar
    val displayedChannel = monitoredChannels[displayedChannelId]
    val displayedChannelName = displayedChannel?.channelName
    val isPrimaryDisplayed = displayedChannel?.isPrimary ?: true
    val displayedSpeaker = displayedChannel?.currentSpeaker

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

    // Scan mode return effect
    if (scanModeEnabled && !scanModeLocked) {
        val currentPrimary by rememberUpdatedState(primaryChannelId)
        val currentDisplayed by rememberUpdatedState(displayedChannelId)

        // Find if any non-primary channel has active speaker
        val anyNonPrimaryActive = monitoredChannels.values.any {
            it.currentSpeaker != null && !it.isPrimary && !it.isMuted
        }

        LaunchedEffect(anyNonPrimaryActive) {
            if (!anyNonPrimaryActive && currentDisplayed != null && currentDisplayed != currentPrimary) {
                delay(scanReturnDelay * 1000L)
                viewModel.returnToPrimaryChannel()
            }
        }
    }

    // Toast message handling
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
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
        scanModeEnabled = scanModeEnabled,
        pttTargetMode = pttTargetMode,
        scanReturnDelay = scanReturnDelay,
        audioMixMode = audioMixMode,
        onScanModeEnabledChanged = { viewModel.setScanModeEnabled(it) },
        onPttTargetModeChanged = { viewModel.setPttTargetMode(it) },
        onScanReturnDelayChanged = { viewModel.setScanReturnDelay(it) },
        onAudioMixModeChanged = { viewModel.setAudioMixMode(it) },
        volumeKeyPttConfig = volumeKeyPttConfig,
        bluetoothPttEnabled = bluetoothPttEnabled,
        bluetoothPttButtonKeycode = bluetoothPttButtonKeycode,
        bootAutoStartEnabled = bootAutoStartEnabled,
        onVolumeKeyPttConfigChanged = { viewModel.setVolumeKeyPttConfig(it) },
        onBluetoothPttEnabledChanged = { viewModel.setBluetoothPttEnabled(it) },
        onDetectBluetoothButton = { viewModel.startButtonDetection() },
        onBootAutoStartChanged = { viewModel.setBootAutoStartEnabled(it) },
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
                        // Mute-all button (only when 2+ channels joined)
                        if (monitoredChannels.size > 1) {
                            IconButton(onClick = { viewModel.muteAllExceptPrimary() }) {
                                Icon(Icons.Default.VolumeOff, contentDescription = "Mute all except primary")
                            }
                        }

                        // Audio output device icon
                        Icon(
                            imageVector = when (currentOutputDevice) {
                                AudioOutputDevice.SPEAKER -> Icons.Default.VolumeUp
                                AudioOutputDevice.EARPIECE -> Icons.Default.PhoneInTalk
                                AudioOutputDevice.BLUETOOTH -> Icons.Default.Bluetooth
                                AudioOutputDevice.WIRED_HEADSET -> Icons.Default.Headset
                            },
                            contentDescription = "Audio: ${currentOutputDevice.name}",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))

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
                    displayedChannelName = displayedChannelName,
                    isPrimaryChannel = isPrimaryDisplayed,
                    isLocked = scanModeLocked,
                    currentSpeaker = displayedSpeaker,
                    pttState = pttState,
                    pttMode = pttMode,
                    transmissionDuration = transmissionDuration,
                    onToggleLock = { viewModel.toggleBottomBarLock() },
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
                            val channelState = monitoredChannels[channel.id]
                            ChannelRow(
                                channel = channel,
                                isJoined = channelState != null,
                                isPrimary = channelState?.isPrimary ?: false,
                                isMuted = channelState?.isMuted ?: false,
                                currentSpeaker = channelState?.currentSpeaker,
                                lastSpeaker = channelState?.lastSpeaker,
                                lastSpeakerVisible = channelState?.lastSpeaker != null,
                                onToggle = { viewModel.toggleChannel(channel) },
                                onLongPress = {
                                    if (channelState != null) {
                                        viewModel.setPrimaryChannel(channel.id)
                                    }
                                },
                                onSettingsClick = {
                                    if (channelState != null) {
                                        volumeDialogChannelId = channel.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Volume dialog
        volumeDialogChannelId?.let { channelId ->
            val channelState = monitoredChannels[channelId]
            if (channelState != null) {
                ChannelVolumeDialog(
                    channelName = channelState.channelName,
                    volume = channelState.volume,
                    isMuted = channelState.isMuted,
                    onVolumeChanged = { viewModel.setChannelVolume(channelId, it) },
                    onMuteToggled = {
                        if (channelState.isMuted) viewModel.unmuteChannel(channelId)
                        else viewModel.muteChannel(channelId)
                    },
                    onDismiss = { volumeDialogChannelId = null }
                )
            }
        }

        // Button detection dialog
        if (showButtonDetection) {
            ButtonDetectionDialog(
                isOpen = true,
                detectedKeyCode = detectedKeyCode,
                detectedKeyName = detectedKeyCode?.let { keyCodeToName(it) },
                onDismiss = { viewModel.stopButtonDetection() },
                onConfirm = { viewModel.confirmDetectedButton() }
            )
        }
    }
}
