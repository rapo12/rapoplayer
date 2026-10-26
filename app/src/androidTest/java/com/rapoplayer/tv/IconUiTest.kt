package com.rapoplayer.tv

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.rapoplayer.tv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class IconUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun named(resource: Int) = compose.onNode(hasText(context.getString(resource)) and hasClickAction())
    private fun focus(node: SemanticsNodeInteraction) = node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    private fun capture(name: String) {
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun offlineVectorsAndLicenseArePackaged() {
        RapoGlyph.entries.forEach { glyph ->
            val drawable = context.resources.getDrawable(glyph.drawable, context.theme)
            assertTrue(drawable is android.graphics.drawable.VectorDrawable)
            assertTrue(drawable.intrinsicWidth > 0)
            assertEquals(drawable.intrinsicWidth, drawable.intrinsicHeight)
        }
        val license = context.assets.open("licenses/fontawesome-free-license.txt").bufferedReader().use { it.readText() }
        assertTrue(license.contains("Icons: CC BY 4.0 License"))
        assertTrue(license.contains("Fonticons, Inc."))
    }

    @Test fun labeledSelectedDisabledAndIconOnlyActionsKeepRemoteSemantics() {
        var next = 0
        var disabled = 0
        compose.setContent {
            val first = remember { FocusRequester() }
            var selected by remember { mutableStateOf(true) }
            RapoTheme {
                Column(Modifier.fillMaxSize().background(Background).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Action(stringResource(R.string.search), {}, Modifier.focusRequester(first), icon = RapoGlyph.Search)
                    Action(stringResource(R.string.favorites), { selected = !selected }, selected = selected, icon = RapoGlyph.Favorite)
                    IconAction(R.string.pause, RapoGlyph.Pause, { disabled++ }, enabled = false)
                    IconAction(R.string.next_channel, RapoGlyph.Next, { next++ })
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        named(R.string.search).assertIsFocused()
        named(R.string.favorites).assertIsSelected()
        compose.onAllNodesWithContentDescription(context.getString(R.string.search)).assertCountEquals(0)
        val pause = compose.onNodeWithContentDescription(context.getString(R.string.pause))
        pause.assertIsNotEnabled().performClick()
        assertEquals(0, disabled)
        focus(named(R.string.search))
        capture("icons-states.png")
        named(R.string.search).performKeyInput { pressKey(Key.DirectionDown) }
        named(R.string.favorites).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        named(R.string.favorites).assertIsNotSelected()
        val forward = compose.onNodeWithContentDescription(context.getString(R.string.next_channel))
        focus(forward)
        forward.assertIsFocused().assertIsEnabled().assertHasClickAction()
        forward.performKeyInput { keyDown(Key.Enter) }
        capture("icons-pressed.png")
        forward.performKeyInput { keyUp(Key.Enter) }
        compose.runOnIdle { assertEquals(1, next) }
    }

    @Test fun offlineLicenseReaderPagesWithRemoteAndBackRestoresFocus() {
        compose.setContent {
            var open by remember { mutableStateOf(false) }
            RapoTheme {
                Box(Modifier.fillMaxSize().background(Background).padding(24.dp)) {
                    Action(stringResource(R.string.icon_licenses), { open = true }, icon = RapoGlyph.License)
                    if (open) IconLicenses { open = false }
                }
            }
        }
        focus(named(R.string.icon_licenses))
        named(R.string.icon_licenses).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Icons: CC BY 4.0 License", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.fa_attribution)).assertIsDisplayed()
        named(R.string.license_scroll_up).assertIsNotEnabled()
        focus(named(R.string.license_scroll_down))
        named(R.string.license_scroll_down).performKeyInput { pressKey(Key.DirectionCenter) }
        named(R.string.license_scroll_up).assertIsEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        named(R.string.icon_licenses).assertIsDisplayed().assertIsFocused()
    }
}
