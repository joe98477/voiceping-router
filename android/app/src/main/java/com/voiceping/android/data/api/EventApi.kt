package com.voiceping.android.data.api

import com.voiceping.android.data.network.dto.ChannelResponse
import com.voiceping.android.data.network.dto.EventResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface EventApi {

    @GET("api/events")
    suspend fun getEvents(): List<EventResponse>

    @GET("api/events/{eventId}/channels")
    suspend fun getChannels(
        @Path("eventId") eventId: String
    ): List<ChannelResponse>
}
