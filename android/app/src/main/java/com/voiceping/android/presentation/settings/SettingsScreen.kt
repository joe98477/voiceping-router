package com.voiceping.android.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voiceping.android.domain.model.AudioMixMode
import com.voiceping.android.domain.model.AudioRoute
import com.voiceping.android.domain.model.PttMode
import com.voiceping.android.domain.model.PttTargetMode
import com.voiceping.android.domain.model.VolumeKeyPttConfig
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToButtonDetection: () -> Unit = {}
) {
    // Collect all settings state
    val pttMode by viewModel.pttMode.collectAsState()
    val toggleMaxDuration by viewModel.toggleMaxDuration.collectAsState()
    val audioRoute by viewModel.audioRoute.collectAsState()
    val pttStartToneEnabled by viewModel.pttStartToneEnabled.collectAsState()
    val rogerBeepEnabled by viewModel.rogerBeepEnabled.collectAsState()
    val rxSquelchEnabled by viewModel.rxSquelchEnabled.collectAsState()
    val scanModeEnabled by viewModel.scanModeEnabled.collectAsState()
    val scanReturnDelay by viewModel.scanReturnDelay.collectAsState()
    val pttTargetMode by viewModel.pttTargetMode.collectAsState()
    val audioMixMode by viewModel.audioMixMode.collectAsState()
    val volumeKeyPttConfig by viewModel.volumeKeyPttConfig.collectAsState()
    val bluetoothPttEnabled by viewModel.bluetoothPttEnabled.collectAsState()
    val bluetoothPttButtonKeycode by viewModel.bluetoothPttButtonKeycode.collectAsState()
    val bootAutoStartEnabled by viewModel.bootAutoStartEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // PTT Settings Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "PTT Settings",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // PTT Mode
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = pttMode == PttMode.PRESS_AND_HOLD,
                                onClick = { viewModel.setPttMode(PttMode.PRESS_AND_HOLD) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = pttMode == PttMode.PRESS_AND_HOLD,
                            onClick = { viewModel.setPttMode(PttMode.PRESS_AND_HOLD) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Press and Hold")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = pttMode == PttMode.TOGGLE,
                                onClick = { viewModel.setPttMode(PttMode.TOGGLE) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = pttMode == PttMode.TOGGLE,
                            onClick = { viewModel.setPttMode(PttMode.TOGGLE) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Toggle")
                    }
                }
            }

            // Toggle Max Duration (only show when TOGGLE mode selected)
            if (pttMode == PttMode.TOGGLE) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Max Duration: $toggleMaxDuration seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = toggleMaxDuration.toFloat(),
                            onValueChange = { viewModel.setToggleMaxDuration(it.roundToInt()) },
                            valueRange = 30f..120f,
                            steps = 17,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
            }

            // Audio Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Audio",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Audio Output
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = audioRoute == AudioRoute.SPEAKER,
                                onClick = { viewModel.setAudioRoute(AudioRoute.SPEAKER) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = audioRoute == AudioRoute.SPEAKER,
                            onClick = { viewModel.setAudioRoute(AudioRoute.SPEAKER) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Speaker")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = audioRoute == AudioRoute.EARPIECE,
                                onClick = { viewModel.setAudioRoute(AudioRoute.EARPIECE) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = audioRoute == AudioRoute.EARPIECE,
                            onClick = { viewModel.setAudioRoute(AudioRoute.EARPIECE) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Earpiece")
                    }
                }
            }

            // Audio Tone Switches
            item {
                ListItem(
                    headlineContent = { Text("PTT Start Tone") },
                    trailingContent = {
                        Switch(
                            checked = pttStartToneEnabled,
                            onCheckedChange = { viewModel.setPttStartToneEnabled(it) }
                        )
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Roger Beep") },
                    trailingContent = {
                        Switch(
                            checked = rogerBeepEnabled,
                            onCheckedChange = { viewModel.setRogerBeepEnabled(it) }
                        )
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("RX Squelch") },
                    trailingContent = {
                        Switch(
                            checked = rxSquelchEnabled,
                            onCheckedChange = { viewModel.setRxSquelchEnabled(it) }
                        )
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
            }

            // Scan Mode Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scan Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                ListItem(
                    headlineContent = { Text("Auto-switch channels") },
                    supportingContent = { Text("Bottom bar follows active speaker") },
                    trailingContent = {
                        Switch(
                            checked = scanModeEnabled,
                            onCheckedChange = { viewModel.setScanModeEnabled(it) }
                        )
                    }
                )
            }

            // PTT Target Mode (only visible when scan mode enabled)
            if (scanModeEnabled) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "PTT Target",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = pttTargetMode == PttTargetMode.ALWAYS_PRIMARY,
                                    onClick = { viewModel.setPttTargetMode(PttTargetMode.ALWAYS_PRIMARY) }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pttTargetMode == PttTargetMode.ALWAYS_PRIMARY,
                                onClick = { viewModel.setPttTargetMode(PttTargetMode.ALWAYS_PRIMARY) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Always primary")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = pttTargetMode == PttTargetMode.DISPLAYED_CHANNEL,
                                    onClick = { viewModel.setPttTargetMode(PttTargetMode.DISPLAYED_CHANNEL) }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pttTargetMode == PttTargetMode.DISPLAYED_CHANNEL,
                                onClick = { viewModel.setPttTargetMode(PttTargetMode.DISPLAYED_CHANNEL) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Displayed channel")
                        }
                    }
                }

                // Return Delay Slider
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Return delay: ${scanReturnDelay}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = scanReturnDelay.toFloat(),
                            onValueChange = { viewModel.setScanReturnDelay(it.roundToInt()) },
                            valueRange = 2f..5f,
                            steps = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Audio Mix Mode
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Audio Mix",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = audioMixMode == AudioMixMode.EQUAL_VOLUME,
                                onClick = { viewModel.setAudioMixMode(AudioMixMode.EQUAL_VOLUME) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = audioMixMode == AudioMixMode.EQUAL_VOLUME,
                            onClick = { viewModel.setAudioMixMode(AudioMixMode.EQUAL_VOLUME) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Equal volume")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = audioMixMode == AudioMixMode.PRIMARY_PRIORITY,
                                onClick = { viewModel.setAudioMixMode(AudioMixMode.PRIMARY_PRIORITY) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = audioMixMode == AudioMixMode.PRIMARY_PRIORITY,
                            onClick = { viewModel.setAudioMixMode(AudioMixMode.PRIMARY_PRIORITY) }
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
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
            }

            // Hardware Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Hardware",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Volume Key PTT
            item {
                Text(
                    text = "Volume Key PTT",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = volumeKeyPttConfig == VolumeKeyPttConfig.DISABLED,
                                onClick = { viewModel.setVolumeKeyPttConfig(VolumeKeyPttConfig.DISABLED) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = volumeKeyPttConfig == VolumeKeyPttConfig.DISABLED,
                            onClick = { viewModel.setVolumeKeyPttConfig(VolumeKeyPttConfig.DISABLED) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disabled")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = volumeKeyPttConfig == VolumeKeyPttConfig.VOLUME_UP,
                                onClick = { viewModel.setVolumeKeyPttConfig(VolumeKeyPttConfig.VOLUME_UP) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = volumeKeyPttConfig == VolumeKeyPttConfig.VOLUME_UP,
                            onClick = { viewModel.setVolumeKeyPttConfig(VolumeKeyPttConfig.VOLUME_UP) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Volume Up")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = volumeKeyPttConfig == VolumeKeyPttConfig.VOLUME_DOWN,
                                onClick = { viewModel.setVolumeKeyPttConfig(VolumeKeyPttConfig.VOLUME_DOWN) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = volumeKeyPttConfig == VolumeKeyPttConfig.VOLUME_DOWN,
                            onClick = { viewModel.setVolumeKeyPttConfig(VolumeKeyPttConfig.VOLUME_DOWN) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Volume Down")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = volumeKeyPttConfig == VolumeKeyPttConfig.BOTH,
                                onClick = { viewModel.setVolumeKeyPttConfig(VolumeKeyPttConfig.BOTH) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = volumeKeyPttConfig == VolumeKeyPttConfig.BOTH,
                            onClick = { viewModel.setVolumeKeyPttConfig(VolumeKeyPttConfig.BOTH) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Both Keys")
                    }

                    Text(
                        text = "Long-press activates PTT, short tap adjusts volume",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 48.dp, top = 4.dp)
                    )
                }
            }

            // Bluetooth PTT
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bluetooth PTT",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Enable Bluetooth PTT") },
                    trailingContent = {
                        Switch(
                            checked = bluetoothPttEnabled,
                            onCheckedChange = { viewModel.setBluetoothPttEnabled(it) }
                        )
                    }
                )
            }

            if (bluetoothPttEnabled) {
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "Button: ${keyCodeToName(bluetoothPttButtonKeycode)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = onNavigateToButtonDetection) {
                                Text("Detect Button")
                            }
                        }
                    )
                }
            }

            // Boot Auto-Start
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Start on boot") },
                    supportingContent = { Text("Auto-start when device powers on") },
                    trailingContent = {
                        Switch(
                            checked = bootAutoStartEnabled,
                            onCheckedChange = { viewModel.setBootAutoStartEnabled(it) }
                        )
                    }
                )
            }

            if (bootAutoStartEnabled) {
                item {
                    Text(
                        text = "Android 15+ shows notification instead of auto-starting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
