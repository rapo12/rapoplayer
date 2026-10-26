package com.rapoplayer.tv

import com.rapoplayer.tv.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import okhttp3.HttpUrl.Companion.toHttpUrl

class BrowsingTest {
    @Test fun `search excludes restricted names while all destinations retain explicit lock state`() {
        val locked = Channel("l", "Restricted news", "News", "", favorite=true, watchedAt=2, locked=true)
        val open = Channel("o", "Open news", "News", "", favorite=true, watchedAt=1)
        val list = listOf(locked, open)
        BrowseMode.entries.forEach { mode ->
            assertEquals(listOf(open), browse(list, mode, null, "news"))
            assertTrue(browse(list, mode, null, "").first { it.id == "l" }.locked)
        }
    }
    @Test fun `ten thousand channel fixture imports and searches without dropping identities`() {
        val text = buildString {
            append("#EXTM3U\n")
            repeat(10_000) { i -> append("#EXTINF:-1 tvg-id=\"id-$i\" group-title=\"Test\",Channel $i\nhttps://media.example.invalid/$i.m3u8\n") }
        }
        val imported = PlaylistParser.parse(StringReader(text), "https://provider.example.invalid/".toHttpUrl(), "source")
        assertEquals(10_000, imported.map { it.id }.distinct().size)
        val channels = imported.map { Channel(it.id, it.name, it.category, it.epgId) }
        assertEquals(11, browse(channels, BrowseMode.ALL, "Test", "Channel 999").size)
        assertTrue(browse(channels, BrowseMode.ALL, "Other", "").isEmpty())
    }
}
