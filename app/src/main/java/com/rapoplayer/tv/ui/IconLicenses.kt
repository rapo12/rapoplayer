package com.rapoplayer.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapoplayer.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable fun IconLicenses(dismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    val license by produceState<String?>(null, assets) {
        value = withContext(Dispatchers.IO) {
            runCatching { assets.open("licenses/fontawesome-free-license.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val page = with(LocalDensity.current) { 240.dp.toPx() }
    val focus = remember { FocusRequester() }
    Modal(stringResource(R.string.icon_licenses), dismiss) {
        Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.fa_attribution), color = WarmWhite, fontSize = 16.sp, lineHeight = 24.sp)
            Text(when (val text = license) {
                null -> stringResource(R.string.working)
                "" -> stringResource(R.string.license_unavailable)
                else -> text
            }, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action(stringResource(R.string.license_scroll_up), { scope.launch { scroll.animateScrollBy(-page, tween(150)) } },
                enabled = scroll.canScrollBackward, icon = RapoGlyph.Up)
            Action(stringResource(R.string.license_scroll_down), { scope.launch { scroll.animateScrollBy(page, tween(150)) } },
                enabled = scroll.canScrollForward, icon = RapoGlyph.Down)
            Action(stringResource(R.string.back), dismiss, Modifier.focusRequester(focus), icon = RapoGlyph.Back)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
