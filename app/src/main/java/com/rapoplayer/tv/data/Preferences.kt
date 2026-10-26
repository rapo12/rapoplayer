package com.rapoplayer.tv.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("preferences")
data class Preferences(val audio: String = "", val subtitles: String = "", val subtitlesEnabled: Boolean = false, val fill: Boolean = false)
class PreferenceStore(private val context: Context) {
    private val audio = stringPreferencesKey("audio_language")
    private val subtitle = stringPreferencesKey("subtitle_language")
    private val enabled = booleanPreferencesKey("subtitles_enabled")
    private val fill = booleanPreferencesKey("fill_video")
    val values = context.dataStore.data.map { Preferences(it[audio].orEmpty(), it[subtitle].orEmpty(), it[enabled] ?: false, it[fill] ?: false) }
    suspend fun set(value: Preferences) { context.dataStore.edit { it[audio] = value.audio; it[subtitle] = value.subtitles; it[enabled] = value.subtitlesEnabled; it[fill] = value.fill } }
}
