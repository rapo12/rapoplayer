package com.rapoplayer.tv.core

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import okhttp3.HttpUrl
import java.io.Reader
import java.time.Instant
import java.util.Base64

object XtreamParser {
    private fun JsonElement?.field(key: String): String = runCatching {
        if (this?.isJsonObject == true) asJsonObject[key]?.takeUnless { it.isJsonNull }?.asString.orEmpty() else ""
    }.getOrDefault("")
    fun authenticate(reader: Reader): List<String> {
        val root = JsonParser.parseReader(reader)
        val user = root.takeIf { it.isJsonObject }?.asJsonObject?.get("user_info")
            ?: throw SourceException(Failure.MALFORMED)
        if (!user.isJsonObject) throw SourceException(Failure.MALFORMED)
        val status = user.field("status")
        if (status.isNotBlank() && !status.equals("Active", true)) throw SourceException(Failure.ACCOUNT)
        if (user.field("auth") !in setOf("1", "true")) throw SourceException(Failure.AUTH)
        return runCatching { user.asJsonObject["allowed_output_formats"].asJsonArray.map { it.asString.lowercase() } }
            .getOrDefault(emptyList())
    }
    fun categories(reader: Reader): Map<String, String> = buildMap {
        array(reader) { element ->
            val id = element.field("category_id")
            if (id.isNotBlank()) put(id, element.field("category_name").take(128))
        }
    }
    fun channels(reader: Reader, source: SourceConfig, categories: Map<String, String>, base: HttpUrl,
                 checkCancelled: () -> Unit = {}): List<ImportedChannel> = buildList {
        val ids = HashSet<String>()
        array(reader) { item ->
            checkCancelled()
            val id = item.field("stream_id")
            val title = item.field("name").take(256)
            val type = item.field("stream_type")
            if (id.isBlank() || title.isBlank() || (type.isNotBlank() && type != "live")) return@array
            if (!ids.add(id)) return@array
            val direct = item.field("direct_source")
            val extension = item.field("container_extension").lowercase().takeIf { it in setOf("m3u8", "ts") } ?: source.output
            val stream = if (direct.isNotBlank()) SafeUrl.resolve(base, direct) ?: return@array
                else base.newBuilder().addPathSegment("live").addPathSegment(source.username)
                    .addPathSegment(source.password).addPathSegment("$id.$extension").build().toString()
            val logo = item.field("stream_icon")
            add(ImportedChannel(stableId(source.id, "xtream:$id"), title,
                categories[item.field("category_id")].orEmpty(), item.field("epg_channel_id").take(256), stream,
                if (logo.isBlank()) "" else SafeUrl.resolve(base, logo).orEmpty(), id))
        }
        if (isEmpty()) throw SourceException(Failure.EMPTY)
    }
    fun guide(reader: Reader, channel: String, from: Long, until: Long): List<Programme> {
        val root = JsonParser.parseReader(reader)
        val listings = runCatching { root.asJsonObject["epg_listings"].asJsonArray }.getOrNull() ?: return emptyList()
        return listings.take(100).mapNotNull { item ->
            val start = item.field("start_timestamp").toLongOrNull()?.times(1000) ?: return@mapNotNull null
            val end = (item.field("stop_timestamp").ifBlank { item.field("end_timestamp") }).toLongOrNull()?.times(1000) ?: return@mapNotNull null
            val encoded = item.field("title")
            val title = runCatching {
                val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(encoded))).toString()
                if (decoded.any { it.isISOControl() && it != '\n' && it != '\t' }) encoded else decoded
            }
                .getOrDefault(encoded).take(512)
            if (end <= start || end <= from || start >= until || title.isBlank()) null else Programme(channel, title, start, end)
        }.sortedBy { it.start }
    }
    private fun array(reader: Reader, onItem: (JsonElement) -> Unit) {
        JsonReader(reader).use { json ->
            if (json.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) throw SourceException(Failure.MALFORMED)
            json.beginArray()
            var count = 0
            while (json.hasNext()) {
                if (++count > PlaylistParser.MAX_CHANNELS) throw SourceException(Failure.TOO_LARGE)
                onItem(JsonParser.parseReader(json))
            }
            json.endArray()
        }
    }
}
