package com.voiceping.android.data.network.dto

import com.voiceping.android.domain.model.User

/**
 * Matches control-plane POST /api/auth/login response.
 * Authentication is session-based (cookies), not JWT.
 */
data class LoginResponse(
    val id: String,
    val email: String,
    val displayName: String?,
    val globalRole: String,
    val mustChangePassword: Boolean
) {
    fun toUser() = User(
        id = id,
        name = displayName ?: email,
        email = email
    )
}
