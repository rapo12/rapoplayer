package com.rapoplayer.tv

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.rapoplayer.tv.data.Network
import com.rapoplayer.tv.data.Preferences
import com.rapoplayer.tv.player.PlaybackController
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class PlayerLifecycleTest {
    @Test fun rapidSwitchingReusesOnePlayerAndReleaseCancelsRequests() = runBlocking {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply {
            useHttps(serverTls.sslSocketFactory(), false)
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(503)
            }
            start()
        }
        val network = Network(OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var controller: PlaybackController
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync {
                controller = PlaybackController(ApplicationProvider.getApplicationContext(), network, scope)
                controller.play("first", "Synthetic", server.localUrl("/first.m3u8").toString(), Preferences()) {}
                val first = controller.player
                assertNotNull(first)
                repeat(100) { index ->
                    controller.halt()
                    controller.play("channel-$index", "Synthetic", server.localUrl("/$index.m3u8").toString(), Preferences()) {}
                    assertSame(first, controller.player)
                }
                controller.release()
                assertNull(controller.player)
                assertFalse(controller.state.value.playing)
            }
            withTimeout(5000) { while (network.streaming.dispatcher.runningCallsCount() != 0) delay(50) }
        } finally {
            instrumentation.runOnMainSync { runCatching { controller.release() } }
            scope.cancel(); server.shutdown()
        }
    }
}

private fun MockWebServer.localUrl(path: String) = okhttp3.HttpUrl.Builder()
    .scheme("https").host("localhost").port(port).encodedPath(path).build()
