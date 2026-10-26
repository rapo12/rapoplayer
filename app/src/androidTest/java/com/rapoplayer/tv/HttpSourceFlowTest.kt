package com.rapoplayer.tv

import android.content.Context
import android.security.NetworkSecurityPolicy
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rapoplayer.tv.core.SourceConfig
import com.rapoplayer.tv.core.SourceKind
import com.rapoplayer.tv.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import com.rapoplayer.tv.player.PlaybackController
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

class HttpSourceFlowTest {
    @Test fun cleartextPolicyAllowsExplicitHttpForCatalogueGuideLogosAndPlaybackTransport() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = MockWebServer()
        val today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
        val segmentRequested = AtomicBoolean(false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                "/playlist" -> MockResponse().setBody("#EXTM3U\n#EXTINF:-1 tvg-id=\"one\" tvg-logo=\"/logo.png\",Synthetic HTTP channel\n/live/index.m3u8")
                "/guide.xml" -> MockResponse().setBody("<tv><programme channel=\"one\" start=\"${today}000000 +0000\" stop=\"${today}235959 +0000\"><title>HTTP fixture</title></programme></tv>")
                "/live/index.m3u8" -> MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl").setBody("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts\n#EXT-X-ENDLIST")
                "/live/segment.ts" -> { segmentRequested.set(true); MockResponse().setBody("synthetic bytes") }
                "/logo.png" -> MockResponse().setBody("synthetic bytes")
                "/player_api.php" -> when (request.requestUrl?.queryParameter("action")) {
                    "get_live_categories" -> MockResponse().setBody("[{\"category_id\":1,\"category_name\":\"Test\"}]")
                    "get_live_streams" -> MockResponse().setBody("[{\"stream_id\":1,\"name\":\"HTTP Xtream\",\"category_id\":1}]")
                    else -> MockResponse().setBody("{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"allowed_output_formats\":[\"m3u8\"]}}")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val db = Room.inMemoryDatabaseBuilder(context, RapoDatabase::class.java).build()
        val network = Network()
        val repository = Repository(db, Crypto(), network)
        fun url(path: String) = HttpUrl.Builder().scheme("http").host("localhost").port(server.port).encodedPath(path).build().toString()
        try {
            assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("localhost"))
            val source = SourceConfig(id="http-test", kind=SourceKind.M3U, url=url("/playlist"), epgUrl=url("/guide.xml"))
            repository.import(source)
            val channel = repository.channels(source.id).first().single()
            assertEquals(url("/live/index.m3u8"), repository.stream(source.id, channel.id))
            assertEquals(url("/logo.png"), repository.logo(source.id, channel.id))
            repository.refreshGuide(source)
            assertEquals("HTTP fixture", repository.guide(source.id, channel.id, System.currentTimeMillis()).first().single().title)
            network.streaming.newCall(Request.Builder().url(repository.stream(source.id, channel.id)).build()).execute().use {
                assertTrue(it.body!!.string().startsWith("#EXTM3U"))
            }
            network.streaming.newCall(Request.Builder().url(url("/live/segment.ts")).build()).execute().use { assertTrue(it.isSuccessful) }
            network.logos.newCall(Request.Builder().url(repository.logo(source.id, channel.id)!!).build()).execute().use { assertTrue(it.isSuccessful) }
            // Exercise the actual Media3 factory too. Invalid synthetic TS bytes are
            // intentional: reaching the segment proves transport, not codec support.
            segmentRequested.set(false)
            val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val playback = PlaybackController(context, network, playerScope)
            try {
                val streamUrl = repository.stream(source.id, channel.id)
                withContext(Dispatchers.Main) { playback.play(channel.id, "HTTP fixture", streamUrl, Preferences()) {} }
                withTimeout(15000) { while (!segmentRequested.get()) delay(50) }
            } finally {
                withContext(Dispatchers.Main) { playback.release() }
                playerScope.cancel()
            }
            val xtream = SourceConfig(id="http-xtream", kind=SourceKind.XTREAM, url=url("/"), username="placeholder-user", password="placeholder-password")
            repository.import(xtream)
            assertTrue(repository.stream(xtream.id, repository.channels(xtream.id).first().single().id).startsWith(url("/") + "live/"))
        } finally { server.shutdown(); db.close() }
    }
}
