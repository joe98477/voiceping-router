package com.voiceping.android.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore extension property for shared access across SettingsRepository and BootReceiver.
 *
 * This extension is extracted from SettingsRepository to allow non-Hilt contexts
 * (like BroadcastReceiver) to access the same DataStore instance.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ptt_settings")
