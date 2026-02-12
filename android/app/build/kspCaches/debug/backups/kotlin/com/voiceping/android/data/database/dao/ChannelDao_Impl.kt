package com.voiceping.android.`data`.database.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.voiceping.android.`data`.database.entities.ChannelEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ChannelDao_Impl(
  __db: RoomDatabase,
) : ChannelDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChannelEntity: EntityInsertAdapter<ChannelEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfChannelEntity = object : EntityInsertAdapter<ChannelEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `channels` (`id`,`name`,`teamId`,`teamName`,`eventId`,`lastUpdated`) VALUES (?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ChannelEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.teamId)
        statement.bindText(4, entity.teamName)
        statement.bindText(5, entity.eventId)
        statement.bindLong(6, entity.lastUpdated)
      }
    }
  }

  public override suspend fun insertAll(channels: List<ChannelEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfChannelEntity.insert(_connection, channels)
  }

  public override fun getChannelsFlow(eventId: String): Flow<List<ChannelEntity>> {
    val _sql: String = "SELECT * FROM channels WHERE eventId = ? ORDER BY teamName, name"
    return createFlow(__db, false, arrayOf("channels")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, eventId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfTeamId: Int = getColumnIndexOrThrow(_stmt, "teamId")
        val _columnIndexOfTeamName: Int = getColumnIndexOrThrow(_stmt, "teamName")
        val _columnIndexOfEventId: Int = getColumnIndexOrThrow(_stmt, "eventId")
        val _columnIndexOfLastUpdated: Int = getColumnIndexOrThrow(_stmt, "lastUpdated")
        val _result: MutableList<ChannelEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChannelEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpTeamId: String
          _tmpTeamId = _stmt.getText(_columnIndexOfTeamId)
          val _tmpTeamName: String
          _tmpTeamName = _stmt.getText(_columnIndexOfTeamName)
          val _tmpEventId: String
          _tmpEventId = _stmt.getText(_columnIndexOfEventId)
          val _tmpLastUpdated: Long
          _tmpLastUpdated = _stmt.getLong(_columnIndexOfLastUpdated)
          _item = ChannelEntity(_tmpId,_tmpName,_tmpTeamId,_tmpTeamName,_tmpEventId,_tmpLastUpdated)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getChannels(eventId: String): List<ChannelEntity> {
    val _sql: String = "SELECT * FROM channels WHERE eventId = ? ORDER BY teamName, name"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, eventId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfTeamId: Int = getColumnIndexOrThrow(_stmt, "teamId")
        val _columnIndexOfTeamName: Int = getColumnIndexOrThrow(_stmt, "teamName")
        val _columnIndexOfEventId: Int = getColumnIndexOrThrow(_stmt, "eventId")
        val _columnIndexOfLastUpdated: Int = getColumnIndexOrThrow(_stmt, "lastUpdated")
        val _result: MutableList<ChannelEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChannelEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpTeamId: String
          _tmpTeamId = _stmt.getText(_columnIndexOfTeamId)
          val _tmpTeamName: String
          _tmpTeamName = _stmt.getText(_columnIndexOfTeamName)
          val _tmpEventId: String
          _tmpEventId = _stmt.getText(_columnIndexOfEventId)
          val _tmpLastUpdated: Long
          _tmpLastUpdated = _stmt.getLong(_columnIndexOfLastUpdated)
          _item = ChannelEntity(_tmpId,_tmpName,_tmpTeamId,_tmpTeamName,_tmpEventId,_tmpLastUpdated)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteByEvent(eventId: String) {
    val _sql: String = "DELETE FROM channels WHERE eventId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, eventId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
