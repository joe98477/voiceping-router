package com.voiceping.android.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.voiceping.android.domain.model.Channel

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val teamId: String,
    val teamName: String,
    val eventId: String,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toDomain(): Channel {
        return Channel(
            id = id,
            name = name,
            teamId = teamId,
            teamName = teamName
        )
    }

    companion object {
        fun fromDomain(channel: Channel, eventId: String): ChannelEntity {
            return ChannelEntity(
                id = channel.id,
                name = channel.name,
                teamId = channel.teamId,
                teamName = channel.teamName,
                eventId = eventId,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
