package com.voiceping.android.`data`.database

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.voiceping.android.`data`.database.dao.ChannelDao
import com.voiceping.android.`data`.database.dao.ChannelDao_Impl
import com.voiceping.android.`data`.database.dao.EventDao
import com.voiceping.android.`data`.database.dao.EventDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class VoicePingDatabase_Impl : VoicePingDatabase() {
  private val _eventDao: Lazy<EventDao> = lazy {
    EventDao_Impl(this)
  }

  private val _channelDao: Lazy<ChannelDao> = lazy {
    ChannelDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "7dc18a49b02fe8788b9f5f17d4b09847", "189656aeb3d51ebb4e6e32c6aef63296") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `teams` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `eventId` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `channels` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `teamId` TEXT NOT NULL, `teamName` TEXT NOT NULL, `eventId` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7dc18a49b02fe8788b9f5f17d4b09847')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `events`")
        connection.execSQL("DROP TABLE IF EXISTS `teams`")
        connection.execSQL("DROP TABLE IF EXISTS `channels`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsEvents: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsEvents.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEvents.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEvents.put("description", TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEvents.put("lastUpdated", TableInfo.Column("lastUpdated", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysEvents: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesEvents: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoEvents: TableInfo = TableInfo("events", _columnsEvents, _foreignKeysEvents, _indicesEvents)
        val _existingEvents: TableInfo = read(connection, "events")
        if (!_infoEvents.equals(_existingEvents)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |events(com.voiceping.android.data.database.entities.EventEntity).
              | Expected:
              |""".trimMargin() + _infoEvents + """
              |
              | Found:
              |""".trimMargin() + _existingEvents)
        }
        val _columnsTeams: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTeams.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTeams.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTeams.put("eventId", TableInfo.Column("eventId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTeams.put("lastUpdated", TableInfo.Column("lastUpdated", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTeams: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTeams: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoTeams: TableInfo = TableInfo("teams", _columnsTeams, _foreignKeysTeams, _indicesTeams)
        val _existingTeams: TableInfo = read(connection, "teams")
        if (!_infoTeams.equals(_existingTeams)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |teams(com.voiceping.android.data.database.entities.TeamEntity).
              | Expected:
              |""".trimMargin() + _infoTeams + """
              |
              | Found:
              |""".trimMargin() + _existingTeams)
        }
        val _columnsChannels: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsChannels.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("teamId", TableInfo.Column("teamId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("teamName", TableInfo.Column("teamName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("eventId", TableInfo.Column("eventId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChannels.put("lastUpdated", TableInfo.Column("lastUpdated", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysChannels: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesChannels: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoChannels: TableInfo = TableInfo("channels", _columnsChannels, _foreignKeysChannels, _indicesChannels)
        val _existingChannels: TableInfo = read(connection, "channels")
        if (!_infoChannels.equals(_existingChannels)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |channels(com.voiceping.android.data.database.entities.ChannelEntity).
              | Expected:
              |""".trimMargin() + _infoChannels + """
              |
              | Found:
              |""".trimMargin() + _existingChannels)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "events", "teams", "channels")
  }

  public override fun clearAllTables() {
    super.performClear(false, "events", "teams", "channels")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(EventDao::class, EventDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ChannelDao::class, ChannelDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun eventDao(): EventDao = _eventDao.value

  public override fun channelDao(): ChannelDao = _channelDao.value
}
