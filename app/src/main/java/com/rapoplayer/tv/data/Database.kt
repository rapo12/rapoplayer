package com.rapoplayer.tv.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sources")
data class SourceEntity(@PrimaryKey val id: String, val config: String, val updatedAt: Long)
@Entity(tableName = "channels", indices = [Index("source"), Index(value = ["source", "category"]), Index(value = ["source", "name"])])
data class ChannelEntity(@PrimaryKey val id: String, val source: String, val name: String,
    val category: String, val epgId: String, val secret: String, val providerId: String)
@Entity(tableName = "favorites", primaryKeys = ["source", "channel"])
data class FavoriteEntity(val source: String, val channel: String)
@Entity(tableName = "history", primaryKeys = ["source", "channel"])
data class HistoryEntity(val source: String, val channel: String, val time: Long)
@Entity(tableName = "programmes", primaryKeys = ["source", "channel", "start"], indices = [Index(value = ["source", "channel", "end"])])
data class ProgrammeEntity(val source: String, val channel: String, val title: String, val start: Long, val end: Long)
@Entity(tableName = "locks", primaryKeys = ["source", "kind", "reference"])
data class LockEntity(val source: String, val kind: String, val reference: String)
@Entity(tableName = "parental")
data class PinEntity(@PrimaryKey val id: Int = 1, val salt: String, val verifier: String, val attempts: Int = 0, val retryAt: Long = 0)
data class ChannelRow(val id: String, val name: String, val category: String, val epgId: String, val favorite: Boolean, val watchedAt: Long)
data class GuideMatch(val id: String, val epgId: String)

@Dao
interface RapoDao {
    @Query("SELECT * FROM sources LIMIT 1") fun sourceFlow(): Flow<SourceEntity?>
    @Query("SELECT * FROM sources LIMIT 1") suspend fun source(): SourceEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSource(source: SourceEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChannels(channels: List<ChannelEntity>)
    @Query("DELETE FROM channels WHERE source = :source") suspend fun clearChannels(source: String)
    @Query("SELECT c.id, c.name, c.category, c.epgId, EXISTS(SELECT 1 FROM favorites f WHERE f.source=c.source AND f.channel=c.id) AS favorite, COALESCE((SELECT time FROM history h WHERE h.source=c.source AND h.channel=c.id),0) AS watchedAt FROM channels c WHERE c.source=:source ORDER BY c.name COLLATE NOCASE, c.id")
    fun channels(source: String): Flow<List<ChannelRow>>
    @Query("SELECT * FROM channels WHERE source=:source") suspend fun channelEntities(source: String): List<ChannelEntity>
    @Query("SELECT id, epgId FROM channels WHERE source=:source AND epgId != ''") suspend fun guideMatches(source: String): List<GuideMatch>
    @Query("SELECT * FROM channels WHERE id=:id AND source=:source") suspend fun channel(source: String, id: String): ChannelEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun favorite(favorite: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE source=:source AND channel=:channel") suspend fun unfavorite(source: String, channel: String)
    @Query("DELETE FROM favorites WHERE source=:source AND channel NOT IN (SELECT id FROM channels WHERE source=:source)") suspend fun pruneFavorites(source: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun watched(history: HistoryEntity)
    @Query("DELETE FROM history WHERE source=:source") suspend fun clearHistory(source: String)
    @Query("DELETE FROM history WHERE source=:source AND channel NOT IN (SELECT channel FROM history WHERE source=:source ORDER BY time DESC LIMIT 200)") suspend fun trimHistory(source: String)
    @Query("SELECT * FROM programmes WHERE source=:source AND channel=:channel AND end>:now ORDER BY start LIMIT 2")
    fun guide(source: String, channel: String, now: Long): Flow<List<ProgrammeEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun programmes(programmes: List<ProgrammeEntity>)
    @Query("DELETE FROM programmes WHERE source=:source") suspend fun clearGuide(source: String)
    @Query("DELETE FROM programmes WHERE source=:source AND channel=:channel") suspend fun clearChannelGuide(source: String, channel: String)
    @Query("DELETE FROM programmes WHERE source=:source AND (end<=:from OR start>=:until)") suspend fun pruneGuide(source: String, from: Long, until: Long)
    @Query("DELETE FROM programmes WHERE source=:source AND rowid NOT IN (SELECT rowid FROM programmes WHERE source=:source ORDER BY ABS(start-:now) LIMIT 100000)") suspend fun boundGuide(source: String, now: Long)
    @Query("SELECT * FROM locks WHERE source=:source") fun locks(source: String): Flow<List<LockEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM locks WHERE source=:source AND ((kind='channel' AND reference=:channel) OR (kind='category' AND reference=:category)))")
    suspend fun locked(source: String, channel: String, category: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun lock(lock: LockEntity)
    @Query("SELECT COUNT(*) FROM locks WHERE source=:source") suspend fun lockCount(source: String): Int
    @Query("DELETE FROM locks WHERE source=:source AND kind=:kind AND reference=:reference") suspend fun unlock(source: String, kind: String, reference: String)
    @Query("SELECT * FROM parental WHERE id=1") suspend fun pin(): PinEntity?
    @Query("SELECT * FROM parental WHERE id=1") fun pinFlow(): Flow<PinEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPin(pin: PinEntity)
    @Query("DELETE FROM locks") suspend fun clearLocks()
    @Query("DELETE FROM parental") suspend fun clearPin()
    @Query("DELETE FROM favorites") suspend fun allFavorites()
    @Query("DELETE FROM history") suspend fun allHistory()
    @Query("DELETE FROM programmes") suspend fun allGuide()
    @Query("DELETE FROM locks") suspend fun allLocks()
    @Query("DELETE FROM channels") suspend fun allChannels()
    @Query("DELETE FROM sources") suspend fun allSources()
}
@Database(entities = [SourceEntity::class, ChannelEntity::class, FavoriteEntity::class, HistoryEntity::class,
    ProgrammeEntity::class, LockEntity::class, PinEntity::class], version = 1, exportSchema = true)
abstract class RapoDatabase : RoomDatabase() { abstract fun dao(): RapoDao }
