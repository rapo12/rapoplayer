package com.rapoplayer.tv.data

import com.rapoplayer.tv.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.io.Reader
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Network(transport: OkHttpClient = OkHttpClient()) {
    // No cookies, HTTP cache, logging interceptor, authenticators or implicit redirects.
    val media: OkHttpClient = transport.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .addInterceptor(SafeRedirects()).build()
    private val catalogue = media.newBuilder().callTimeout(45, TimeUnit.SECONDS).build()
    val streaming: OkHttpClient = media.newBuilder().addInterceptor { chain ->
        val started = System.nanoTime()
        val response = chain.proceed(chain.request())
        val type = response.header("Content-Type").orEmpty().lowercase()
        val body = response.body
        if (body == null || !(response.request.url.encodedPath.endsWith(".m3u8", true) || "mpegurl" in type)) return@addInterceptor response
        val bounded = object : ForwardingSource(body.source()) {
            var total = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (System.nanoTime() - started > TimeUnit.SECONDS.toNanos(45)) throw IOException("Manifest deadline")
                val n = super.read(sink, byteCount)
                if (n > 0) total += n
                if (total > 2L * 1024 * 1024) throw IOException("Manifest limit")
                return n
            }
        }.buffer()
        response.newBuilder().body(object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source() = bounded
        }).build()
    }.build()
    val logos: OkHttpClient = media.newBuilder().callTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body ?: return@addInterceptor response
            val source = object : ForwardingSource(body.source()) {
                var read = 0L
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val n = super.read(sink, byteCount)
                    if (n > 0) read += n
                    if (read > 1024 * 1024) throw IOException("Image limit")
                    return n
                }
            }.buffer()
            response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = source
            }).build()
        }.build()

    suspend fun <T> read(url: String, maxBytes: Long = 16L * 1024 * 1024, parse: (Reader) -> T): T {
        return readResolved(url, maxBytes) { reader, _ -> parse(reader) }
    }
    suspend fun <T> readResolved(url: String, maxBytes: Long = 16L * 1024 * 1024, parse: (Reader, HttpUrl) -> T): T {
        val request = Request.Builder().url(SafeUrl.parse(url)).header("Accept-Encoding", "identity").build()
        return suspendCancellableCoroutine { continuation ->
            val call = catalogue.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(SourceException(classify(e)))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!response.isSuccessful) throw SourceException(httpFailure(response.code))
                            if (response.header("Content-Encoding").orEmpty().let { it.isNotBlank() && it != "identity" })
                                throw SourceException(Failure.UNSUPPORTED)
                            val body = response.body ?: throw SourceException(Failure.EMPTY)
                            if (body.contentLength() > maxBytes) throw SourceException(Failure.TOO_LARGE)
                            val bounded = object : java.io.FilterInputStream(body.byteStream()) {
                                var total = 0L
                                override fun read(): Int {
                                    val b = super.read(); if (b >= 0 && ++total > maxBytes) throw SourceException(Failure.TOO_LARGE); return b
                                }
                                override fun read(b: ByteArray, off: Int, len: Int): Int {
                                    val n = super.read(b, off, len)
                                    if (n > 0) total += n
                                    if (total > maxBytes) throw SourceException(Failure.TOO_LARGE)
                                    return n
                                }
                            }
                            val value = bounded.reader(Charsets.UTF_8).use { parse(it, response.request.url) }
                            if (continuation.isActive) continuation.resume(value)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(
                                if (error is SourceException) error else SourceException(if (error is IOException) classify(error) else Failure.MALFORMED))
                        }
                    }
                }
            })
        }
    }
    companion object {
        fun httpFailure(code: Int) = when (code) {
            401 -> Failure.AUTH
            403 -> Failure.FORBIDDEN
            429 -> Failure.HTTP // A rate limit does not establish a provider connection limit.
            else -> Failure.HTTP
        }
        fun classify(error: Throwable): Failure = when (error) {
            is SourceException -> error.failure
            is SocketTimeoutException -> Failure.TIMEOUT
            is SSLException -> Failure.TLS
            is UnknownHostException, is IOException -> Failure.NETWORK
            else -> Failure.UNKNOWN
        }
    }
}

class SafeRedirects : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        SafeUrl.parse(request.url.toString())
        repeat(4) { attempt ->
            val response = chain.proceed(request)
            if (response.code !in setOf(301, 302, 303, 307, 308)) return response
            val target = response.header("Location")?.let { request.url.resolve(it) }
            response.close()
            if (attempt == 3 || target == null || !allowed(request.url, target)) throw IOException("Redirect denied")
            request = request.newBuilder().url(target).removeHeader("Authorization").build()
        }
        throw IOException("Redirect limit")
    }
    companion object {
        fun allowed(from: HttpUrl, to: HttpUrl): Boolean {
            if (to.scheme !in setOf("http", "https") || to.username.isNotEmpty() ||
                to.password.isNotEmpty() || to.fragment != null || to.host != from.host) return false
            // A user can explicitly configure HTTP, but a TLS request never downgrades.
            // Permit a same-host TLS upgrade, including a provider's separate TLS port.
            return (from.scheme == to.scheme && from.port == to.port) ||
                (from.scheme == "http" && to.scheme == "https")
        }
    }
}
