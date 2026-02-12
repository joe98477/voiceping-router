package com.voiceping.android.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.voiceping.android.domain.model.Team

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val eventId: String,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toDomain(): Team {
        return Team(
            id = id,
            name = name,
            eventId = eventId
        )
    }

    companion object {
        fun fromDomain(team: Team): TeamEntity {
            return TeamEntity(
                id = team.id,
                name = team.name,
                eventId = team.eventId,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
