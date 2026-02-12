package com.voiceping.android.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "voiceping_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        encryptedPrefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getToken(): String? = encryptedPrefs.getString(KEY_JWT_TOKEN, null)

    fun isTokenExpired(): Boolean {
        val timestamp = encryptedPrefs.getLong(KEY_TOKEN_TIMESTAMP, 0)
        if (timestamp == 0L) return true
        val ageMillis = System.currentTimeMillis() - timestamp
        return ageMillis > TOKEN_TTL_MS // 1 hour = 3600000ms
    }

    fun needsRefresh(): Boolean {
        val timestamp = encryptedPrefs.getLong(KEY_TOKEN_TIMESTAMP, 0)
        if (timestamp == 0L) return false
        val ageMillis = System.currentTimeMillis() - timestamp
        return ageMillis > REFRESH_THRESHOLD_MS // 55 minutes = 3300000ms
    }

    fun saveCredentials(email: String, password: String) {
        encryptedPrefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getStoredCredentials(): Credentials? {
        val email = encryptedPrefs.getString(KEY_EMAIL, null)
        val password = encryptedPrefs.getString(KEY_PASSWORD, null)
        return if (email != null && password != null) {
            Credentials(email, password)
        } else null
    }

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    fun saveUserInfo(name: String, email: String) {
        encryptedPrefs.edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun getUserName(): String? = encryptedPrefs.getString(KEY_USER_NAME, null)

    fun getUserEmail(): String? = encryptedPrefs.getString(KEY_USER_EMAIL, null)

    companion object {
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val TOKEN_TTL_MS = 3600000L // 1 hour
        private const val REFRESH_THRESHOLD_MS = 3300000L // 55 minutes
    }
}

data class Credentials(
    val email: String,
    val password: String
)
