package com.rapoplayer.tv.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.BufferedReader
import java.io.Reader

object SafeUrl {
    fun parse(value: String): HttpUrl {
        val url = value.trim().toHttpUrlOrNull() ?: throw SourceException(Failure.INPUT)
        if (url.scheme !in setOf("http", "https") || url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null)
            throw SourceException(Failure.INPUT)
        return url
    }
    fun resolve(base: HttpUrl, value: String): String? = runCatching {
        val resolved = base.resolve(value.trim()) ?: return null
        parse(resolved.toString()).toString()
    }.getOrNull()
}

object PlaylistParser {
    private val attribute = Regex("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s]+))")
    const val MAX_CHANNELS = 50_000
    private const val MAX_LINE = 16_384

    // Identity: unique tvg-id, otherwise normalized absolute stream URL. Duplicate tvg-id
    // entries get a URL-derived suffix. Sorting by URL makes refresh order irrelevant.
    fun parse(reader: Reader, base: HttpUrl, source: String, checkCancelled: () -> Unit = {}): List<ImportedChannel> {
        val input = if (reader is BufferedReader) reader else reader.buffered()
        val channels = ArrayList<ImportedChannel>()
        var attributes = emptyMap<String, String>()
        var name = ""
        var pending = false
        var header = false
        var lineNumber = 0
        while (true) {
            checkCancelled()
            val line = boundedLine(input)?.trim()?.trimStart('\uFEFF') ?: break
            lineNumber++
            if (line.isBlank()) continue
            if (line.startsWith("#EXTM3U", true)) { header = true; continue }
            if (line.startsWith("#EXTINF:", true)) {
                val comma = metadataComma(line)
                attributes = attribute.findAll(if (comma >= 0) line.substring(0, comma) else line)
                    .associate { it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty() }
                name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                pending = true
                continue
            }
            if (line.startsWith('#')) continue
            if (!pending) continue
            pending = false
            // Explicit VOD declarations and common file extensions are omitted. Ambiguous
            // negative/zero EXTINF durations are treated as live; duration alone isn't reliable.
            val explicitType = attributes["type"].orEmpty().lowercase()
            val path = base.resolve(line)?.encodedPath.orEmpty().lowercase()
            if (explicitType in setOf("vod", "movie", "series") || Regex("\\.(mp4|mkv|avi|mov|webm)$").containsMatchIn(path)) continue
            val stream = SafeUrl.resolve(base, line) ?: continue
            val title = (name.ifBlank { attributes["tvg-name"].orEmpty() }).take(256)
            if (title.isBlank()) continue
            val epg = attributes["tvg-id"].orEmpty().trim().take(256)
            channels += ImportedChannel("", title, attributes["group-title"].orEmpty().trim().take(128), epg,
                stream, SafeUrl.resolve(base, attributes["tvg-logo"].orEmpty()).orEmpty().takeIf { attributes["tvg-logo"].orEmpty().isNotBlank() }.orEmpty())
            if (channels.size > MAX_CHANNELS) throw SourceException(Failure.TOO_LARGE)
        }
        if (!header || lineNumber == 0) throw SourceException(Failure.MALFORMED)
        if (channels.isEmpty()) throw SourceException(Failure.EMPTY)
        val unique = channels.distinctBy { "${it.epgId}\u0000${it.stream}" }
        val counts = unique.filter { it.epgId.isNotBlank() }.groupingBy { it.epgId }.eachCount()
        return unique.map { c ->
            val identity = when {
                c.epgId.isBlank() -> "url:${c.stream}"
                counts[c.epgId] == 1 -> "epg:${c.epgId}"
                else -> "epg:${c.epgId}|url:${c.stream}"
            }
            c.copy(id = stableId(source, identity))
        }.distinctBy { it.id }
    }
    private fun metadataComma(line: String): Int {
        var quote: Char? = null
        line.forEachIndexed { index, c ->
            if (c == '"' || c == '\'') { if (quote == c) quote = null else if (quote == null) quote = c }
            if (c == ',' && quote == null) return index
        }
        return -1
    }
    private fun boundedLine(reader: Reader): String? {
        val line = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c == -1) return line.toString().takeIf { it.isNotEmpty() }
            if (c == 10) return line.toString().trimEnd('\r')
            line.append(c.toChar())
            if (line.length > MAX_LINE) throw SourceException(Failure.TOO_LARGE)
        }
    }
}
