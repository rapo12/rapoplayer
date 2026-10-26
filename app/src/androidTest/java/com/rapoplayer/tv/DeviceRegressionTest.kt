package com.rapoplayer.tv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rapoplayer.tv.core.*
import com.rapoplayer.tv.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceRegressionTest {
    @Test fun encryptedCredentialsRefreshFavoritesAndParentalEntryPoints() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RapoDatabase::class.java).build()
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val crypto = Crypto()
        val repository = Repository(db, crypto, Network(OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()))
        try {
            val source = SourceConfig(id="test-source", kind=SourceKind.M3U, url=server.localUrl("/playlist?token=placeholder").toString())
            fun catalogue(title: String) = MockResponse().setBody("#EXTM3U\n#EXTINF:-1 tvg-id=\"stable\" group-title=\"Test\",$title\nhttps://media.example.invalid/live.m3u8?token=placeholder")
            server.enqueue(catalogue("Old")); repository.import(source)
            val original = repository.channels(source.id).first().single()
            repository.favorite(source.id, original)
            server.enqueue(catalogue("Renamed")); repository.import(source)
            val refreshed = repository.channels(source.id).first().single()
            assertEquals(original.id, refreshed.id); assertTrue(refreshed.favorite)
            assertFalse(db.dao().source()!!.config.contains("placeholder"))
            assertFalse(db.dao().channel(source.id, refreshed.id)!!.secret.contains("https"))
            server.enqueue(MockResponse().setResponseCode(500))
            runCatching { repository.import(source) }
            assertEquals("Renamed", repository.channels(source.id).first().single().name)
            repository.setPin("1234")
            assertTrue(repository.verifyPin("1234"))
            repository.toggleLock(source.id, "category", "Test", false)
            repository.revokeGrants()
            assertTrue(repository.channels(source.id).first().single().locked)
            assertTrue(runCatching { repository.stream(source.id, refreshed.id) }.isFailure)
            assertFalse(repository.verifyPin("9999"))
            assertTrue(runCatching { repository.disablePin() }.isFailure)
            assertTrue(runCatching { repository.deleteSource() }.isFailure)
            assertFalse(repository.verifyPin("9999")); assertFalse(repository.verifyPin("9999"))
            assertTrue(repository.pinRetrySeconds() > 0)
            assertFalse(repository.verifyPin("1234"))
            kotlinx.coroutines.delay(1100)
            assertTrue(repository.verifyPin("1234", refreshed.id))
            assertTrue(repository.stream(source.id, refreshed.id).startsWith("https"))
            repository.deleteSource()
            assertNull(repository.currentSource())
            assertTrue(db.dao().channelEntities(source.id).isEmpty())
            assertTrue(repository.pinEnabled.first())
        } finally { server.shutdown(); db.close() }
    }
    @Test fun keystoreCiphertextAuthenticatesItsContextAndTampering() {
        val crypto = Crypto()
        val sealed = crypto.seal("placeholder-secret", "test-a")
        assertEquals("placeholder-secret", crypto.open(sealed, "test-a"))
        assertTrue(runCatching { crypto.open(sealed, "test-b") }.isFailure)
        val bytes = java.util.Base64.getDecoder().decode(sealed)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(runCatching { crypto.open(java.util.Base64.getEncoder().encodeToString(bytes), "test-a") }.isFailure)
    }
}

private fun MockWebServer.localUrl(path: String) = url(path).newBuilder().host("localhost").build()
