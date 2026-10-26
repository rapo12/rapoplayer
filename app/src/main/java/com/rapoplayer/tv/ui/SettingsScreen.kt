package com.rapoplayer.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rapoplayer.tv.BuildConfig
import com.rapoplayer.tv.R
import com.rapoplayer.tv.core.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(model: MainViewModel, state: UiState, edit: () -> Unit) {
    var dialog by remember { mutableStateOf<String?>(null) }
    var afterPin by remember { mutableStateOf<String?>(null) }
    var admin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun protected(action: String) {
        if (state.pinEnabled) { afterPin = action; dialog = "auth" } else dialog = action
    }
    fun close() { dialog = null }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(5.dp)) {
        item { Text(stringResource(R.string.source_management), color = Accent, fontSize = 17.sp) }
        item { Text(state.source?.name?.ifBlank { stringResource(R.string.app_name) }.orEmpty(), color = WarmWhite, fontSize = 21.sp) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(stringResource(R.string.edit_source), { protected("edit") }, icon = RapoGlyph.Edit)
                Action(stringResource(R.string.reconnect), model::refresh, enabled = !state.busy, icon = RapoGlyph.Connect)
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(stringResource(R.string.catalogue_refresh), model::refresh, enabled = !state.busy, icon = RapoGlyph.Refresh)
                Action(stringResource(R.string.epg_refresh), { model.refreshGuide() }, icon = RapoGlyph.Guide)
                if (state.busy) Action(stringResource(R.string.cancel), model::cancelImport, icon = RapoGlyph.Close)
            }
        }
        if (state.busy) item { Text(stringResource(R.string.working), color = Accent) }
        if (state.guideFailure) item { Text(stringResource(R.string.guide_failed), color = Muted) }
        item { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.playback_preferences), color = Accent, fontSize = 17.sp) }
        item { Action(stringResource(R.string.playback_preferences), { dialog = "preferences" }, icon = RapoGlyph.Settings) }
        item { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.parental_controls), color = Accent, fontSize = 17.sp) }
        item { Action(stringResource(if (state.pinEnabled) R.string.parental_enabled else R.string.parental_disabled), { protected(if (state.pinEnabled) "parental" else "setPin") }, icon = RapoGlyph.Parental) }
        item { Text(stringResource(R.string.pin_explanation), color = Muted, fontSize = 13.sp) }
        item { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.data_storage), color = Accent, fontSize = 17.sp) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(stringResource(R.string.clear_history), { dialog = "history" }, icon = RapoGlyph.Recent)
                Action(stringResource(R.string.clear_cache), { dialog = "cache" }, icon = RapoGlyph.ClearCache)
                Action(stringResource(R.string.delete_source), { protected("delete") }, icon = RapoGlyph.Delete)
            }
        }
        item { Action(stringResource(R.string.about), { dialog = "about" }, icon = RapoGlyph.About) }
    }
    when (dialog) {
        "edit" -> { LaunchedEffect(Unit) { dialog = null; edit() } }
        "auth" -> PinPrompt(model, ::close) { pin ->
            admin = model.authenticatePin(pin)
            if (admin) { dialog = afterPin; afterPin = null }
        }
        "setPin", "changePin" -> PinEditor(model, ::close)
        "parental" -> Modal(stringResource(R.string.parental_controls), ::close) {
            Action(stringResource(R.string.manage_locks), { dialog = "locks" }, Modifier.fillMaxWidth(), icon = RapoGlyph.Lock)
            Action(stringResource(R.string.change_pin), { dialog = "changePin" }, Modifier.fillMaxWidth(), icon = RapoGlyph.Pin)
            Action(stringResource(R.string.disable_pin), { dialog = "disable" }, Modifier.fillMaxWidth(), icon = RapoGlyph.Unlock)
            Action(stringResource(R.string.back), ::close, icon = RapoGlyph.Back)
        }
        "locks" -> RestrictionEditor(model, state, ::close)
        "disable" -> Confirmation(stringResource(R.string.disable_pin), stringResource(R.string.pin_explanation), ::close) {
            scope.launch { runCatching { model.repository.disablePin() }.onFailure { protected("parental") }.onSuccess { close() } }
        }
        "history" -> Confirmation(stringResource(R.string.clear_history), stringResource(R.string.history_explanation), ::close) { model.clearHistory(); close() }
        "cache" -> Confirmation(stringResource(R.string.clear_cache), stringResource(R.string.cache_explanation), ::close) { model.clearCache(); close() }
        "delete" -> Confirmation(stringResource(R.string.delete_source), stringResource(R.string.delete_explanation), ::close) { model.deleteSource(); close() }
        "preferences" -> PreferenceEditor(model, state, ::close)
        "licenses" -> IconLicenses { dialog = "about" }
        "about" -> Modal(stringResource(R.string.about), ::close) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = WarmWhite)
            Text(stringResource(R.string.content_notice), color = Muted)
            Text(stringResource(R.string.format_notice), color = Muted)
            Text(stringResource(R.string.fa_attribution), color = Muted, fontSize = 14.sp)
            Text(Redaction.diagnostic(state.failure, state.channels.size, BuildConfig.VERSION_NAME), color = Accent, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(stringResource(R.string.icon_licenses), { dialog = "licenses" }, icon = RapoGlyph.License)
                Action(stringResource(R.string.close), ::close, icon = RapoGlyph.Close)
            }
        }
    }
}

@Composable private fun Confirmation(title: String, message: String, dismiss: () -> Unit, confirm: () -> Unit) {
    Modal(title, dismiss) {
        Text(message, color = Muted, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action(stringResource(R.string.cancel), dismiss, icon = RapoGlyph.Close)
            Action(stringResource(R.string.confirm), confirm, icon = RapoGlyph.Check)
        }
    }
}

