package com.voiceping.android.data.api

import com.voiceping.android.data.network.dto.ChannelResponse
import com.voiceping.android.data.network.dto.EventResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface EventApi {

    @GET("api/events")
    suspend fun getEvents(
        @Header("Authorization") token: String
    ): List<EventResponse>

    @GET("api/events/{eventId}/channels")
    suspend fun getChannels(
        @Header("Authorization") token: String,
        @Path("eventId") eventId: String
    ): List<ChannelResponse>
}
