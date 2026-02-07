package com.voiceping.android.data.network.dto

import com.voiceping.android.domain.model.User

data class LoginResponse(
    val token: String,
    val expiresIn: Int,
    val userId: String,
    val userName: String,
    val eventId: String?,
    val channelIds: List<String>?,
    val globalRole: String,
    val eventRole: String?
) {
    fun toUser() = User(
        id = userId,
        name = userName,
        email = "" // Email not returned from login response, stored separately
    )
}
