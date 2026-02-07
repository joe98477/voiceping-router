package com.voiceping.android.data.storage

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "voiceping_prefs",
        Context.MODE_PRIVATE
    )

    fun saveLastEventId(eventId: String) {
        prefs.edit().putString(KEY_LAST_EVENT_ID, eventId).apply()
    }

    fun getLastEventId(): String? = prefs.getString(KEY_LAST_EVENT_ID, null)

    fun clearLastEventId() {
        prefs.edit().remove(KEY_LAST_EVENT_ID).apply()
    }

    companion object {
        private const val KEY_LAST_EVENT_ID = "last_event_id"
    }
}
