package com.voiceping.android.presentation.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import com.voiceping.android.domain.model.AudioMixMode
import com.voiceping.android.domain.model.AudioRoute
import com.voiceping.android.domain.model.PttMode
import com.voiceping.android.domain.model.PttTargetMode
import com.voiceping.android.domain.model.VolumeKeyPttConfig
import com.voiceping.android.presentation.settings.keyCodeToName
import kotlin.math.roundToInt

@Composable
fun ProfileDrawer(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    userName: String,
    userEmail: String,
    appVersion: String = "1.0.0",
    pttMode: PttMode = PttMode.PRESS_AND_HOLD,
    audioRoute: AudioRoute = AudioRoute.SPEAKER,
    toggleMaxDuration: Int = 60,
    pttStartToneEnabled: Boolean = true,
    rogerBeepEnabled: Boolean = true,
    rxSquelchEnabled: Boolean = false,
    onPttModeChanged: (PttMode) -> Unit = {},
    onAudioRouteChanged: (AudioRoute) -> Unit = {},
    onToggleMaxDurationChanged: (Int) -> Unit = {},
    onPttStartToneChanged: (Boolean) -> Unit = {},
    onRogerBeepChanged: (Boolean) -> Unit = {},
    onRxSquelchChanged: (Boolean) -> Unit = {},
    // Scan mode settings
    scanModeEnabled: Boolean = true,
    pttTargetMode: PttTargetMode = PttTargetMode.ALWAYS_PRIMARY,
    scanReturnDelay: Int = 2,
    audioMixMode: AudioMixMode = AudioMixMode.EQUAL_VOLUME,
    onScanModeEnabledChanged: (Boolean) -> Unit = {},
    onPttTargetModeChanged: (PttTargetMode) -> Unit = {},
    onScanReturnDelayChanged: (Int) -> Unit = {},
    onAudioMixModeChanged: (AudioMixMode) -> Unit = {},
    // Hardware button settings
    volumeKeyPttConfig: VolumeKeyPttConfig = VolumeKeyPttConfig.DISABLED,
    bluetoothPttEnabled: Boolean = false,
    bluetoothPttButtonKeycode: Int = 85, // KEYCODE_MEDIA_PLAY_PAUSE
    bootAutoStartEnabled: Boolean = false,
    onVolumeKeyPttConfigChanged: (VolumeKeyPttConfig) -> Unit = {},
    onBluetoothPttEnabledChanged: (Boolean) -> Unit = {},
    onDetectBluetoothButton: () -> Unit = {},
    onBootAutoStartChanged: (Boolean) -> Unit = {},
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

                        // PTT Settings Section
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "PTT Settings",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // PTT Mode
                        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = pttMode == PttMode.PRESS_AND_HOLD,
                                        onClick = { onPttModeChanged(PttMode.PRESS_AND_HOLD) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = pttMode == PttMode.PRESS_AND_HOLD,
                                    onClick = { onPttModeChanged(PttMode.PRESS_AND_HOLD) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Press and Hold")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = pttMode == PttMode.TOGGLE,
                                        onClick = { onPttModeChanged(PttMode.TOGGLE) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = pttMode == PttMode.TOGGLE,
                                    onClick = { onPttModeChanged(PttMode.TOGGLE) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Toggle")
                            }

                            // Toggle max duration slider (only show when TOGGLE mode selected)
                            if (pttMode == PttMode.TOGGLE) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Max Duration: $toggleMaxDuration seconds",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = toggleMaxDuration.toFloat(),
                                    onValueChange = { onToggleMaxDurationChanged(it.roundToInt()) },
                                    valueRange = 30f..120f,
                                    steps = 17, // (120-30)/5 - 1 = 17 steps for 5-second increments
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()

                        // Audio Output Section
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Audio Output",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = audioRoute == AudioRoute.SPEAKER,
                                        onClick = { onAudioRouteChanged(AudioRoute.SPEAKER) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = audioRoute == AudioRoute.SPEAKER,
                                    onClick = { onAudioRouteChanged(AudioRoute.SPEAKER) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Speaker")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = audioRoute == AudioRoute.EARPIECE,
                                        onClick = { onAudioRouteChanged(AudioRoute.EARPIECE) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = audioRoute == AudioRoute.EARPIECE,
                                    onClick = { onAudioRouteChanged(AudioRoute.EARPIECE) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Earpiece")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()

                        // Audio Tones Section
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Audio Tones",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // PTT Start Tone
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PTT Start Tone")
                            Switch(
                                checked = pttStartToneEnabled,
                                onCheckedChange = onPttStartToneChanged
                            )
                        }

                        // Roger Beep
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Roger Beep")
                            Switch(
                                checked = rogerBeepEnabled,
                                onCheckedChange = onRogerBeepChanged
                            )
                        }

                        // RX Squelch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("RX Squelch")
                            Switch(
                                checked = rxSquelchEnabled,
                                onCheckedChange = onRxSquelchChanged
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()

                        // Hardware Buttons Section
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Hardware Buttons",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Volume Key PTT subsection
                        Text(
                            text = "Volume Key PTT",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = volumeKeyPttConfig == VolumeKeyPttConfig.DISABLED,
                                        onClick = { onVolumeKeyPttConfigChanged(VolumeKeyPttConfig.DISABLED) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = volumeKeyPttConfig == VolumeKeyPttConfig.DISABLED,
                                    onClick = { onVolumeKeyPttConfigChanged(VolumeKeyPttConfig.DISABLED) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Disabled")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = volumeKeyPttConfig == VolumeKeyPttConfig.VOLUME_UP,
                                        onClick = { onVolumeKeyPttConfigChanged(VolumeKeyPttConfig.VOLUME_UP) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = volumeKeyPttConfig == VolumeKeyPttConfig.VOLUME_UP,
                                    onClick = { onVolumeKeyPttConfigChanged(VolumeKeyPttConfig.VOLUME_UP) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Volume Up")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = volumeKeyPttConfig == VolumeKeyPttConfig.VOLUME_DOWN,
                                        onClick = { onVolumeKeyPttConfigChanged(VolumeKeyPttConfig.VOLUME_DOWN) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = volumeKeyPttConfig == VolumeKeyPttConfig.VOLUME_DOWN,
                                    onClick = { onVolumeKeyPttConfigChanged(VolumeKeyPttConfig.VOLUME_DOWN) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Volume Down")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = volumeKeyPttConfig == VolumeKeyPttConfig.BOTH,
                                        onClick = { onVolumeKeyPttConfigChanged(VolumeKeyPttConfig.BOTH) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = volumeKeyPttConfig == VolumeKeyPttConfig.BOTH,
                                    onClick = { onVolumeKeyPttConfigChanged(VolumeKeyPttConfig.BOTH) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Both Keys")
                            }

                            // Helper text
                            Text(
                                text = "Long-press activates PTT, short tap adjusts volume",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bluetooth PTT subsection
                        Text(
                            text = "Bluetooth PTT Button",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Bluetooth PTT")
                            Switch(
                                checked = bluetoothPttEnabled,
                                onCheckedChange = onBluetoothPttEnabledChanged
                            )
                        }

                        // Show configured button and detect button when BT PTT enabled
                        if (bluetoothPttEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Button: ${keyCodeToName(bluetoothPttButtonKeycode)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = onDetectBluetoothButton) {
                                    Text("Detect Button")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()

                        // Boot Auto-Start subsection
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Startup",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Start on boot")
                                Text(
                                    text = "Auto-start when device powers on",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = bootAutoStartEnabled,
                                onCheckedChange = onBootAutoStartChanged
                            )
                        }

                        if (bootAutoStartEnabled) {
                            Text(
                                text = "Android 15+ shows notification instead of auto-starting",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()

                        // Scan Mode Section
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scan Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Scan Mode Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Auto-switch channels")
                                Text(
                                    text = "Bottom bar follows active speaker",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = scanModeEnabled,
                                onCheckedChange = onScanModeEnabledChanged
                            )
                        }

                        // PTT Target Mode (only visible when scan mode enabled)
                        if (scanModeEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "PTT Target",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = pttTargetMode == PttTargetMode.ALWAYS_PRIMARY,
                                            onClick = { onPttTargetModeChanged(PttTargetMode.ALWAYS_PRIMARY) }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = pttTargetMode == PttTargetMode.ALWAYS_PRIMARY,
                                        onClick = { onPttTargetModeChanged(PttTargetMode.ALWAYS_PRIMARY) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Always primary")
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = pttTargetMode == PttTargetMode.DISPLAYED_CHANNEL,
                                            onClick = { onPttTargetModeChanged(PttTargetMode.DISPLAYED_CHANNEL) }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = pttTargetMode == PttTargetMode.DISPLAYED_CHANNEL,
                                        onClick = { onPttTargetModeChanged(PttTargetMode.DISPLAYED_CHANNEL) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Displayed channel")
                                }
                            }

                            // Return Delay Slider (only visible when scan mode enabled)
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    text = "Return delay: ${scanReturnDelay}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = scanReturnDelay.toFloat(),
                                    onValueChange = { onScanReturnDelayChanged(it.roundToInt()) },
                                    valueRange = 2f..5f,
                                    steps = 2, // For 2, 3, 4, 5 (3 steps between)
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Audio Mix Mode
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Audio Mix",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = audioMixMode == AudioMixMode.EQUAL_VOLUME,
                                        onClick = { onAudioMixModeChanged(AudioMixMode.EQUAL_VOLUME) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = audioMixMode == AudioMixMode.EQUAL_VOLUME,
                                    onClick = { onAudioMixModeChanged(AudioMixMode.EQUAL_VOLUME) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Equal volume")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = audioMixMode == AudioMixMode.PRIMARY_PRIORITY,
                                        onClick = { onAudioMixModeChanged(AudioMixMode.PRIMARY_PRIORITY) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = audioMixMode == AudioMixMode.PRIMARY_PRIORITY,
                                    onClick = { onAudioMixModeChanged(AudioMixMode.PRIMARY_PRIORITY) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Primary priority")
                                    Text(
                                        text = "Non-primary channels play quieter",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
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
