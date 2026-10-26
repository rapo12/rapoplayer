package com.rapoplayer.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rapoplayer.tv.R
import com.rapoplayer.tv.core.*
import java.util.UUID

private enum class SourceField { URL, USERNAME, PASSWORD, GUIDE }

@Composable fun SourceForm(model: MainViewModel, previous: SourceConfig?, dismiss: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val revision = remember { state.importRevision }
    // Deliberately not rememberSaveable: connection details stay out of instance state.
    var kind by remember { mutableStateOf(previous?.kind ?: SourceKind.M3U) }
    var name by remember { mutableStateOf(previous?.name.orEmpty()) }
    var url by remember { mutableStateOf(previous?.url.orEmpty()) }
    var username by remember { mutableStateOf(previous?.username.orEmpty()) }
    var password by remember { mutableStateOf(previous?.password.orEmpty()) }
    var epg by remember { mutableStateOf(previous?.epgUrl.orEmpty()) }
    var output by remember { mutableStateOf(previous?.output ?: "m3u8") }
    var visible by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf<SourceField?>(null) }
    val focus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val fieldFocus = remember { SourceField.entries.associateWith { FocusRequester() } }
    val close = { model.cancelImport(); model.clearFailure(); dismiss() }
    Modal(stringResource(if (previous == null) R.string.connect_source else R.string.edit_source), close) {
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
            Text(stringResource(when (state.progress) { 1 -> R.string.import_reading; 2 -> R.string.import_saving; else -> R.string.import_connecting }), modifier = Modifier.tvEnter(state.progress))
            Action(stringResource(R.string.cancel), close, Modifier.focusRequester(cancelFocus))
        } else Column(
            // This bounded form stays composed so an invalid offscreen field can
            // take focus and scroll into view without a focus trap.
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(stringResource(R.string.m3u), { kind = SourceKind.M3U; invalid = null }, Modifier.then(if (kind == SourceKind.M3U) Modifier.focusRequester(focus) else Modifier), selected = kind == SourceKind.M3U)
                Action(stringResource(R.string.xtream), { kind = SourceKind.XTREAM; invalid = null }, Modifier.then(if (kind == SourceKind.XTREAM) Modifier.focusRequester(focus) else Modifier), selected = kind == SourceKind.XTREAM)
            }
            state.failure?.let { FailureText(it) }
            Input(name, { name = it.take(80) }, stringResource(R.string.display_name))
            Input(url, { url = it.take(4096); if (invalid == SourceField.URL) invalid = null },
                stringResource(if (kind == SourceKind.M3U) R.string.playlist_url else R.string.server_url),
                Modifier.focusRequester(fieldFocus.getValue(SourceField.URL)), keyboard = KeyboardType.Uri,
                placeholder = stringResource(R.string.url_placeholder),
                error = if (invalid == SourceField.URL) stringResource(R.string.error_source_url) else null)
            if (url.trim().startsWith("http://", true) || epg.trim().startsWith("http://", true)) {
                Text(stringResource(R.string.http_notice), color = Muted)
            }
            if (kind == SourceKind.XTREAM) {
                Input(username, { username = it.take(256); if (invalid == SourceField.USERNAME) invalid = null }, stringResource(R.string.username),
                    Modifier.focusRequester(fieldFocus.getValue(SourceField.USERNAME)), keyboard = KeyboardType.Ascii,
                    error = if (invalid == SourceField.USERNAME) stringResource(R.string.error_username_required) else null)
                Input(password, { password = it.take(1024); if (invalid == SourceField.PASSWORD) invalid = null }, stringResource(R.string.password),
                    Modifier.focusRequester(fieldFocus.getValue(SourceField.PASSWORD)), keyboard = KeyboardType.Password,
                    transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    error = if (invalid == SourceField.PASSWORD) stringResource(R.string.error_password_required) else null)
                Action(stringResource(if (visible) R.string.hide_password else R.string.show_password), { visible = !visible })
                Text(stringResource(R.string.stream_output), color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Action(stringResource(R.string.output_hls), { output = "m3u8" }, selected = output == "m3u8")
                    Action(stringResource(R.string.output_ts), { output = "ts" }, selected = output == "ts")
                }
            }
            Input(epg, { epg = it.take(4096); if (invalid == SourceField.GUIDE) invalid = null }, stringResource(R.string.epg_url),
                Modifier.focusRequester(fieldFocus.getValue(SourceField.GUIDE)), keyboard = KeyboardType.Uri,
                error = if (invalid == SourceField.GUIDE) stringResource(R.string.error_guide_url) else null)
            Text(stringResource(R.string.source_private), color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(stringResource(R.string.import_source), {
                    val parsed = runCatching { SafeUrl.parse(url) }.getOrNull()
                    invalid = when {
                        parsed == null || (kind == SourceKind.XTREAM && parsed.query != null) -> SourceField.URL
                        kind == SourceKind.XTREAM && username.isBlank() -> SourceField.USERNAME
                        kind == SourceKind.XTREAM && password.isBlank() -> SourceField.PASSWORD
                        epg.isNotBlank() && runCatching { SafeUrl.parse(epg) }.isFailure -> SourceField.GUIDE
                        else -> null
                    }
                    if (invalid == null) {
                        val same = previous != null && previous.kind == kind && previous.url == url.trim() && previous.username == username
                        model.import(SourceConfig(if (same) previous.id else UUID.randomUUID().toString(), kind, name.trim(), url.trim(), username, password, epg.trim(), output))
                    } else fieldFocus.getValue(invalid!!).requestFocus()
                })
                Action(stringResource(R.string.cancel), close)
            }
            Text(stringResource(R.string.content_notice), color = Muted)
        }
    }
    LaunchedEffect(state.importRevision) { if (state.importRevision > revision) dismiss() }
    LaunchedEffect(state.busy) { if (state.busy) cancelFocus.requestFocus() else focus.requestFocus() }
}
