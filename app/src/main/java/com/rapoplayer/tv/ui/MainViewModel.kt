package com.rapoplayer.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.rapoplayer.tv.RapoApplication
import com.rapoplayer.tv.core.*
import com.rapoplayer.tv.data.*
import com.rapoplayer.tv.player.PlaybackController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Destination { LIVE, FAVORITES, RECENT, SEARCH, SETTINGS }
data class BrowserFilter(val destination: Destination = Destination.LIVE, val category: String? = null, val query: String = "")
data class UiState(
    val starting: Boolean = true, val source: SourceConfig? = null,
    val channels: List<Channel> = emptyList(), val filtered: List<Channel> = emptyList(),
    val categories: List<String> = emptyList(), val selectedId: String? = null,
    val playing: Channel? = null, val suspended: Boolean = false,
    val busy: Boolean = false, val progress: Int = 0, val failure: Failure? = null,
    val guideFailure: Boolean = false, val guide: List<Programme> = emptyList(),
    val pinEnabled: Boolean = false, val pinChannel: Channel? = null, val pinError: Boolean = false, val pinBusy: Boolean = false,
    val retrySeconds: Long = 0, val preferences: Preferences = Preferences(),
    val importRevision: Int = 0,
)

@OptIn(FlowPreview::class)
class MainViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as RapoApplication
    val repository = app.repository
    val playback = PlaybackController(app, app.network, viewModelScope)
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()
    private val filters = MutableStateFlow(BrowserFilter(
        destination = runCatching { Destination.valueOf(saved.get<String>("destination") ?: "LIVE") }.getOrDefault(Destination.LIVE),
        category = saved["category"], query = saved["query"] ?: ""))
    val filter = filters.asStateFlow()
    private var operation: Job? = null
    private var selection: Job? = null
    private var guideJob: Job? = null
    private var playJob: Job? = null
    private var foreground = true
    init {
        playback.channelKey = ::switchChannel
        viewModelScope.launch {
            repository.source.catch { mutable.update { it.copy(starting = false, failure = Failure.UNKNOWN) } }.collect { source ->
                mutable.update { old -> old.copy(starting = false, source = source) }
            }
        }
        viewModelScope.launch {
            repository.source.catch { mutable.update { it.copy(failure = Failure.UNKNOWN) }; emit(null) }.map { it?.id }.distinctUntilChanged().collectLatest { source ->
                mutable.update { it.copy(channels = emptyList(), selectedId = saved["selected"]) }
                if (source != null) repository.channels(source).collect { channels ->
                    mutable.update { it.copy(channels = channels, categories = channels.map { c -> c.category }.distinct().sorted(),
                        playing = it.playing?.let { playing -> channels.find { c -> c.id == playing.id } }) }
                }
            }
        }
        viewModelScope.launch {
            combine(mutable.map { it.channels }.distinctUntilChanged(), filters.debounce(120)) { channels, filter ->
                withContext(Dispatchers.Default) {
                    browse(channels, when (filter.destination) { Destination.FAVORITES -> BrowseMode.FAVORITES
                        Destination.RECENT -> BrowseMode.RECENT; else -> BrowseMode.ALL }, filter.category, filter.query)
                }
            }.collect { channels -> mutable.update { it.copy(filtered = channels) } }
        }
        viewModelScope.launch { repository.pinEnabled.collect { enabled -> mutable.update { it.copy(pinEnabled = enabled) } } }
        viewModelScope.launch { app.preferences.values.collect { p -> mutable.update { it.copy(preferences = p) } } }
        viewModelScope.launch {
            while (isActive) {
                mutable.update { it.copy(retrySeconds = repository.pinRetrySeconds()) }
                delay(1000)
            }
        }
    }
    fun navigate(destination: Destination) {
        filters.value = BrowserFilter(destination)
        saved["destination"] = destination.name; saved["category"] = null; saved["query"] = ""
    }
    fun category(value: String?) { filters.update { it.copy(category = value) }; saved["category"] = value }
    fun search(value: String) { filters.update { it.copy(query = value.take(200), category = null) }; saved["query"] = value.take(200) }
    fun select(channel: Channel) {
        saved["selected"] = channel.id
        mutable.update { it.copy(selectedId = channel.id) }
        selection?.cancel()
        val source = mutable.value.source ?: return
        selection = viewModelScope.launch {
            delay(150)
            if (!channel.locked) while (isActive) {
                repository.guide(source.id, channel.id, System.currentTimeMillis()).take(1).collect { p -> mutable.update { it.copy(guide = p) } }
                delay(30_000)
            } else mutable.update { it.copy(guide = emptyList()) }
        }
    }
    fun import(config: SourceConfig) {
        operation?.cancel()
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true, failure = null, progress = 0) }
            try {
                val oldSourceId = mutable.value.source?.id
                repository.import(config) { progress -> mutable.update { it.copy(progress = progress) } }
                if (oldSourceId != config.id) {
                    saved["selected"] = null
                    mutable.update { it.copy(selectedId = null) }
                    navigate(Destination.LIVE)
                }
                mutable.update { it.copy(busy = false, importRevision = it.importRevision + 1) }
                refreshGuide(silent = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(busy = false, failure = Network.classify(error)) } }
        }
    }
    fun cancelImport() { operation?.cancel(); mutable.update { it.copy(busy = false) } }
    fun clearFailure() { mutable.update { it.copy(failure = null, pinError = false) } }
    fun refresh() { mutable.value.source?.let(::import) }
    fun refreshGuide(silent: Boolean = false) {
        val config = mutable.value.source ?: return
        guideJob?.cancel()
        guideJob = viewModelScope.launch {
            try { repository.refreshGuide(config); mutable.update { it.copy(guideFailure = false) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(guideFailure = !silent) } }
        }
    }
    fun watch(channel: Channel) {
        selection?.cancel(); guideJob?.cancel()
        playJob?.cancel(); playback.halt()
        val source = mutable.value.source ?: return
        mutable.update { it.copy(pinChannel = null, playing = channel.takeUnless { it.locked }, guide = emptyList(), suspended = false) }
        if (channel.locked) { playback.release(); mutable.update { it.copy(pinChannel = channel, pinError = false) }; return }
        startPlayback(source, channel)
    }
    private fun startPlayback(source: SourceConfig, channel: Channel) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val url = repository.stream(source.id, channel.id)
                ensureActive()
                if (!foreground || mutable.value.source?.id != source.id) return@launch
                mutable.update { it.copy(playing = channel, suspended = false, pinChannel = null, failure = null) }
                playback.play(channel.id, channel.name, url, mutable.value.preferences) {
                    viewModelScope.launch { repository.watched(source.id, channel.id) }
                }
                guideJob?.cancel()
                guideJob = launch {
                    runCatching { repository.shortGuide(source, channel.id) }
                    while (isActive) {
                        repository.guide(source.id, channel.id, System.currentTimeMillis()).take(1).collect { programmes -> mutable.update { it.copy(guide = programmes) } }
                        delay(30_000)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(failure = Network.classify(error)) } }
        }
    }
    suspend fun verifyChannelPin(pin: String) {
        val channel = mutable.value.pinChannel ?: return
        val source = mutable.value.source ?: return
        if (authenticatePin(pin, channel.id) && mutable.value.pinChannel?.id == channel.id && mutable.value.source?.id == source.id) {
            startPlayback(source, channel)
        }
    }
    fun dismissPin() { mutable.update { it.copy(pinChannel = null, pinError = false) } }
    fun clearPinError() { mutable.update { it.copy(pinError = false) } }
    suspend fun authenticatePin(pin: String, channel: String? = null): Boolean {
        mutable.update { it.copy(pinBusy = true, pinError = false) }
        return try {
            val valid = repository.verifyPin(pin, channel)
            if (!foreground) { repository.revokeGrants(); false } else {
                val retrySeconds = repository.pinRetrySeconds()
                mutable.update { it.copy(pinError = !valid, retrySeconds = retrySeconds) }
                valid
            }
        } catch (cancelled: CancellationException) {
            repository.revokeGrants()
            throw cancelled
        } finally { mutable.update { it.copy(pinBusy = false) } }
    }
    fun switchChannel(delta: Int) {
        val current = mutable.value.playing ?: return
        val channels = mutable.value.filtered.ifEmpty { mutable.value.channels }
        if (channels.isEmpty()) return
        val next = (channels.indexOfFirst { it.id == current.id } + delta).mod(channels.size)
        watch(channels[next])
    }
    fun leavePlayback() {
        playJob?.cancel(); guideJob?.cancel(); playback.release(); repository.revokeGrants()
        mutable.update { it.copy(playing = null, suspended = false) }
        mutable.value.channels.find { it.id == mutable.value.selectedId }?.let(::select)
    }
    fun onForeground(active: Boolean) {
        foreground = active
        repository.revokeGrants()
        if (!active) {
            playJob?.cancel(); playback.release(); repository.revokeGrants()
            mutable.update { it.copy(suspended = it.playing != null) }
        }
    }
    fun resumePlayback() { mutable.value.playing?.let(::watch) }
    fun favorite(channel: Channel) { val source = mutable.value.source ?: return; viewModelScope.launch { repository.favorite(source.id, channel) } }
    fun preferences(prefs: Preferences) { viewModelScope.launch { app.preferences.set(prefs) } }
    fun clearHistory() { mutable.value.source?.let { source -> viewModelScope.launch { repository.clearHistory(source.id) } } }
    fun clearCache() { mutable.value.source?.let { source -> viewModelScope.launch { repository.clearCache(source.id); coil.Coil.imageLoader(app).memoryCache?.clear(); mutable.update { it.copy(guide = emptyList()) } } } }
    fun deleteSource() {
        cancelImport(); selection?.cancel(); guideJob?.cancel(); playJob?.cancel(); playback.release()
        mutable.update { it.copy(playing = null, suspended = false) }
        viewModelScope.launch {
            try {
                repository.deleteSource(); saved["selected"] = null
                mutable.update { it.copy(selectedId = null) }
                navigate(Destination.LIVE)
            }
            catch (e: Exception) { mutable.update { it.copy(failure = Network.classify(e)) } }
        }
    }
    override fun onCleared() { playback.release() }
}
