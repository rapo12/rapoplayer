package com.rapoplayer.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

/** Focus is navigation; OK/Enter or a pointer gesture explicitly starts editing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Input(
    value: String,
    change: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    transformation: VisualTransformation = VisualTransformation.None,
    error: String? = null,
    placeholder: String? = null,
    icon: RapoGlyph? = null,
) {
    // Never use rememberTextFieldState/rememberSaveable here: passwords, PINs and
    // token-bearing URLs must not be written into saved-instance state.
    val text = remember { TextFieldState(value) }
    val currentValue by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(change)
    val focus = remember { FocusRequester() }
    val intoView = remember { BringIntoViewRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var activating by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    val windowSize = LocalWindowInfo.current.containerSize
    val navigationHint = stringResource(com.rapoplayer.tv.R.string.input_navigation_hint)
    val editingHint = stringResource(com.rapoplayer.tv.R.string.input_editing_hint)

    LaunchedEffect(value) {
        if (text.text.toString() != value) text.edit { replace(0, length, value) }
    }
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.distinctUntilChanged().collect { entered ->
            if (entered != currentValue) onChange(entered)
        }
    }
    // Modern IMEs can consume Back themselves. Keep the field focused, but return
    // to navigation mode when the keyboard has finished hiding.
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) editing = false
        if (imeVisible) activating = false
        imeWasVisible = imeVisible
    }
    LaunchedEffect(focused, editing, imeVisible, windowSize) {
        if (focused && editing && imeVisible) {
            // A floating TV IME can resize/reposition our dialog after focus was
            // granted. Scroll the whole editor after the new viewport is laid out.
            withFrameNanos { }
            intoView.bringIntoView()
        }
    }
    fun finishEditing() {
        editing = false
        activating = false
        keyboardController?.hide()
        focus.requestFocus()
    }
    BackHandler(enabled = focused && editing) { finishEditing() }

    val fieldModifier = modifier.fillMaxWidth().heightIn(min = 60.dp)
        .bringIntoViewRequester(intoView)
        .focusRequester(focus)
        .focusProperties {
            // Compose text fields use the caret rectangle for focus search.
            // Remote navigation needs the whole field, including empty inputs.
            if (!editing) focusRect = FocusProperties.UnsetFocusRect
        }
        .onFocusChanged { focused = it.hasFocus; if (!it.hasFocus) { editing = false; activating = false } }
        .semantics { stateDescription = if (editing) editingHint else navigationHint }
        .onPreviewKeyEvent { event ->
            val enter = event.key in setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
            if (enter && (activating || !editing || event.key == Key.DirectionCenter)) {
                if (event.type == KeyEventType.KeyDown) {
                    activating = true
                    editing = true
                    keyboardController?.show()
                } else if (event.type == KeyEventType.KeyUp) activating = false
                true
            } else if (!editing) {
                val direction = when (event.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> null
                }
                // Don't let a navigation press move an invisible text cursor.
                if (direction != null) {
                    if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                    true
                } else false
            } else false
        }
        .pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown(requireUnconsumed = false); editing = true }
        }
    val options = KeyboardOptions(
        keyboardType = keyboard, imeAction = ImeAction.Done,
        autoCorrectEnabled = keyboard == KeyboardType.Text,
        // Compose does not create an input connection while this is false.
        // Explicit editing enables the connection and opens the IME.
        showKeyboardOnFocus = editing,
    )
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent, focusedLabelColor = Accent,
        focusedContainerColor = Color(0xFF182527),
        unfocusedContainerColor = Background,
        cursorColor = if (editing) Accent else Color.Transparent,
    )
    val labelContent: @Composable TextFieldLabelScope.() -> Unit = { Text(label, fontSize = 16.sp) }
    val hint: @Composable () -> Unit = {
        if (focused && !editing) Text(stringResource(com.rapoplayer.tv.R.string.input_edit_key), color = Accent, fontSize = 12.sp)
    }
    val supporting: (@Composable () -> Unit)? = error?.let { message -> { Text(message, fontSize = 14.sp) } }
    val placeholderContent: (@Composable () -> Unit)? = placeholder?.let { hintText -> { Text(hintText, color = Muted, fontSize = 16.sp) } }
    val leading: (@Composable () -> Unit)? = icon?.let { glyph -> {
        RapoIcon(glyph, tint = if (error != null) Color(0xFFFFC8B4) else if (focused) Accent else Muted)
    } }
    val secure = keyboard == KeyboardType.Password || keyboard == KeyboardType.NumberPassword
    if (secure) {
        OutlinedSecureTextField(
            state = text, modifier = fieldModifier, label = labelContent,
            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
            keyboardOptions = options, onKeyboardAction = { finishEditing() },
            textObfuscationMode = if (transformation is PasswordVisualTransformation) TextObfuscationMode.Hidden else TextObfuscationMode.Visible,
            leadingIcon = leading, trailingIcon = hint, supportingText = supporting, placeholder = placeholderContent,
            isError = error != null, colors = colors,
        )
    } else {
        OutlinedTextField(
            state = text, modifier = fieldModifier, label = labelContent,
            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = options, onKeyboardAction = { finishEditing() },
            leadingIcon = leading, trailingIcon = hint, supportingText = supporting, placeholder = placeholderContent,
            isError = error != null, colors = colors,
        )
    }
}
