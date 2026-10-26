package com.rapoplayer.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rapoplayer.tv.R
import com.rapoplayer.tv.core.*
import java.util.Date

@Composable fun RapoApp(model: MainViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    var form by remember { mutableStateOf(false) }
    var setupPin by remember { mutableStateOf(false) }
    fun openSource() { if (state.pinEnabled) setupPin = true else form = true }
    RapoTheme {
        Box(Modifier.fillMaxSize().background(Background)) {
            when {
                state.starting -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
                state.playing != null -> PlaybackScreen(model, state)
                state.source == null -> Welcome(::openSource)
                else -> Browser(model, state, filter) { form = true }
            }
            if (form && state.playing == null) SourceForm(model, state.source) { form = false }
            if (setupPin) PinPrompt(model, dismiss = { setupPin = false }) { pin ->
                if (model.authenticatePin(pin)) { setupPin = false; form = true }
            }
            if (state.pinChannel != null) PinPrompt(model, dismiss = model::dismissPin) { pin -> model.verifyChannelPin(pin) }
            if (state.failure != null && !form && state.playing == null) Modal(stringResource(R.string.app_name), model::clearFailure) {
                FailureText(state.failure!!)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Action(stringResource(R.string.retry), { model.clearFailure(); model.refresh() }, icon = RapoGlyph.Refresh)
                    Action(stringResource(R.string.edit_source), { model.clearFailure(); openSource() }, icon = RapoGlyph.Edit)
                    Action(stringResource(R.string.close), model::clearFailure, icon = RapoGlyph.Close)
                }
            }
        }
    }
}

@Composable private fun Welcome(connect: () -> Unit) {
    val focus = remember { FocusRequester() }
    var licenses by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().tvEnter(Unit).padding(56.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(60.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Brand()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.welcome_title), color = WarmWhite, fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.welcome_body), color = Muted, fontSize = 19.sp, lineHeight = 28.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(stringResource(R.string.connect_source), connect, Modifier.focusRequester(focus), icon = RapoGlyph.Connect)
                Action(stringResource(R.string.icon_licenses), { licenses = true }, icon = RapoGlyph.License)
            }
            Text(stringResource(R.string.content_notice), color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Box(Modifier.size(240.dp).background(Panel, androidx.compose.foundation.shape.RoundedCornerShape(48.dp)), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.brand_initial), color = Accent, fontSize = 180.sp, fontWeight = FontWeight.Black)
        }
    }
    if (licenses) IconLicenses { licenses = false }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable private fun Browser(model: MainViewModel, state: UiState, filter: BrowserFilter, edit: () -> Unit) {
    val navFocus = remember { FocusRequester() }
    BackHandler(filter.destination != Destination.LIVE || filter.category != null || filter.query.isNotEmpty()) {
        if (filter.category != null) model.category(null) else model.navigate(Destination.LIVE)
    }
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(164.dp).fillMaxHeight().focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Brand(compact = true)
            Spacer(Modifier.height(14.dp))
            Destination.entries.forEach { destination ->
                val title = stringResource(destination.title())
                Action(title, { model.navigate(destination) }, Modifier.fillMaxWidth().then(if (destination == Destination.LIVE) Modifier.focusRequester(navFocus) else Modifier), selected = filter.destination == destination, icon = destination.icon())
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.app_name), color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight().tvEnter(filter.destination), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(filter.destination.title()), color = WarmWhite, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(state.source?.name?.ifBlank { stringResource(R.string.app_name) }.orEmpty(), color = Muted, fontSize = 14.sp)
                }
                if (state.busy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.working), color = Accent, fontSize = 14.sp)
                    Action(stringResource(R.string.cancel), model::cancelImport, icon = RapoGlyph.Close)
                } else Text(pluralStringResource(R.plurals.channel_count, state.channels.size, state.channels.size), color = Accent, fontSize = 14.sp)
            }
            if (filter.destination == Destination.SETTINGS) SettingsScreen(model, state, edit)
            else {
                if (filter.destination == Destination.SEARCH) Input(filter.query, model::search,
                    stringResource(R.string.search_hint), keyboard = KeyboardType.Text, icon = RapoGlyph.Search)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (filter.destination == Destination.LIVE) LazyColumn(Modifier.width(164.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Action(stringResource(R.string.all_channels), { model.category(null) }, Modifier.fillMaxWidth(), selected = filter.category == null, icon = RapoGlyph.Playlist) }
                        items(state.categories, key = { it }) { category ->
                            Action(category.ifBlank { stringResource(R.string.uncategorized) }, { model.category(category) }, Modifier.fillMaxWidth(), selected = filter.category == category, icon = RapoGlyph.Category)
                        }
                    }
                    ChannelList(model, state, Modifier.weight(1f))
                    if (filter.destination != Destination.SEARCH) ChannelDetails(model, state, Modifier.width(235.dp))
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (state.selectedId == null) navFocus.requestFocus() }
}

private fun Destination.title(): Int = when (this) {
    Destination.LIVE -> R.string.live; Destination.FAVORITES -> R.string.favorites; Destination.RECENT -> R.string.recent
    Destination.SEARCH -> R.string.search; Destination.SETTINGS -> R.string.settings
}

private fun Destination.icon(): RapoGlyph = when (this) {
    Destination.LIVE -> RapoGlyph.Television; Destination.FAVORITES -> RapoGlyph.Favorite
    Destination.RECENT -> RapoGlyph.Recent; Destination.SEARCH -> RapoGlyph.Search; Destination.SETTINGS -> RapoGlyph.Settings
}

