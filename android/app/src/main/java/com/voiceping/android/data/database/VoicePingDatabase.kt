package com.voiceping.android.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.voiceping.android.data.database.dao.ChannelDao
import com.voiceping.android.data.database.dao.EventDao
import com.voiceping.android.data.database.entities.ChannelEntity
import com.voiceping.android.data.database.entities.EventEntity
import com.voiceping.android.data.database.entities.TeamEntity

@Database(
    entities = [EventEntity::class, TeamEntity::class, ChannelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VoicePingDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun channelDao(): ChannelDao
}
