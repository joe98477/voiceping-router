package com.voiceping.android.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voiceping.android.domain.model.AudioRoute
import com.voiceping.android.domain.model.PttMode
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
 *
 * Defaults:
 * - PTT mode: PRESS_AND_HOLD
 * - Audio route: SPEAKER (loudspeaker when no headset)
 * - PTT start tone: ON
 * - Roger beep: ON
 * - RX squelch: OFF
 * - Toggle max duration: 60 seconds
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
}
