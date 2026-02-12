package com.voiceping.android.data.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import android.view.KeyEvent
import com.voiceping.android.domain.model.AudioMixMode
import com.voiceping.android.domain.model.AudioRoute
import com.voiceping.android.domain.model.PttMode
import com.voiceping.android.domain.model.PttTargetMode
import com.voiceping.android.domain.model.VolumeKeyPttConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings repository for PTT preferences using Jetpack DataStore.
 *
 * Persists:
 * - PTT mode (PRESS_AND_HOLD or TOGGLE)
 * - Audio output route (SPEAKER, EARPIECE, BLUETOOTH)
 * - Audio tone toggles (PTT start, roger beep, RX squelch)
 * - Toggle mode max transmission duration
 * - Multi-channel monitoring: monitored channels, primary channel
 * - Scan mode: enabled, return delay, PTT target mode, audio mix mode
 * - Hardware buttons: volume key config, Bluetooth button keycode/enabled, boot auto-start, long press threshold
 *
 * Defaults:
 * - PTT mode: PRESS_AND_HOLD
 * - Audio route: SPEAKER (loudspeaker when no headset)
 * - PTT start tone: ON
 * - Roger beep: ON
 * - RX squelch: OFF
 * - Toggle max duration: 60 seconds
 * - Scan mode enabled: ON
 * - Scan return delay: 2 seconds
 * - PTT target mode: ALWAYS_PRIMARY
 * - Audio mix mode: EQUAL_VOLUME
 * - Volume key PTT config: DISABLED (user must opt-in)
 * - Bluetooth PTT button keycode: KEYCODE_MEDIA_PLAY_PAUSE (85)
 * - Bluetooth PTT enabled: OFF
 * - Boot auto-start enabled: OFF
 * - Long press threshold: 300ms
 */

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PTT_MODE = stringPreferencesKey("ptt_mode")
        val AUDIO_ROUTE = stringPreferencesKey("audio_route")
        val PTT_START_TONE_ENABLED = booleanPreferencesKey("ptt_start_tone_enabled")
        val ROGER_BEEP_ENABLED = booleanPreferencesKey("roger_beep_enabled")
        val RX_SQUELCH_ENABLED = booleanPreferencesKey("rx_squelch_enabled")
        val TOGGLE_MAX_DURATION = intPreferencesKey("toggle_max_duration")

        // Multi-channel monitoring and scan mode
        val MONITORED_CHANNEL_IDS = stringSetPreferencesKey("monitored_channel_ids")
        val PRIMARY_CHANNEL_ID = stringPreferencesKey("primary_channel_id")
        val SCAN_MODE_ENABLED = booleanPreferencesKey("scan_mode_enabled")
        val SCAN_RETURN_DELAY = intPreferencesKey("scan_return_delay")
        val PTT_TARGET_MODE = stringPreferencesKey("ptt_target_mode")
        val AUDIO_MIX_MODE = stringPreferencesKey("audio_mix_mode")

        // Hardware button settings
        val VOLUME_KEY_PTT_CONFIG = stringPreferencesKey("volume_key_ptt_config")
        val BLUETOOTH_PTT_BUTTON_KEYCODE = intPreferencesKey("bluetooth_ptt_button_keycode")
        val BLUETOOTH_PTT_ENABLED = booleanPreferencesKey("bluetooth_ptt_enabled")
        val BOOT_AUTO_START_ENABLED = booleanPreferencesKey("boot_auto_start_enabled")
        val LONG_PRESS_THRESHOLD_MS = intPreferencesKey("long_press_threshold_ms")
    }

    // PTT Mode
    suspend fun setPttMode(mode: PttMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PTT_MODE] = mode.name
        }
    }

    fun getPttMode(): Flow<PttMode> = context.dataStore.data.map { preferences ->
        val modeName = preferences[Keys.PTT_MODE] ?: PttMode.PRESS_AND_HOLD.name
        try {
            PttMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            PttMode.PRESS_AND_HOLD
        }
    }

    // Audio Route
    suspend fun setAudioRoute(route: AudioRoute) {
        context.dataStore.edit { preferences ->
            preferences[Keys.AUDIO_ROUTE] = route.name
        }
    }

    fun getAudioRoute(): Flow<AudioRoute> = context.dataStore.data.map { preferences ->
        val routeName = preferences[Keys.AUDIO_ROUTE] ?: AudioRoute.SPEAKER.name
        try {
            AudioRoute.valueOf(routeName)
        } catch (e: IllegalArgumentException) {
            AudioRoute.SPEAKER
        }
    }

    // PTT Start Tone
    suspend fun setPttStartToneEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PTT_START_TONE_ENABLED] = enabled
        }
    }

    fun getPttStartToneEnabled(): Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.PTT_START_TONE_ENABLED] ?: true
    }

    /**
     * Get PTT start tone enabled setting synchronously.
     * Safe to call from audio thread - DataStore caches after first read.
     */
    fun getCachedPttStartToneEnabled(): Boolean = runBlocking {
        context.dataStore.data.first()[Keys.PTT_START_TONE_ENABLED] ?: true
    }

    // Roger Beep
    suspend fun setRogerBeepEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.ROGER_BEEP_ENABLED] = enabled
        }
    }

    fun getRogerBeepEnabled(): Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.ROGER_BEEP_ENABLED] ?: true
    }

    /**
     * Get roger beep enabled setting synchronously.
     * Safe to call from audio thread - DataStore caches after first read.
     */
    fun getCachedRogerBeepEnabled(): Boolean = runBlocking {
        context.dataStore.data.first()[Keys.ROGER_BEEP_ENABLED] ?: true
    }

    // RX Squelch
    suspend fun setRxSquelchEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.RX_SQUELCH_ENABLED] = enabled
        }
    }

    fun getRxSquelchEnabled(): Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.RX_SQUELCH_ENABLED] ?: false
    }

    /**
     * Get RX squelch enabled setting synchronously.
     * Safe to call from audio thread - DataStore caches after first read.
     */
    fun getCachedRxSquelchEnabled(): Boolean = runBlocking {
        context.dataStore.data.first()[Keys.RX_SQUELCH_ENABLED] ?: false
    }

    // Toggle Max Duration
    suspend fun setToggleMaxDuration(durationSeconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TOGGLE_MAX_DURATION] = durationSeconds
        }
    }

    fun getToggleMaxDuration(): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[Keys.TOGGLE_MAX_DURATION] ?: 60
    }

    // Monitored Channels
    suspend fun setMonitoredChannels(channelIds: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[Keys.MONITORED_CHANNEL_IDS] = channelIds
        }
    }

    fun getMonitoredChannels(): Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[Keys.MONITORED_CHANNEL_IDS] ?: emptySet()
    }

    // Primary Channel
    suspend fun setPrimaryChannel(channelId: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PRIMARY_CHANNEL_ID] = channelId
        }
    }

    fun getPrimaryChannel(): Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[Keys.PRIMARY_CHANNEL_ID]
    }

    // Scan Mode Enabled
    suspend fun setScanModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SCAN_MODE_ENABLED] = enabled
        }
    }

    fun getScanModeEnabled(): Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.SCAN_MODE_ENABLED] ?: true
    }

    // Scan Return Delay
    suspend fun setScanReturnDelay(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SCAN_RETURN_DELAY] = seconds
        }
    }

    fun getScanReturnDelay(): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[Keys.SCAN_RETURN_DELAY] ?: 2
    }

    // PTT Target Mode
    suspend fun setPttTargetMode(mode: PttTargetMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PTT_TARGET_MODE] = mode.name
        }
    }

    fun getPttTargetMode(): Flow<PttTargetMode> = context.dataStore.data.map { preferences ->
        val modeName = preferences[Keys.PTT_TARGET_MODE] ?: PttTargetMode.ALWAYS_PRIMARY.name
        try {
            PttTargetMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            PttTargetMode.ALWAYS_PRIMARY
        }
    }

    // Audio Mix Mode
    suspend fun setAudioMixMode(mode: AudioMixMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.AUDIO_MIX_MODE] = mode.name
        }
    }

    fun getAudioMixMode(): Flow<AudioMixMode> = context.dataStore.data.map { preferences ->
        val modeName = preferences[Keys.AUDIO_MIX_MODE] ?: AudioMixMode.EQUAL_VOLUME.name
        try {
            AudioMixMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            AudioMixMode.EQUAL_VOLUME
        }
    }

    // Volume Key PTT Config
    suspend fun setVolumeKeyPttConfig(config: VolumeKeyPttConfig) {
        context.dataStore.edit { preferences ->
            preferences[Keys.VOLUME_KEY_PTT_CONFIG] = config.name
        }
    }

    fun getVolumeKeyPttConfig(): Flow<VolumeKeyPttConfig> = context.dataStore.data.map { preferences ->
        val configName = preferences[Keys.VOLUME_KEY_PTT_CONFIG] ?: VolumeKeyPttConfig.DISABLED.name
        try {
            VolumeKeyPttConfig.valueOf(configName)
        } catch (e: IllegalArgumentException) {
            VolumeKeyPttConfig.DISABLED
        }
    }

    /**
     * Get volume key PTT config synchronously.
     * Safe to call from key event handler on main thread - DataStore caches after first read.
     */
    fun getCachedVolumeKeyPttConfig(): VolumeKeyPttConfig = runBlocking {
        val configName = context.dataStore.data.first()[Keys.VOLUME_KEY_PTT_CONFIG] ?: VolumeKeyPttConfig.DISABLED.name
        try {
            VolumeKeyPttConfig.valueOf(configName)
        } catch (e: IllegalArgumentException) {
            VolumeKeyPttConfig.DISABLED
        }
    }

    // Bluetooth PTT Button Keycode
    suspend fun setBluetoothPttButtonKeycode(keycode: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.BLUETOOTH_PTT_BUTTON_KEYCODE] = keycode
        }
    }

    fun getBluetoothPttButtonKeycode(): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[Keys.BLUETOOTH_PTT_BUTTON_KEYCODE] ?: KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    }

    // Bluetooth PTT Enabled
    suspend fun setBluetoothPttEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.BLUETOOTH_PTT_ENABLED] = enabled
        }
    }

    fun getBluetoothPttEnabled(): Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.BLUETOOTH_PTT_ENABLED] ?: false
    }

    // Boot Auto-Start Enabled
    suspend fun setBootAutoStartEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.BOOT_AUTO_START_ENABLED] = enabled
        }
    }

    fun getBootAutoStartEnabled(): Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.BOOT_AUTO_START_ENABLED] ?: false
    }

    // Long Press Threshold
    suspend fun setLongPressThresholdMs(thresholdMs: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LONG_PRESS_THRESHOLD_MS] = thresholdMs
        }
    }

    fun getLongPressThresholdMs(): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[Keys.LONG_PRESS_THRESHOLD_MS] ?: 300
    }

    /**
     * Get long press threshold synchronously.
     * Safe to call from key event handler on main thread - DataStore caches after first read.
     */
    fun getCachedLongPressThresholdMs(): Int = runBlocking {
        context.dataStore.data.first()[Keys.LONG_PRESS_THRESHOLD_MS] ?: 300
    }

    /**
     * Clear all monitored channels and primary channel setting.
     * Used on logout or disconnect.
     */
    suspend fun clearMonitoredChannels() {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.MONITORED_CHANNEL_IDS)
            preferences.remove(Keys.PRIMARY_CHANNEL_ID)
        }
    }
}
