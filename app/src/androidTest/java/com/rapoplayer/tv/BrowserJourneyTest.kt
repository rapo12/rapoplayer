package com.rapoplayer.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import com.rapoplayer.tv.data.*
import com.rapoplayer.tv.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalTestApi::class)
class BrowserJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as RapoApplication
    @Before fun syntheticSource() = runBlocking {
        // Never erase an existing user's source to prepare a test fixture.
        Assume.assumeTrue(app.repository.currentSource() == null)
        Assume.assumeFalse(app.repository.pinEnabled.first())
        val crypto = Crypto()
        val config = """{"id":"ui-fixture","kind":"M3U","name":"Synthetic test catalogue","url":"https://provider.example.invalid/catalogue","username":"","password":"","epg":"","output":"m3u8"}"""
        app.repository.db.withTransaction {
            app.repository.db.dao().putSource(SourceEntity("ui-fixture", crypto.seal(config, "source:ui-fixture"), 1))
            app.repository.db.dao().putChannels(listOf("News", "Culture").mapIndexed { index, title ->
                val id = "ui-channel-$index"
                ChannelEntity(id, "ui-fixture", "Synthetic $title", "Test", "", crypto.seal(
                    """{"stream":"https://media.example.invalid/$index.m3u8","logo":""}""", "channel:$id"), "")
            })
        }
    }
    @After fun removeOwnedFixture() = runBlocking {
        if (app.repository.currentSource()?.id == "ui-fixture") app.repository.deleteSource()
    }
    private fun action(resource: Int) = compose.onNode(hasText(compose.activity.getString(resource)) and hasClickAction())
    private fun remoteAction(resource: Int) {
        action(resource).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        action(resource).performKeyInput { pressKey(Key.DirectionCenter) }
    }
    @Test fun browseSearchFavoritePlaybackBackAndFocusRestoration() {
        compose.waitUntil(10000) { compose.onAllNodesWithText("Synthetic Culture").fetchSemanticsNodes().isNotEmpty() }
        val culture = compose.onNode(hasText("Synthetic Culture") and hasClickAction())
        culture.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.runOnIdle {
            assertNull(ViewModelProvider(compose.activity)[MainViewModel::class.java].playback.player)
        }
        java.io.File(app.getExternalFilesDir(null), "browser.png").outputStream().use { out ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        remoteAction(R.string.favorite_add)
        remoteAction(R.string.favorites)
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Synthetic Culture") and hasClickAction()).fetchSemanticsNodes().size == 1 &&
            compose.onAllNodes(hasText("Synthetic News") and hasClickAction()).fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasText("Synthetic Culture") and hasClickAction())
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNode(hasText("Synthetic Culture") and hasClickAction()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { compose.onAllNodesWithText(compose.activity.getString(R.string.hide_controls)).fetchSemanticsNodes().isNotEmpty() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Synthetic Culture") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Synthetic Culture") and hasClickAction()).assertIsFocused()
        remoteAction(R.string.search)
        try {
            compose.waitUntil(5000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1 }
        } catch (error: Throwable) {
            java.io.File(app.getExternalFilesDir(null), "search-tree.txt").writeText(compose.onRoot().printToString())
            java.io.File(app.getExternalFilesDir(null), "search-failure.png").outputStream().use { out ->
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            throw error
        }
        compose.onNode(hasSetTextAction()).performTextInput("News")
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Synthetic News") and hasClickAction()).fetchSemanticsNodes().size == 1 &&
            compose.onAllNodes(hasText("Synthetic Culture") and hasClickAction()).fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasText("Synthetic Culture") and hasClickAction()).assertDoesNotExist()
        compose.runOnIdle { assertNull(ViewModelProvider(compose.activity)[MainViewModel::class.java].playback.player) }
    }
}
