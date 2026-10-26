package com.rapoplayer.tv

import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.rapoplayer.tv.ui.Action
import com.rapoplayer.tv.ui.Input
import com.rapoplayer.tv.ui.Modal
import com.rapoplayer.tv.ui.RapoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTestApi::class, ExperimentalLayoutApi::class)
class InputBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ime = AtomicBoolean(false)
    private val back = AtomicInteger(0)
    private val kinds = listOf(KeyboardType.Text, KeyboardType.Uri, KeyboardType.Ascii, KeyboardType.Password, KeyboardType.NumberPassword)
    private fun configureWindow() {
        // Match MainActivity's edge-to-edge/inset configuration so this test
        // observes the real IME, rather than a host window's consumed insets.
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
    private fun screen() {
        configureWindow()
        compose.setContent {
            val visible = WindowInsets.isImeVisible
            SideEffect { ime.set(visible) }
            BackHandler { back.incrementAndGet() }
            RapoTheme {
                Column(Modifier.fillMaxSize().imePadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action("Before", {}, Modifier.testTag("before"))
                    kinds.forEachIndexed { index, type ->
                        var value by remember { mutableStateOf("") }
                        Input(value, { value = it }, "Field $index", Modifier.testTag("field-$index"), keyboard = type,
                            transformation = if (type == KeyboardType.Password || type == KeyboardType.NumberPassword) PasswordVisualTransformation() else VisualTransformation.None)
                    }
                    Action("After", {}, Modifier.testTag("after"))
                }
            }
        }
    }
    private fun field(index: Int) = compose.onNodeWithTag("field-$index")
    private fun focus(index: Int) { field(index).performSemanticsAction(SemanticsActions.RequestFocus) { it() }; compose.waitForIdle() }
    private fun waitIme(visible: Boolean) = compose.waitUntil(10000) { ime.get() == visible }

    @Test fun remoteFocusAndDirectionalNavigationNeverOpenIme() {
        screen()
        kinds.indices.forEach { index ->
            focus(index)
            field(index).assertIsFocused()
            assertFalse("Focusing field $index must not open IME", ime.get())
            field(index).assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Press OK to edit"))
        }
        focus(0)
        field(0).performKeyInput { pressKey(Key.DirectionDown) }
        field(1).assertIsFocused()
        assertFalse(ime.get())
        field(1).performKeyInput { pressKey(Key.DirectionUp) }
        field(0).assert(isFocused()) { compose.onRoot().printToString() }
    }
    @Test fun okOpensImeDonePreservesValueAndRemoteFocus() {
        screen(); focus(1)
        field(1).performKeyInput { pressKey(Key.DirectionCenter) }
        waitIme(true)
        field(1).performTextInput("http://provider.example.invalid/placeholder")
        field(1).performImeAction()
        waitIme(false)
        field(1).assertIsFocused().assertTextContains("http://provider.example.invalid/placeholder")
        field(1).performKeyInput { pressKey(Key.DirectionDown) }
        field(2).assertIsFocused()
        assertFalse(ime.get())
    }
    @Test fun backClosesImeBeforeParentAndRetainsText() {
        screen(); focus(0)
        field(0).performKeyInput { pressKey(Key.Enter) }
        waitIme(true)
        field(0).performTextInput("Preserved value")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitIme(false)
        field(0).assertIsFocused().assertTextContains("Preserved value")
        assertEquals(0, back.get())
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { back.get() == 1 }
    }
    @Test fun touchStillOpensImeAndPasswordFieldKeepsSecureSemantics() {
        screen()
        field(0).performClick()
        waitIme(true)
        field(0).performTextInput("Touch entry")
        field(0).performImeAction(); waitIme(false)
        field(0).assertTextContains("Touch entry")
        focus(3)
        assertFalse(ime.get())
        field(3).assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
        field(3).performKeyInput { pressKey(Key.DirectionCenter) }; waitIme(true)
        field(3).performTextInput("placeholder-password")
        field(3).performImeAction(); waitIme(false)
        field(3).assertIsFocused()
    }
    @Test fun dialogBackClosesKeyboardThenDialogAndKeepsEnteredValue() {
        configureWindow()
        val floatingKeyboard = AtomicBoolean(false)
        val fieldBottom = AtomicInteger(0)
        val fullyVisible = AtomicBoolean(false)
        val screenHeight = compose.activity.resources.displayMetrics.heightPixels
        lateinit var dialogView: android.view.View
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            RapoTheme {
                if (open) Modal("Input dialog", { open = false; back.incrementAndGet() }) {
                    val visible = WindowInsets.isImeVisible
                    val density = LocalDensity.current
                    val view = LocalView.current
                    val inset = WindowInsets.ime.getBottom(density)
                    SideEffect { dialogView = view; ime.set(visible); floatingKeyboard.set(visible && inset == 0) }
                    var value by remember { mutableStateOf("") }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { index ->
                            var other by remember { mutableStateOf("") }
                            Input(other, { other = it }, "Earlier field $index")
                        }
                        Input(value, { value = it }, "Dialog field", Modifier.testTag("field-0").onGloballyPositioned {
                            val bounds = it.boundsInRoot()
                            fieldBottom.set(bounds.bottom.toInt())
                            fullyVisible.set(bounds.width > 0 && bounds.height >= it.size.height - 1f)
                        })
                        Action("Cancel", { open = false; back.incrementAndGet() })
                    }
                }
            }
        }
        focus(0)
        assertFalse(ime.get())
        field(0).performKeyInput { pressKey(Key.DirectionCenter) }; waitIme(true)
        compose.waitUntil(5000) {
            !floatingKeyboard.get() || fullyVisible.get() && compose.runOnUiThread {
                val location = IntArray(2)
                dialogView.getLocationOnScreen(location)
                location[1] + fieldBottom.get() in 1..(screenHeight / 2)
            }
        }
        field(0).assertIsDisplayed()
        field(0).performTextInput("Dialog entry")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitIme(false)
        field(0).assertIsFocused().assertTextContains("Dialog entry")
        assertEquals(0, back.get())
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { back.get() == 1 }
    }
}
