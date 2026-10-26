@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.rapoplayer.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rapoplayer.tv.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PlaybackScreen(model: MainViewModel, state: UiState) {
    val channel = state.playing ?: return
    val playback by model.playback.state.collectAsStateWithLifecycle()
    var controls by remember(channel.id) { mutableStateOf(true) }
    var tracks by remember { mutableStateOf<Int?>(null) }
    var interaction by remember { mutableLongStateOf(0) }
    val root = remember { FocusRequester() }
    val firstControl = remember { FocusRequester() }
    val recoveryFocus = remember { FocusRequester() }
    val activity = LocalActivity.current ?: return
    val actual = state.channels.find { it.id == channel.id } ?: channel
    DisposableEffect(playback.playing, playback.buffering) {
        if (playback.playing || playback.buffering) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler {
        when {
            tracks != null -> tracks = null
            controls && !state.suspended -> { controls = false; root.requestFocus() }
            else -> model.leavePlayback()
        }
    }
    LaunchedEffect(controls, channel.id, state.suspended, playback.ready, playback.error, state.failure) {
        delay(40)
        if ((state.suspended || playback.error != null || state.failure != null) && tracks == null) runCatching { recoveryFocus.requestFocus() }
        else if (controls && !state.suspended && tracks == null) runCatching { firstControl.requestFocus() }.onFailure { root.requestFocus() }
        else if (tracks == null) runCatching { root.requestFocus() }
    }
    LaunchedEffect(controls, interaction, playback.playing, tracks) {
        if (controls && playback.playing && tracks == null) {
            delay(7000); controls = false; root.requestFocus()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(root)
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                interaction++
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_CHANNEL_UP, AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> { model.switchChannel(1); return@onPreviewKeyEvent true }
                    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN, AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> { model.switchChannel(-1); return@onPreviewKeyEvent true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { model.playback.togglePause(); return@onPreviewKeyEvent true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { model.playback.resume(); return@onPreviewKeyEvent true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { model.playback.player?.pause(); return@onPreviewKeyEvent true }
                }
                if (!controls && tracks == null) when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { controls = true; return@onPreviewKeyEvent true }
                    Key.DirectionUp -> { model.switchChannel(-1); return@onPreviewKeyEvent true }
                    Key.DirectionDown -> { model.switchChannel(1); return@onPreviewKeyEvent true }
                }
            }
            false
        }.focusable()) {
        AndroidView(factory = { context -> PlayerView(context).apply {
            useController = false; isFocusable = false; isFocusableInTouchMode = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        } }, update = { view ->
            view.player = model.playback.player
            view.resizeMode = if (state.preferences.fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
        if (playback.buffering && !state.suspended) Column(Modifier.align(Alignment.Center).background(Background).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Accent)
            Text(stringResource(R.string.buffering), color = WarmWhite, fontSize = 18.sp)
        }
        if (state.suspended) Column(Modifier.align(Alignment.Center).background(Background).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.resume_notice), color = WarmWhite, fontSize = 22.sp)
            Action(stringResource(R.string.resume_playback), model::resumePlayback, Modifier.focusRequester(recoveryFocus), icon = RapoGlyph.Play)
            Action(stringResource(R.string.back), model::leavePlayback, icon = RapoGlyph.Back)
        }
        if (playback.error != null || state.failure != null) Column(Modifier.align(Alignment.Center).widthIn(max = 600.dp)
            .background(Background).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FailureText(playback.error ?: state.failure!!)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(stringResource(R.string.retry), { model.watch(channel) }, Modifier.focusRequester(recoveryFocus), icon = RapoGlyph.Refresh)
                Action(stringResource(R.string.next_channel), { model.switchChannel(1) }, icon = RapoGlyph.Next)
                Action(stringResource(R.string.back), model::leavePlayback, icon = RapoGlyph.Back)
            }
        }
        if (controls && !state.suspended) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Background.copy(alpha = .96f), Background)))
            .padding(horizontal = 30.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(channel.name, color = WarmWhite, fontSize = 28.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RapoIcon(if (playback.live) RapoGlyph.Live else RapoGlyph.Television, tint = Accent, size = IconSizes.Status)
                    Text(if (playback.live) stringResource(R.string.live) else stringResource(R.string.app_name), color = Accent, fontSize = 14.sp)
                }
                if (playback.recoveredLive) Text(stringResource(R.string.live_recovered), color = Muted, fontSize = 14.sp)
                if (!playback.playing && playback.ready) Text(stringResource(R.string.paused), color = Muted, fontSize = 14.sp)
            }
            if (state.guide.isNotEmpty()) Text(state.guide.firstOrNull { it.start <= System.currentTimeMillis() && it.end > System.currentTimeMillis() }?.title.orEmpty(), color = WarmWhite, fontSize = 18.sp)
            if (playback.unsupportedTracks) Text(stringResource(R.string.unsupported_tracks), color = Muted, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(if (playback.playing) R.string.pause else R.string.resume,
                    if (playback.playing) RapoGlyph.Pause else RapoGlyph.Play, model.playback::togglePause,
                    Modifier.focusRequester(firstControl), enabled = playback.ready)
                Action(stringResource(R.string.previous_channel), { model.switchChannel(-1) }, icon = RapoGlyph.Previous)
                Action(stringResource(R.string.next_channel), { model.switchChannel(1) }, icon = RapoGlyph.Next)
                Action(stringResource(if (actual.favorite) R.string.favorite_remove else R.string.favorite_add), { model.favorite(actual) }, selected = actual.favorite, icon = RapoGlyph.Favorite)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (playback.live) Action(stringResource(R.string.return_live), model.playback::liveEdge, icon = RapoGlyph.Live)
                if (playback.audio.size > 1) Action(stringResource(R.string.audio_tracks), { tracks = C.TRACK_TYPE_AUDIO }, icon = RapoGlyph.Audio)
                if (playback.subtitles.isNotEmpty()) Action(stringResource(R.string.subtitle_tracks), { tracks = C.TRACK_TYPE_TEXT }, icon = RapoGlyph.Subtitles)
                Action(stringResource(if (state.preferences.fill) R.string.fill else R.string.fit), { model.preferences(state.preferences.copy(fill = !state.preferences.fill)) }, icon = if (state.preferences.fill) RapoGlyph.Fill else RapoGlyph.Fit)
                Action(stringResource(R.string.hide_controls), { controls = false; root.requestFocus() }, icon = RapoGlyph.Hide)
            }
            Text(stringResource(R.string.playback_controls_hint), color = Muted, fontSize = 12.sp)
        }
        if (tracks != null) {
            val type = tracks!!
            val choices = if (type == C.TRACK_TYPE_AUDIO) playback.audio else playback.subtitles
            Modal(stringResource(if (type == C.TRACK_TYPE_AUDIO) R.string.audio_tracks else R.string.subtitle_tracks), { tracks = null }) {
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                    item { Action(stringResource(if (type == C.TRACK_TYPE_TEXT) R.string.off else R.string.automatic), { model.playback.choose(type, null); tracks = null }, icon = if (type == C.TRACK_TYPE_TEXT) RapoGlyph.Subtitles else RapoGlyph.Audio) }
                    itemsIndexed(choices) { index, choice ->
                        val label = choice.label?.takeIf { it.isNotBlank() } ?: choice.language?.takeIf { it.isNotBlank() } ?: stringResource(R.string.track_number, index + 1)
                        Action(label, { model.playback.choose(type, choice); tracks = null }, Modifier.fillMaxWidth(), selected = choice.selected)
                    }
                }
                Action(stringResource(R.string.back), { tracks = null }, icon = RapoGlyph.Back)
            }
        }
    }
}
