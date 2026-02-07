package com.voiceping.android.data.repository

import com.voiceping.android.data.api.EventApi
import com.voiceping.android.data.storage.TokenManager
import com.voiceping.android.domain.model.Channel
import com.voiceping.android.domain.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val eventApi: EventApi,
    private val tokenManager: TokenManager
) {

    suspend fun getEvents(): Result<List<Event>> = withContext(Dispatchers.IO) {
        try {
            val token = tokenManager.getToken() ?: return@withContext Result.failure(
                Exception("No authentication token")
            )

            val response = eventApi.getEvents("Bearer $token")
            val events = response.map { dto ->
                Event(
                    id = dto.id,
                    name = dto.name,
                    description = dto.description
                )
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChannelsForEvent(eventId: String): Result<List<Channel>> = withContext(Dispatchers.IO) {
        try {
            val token = tokenManager.getToken() ?: return@withContext Result.failure(
                Exception("No authentication token")
            )

            val response = eventApi.getChannels("Bearer $token", eventId)
            val channels = response.map { dto ->
                Channel(
                    id = dto.id,
                    name = dto.name,
                    teamId = dto.teamId,
                    teamName = dto.teamName
                )
            }
            Result.success(channels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
