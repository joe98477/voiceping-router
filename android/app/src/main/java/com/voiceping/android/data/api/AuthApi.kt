package com.voiceping.android.data.api

import com.voiceping.android.data.network.dto.LoginRequest
import com.voiceping.android.data.network.dto.LoginResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}
