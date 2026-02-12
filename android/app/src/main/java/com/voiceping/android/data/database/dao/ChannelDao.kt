package com.voiceping.android.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voiceping.android.data.database.entities.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE eventId = :eventId ORDER BY teamName, name")
    fun getChannelsFlow(eventId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE eventId = :eventId ORDER BY teamName, name")
    suspend fun getChannels(eventId: String): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE eventId = :eventId")
    suspend fun deleteByEvent(eventId: String)
}
