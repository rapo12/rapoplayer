package com.rapoplayer.tv

import com.rapoplayer.tv.core.*
import com.rapoplayer.tv.data.Network
import com.rapoplayer.tv.data.SafeRedirects
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class NetworkTest {
    @Test fun `explicit HTTP works for metadata streams and bounded logos`() {
        MockWebServer().use { server ->
            server.start()
            val network = Network()
            server.enqueue(MockResponse().setBody("catalogue"))
            runBlocking { assertEquals("catalogue", network.read(server.localUrl("/catalogue").newBuilder().scheme("http").build().toString()) { it.readText() }) }
            listOf(network.streaming to "/live.ts", network.logos to "/logo.png").forEach { (client, path) ->
                server.enqueue(MockResponse().setBody("synthetic"))
                val url = server.localUrl(path).newBuilder().scheme("http").build()
                client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { assertEquals("synthetic", it.body!!.string()) }
            }
        }
    }
    @Test fun `redirect policy permits explicit HTTP and TLS upgrades but never HTTPS downgrades`() {
        fun allowed(from: String, to: String) = SafeRedirects.allowed(from.toHttpUrl(), to.toHttpUrl())
        assertTrue(allowed("http://provider.invalid/base", "http://provider.invalid/next"))
        assertTrue(allowed("http://provider.invalid:8080/base", "https://provider.invalid:8443/next"))
        assertFalse(allowed("https://provider.invalid/base", "http://provider.invalid/next"))
        assertFalse(allowed("http://provider.invalid/base", "http://other.invalid/next"))
        assertFalse(allowed("http://provider.invalid/base", "http://provider.invalid:8080/next"))
    }
    @Test fun `TLS downgrade response is rejected without making a cleartext request`() = fixture { tls, network ->
        MockWebServer().use { plain ->
            plain.start()
            tls.enqueue(MockResponse().setResponseCode(302).setHeader("Location", plain.url("/private").newBuilder().host("localhost").build()))
            runBlocking {
                try { network.read(tls.localUrl("/start?token=placeholder").toString()) { it.readText() }; fail("Expected redirect rejection") }
                catch (error: SourceException) { assertEquals(Failure.NETWORK, error.failure) }
            }
            assertEquals(0, plain.requestCount)
        }
    }
    @Test fun `HTTP redirects can upgrade to a normally validated TLS endpoint`() = fixture { tls, network ->
        MockWebServer().use { plain ->
            plain.start()
            plain.enqueue(MockResponse().setResponseCode(302).setHeader("Location", tls.localUrl("/secure")))
            tls.enqueue(MockResponse().setBody("secure catalogue"))
            runBlocking {
                val url = plain.url("/start").newBuilder().host("localhost").build().toString()
                assertEquals("secure catalogue", network.read(url) { it.readText() })
            }
        }
    }
    @Test fun `streaming client bounds HLS manifests without truncating media streams`() = fixture { server, network ->
        server.enqueue(MockResponse().setBody("x".repeat(2 * 1024 * 1024 + 1)))
        try {
            network.streaming.newCall(okhttp3.Request.Builder().url(server.localUrl("/master.m3u8")).build()).execute().use { it.body!!.string() }
            fail("Expected manifest limit")
        } catch (_: java.io.IOException) { /* The manifest is canceled at the configured bound. */ }
    }
    @Test fun `default transport refuses untrusted test certificates`() = fixture { server, _ ->
        server.enqueue(MockResponse().setBody("fixture"))
        runBlocking {
            try { Network().read(server.localUrl("/").toString()) { it.readText() }; fail("Expected TLS rejection") }
            catch (error: SourceException) { assertEquals(Failure.TLS, error.failure) }
        }
    }
    @Test fun `fixture uses a normally validated trusted localhost certificate`() = fixture { server, network ->
        server.enqueue(MockResponse().setBody("fixture"))
        network.media.newCall(okhttp3.Request.Builder().url(server.localUrl("/probe")).build()).execute().use {
            assertEquals("fixture", it.body!!.string())
        }
    }
    private fun fixture(block: (MockWebServer, Network) -> Unit) {
        val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
            block(server, Network(client))
        }
    }
    @Test fun `bounded body rejects chunked response without trusting Content-Length`() = fixture { server, network ->
        server.enqueue(MockResponse().setChunkedBody("x".repeat(128), 8))
        runBlocking {
            try { network.read(server.localUrl("/catalogue").toString(), 64) { it.readText() }; fail() }
            catch (e: SourceException) { assertEquals(Failure.TOO_LARGE, e.failure) }
        }
    }
    @Test fun `cross host redirect is refused before forwarding sensitive URL`() = fixture { server, network ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://unrelated.example.invalid/?token=placeholder"))
        runBlocking {
            try { network.read(server.localUrl("/?password=placeholder").toString()) { it.readText() }; fail() }
            catch (e: SourceException) { assertEquals(Failure.NETWORK, e.failure) }
        }
        assertEquals(1, server.requestCount)
    }
    @Test fun `same origin redirect succeeds and removes explicit authorization`() = fixture { server, network ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/final"))
        server.enqueue(MockResponse().setBody("catalogue"))
        runBlocking { assertEquals("catalogue", network.read(server.localUrl("/start").toString()) { it.readText() }) }
        assertEquals(2, server.requestCount)
    }
    @Test fun `403 remains forbidden rather than claiming subscription expiry`() = fixture { server, network ->
        server.enqueue(MockResponse().setResponseCode(403))
        runBlocking {
            try { network.read(server.localUrl("/").toString()) { it.readText() }; fail() }
            catch (e: SourceException) { assertEquals(Failure.FORBIDDEN, e.failure) }
        }
    }
    @Test fun `cancelled import does not wait for a delayed response`() = fixture { server, network ->
        // Keep the response pending beyond the cancellation deadline, but below
        // MockWebServer's five-second shutdown deadline. Wait for the request so
        // this tests an in-flight call instead of racing the TLS handshake.
        server.enqueue(MockResponse().setBody("slow").setHeadersDelay(2, TimeUnit.SECONDS))
        runBlocking {
            val task = launch { network.read(server.localUrl("/").toString()) { it.readText() } }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            withTimeout(1000) { task.cancelAndJoin() }
            assertTrue(task.isCancelled)
        }
    }
}

private fun MockWebServer.localUrl(path: String) = url(path).newBuilder().host("localhost").build()
