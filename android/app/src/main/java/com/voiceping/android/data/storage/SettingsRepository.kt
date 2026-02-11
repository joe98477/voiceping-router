package com.voiceping.android.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voiceping.android.domain.model.AudioMixMode
import com.voiceping.android.domain.model.AudioRoute
import com.voiceping.android.domain.model.PttMode
import com.voiceping.android.domain.model.PttTargetMode
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
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ptt_settings")

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
