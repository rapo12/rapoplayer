package com.rapoplayer.tv

import com.rapoplayer.tv.core.*
import com.rapoplayer.tv.data.Network
import com.rapoplayer.tv.data.PinHash
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.time.Instant

class CoreTest {
    private val base = "https://provider.example.invalid/catalogue/list.m3u".toHttpUrl()
    private fun fixture(name: String) = javaClass.getResourceAsStream("/fixtures/$name")!!.reader()
    @Test fun `M3U normalizes BOM unicode quoted commas relative paths and skips VOD and unsafe URLs`() {
        val list = PlaylistParser.parse(fixture("catalogue.m3u"), base, "source")
        assertEquals(5, list.size)
        assertEquals("أخبار Test", list[0].name)
        assertEquals("News, local", list[0].category)
        assertEquals("https://provider.example.invalid/live/news.m3u8?token=placeholder", list[0].stream)
        assertEquals("https://provider.example.invalid/logos/news.png", list[0].logo)
        assertEquals("", list[1].category)
        assertEquals(list.size, list.map { it.id }.toSet().size)
    }
    @Test fun `duplicate guide IDs stay distinct and do not depend on order`() {
        fun parse(rows: List<String>) = PlaylistParser.parse(StringReader("#EXTM3U\n"+rows.joinToString("\n")), base, "s")
        val a = listOf("#EXTINF:-1 tvg-id=\"same\",A\n/a.ts", "#EXTINF:-1 tvg-id=\"same\",B\n/b.ts")
        assertEquals(parse(a).map { it.id }.toSet(), parse(a.reversed()).map { it.id }.toSet())
        assertEquals(2, parse(a).map { it.id }.distinct().size)
    }
    @Test fun `stable identity ignores refreshed display metadata and signed URL changes when guide ID is unique`() {
        fun parse(name: String, token: String) = PlaylistParser.parse(StringReader("#EXTM3U\n#EXTINF:-1 tvg-id=\"stable\",$name\n/live.m3u8?token=$token"), base, "s").single()
        assertEquals(parse("Old", "a").id, parse("New", "b").id)
        assertNotEquals(stableId("sourceA", "channel"), stableId("sourceB", "channel"))
    }
    @Test fun `missing names malformed headers and excessive lines are actionable failures`() {
        assertFailure(Failure.MALFORMED) { PlaylistParser.parse(StringReader("not a playlist"), base, "s") }
        assertFailure(Failure.EMPTY) { PlaylistParser.parse(StringReader("#EXTM3U\n#EXTINF:-1,\n/a.ts"), base, "s") }
        assertFailure(Failure.TOO_LARGE) { PlaylistParser.parse(StringReader("#EXTM3U\n"+"x".repeat(16385)), base, "s") }
    }
    @Test fun `playlist parsing cooperates with cancellation`() {
        var checks = 0
        try {
            PlaylistParser.parse(fixture("catalogue.m3u"), base, "s") { if (++checks > 2) throw java.util.concurrent.CancellationException() }
            fail("Expected cancellation")
        } catch (_: java.util.concurrent.CancellationException) { assertTrue(checks > 2) }
    }
    @Test fun `Xtream handles optional fields numeric string IDs direct sources and output extensions`() {
        val source = SourceConfig(id="s", kind=SourceKind.XTREAM, url="https://provider.example.invalid/base/", username="user name", password="p/word")
        val list = XtreamParser.channels(fixture("xtream-channels.json"), source, mapOf("10" to "News"), source.url.toHttpUrl())
        assertEquals(2, list.size)
        assertEquals("News", list.first().category)
        assertEquals("https://provider.example.invalid/base/live/user%20name/p%2Fword/1.ts", list.first().stream)
        assertTrue(list[1].stream.startsWith("https://cdn.example.invalid/"))
        assertEquals("", list[1].category)
    }
    @Test fun `Xtream authentication distinguishes rejected unavailable and malformed accounts`() {
        assertFailure(Failure.AUTH) { XtreamParser.authenticate(StringReader("{\"user_info\":{\"auth\":0}}")) }
        assertFailure(Failure.ACCOUNT) { XtreamParser.authenticate(StringReader("{\"user_info\":{\"auth\":\"1\",\"status\":\"Expired\"}}")) }
        assertFailure(Failure.ACCOUNT) { XtreamParser.authenticate(StringReader("{\"user_info\":{\"auth\":0,\"status\":\"Expired\"}}")) }
        assertFailure(Failure.MALFORMED) { XtreamParser.authenticate(StringReader("{}")) }
        assertEquals(listOf("m3u8", "ts"), XtreamParser.authenticate(StringReader("{\"user_info\":{\"auth\":1,\"allowed_output_formats\":[\"m3u8\",\"ts\"]}}")))
    }
    @Test fun `Xtream short guide uses epoch times and decodes title`() {
        val json = """{"epg_listings":[{"start_timestamp":"1000","stop_timestamp":2000,"title":"VGVzdCBzaG93"},{"start_timestamp":"bad"}]}"""
        val result = XtreamParser.guide(StringReader(json), "c", 0, 3_000_000)
        assertEquals("Test show", result.single().title)
        assertEquals(1_000_000L, result.single().start)
    }
    @Test fun `XMLTV matches exact IDs and honors DST offsets`() {
        val guide = XmlTvParser.parse(fixture("guide.xml"), mapOf("news.test" to listOf("local-id")),
            Instant.parse("2026-10-24T00:00:00Z").toEpochMilli(), Instant.parse("2026-10-27T00:00:00Z").toEpochMilli())
        assertEquals(2, guide.size)
        assertEquals("local-id", guide[0].channelId)
        assertEquals(2 * 60 * 60 * 1000L, guide[1].start - guide[0].start)
        assertEquals(Instant.parse("2026-10-25T01:00:00Z").toEpochMilli(), XmlTvParser.timestamp("20261025010000"))
        assertNull(XmlTvParser.timestamp("20261025"))
        assertNull(XmlTvParser.timestamp("20260230010000 +0000"))
    }
    @Test fun `Xtream plaintext titles that resemble base64 remain readable`() {
        val result = XtreamParser.guide(StringReader("""{"epg_listings":[{"start_timestamp":1000,"stop_timestamp":2000,"title":"News"}]}"""), "c", 0, 3_000_000)
        assertEquals("News", result.single().title)
    }
    @Test fun `XMLTV refuses DTDs and external entities`() {
        val xml = """<!DOCTYPE tv [<!ENTITY xxe SYSTEM "file:///private/secret">]><tv><programme channel="c"><title>&xxe;</title></programme></tv>"""
        assertFailure(Failure.MALFORMED) { XmlTvParser.parse(StringReader(xml), mapOf("c" to listOf("c")), 0, Long.MAX_VALUE) }
    }
    @Test fun `guide drops out of window and invalid durations`() {
        val xml = """<tv><programme channel="c" start="20261025000000 +0000" stop="20261024000000 +0000"><title>Invalid</title></programme></tv>"""
        assertTrue(XmlTvParser.parse(StringReader(xml), mapOf("c" to listOf("c")), 0, Long.MAX_VALUE).isEmpty())
    }
    @Test fun `URL validation accepts HTTP and HTTPS but rejects credentials unsafe schemes and fragments`() {
        listOf("https://u:p@example.invalid", "http://u:p@example.invalid", "https://example.invalid/#token", "http://example.invalid/#token", "rtsp://example.invalid", "ftp://example.invalid").forEach { value ->
            assertFailure(Failure.INPUT) { SafeUrl.parse(value) }
        }
        assertEquals("https", SafeUrl.parse("https://example.invalid/path?token=placeholder").scheme)
        assertEquals("http", SafeUrl.parse("http://example.invalid:8080/path?token=placeholder").scheme)
    }
    @Test fun `HTTP playlist resolves streams and logos without rewriting the requested scheme`() {
        val source = "http://provider.example.invalid:8080/base/list.m3u".toHttpUrl()
        val parsed = PlaylistParser.parse(StringReader("#EXTM3U\n#EXTINF:-1 tvg-id=\"one\" tvg-logo=\"/logo.png\",One\n../live/one.m3u8"), source, "s").single()
        assertEquals("http://provider.example.invalid:8080/live/one.m3u8", parsed.stream)
        assertEquals("http://provider.example.invalid:8080/logo.png", parsed.logo)
    }
    @Test fun `HTTP Xtream retains base path and encodes credentials in the stream path`() {
        val source = SourceConfig(id="s", kind=SourceKind.XTREAM, url="http://provider.example.invalid:8080/base/", username="user name", password="p/word")
        val result = XtreamParser.channels(fixture("xtream-channels.json"), source, emptyMap(), source.url.toHttpUrl())
        assertEquals("http://provider.example.invalid:8080/base/live/user%20name/p%2Fword/1.ts", result.first().stream)
    }
    @Test fun `secret redaction never exposes source connection fields`() {
        val config = SourceConfig(kind=SourceKind.XTREAM, url="https://private.invalid/token", username="secret-user", password="secret-password")
        val output = config.toString()+Redaction.diagnostic(Failure.AUTH, 10, "test")+Redaction.privateValue(config.url)
        listOf("private.invalid", "secret-user", "secret-password", "token").forEach { assertFalse(output.contains(it)) }
    }
    @Test fun `PIN verifier uses random salt and constant time matching and clears caller array`() {
        val salt = PinHash.salt(); val another = PinHash.salt()
        val pin = "1234".toCharArray(); val hash = PinHash.derive(pin, salt)
        assertTrue(pin.all { it == '\u0000' })
        assertTrue(PinHash.matches("1234".toCharArray(), salt, hash))
        assertFalse(PinHash.matches("4321".toCharArray(), salt, hash))
        assertFalse(hash.contentEquals(PinHash.derive("1234".toCharArray(), another)))
    }
    @Test fun `retry is bounded and does not mislabel or retry forbidden and authentication failures`() {
        assertEquals(Failure.FORBIDDEN, Network.httpFailure(403))
        assertEquals(Failure.AUTH, Network.httpFailure(401))
        assertEquals(1000L, RetryPolicy.delayMillis(Failure.NETWORK, 0))
        assertEquals(2000L, RetryPolicy.delayMillis(Failure.TIMEOUT, 1))
        assertNull(RetryPolicy.delayMillis(Failure.NETWORK, 2))
        assertNull(RetryPolicy.delayMillis(Failure.AUTH, 0))
        assertNull(RetryPolicy.delayMillis(Failure.FORBIDDEN, 0))
    }
    private fun assertFailure(expected: Failure, block: () -> Unit) {
        try { block(); fail("Expected $expected") } catch (error: SourceException) { assertEquals(expected, error.failure) }
    }
}
