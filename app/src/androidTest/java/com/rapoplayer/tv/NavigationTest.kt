package com.rapoplayer.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Rule
import org.junit.Test

/** Run against a clean app install. Requires a TV emulator or TV device. */
@OptIn(ExperimentalTestApi::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun welcomeRemoteJourneyAndBackRestoresFocus() {
        val connect = compose.activity.getString(R.string.connect_source)
        compose.waitUntil(10000) { compose.onAllNodesWithText(connect).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(connect).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText(compose.activity.getString(R.string.m3u), substring = true).assertIsDisplayed()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText(connect).assertIsDisplayed().assertIsFocused()
    }

    @Test fun invalidSourceFocusesTheFieldWithoutEnteringEditMode() {
        val connect = compose.activity.getString(R.string.connect_source)
        compose.waitUntil(10000) { compose.onAllNodesWithText(connect).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(connect).performKeyInput { pressKey(Key.DirectionCenter) }
        fun field(label: Int) = compose.onNode(hasSetTextAction() and hasText(compose.activity.getString(label)))
        fun activate(text: Int) {
            val node = compose.onNodeWithText(compose.activity.getString(text))
            node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            node.performKeyInput { pressKey(Key.DirectionCenter) }
        }
        field(R.string.playlist_url).performTextInput("ftp://provider.example.invalid/playlist")
        activate(R.string.import_source)
        field(R.string.playlist_url).assertIsFocused()
        compose.onNodeWithText(compose.activity.getString(R.string.error_source_url)).assertIsDisplayed()
        field(R.string.playlist_url).assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Press OK to edit"))

        activate(R.string.xtream)
        field(R.string.server_url).performTextReplacement("https://provider.example.invalid/")
        activate(R.string.import_source)
        field(R.string.username).assertIsFocused()
        compose.onNodeWithText(compose.activity.getString(R.string.error_username_required)).assertIsDisplayed()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText(connect).assertIsDisplayed().assertIsFocused()
    }
}