@Composable fun PinPrompt(model: MainViewModel, dismiss: () -> Unit, confirm: suspend (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { model.clearPinError() }
    Modal(stringResource(R.string.enter_pin), dismiss) {
        Input(pin, { pin = it.filter(Char::isDigit).take(8) }, stringResource(R.string.enter_pin), keyboard = KeyboardType.NumberPassword, transformation = PasswordVisualTransformation(), icon = RapoGlyph.Pin)
        if (state.retrySeconds > 0) Text(pluralStringResource(R.plurals.pin_wait, state.retrySeconds.toInt(), state.retrySeconds), color = Muted)
        else if (state.pinBusy) Text(stringResource(R.string.working), color = Accent)
        else if (state.pinError) Text(stringResource(R.string.pin_invalid), color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action(stringResource(R.string.continue_action), {
                val entered = pin; pin = ""
                // Owned by this dialog: canceling it cancels the pending verification.
                scope.launch { confirm(entered) }
            }, enabled = pin.length >= 4 && state.retrySeconds == 0L && !state.pinBusy, icon = RapoGlyph.Check)
            Action(stringResource(R.string.cancel), dismiss, icon = RapoGlyph.Close)
        }
    }
}

@Composable private fun PinEditor(model: MainViewModel, dismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var expired by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Modal(stringResource(R.string.change_pin), dismiss) {
        Input(pin, { pin = it.filter(Char::isDigit).take(8) }, stringResource(R.string.new_pin), keyboard = KeyboardType.NumberPassword, transformation = PasswordVisualTransformation(), icon = RapoGlyph.Pin)
        Input(confirmation, { confirmation = it.filter(Char::isDigit).take(8) }, stringResource(R.string.confirm_pin), keyboard = KeyboardType.NumberPassword, transformation = PasswordVisualTransformation(), icon = RapoGlyph.Check)
        if (error) Text(stringResource(if (expired) R.string.session_expired else R.string.pin_mismatch), color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action(stringResource(R.string.save), {
                error = pin != confirmation || !pin.matches(Regex("[0-9]{4,8}"))
                if (!error) scope.launch {
                    runCatching { model.repository.setPin(pin) }.onSuccess { pin = ""; confirmation = ""; dismiss() }
                        .onFailure { error = true; expired = true }
                }
            }, icon = RapoGlyph.Check)
            Action(stringResource(R.string.cancel), dismiss, icon = RapoGlyph.Close)
        }
    }
}

@Composable private fun RestrictionEditor(model: MainViewModel, state: UiState, dismiss: () -> Unit) {
    val source = state.source ?: return
    val locks by remember(source.id) { model.repository.restrictions(source.id) }.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var expired by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val refs = locks.map { "${it.kind}:${it.reference}" }.toSet()
    Modal(stringResource(R.string.manage_locks), dismiss) {
        Input(query, { query = it.take(200) }, stringResource(R.string.search_hint), icon = RapoGlyph.Search)
        if (expired) Text(stringResource(R.string.session_expired), color = Muted)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
            items(state.categories.filter { it.contains(query, true) }, key = { "category:$it" }) { category ->
                val locked = "category:$category" in refs
                val label = category.ifBlank { stringResource(R.string.uncategorized) }
                Action(stringResource(if (locked) R.string.unlock_action else R.string.lock_action, label), {
                    scope.launch { runCatching { model.repository.toggleLock(source.id, "category", category, locked) }.onFailure { expired = true } }
                }, Modifier.fillMaxWidth(), icon = if (locked) RapoGlyph.Unlock else RapoGlyph.Lock)
            }
            items(state.channels.filter { it.name.contains(query, true) }, key = Channel::id) { c ->
                val locked = "channel:${c.id}" in refs
                Action(stringResource(if (locked) R.string.unlock_action else R.string.lock_action, c.name), {
                    scope.launch { runCatching { model.repository.toggleLock(source.id, "channel", c.id, locked) }.onFailure { expired = true } }
                }, Modifier.fillMaxWidth(), icon = if (locked) RapoGlyph.Unlock else RapoGlyph.Lock)
            }
        }
        Action(stringResource(R.string.back), dismiss, icon = RapoGlyph.Back)
    }
}

@Composable private fun PreferenceEditor(model: MainViewModel, state: UiState, dismiss: () -> Unit) {
    var prefs by remember { mutableStateOf(state.preferences) }
    Modal(stringResource(R.string.playback_preferences), dismiss) {
        Input(prefs.audio, { prefs = prefs.copy(audio = it.take(12)) }, stringResource(R.string.audio_language), keyboard = KeyboardType.Ascii, icon = RapoGlyph.Language)
        Input(prefs.subtitles, { prefs = prefs.copy(subtitles = it.take(12)) }, stringResource(R.string.subtitle_language), keyboard = KeyboardType.Ascii, icon = RapoGlyph.Subtitles)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action(stringResource(if (prefs.subtitlesEnabled) R.string.subtitles_on else R.string.subtitles_off), { prefs = prefs.copy(subtitlesEnabled = !prefs.subtitlesEnabled) }, selected = prefs.subtitlesEnabled, icon = RapoGlyph.Subtitles)
            Action(stringResource(if (prefs.fill) R.string.fill else R.string.fit), { prefs = prefs.copy(fill = !prefs.fill) }, icon = if (prefs.fill) RapoGlyph.Fill else RapoGlyph.Fit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action(stringResource(R.string.save), { model.preferences(prefs); dismiss() }, icon = RapoGlyph.Check)
            Action(stringResource(R.string.cancel), dismiss, icon = RapoGlyph.Close)
        }
    }
}
