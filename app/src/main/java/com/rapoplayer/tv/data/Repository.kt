package com.rapoplayer.tv.data

import android.os.SystemClock
import androidx.room.withTransaction
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rapoplayer.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

class Repository(val db: RapoDatabase, private val crypto: Crypto, val network: Network) {
    private val dao = db.dao()
    private val mutation = Mutex()
    private var adminUntil = 0L
    private var channelGrant: String? = null
    val pinEnabled = dao.pinFlow().map { it != null }
    val source: Flow<SourceConfig?> = dao.sourceFlow().map { it?.let { decodeConfig(crypto.open(it.config, "source:${it.id}")) } }.flowOn(Dispatchers.IO)
    private fun encodeConfig(c: SourceConfig) = JsonObject().apply {
        addProperty("id", c.id); addProperty("kind", c.kind.name); addProperty("name", c.name)
        addProperty("url", c.url); addProperty("username", c.username); addProperty("password", c.password)
        addProperty("epg", c.epgUrl); addProperty("output", c.output)
    }.toString()
    private fun decodeConfig(value: String): SourceConfig {
        val o = JsonParser.parseString(value).asJsonObject
        fun s(k: String) = o[k]?.asString.orEmpty()
        return SourceConfig(s("id"), SourceKind.valueOf(s("kind")), s("name"), s("url"), s("username"), s("password"), s("epg"), s("output"))
    }
    suspend fun currentSource(): SourceConfig? = withContext(Dispatchers.IO) { dao.source()?.let { decodeConfig(crypto.open(it.config, "source:${it.id}")) } }

    suspend fun import(config: SourceConfig, progress: (Int) -> Unit = {}) = mutation.withLock {
        withContext(Dispatchers.IO) {
            if (dao.pin() != null) {
                val previous = currentSource()
                if (previous != config) requireAdmin()
            }
            val base = SafeUrl.parse(config.url)
            if (config.epgUrl.isNotBlank()) SafeUrl.parse(config.epgUrl)
            if (config.kind == SourceKind.XTREAM && (base.query != null || config.username.isBlank() || config.password.isBlank() || config.output !in setOf("m3u8", "ts")))
                throw SourceException(Failure.INPUT)
            val context = currentCoroutineContext()
            progress(1)
            val channels = if (config.kind == SourceKind.M3U) network.readResolved(config.url) { reader, resolved -> PlaylistParser.parse(reader, resolved, config.id) { context.ensureActive() } }
            else {
                val formats = network.read(api(config, null)) { XtreamParser.authenticate(it) }
                if (formats.isNotEmpty() && config.output !in formats) throw SourceException(Failure.UNSUPPORTED)
                val categories = network.read(api(config, "get_live_categories")) { XtreamParser.categories(it) }
                network.read(api(config, "get_live_streams")) { XtreamParser.channels(it, config, categories, base) { context.ensureActive() } }
            }
            progress(2)
            val entities = channels.map { c ->
                context.ensureActive()
                val secrets = JsonObject().apply { addProperty("stream", c.stream); addProperty("logo", c.logo) }.toString()
                ChannelEntity(c.id, config.id, c.name, c.category, c.epgId, crypto.seal(secrets, "channel:${c.id}"), c.providerId)
            }
            context.ensureActive()
            db.withTransaction {
                val old = dao.source()
                if (old != null && old.id != config.id) purgeSourceData()
                dao.clearChannels(config.id)
                entities.chunked(500).forEach { dao.putChannels(it) }
                dao.pruneFavorites(config.id)
                dao.putSource(SourceEntity(config.id, crypto.seal(encodeConfig(config), "source:${config.id}"), System.currentTimeMillis()))
            }
        }
    }
    fun channels(source: String): Flow<List<Channel>> = combine(dao.channels(source), dao.locks(source)) { rows, locks ->
        val channelLocks = locks.filter { it.kind == "channel" }.map { it.reference }.toHashSet()
        val categoryLocks = locks.filter { it.kind == "category" }.map { it.reference }.toHashSet()
        rows.map { row -> with(row) { Channel(id, name, category, epgId, favorite, watchedAt,
            id in channelLocks || category in categoryLocks) } }
    }.flowOn(Dispatchers.Default)

    suspend fun favorite(source: String, channel: Channel) {
        if (channel.favorite) dao.unfavorite(source, channel.id) else dao.favorite(FavoriteEntity(source, channel.id))
    }
    suspend fun watched(source: String, id: String) { dao.watched(HistoryEntity(source, id, System.currentTimeMillis())); dao.trimHistory(source) }
    suspend fun clearHistory(source: String) = dao.clearHistory(source)
    suspend fun stream(source: String, id: String): String = withContext(Dispatchers.IO) {
        val channel = dao.channel(source, id) ?: throw SourceException(Failure.EMPTY)
        if (dao.locked(source, id, channel.category) && channelGrant != id) throw SourceException(Failure.AUTH)
        JsonParser.parseString(crypto.open(channel.secret, "channel:$id")).asJsonObject["stream"].asString
    }
    suspend fun logo(source: String, id: String): String? = withContext(Dispatchers.IO) {
        val channel = dao.channel(source, id) ?: return@withContext null
        if (dao.locked(source, id, channel.category)) return@withContext null
        JsonParser.parseString(crypto.open(channel.secret, "channel:$id")).asJsonObject["logo"].asString.takeIf { it.isNotBlank() }
    }
    private fun api(c: SourceConfig, action: String?, id: String? = null): String = SafeUrl.parse(c.url).newBuilder()
        .addPathSegment("player_api.php").addQueryParameter("username", c.username).addQueryParameter("password", c.password)
        .apply { action?.let { addQueryParameter("action", it) }; id?.let { addQueryParameter("stream_id", it); addQueryParameter("limit", "20") } }.build().toString()