@Composable private fun ChannelList(model: MainViewModel, state: UiState, modifier: Modifier) {
    val list = rememberLazyListState()
    var focusedId by rememberSaveable { mutableStateOf(state.selectedId) }
    val restore = remember { FocusRequester() }
    var restored by remember { mutableStateOf(false) }
    if (state.filtered.isEmpty()) Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.empty_channels), fontSize = 22.sp, color = WarmWhite)
        Text(stringResource(R.string.empty_help), color = Muted, fontSize = 16.sp)
        Action(stringResource(R.string.catalogue_refresh), model::refresh, enabled = !state.busy, icon = RapoGlyph.Refresh)
    } else LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
        items(state.filtered, key = Channel::id) { channel ->
            val isRestore = channel.id == (focusedId ?: state.filtered.first().id)
            androidx.tv.material3.Button(onClick = { focusedId = channel.id; model.select(channel); model.watch(channel) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .then(if (isRestore) Modifier.focusRequester(restore) else Modifier)
                    .onFocusChanged { if (it.isFocused) { focusedId = channel.id; model.select(channel) } },
                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Panel, contentColor = WarmWhite,
                    focusedContainerColor = Accent, focusedContentColor = Background),
                scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.015f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChannelLogo(model, state.source?.id, channel)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (channel.locked) stringResource(R.string.locked) else channel.name, color = androidx.tv.material3.LocalContentColor.current, fontSize = 18.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (channel.favorite) RapoIcon(RapoGlyph.Favorite, size = IconSizes.Status)
                            Text(if (channel.favorite) stringResource(R.string.favorite_marker) else channel.category.ifBlank { stringResource(R.string.uncategorized) }, color = androidx.tv.material3.LocalContentColor.current, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(state.filtered.map { it.id }, Unit) {
        // Only request focus on return/recreation, never on each background refresh.
        val id = focusedId ?: return@LaunchedEffect
        val index = state.filtered.indexOfFirst { it.id == id }
        if (index >= 0 && !restored) { list.scrollToItem(index); kotlinx.coroutines.delay(60); runCatching { restore.requestFocus() }; restored = true }
    }
}

@Composable private fun ChannelLogo(model: MainViewModel, source: String?, channel: Channel) {
    val url by produceState<String?>(null, source, channel.id, channel.locked) {
        if (source != null && !channel.locked) value = runCatching { model.repository.logo(source, channel.id) }.getOrNull()
    }
    Box(Modifier.size(38.dp).background(ColorLogo, androidx.compose.foundation.shape.RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(ImageRequest.Builder(LocalContext.current).data(url).size(96, 96).memoryCacheKey(stableId("logo", url.orEmpty())).build(),
            contentDescription = null, modifier = Modifier.size(32.dp))
        else if (channel.locked) RapoIcon(RapoGlyph.Lock, tint = Accent)
        else Text(channel.name.take(1), color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
private val ColorLogo = androidx.compose.ui.graphics.Color(0xFF293739)

@Composable private fun ChannelDetails(model: MainViewModel, state: UiState, modifier: Modifier) {
    val channel = state.channels.find { it.id == state.selectedId }
    Column(modifier.fillMaxHeight().background(Panel, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.now), color = Accent, fontSize = 12.sp, letterSpacing = 2.sp)
        Text(if (channel?.locked == true) stringResource(R.string.locked) else channel?.name ?: stringResource(R.string.select_channel),
            color = WarmWhite, fontSize = 24.sp, lineHeight = 30.sp, maxLines = 3)
        if (channel != null && !channel.locked) GuideDetails(state.guide)
        Spacer(Modifier.weight(1f))
        if (channel != null) {
            Action(stringResource(if (channel.locked) R.string.unlock_watch else R.string.watch), { model.watch(channel) }, Modifier.fillMaxWidth(), icon = if (channel.locked) RapoGlyph.Unlock else RapoGlyph.Play)
            Action(stringResource(if (channel.favorite) R.string.favorite_remove else R.string.favorite_add), { model.favorite(channel) }, Modifier.fillMaxWidth(), selected = channel.favorite, icon = RapoGlyph.Favorite)
        }
        Text(stringResource(R.string.browse_hint), color = Muted, fontSize = 12.sp)
    }
}

@Composable fun GuideDetails(guide: List<Programme>) {
    val now = System.currentTimeMillis()
    val current = guide.firstOrNull { it.start <= now && it.end > now }
    val next = guide.firstOrNull { it.start > now }
    val time = android.text.format.DateFormat.getTimeFormat(LocalContext.current)
    if (current == null) Text(stringResource(R.string.no_guide), color = Muted, fontSize = 15.sp)
    else {
        Text(current.title, color = WarmWhite, fontSize = 18.sp, maxLines = 2)
        Text(stringResource(R.string.programme_times, time.format(Date(current.start)), time.format(Date(current.end))), color = Muted, fontSize = 13.sp)
        LinearProgressIndicator(progress = { current.progress(now) }, color = Accent, trackColor = Background, modifier = Modifier.fillMaxWidth())
    }
    if (next != null) {
        Text(stringResource(R.string.next), color = Accent, fontSize = 12.sp, letterSpacing = 2.sp)
        Text(next.title, color = Muted, fontSize = 15.sp, maxLines = 2)
    }
}
