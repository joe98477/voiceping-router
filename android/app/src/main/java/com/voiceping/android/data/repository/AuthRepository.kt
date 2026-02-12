package com.voiceping.android.data.repository

import com.voiceping.android.data.api.AuthApi
import com.voiceping.android.data.network.dto.LoginRequest
import com.voiceping.android.data.network.dto.RouterTokenRequest
import com.voiceping.android.data.storage.PreferencesManager
import com.voiceping.android.data.storage.TokenManager
import com.voiceping.android.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
    private val preferencesManager: PreferencesManager
) {
    /**
     * Login with email/password. Session cookie is handled automatically by CookieJar.
     * No JWT is returned from login — that comes from getRouterToken().
     */
    suspend fun login(email: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authApi.login(LoginRequest(email, password))
                tokenManager.saveUserInfo(response.displayName ?: email, email)
                tokenManager.saveCredentials(email, password)
                Result.success(response.toUser())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Get a JWT router token for WebSocket authentication.
     * Requires an active session (login first) and an eventId.
     */
    suspend fun getRouterToken(eventId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authApi.getRouterToken(RouterTokenRequest(eventId))
                tokenManager.saveToken(response.token)
                Result.success(response.token)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Refresh the session and router token using stored credentials.
     * Re-logins to establish a fresh session, then fetches a new router token.
     */
    suspend fun refreshToken(): Result<String> {
        return withContext(Dispatchers.IO) {
            val credentials = tokenManager.getStoredCredentials()
                ?: return@withContext Result.failure(Exception("No stored credentials"))

            val eventId = preferencesManager.getLastEventId()
                ?: return@withContext Result.failure(Exception("No event selected"))

            try {
                // Re-login to establish fresh session
                authApi.login(LoginRequest(credentials.email, credentials.password))
                // Get new router token
                val tokenResponse = authApi.getRouterToken(RouterTokenRequest(eventId))
                tokenManager.saveToken(tokenResponse.token)
                Result.success(tokenResponse.token)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun refreshTokenWithRetry(): Result<String> {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null

            repeat(3) { attempt ->
                val result = refreshToken()
                if (result.isSuccess) {
                    return@withContext result
                }
                lastException = result.exceptionOrNull() as? Exception

                if (attempt < 2) {
                    delay(1000)
                }
            }

            tokenManager.clearAll()
            Result.failure(
                lastException ?: Exception("Session expired. Please login again.")
            )
        }
    }

    suspend fun logout(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            tokenManager.clearAll()
            Result.success(Unit)
        }
    }

    fun hasStoredCredentials(): Boolean {
        return tokenManager.getStoredCredentials() != null
    }
}
