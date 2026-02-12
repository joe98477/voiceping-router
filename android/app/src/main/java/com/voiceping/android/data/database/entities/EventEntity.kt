package com.voiceping.android.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.voiceping.android.domain.model.Event

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toDomain(): Event {
        return Event(
            id = id,
            name = name,
            description = description
        )
    }

    companion object {
        fun fromDomain(event: Event): EventEntity {
            return EventEntity(
                id = event.id,
                name = event.name,
                description = event.description ?: "",
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
