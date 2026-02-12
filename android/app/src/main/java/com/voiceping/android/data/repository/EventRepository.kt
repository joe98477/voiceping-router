package com.voiceping.android.data.repository

import com.voiceping.android.data.api.EventApi
import com.voiceping.android.data.database.dao.ChannelDao
import com.voiceping.android.data.database.dao.EventDao
import com.voiceping.android.data.database.entities.ChannelEntity
import com.voiceping.android.data.database.entities.EventEntity
import com.voiceping.android.domain.model.Channel
import com.voiceping.android.domain.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val eventApi: EventApi,
    private val eventDao: EventDao,
    private val channelDao: ChannelDao
) {

    suspend fun getEvents(): Result<List<Event>> = withContext(Dispatchers.IO) {
        try {
            val response = eventApi.getEvents()
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
            val response = eventApi.getChannels(eventId)
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

    suspend fun getEventsWithCache(): Result<List<Event>> {
        return try {
            val networkResult = getEvents()
            if (networkResult.isSuccess) {
                val events = networkResult.getOrThrow()
                withContext(Dispatchers.IO) {
                    eventDao.insertAll(events.map { EventEntity.fromDomain(it) })
                }
                networkResult
            } else {
                getCachedEvents()
            }
        } catch (e: Exception) {
            val cached = getCachedEvents()
            if (cached.isSuccess && cached.getOrThrow().isNotEmpty()) {
                cached
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun getCachedEvents(): Result<List<Event>> {
        return try {
            val cached = withContext(Dispatchers.IO) {
                eventDao.getAllEvents()
            }
            Result.success(cached.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChannelsWithCache(eventId: String): Result<List<Channel>> {
        return try {
            val networkResult = getChannelsForEvent(eventId)
            if (networkResult.isSuccess) {
                val channels = networkResult.getOrThrow()
                withContext(Dispatchers.IO) {
                    channelDao.deleteByEvent(eventId)
                    channelDao.insertAll(channels.map { ChannelEntity.fromDomain(it, eventId) })
                }
                networkResult
            } else {
                getCachedChannels(eventId)
            }
        } catch (e: Exception) {
            val cached = getCachedChannels(eventId)
            if (cached.isSuccess && cached.getOrThrow().isNotEmpty()) {
                cached
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun getCachedChannels(eventId: String): Result<List<Channel>> {
        return try {
            val cached = withContext(Dispatchers.IO) {
                channelDao.getChannels(eventId)
            }
            Result.success(cached.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeChannels(eventId: String): Flow<List<Channel>> {
        return channelDao.getChannelsFlow(eventId)
            .map { entities -> entities.map { it.toDomain() } }
    }
}
