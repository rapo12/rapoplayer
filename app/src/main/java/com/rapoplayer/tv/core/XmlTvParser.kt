package com.rapoplayer.tv.core

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.Reader
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object XmlTvParser {
    fun timestamp(value: String): Long? = runCatching {
        val parts = value.trim().split(Regex("\\s+"))
        // An absent XMLTV offset means UTC. Never use the device's present DST offset.
        val digits = parts[0]
        if (digits.length != 14) return null
        val date = LocalDateTime.parse(digits, DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(java.time.format.ResolverStyle.STRICT))
        val offset = if (parts.size > 1) ZoneOffset.of(parts[1]) else ZoneOffset.UTC
        date.toInstant(offset).toEpochMilli()
    }.getOrNull()

    fun parse(reader: Reader, matchingIds: Map<String, List<String>>, from: Long, until: Long,
              checkCancelled: () -> Unit = {}): List<Programme> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(reader)
        val result = ArrayList<Programme>()
        var matched = emptyList<String>()
        var start: Long? = null
        var end: Long? = null
        var title = ""
        var inTitle = false
        var depth = 0
        var programmes = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            checkCancelled()
            when (parser.eventType) {
                XmlPullParser.DOCDECL -> throw SourceException(Failure.MALFORMED)
                XmlPullParser.START_TAG -> {
                    if (++depth > 32) throw SourceException(Failure.TOO_LARGE)
                    when (parser.name) {
                        "programme" -> {
                            if (++programmes > 500_000) throw SourceException(Failure.TOO_LARGE)
                            matched = matchingIds[parser.getAttributeValue(null, "channel")].orEmpty()
                            start = timestamp(parser.getAttributeValue(null, "start").orEmpty())
                            end = timestamp(parser.getAttributeValue(null, "stop").orEmpty())
                            title = ""
                        }
                        "title" -> inTitle = true
                    }
                }
                XmlPullParser.TEXT -> if (inTitle && title.length < 512) title += parser.text.take(512 - title.length)
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "title" -> inTitle = false
                        "programme" -> {
                            val s = start; val e = end
                            if (s != null && e != null && e > s && e > from && s < until && title.isNotBlank()) {
                                matched.forEach { result += Programme(it, title, s, e) }
                                if (result.size > 100_000) throw SourceException(Failure.TOO_LARGE)
                            }
                            matched = emptyList()
                        }
                    }
                    depth--
                }
            }
            parser.nextToken() // DOCDECL is exposed and rejected; external entities are never enabled.
        }
        return result.distinctBy { "${it.channelId}:${it.start}" }
    }
}