    fun guide(source: String, channel: String, now: Long): Flow<List<Programme>> = dao.guide(source, channel, now)
        .map { it.map { p -> Programme(p.channel, p.title, p.start, p.end) } }
    suspend fun refreshGuide(config: SourceConfig) = mutation.withLock {
        withContext(Dispatchers.IO) {
            val url = if (config.epgUrl.isNotBlank()) config.epgUrl else if (config.kind == SourceKind.XTREAM)
                SafeUrl.parse(config.url).newBuilder().addPathSegment("xmltv.php").addQueryParameter("username", config.username)
                    .addQueryParameter("password", config.password).build().toString() else throw SourceException(Failure.GUIDE)
            val channels = dao.guideMatches(config.id)
            val matching = channels.filter { it.epgId.isNotBlank() }.groupBy({ it.epgId }, { it.id })
            val (from, until) = guideWindow()
            val context = currentCoroutineContext()
            val programmes = network.read(url, 32L * 1024 * 1024) { XmlTvParser.parse(it, matching, from, until) { context.ensureActive() } }
            if (programmes.isEmpty()) throw SourceException(Failure.GUIDE)
            context.ensureActive()
            db.withTransaction {
                if (dao.source()?.id != config.id) throw CancellationException()
                dao.clearGuide(config.id)
                programmes.chunked(500).forEach { chunk -> dao.programmes(chunk.map { ProgrammeEntity(config.id, it.channelId, it.title, it.start, it.end) }) }
            }
        }
    }
    suspend fun shortGuide(config: SourceConfig, id: String) = withContext(Dispatchers.IO) {
        if (config.kind != SourceKind.XTREAM) return@withContext
        val c = dao.channel(config.id, id) ?: return@withContext
        if (c.providerId.isBlank()) return@withContext
        val (from, until) = guideWindow()
        val programmes = network.read(api(config, "get_short_epg", c.providerId), 512 * 1024) { XtreamParser.guide(it, id, from, until) }
        if (programmes.isNotEmpty()) db.withTransaction {
            if (dao.source()?.id != config.id) throw CancellationException()
            dao.clearChannelGuide(config.id, id)
            dao.programmes(programmes.map { ProgrammeEntity(config.id, id, it.title, it.start, it.end) })
            dao.pruneGuide(config.id, from, until)
            dao.boundGuide(config.id, System.currentTimeMillis())
        }
    }
    private fun guideWindow(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
        return today.atStartOfDay(zone).toInstant().toEpochMilli() to today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        if (!pin.matches(Regex("[0-9]{4,8}"))) throw SourceException(Failure.INPUT)
        if (dao.pin() != null) requireAdmin()
        val salt = PinHash.salt()
        val hash = PinHash.derive(pin.toCharArray(), salt)
        dao.putPin(PinEntity(salt = Base64.getEncoder().encodeToString(salt), verifier = Base64.getEncoder().encodeToString(hash)))
        revokeGrants()
    }
    suspend fun verifyPin(pin: String, channel: String? = null): Boolean = mutation.withLock {
        withContext(Dispatchers.IO) {
            val record = dao.pin() ?: return@withContext false
            if (System.currentTimeMillis() < record.retryAt) return@withContext false
            val matches = pin.matches(Regex("[0-9]{4,8}")) && PinHash.matches(pin.toCharArray(), Base64.getDecoder().decode(record.salt), Base64.getDecoder().decode(record.verifier))
            if (matches) {
                dao.putPin(record.copy(attempts = 0, retryAt = 0))
                adminUntil = SystemClock.elapsedRealtime() + 120_000
                channelGrant = channel
            } else {
                val attempts = (record.attempts + 1).coerceAtMost(100)
                val delay = if (attempts < 3) 0 else (1000L shl (attempts - 3).coerceAtMost(6)).coerceAtMost(60_000)
                dao.putPin(record.copy(attempts = attempts, retryAt = System.currentTimeMillis() + delay))
            }
            matches
        }
    }
    suspend fun pinRetrySeconds(): Long = ((dao.pin()?.retryAt ?: 0) - System.currentTimeMillis()).coerceAtLeast(0).let { (it + 999) / 1000 }
    fun revokeGrants() { adminUntil = 0; channelGrant = null }
    private fun requireAdmin() { if (SystemClock.elapsedRealtime() >= adminUntil) throw SourceException(Failure.AUTH) }
    suspend fun disablePin() { requireAdmin(); db.withTransaction { dao.clearLocks(); dao.clearPin() }; revokeGrants() }
    suspend fun toggleLock(source: String, kind: String, reference: String, locked: Boolean) {
        requireAdmin()
        require(kind in setOf("channel", "category"))
        if (locked) dao.unlock(source, kind, reference) else {
            if (dao.lockCount(source) >= 100_000) throw SourceException(Failure.TOO_LARGE)
            dao.lock(LockEntity(source, kind, reference))
        }
    }
    fun restrictions(source: String) = dao.locks(source)
    suspend fun clearCache(source: String) { dao.clearGuide(source) }
    suspend fun deleteSource() = mutation.withLock {
        if (dao.pin() != null) requireAdmin()
        db.withTransaction { purgeSourceData() }
        revokeGrants()
    }
    private suspend fun purgeSourceData() {
        dao.allFavorites(); dao.allHistory(); dao.allGuide(); dao.allLocks(); dao.allChannels(); dao.allSources()
    }
}
