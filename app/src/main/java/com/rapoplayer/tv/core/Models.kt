package com.rapoplayer.tv.core

import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

enum class SourceKind { M3U, XTREAM }

data class SourceConfig(
    val id: String = UUID.randomUUID().toString(),
    val kind: SourceKind,
    val name: String = "",
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val output: String = "m3u8",
) { override fun toString() = "SourceConfig([private])" }

data class ImportedChannel(
    val id: String, val name: String, val category: String,
    val epgId: String, val stream: String, val logo: String = "",
    val providerId: String = "",
) { override fun toString() = "ImportedChannel($id)" }

data class Channel(
    val id: String, val name: String, val category: String, val epgId: String,
    val favorite: Boolean = false, val watchedAt: Long = 0, val locked: Boolean = false,
)
data class Programme(val channelId: String, val title: String, val start: Long, val end: Long) {
    fun progress(now: Long = Instant.now().toEpochMilli()): Float =
        ((now - start).toDouble() / (end - start).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
}
enum class Failure {
    INPUT, NETWORK, TIMEOUT, TLS, AUTH, ACCOUNT, FORBIDDEN, HTTP, EMPTY, MALFORMED,
    TOO_LARGE, UNSUPPORTED, DECODER, CONNECTION_LIMIT, GUIDE, UNKNOWN,
}
class SourceException(val failure: Failure) : Exception(failure.name)

fun stableId(source: String, identity: String): String = MessageDigest.getInstance("SHA-256")
    .digest("$source\u0000$identity".toByteArray()).joinToString("") { "%02x".format(it) }

object Redaction {
    // Diagnostic output is deliberately an allowlist, never a sanitized exception.message.
    fun diagnostic(failure: Failure?, count: Int, version: String): String =
        "RAPOPLAYER $version | channels=${count.coerceAtLeast(0)} | status=${failure?.name ?: "OK"}"
    fun privateValue(@Suppress("UNUSED_PARAMETER") value: String) = "[private]"
}

object RetryPolicy {
    fun delayMillis(failure: Failure, attempt: Int): Long? =
        if (failure in setOf(Failure.NETWORK, Failure.TIMEOUT, Failure.HTTP) && attempt in 0..1)
            1000L shl attempt else null
}
