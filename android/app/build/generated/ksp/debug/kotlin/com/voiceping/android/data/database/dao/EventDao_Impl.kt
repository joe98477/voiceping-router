package com.voiceping.android.`data`.database.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.voiceping.android.`data`.database.entities.EventEntity
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
public class EventDao_Impl(
  __db: RoomDatabase,
) : EventDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfEventEntity: EntityInsertAdapter<EventEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfEventEntity = object : EntityInsertAdapter<EventEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `events` (`id`,`name`,`description`,`lastUpdated`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: EventEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.description)
        statement.bindLong(4, entity.lastUpdated)
      }
    }
  }

  public override suspend fun insertAll(events: List<EventEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfEventEntity.insert(_connection, events)
  }

  public override fun getAllEventsFlow(): Flow<List<EventEntity>> {
    val _sql: String = "SELECT * FROM events ORDER BY name"
    return createFlow(__db, false, arrayOf("events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLastUpdated: Int = getColumnIndexOrThrow(_stmt, "lastUpdated")
        val _result: MutableList<EventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: EventEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLastUpdated: Long
          _tmpLastUpdated = _stmt.getLong(_columnIndexOfLastUpdated)
          _item = EventEntity(_tmpId,_tmpName,_tmpDescription,_tmpLastUpdated)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllEvents(): List<EventEntity> {
    val _sql: String = "SELECT * FROM events ORDER BY name"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLastUpdated: Int = getColumnIndexOrThrow(_stmt, "lastUpdated")
        val _result: MutableList<EventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: EventEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLastUpdated: Long
          _tmpLastUpdated = _stmt.getLong(_columnIndexOfLastUpdated)
          _item = EventEntity(_tmpId,_tmpName,_tmpDescription,_tmpLastUpdated)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM events"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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
