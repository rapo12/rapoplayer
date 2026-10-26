package com.rapoplayer.tv.ui

import android.view.Gravity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import com.rapoplayer.tv.R
import com.rapoplayer.tv.core.Failure

val Background = Color(0xFF111719)
val Panel = Color(0xFF1C2528)
val Accent = Color(0xFF5DE0C3)
val WarmWhite = Color(0xFFF4F2EB)
val Muted = Color(0xFFA6B5B6)

@Composable fun RapoTheme(content: @Composable () -> Unit) {
    androidx.tv.material3.MaterialTheme(colorScheme = androidx.tv.material3.darkColorScheme(
        primary = Accent, onPrimary = Background, background = Background, onBackground = WarmWhite,
        surface = Panel, onSurface = WarmWhite)) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Accent, onPrimary = Background, background = Background,
            surface = Panel, onSurface = WarmWhite, onBackground = WarmWhite), content = content)
    }
}
@Composable fun Action(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                       selected: Boolean? = null, enabled: Boolean = true, onFocus: (() -> Unit)? = null,
                       icon: RapoGlyph? = null) {
    ActionButton(onClick, modifier, selected, enabled, onFocus) {
        val glyph = icon ?: RapoGlyph.Check.takeIf { selected == true }
        if (glyph != null) {
            RapoIcon(glyph)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, modifier = Modifier.weight(1f, fill = false),
            color = androidx.tv.material3.LocalContentColor.current, fontSize = 16.sp, maxLines = 2)
        if (selected == true && icon != null) {
            Spacer(Modifier.width(8.dp))
            RapoIcon(RapoGlyph.Check, size = IconSizes.Status)
        }
    }
}

/** The localized accessible name is required even though the visible control is compact. */
@Composable fun IconAction(@StringRes label: Int, icon: RapoGlyph, onClick: () -> Unit,
                          modifier: Modifier = Modifier, enabled: Boolean = true) {
    ActionButton(onClick, modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp), null, enabled, null, iconOnly = true) {
        RapoIcon(icon, description = stringResource(label), size = IconSizes.Transport)
    }
}

@Composable private fun ActionButton(onClick: () -> Unit, modifier: Modifier, selection: Boolean?,
                                    enabled: Boolean, onFocus: (() -> Unit)?, iconOnly: Boolean = false,
                                    content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp)
            .semantics { selection?.let { selected = it } }
            .onFocusChanged { if (it.isFocused) onFocus?.invoke() },
        contentPadding = if (iconOnly) PaddingValues(12.dp) else PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = ButtonDefaults.colors(containerColor = if (selection == true) Color(0xFF2A3B3C) else Panel,
            contentColor = if (selection == true) Accent else WarmWhite,
            focusedContainerColor = Accent, focusedContentColor = Background,
            pressedContainerColor = Accent, pressedContentColor = Background,
            disabledContainerColor = Panel.copy(alpha = .6f), disabledContentColor = Muted),
        content = content)
}
@Composable fun Brand(compact: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.brand_initial), color = Background, fontWeight = FontWeight.Black,
            fontSize = if (compact) 22.sp else 40.sp,
            modifier = Modifier.background(Accent, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp))
        if (!compact) Column {
            Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, color = WarmWhite)
            Text(stringResource(R.string.brand_tagline), fontSize = 13.sp, color = Muted)
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun Modal(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Read the parent window before entering the dialog: the dialog's own
    // container shrinks when editing and must not feed back into its height.
    val parentSize = LocalWindowInfo.current.containerSize
    val parentHeight = with(LocalDensity.current) { parentSize.height.toDp() }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val density = LocalDensity.current
        val floatingKeyboard = WindowInsets.isImeVisible && WindowInsets.ime.getBottom(density) == 0
        val topMargin = 24.dp
        val height = if (floatingKeyboard) (parentHeight / 2 - topMargin).coerceAtLeast(0.dp) else 490.dp
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val offset = with(density) { topMargin.roundToPx() }
        LaunchedEffect(window, floatingKeyboard, offset) {
            // TV floating keyboards can report visibility with no occlusion inset.
            // Keep the editor in the upper half; normal IMEs use their real insets.
            window?.let {
                val attributes = it.attributes
                attributes.gravity = if (floatingKeyboard) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
                attributes.y = if (floatingKeyboard) offset else 0
                it.attributes = attributes
            }
        }
        Surface(shape = RoundedCornerShape(18.dp), color = Background, tonalElevation = 8.dp, modifier = Modifier.imePadding().tvEnter(title)) {
            Column(Modifier.width(650.dp).heightIn(max = height).padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, color = WarmWhite, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                content()
            }
        }
    }
}
@Composable fun FailureText(failure: Failure) {
    Text(stringResource(when (failure) {
        Failure.INPUT -> R.string.error_input; Failure.NETWORK -> R.string.error_network
        Failure.TIMEOUT -> R.string.error_timeout; Failure.TLS -> R.string.error_tls
        Failure.AUTH -> R.string.error_auth; Failure.ACCOUNT -> R.string.error_account
        Failure.FORBIDDEN -> R.string.error_forbidden; Failure.HTTP -> R.string.error_http
        Failure.EMPTY -> R.string.error_empty; Failure.MALFORMED -> R.string.error_malformed
        Failure.TOO_LARGE -> R.string.error_large; Failure.UNSUPPORTED -> R.string.error_unsupported
        Failure.DECODER -> R.string.error_decoder; Failure.CONNECTION_LIMIT -> R.string.error_limit
        Failure.GUIDE -> R.string.error_guide; Failure.UNKNOWN -> R.string.error_unknown
    }), color = Color(0xFFFFC8B4), fontSize = 16.sp)
}
