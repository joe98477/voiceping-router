package com.voiceping.android.data.repository

import com.voiceping.android.data.api.AuthApi
import com.voiceping.android.data.network.dto.LoginRequest
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
    private val tokenManager: TokenManager
) {
    suspend fun login(email: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authApi.login(LoginRequest(email, password))
                tokenManager.saveToken(response.token)
                tokenManager.saveCredentials(email, password)
                Result.success(response.toUser().copy(email = email))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun refreshToken(): Result<String> {
        return withContext(Dispatchers.IO) {
            val credentials = tokenManager.getStoredCredentials()
                ?: return@withContext Result.failure(Exception("No stored credentials"))

            try {
                val response = authApi.login(LoginRequest(credentials.email, credentials.password))
                tokenManager.saveToken(response.token)
                Result.success(response.token)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun refreshTokenWithRetry(): Result<String> {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null

            // Attempt refresh up to 3 times with 1-second delays
            repeat(3) { attempt ->
                val result = refreshToken()
                if (result.isSuccess) {
                    return@withContext result
                }
                lastException = result.exceptionOrNull() as? Exception

                // Wait before retry (except on last attempt)
                if (attempt < 2) {
                    delay(1000)
                }
            }

            // All retries failed - clear credentials and return failure
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

    fun hasValidSession(): Boolean {
        val token = tokenManager.getToken()
        return token != null && !tokenManager.isTokenExpired()
    }
}
