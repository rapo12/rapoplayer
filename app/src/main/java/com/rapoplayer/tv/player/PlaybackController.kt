@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.rapoplayer.tv.player

import android.content.Context
import android.view.KeyEvent
import androidx.media3.common.*
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import com.rapoplayer.tv.core.*
import com.rapoplayer.tv.data.Network
import com.rapoplayer.tv.data.Preferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackChoice(val group: TrackGroup, val index: Int, val language: String?, val label: String?, val selected: Boolean)
data class PlaybackState(
    val buffering: Boolean = false, val playing: Boolean = false, val ready: Boolean = false,
    val live: Boolean = false, val seekable: Boolean = false, val error: Failure? = null,
    val recoveredLive: Boolean = false, val audio: List<TrackChoice> = emptyList(), val subtitles: List<TrackChoice> = emptyList(),
    val unsupportedTracks: Boolean = false,
)

class PlaybackController(private val context: Context, private val network: Network, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
    var player: ExoPlayer? = null; private set
    private var session: MediaSession? = null
    private var retry: Job? = null
    private var attempts = 0
    private var generation = 0
    private var firstFrame: (() -> Unit)? = null
    var channelKey: ((Int) -> Unit)? = null

    fun play(id: String, title: String, url: String, prefs: Preferences, onFirstFrame: () -> Unit) {
        SafeUrl.parse(url)
        retry?.cancel(); generation++; attempts = 0
        val p = player ?: create().also { player = it }
        p.stop(); p.clearMediaItems()
        firstFrame = onFirstFrame
        mutable.value = PlaybackState(buffering = true)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .clearOverrides().setPreferredAudioLanguage(prefs.audio.ifBlank { null })
            .setPreferredTextLanguage(prefs.subtitles.ifBlank { null }).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !prefs.subtitlesEnabled).build()
        p.setMediaItem(MediaItem.Builder().setMediaId(id).setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build())
        p.prepare(); p.playWhenReady = true
    }
    private fun create(): ExoPlayer {
        val policy = object : DefaultLoadErrorHandlingPolicy(0) {
            override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long = C.TIME_UNSET
        }
        val factory = DefaultMediaSourceFactory(OkHttpDataSource.Factory(network.streaming).setUserAgent("RAPOPLAYER/0.1"))
            .setLoadErrorHandlingPolicy(policy)
        val p = ExoPlayer.Builder(context).setMediaSourceFactory(factory).build()
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        p.setHandleAudioBecomingNoisy(true)
        p.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                mutable.value = mutable.value.copy(buffering = player.playbackState == Player.STATE_BUFFERING,
                    playing = player.isPlaying, ready = player.playbackState == Player.STATE_READY,
                    live = player.isCurrentMediaItemLive, seekable = player.isCurrentMediaItemSeekable)
            }
            override fun onRenderedFirstFrame() { firstFrame?.invoke(); firstFrame = null }
            override fun onTracksChanged(tracks: Tracks) {
                fun choices(type: Int) = tracks.groups.filter { it.type == type }.flatMap { group ->
                    (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                        val format = group.getTrackFormat(index)
                        TrackChoice(group.mediaTrackGroup, index, format.language, format.label, group.isTrackSelected(index))
                    }
                }
                mutable.value = mutable.value.copy(audio = choices(C.TRACK_TYPE_AUDIO), subtitles = choices(C.TRACK_TYPE_TEXT),
                    unsupportedTracks = tracks.groups.any { g -> (0 until g.length).any { !g.isTrackSupported(it) } })
            }
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && attempts++ < 1) {
                    mutable.value = mutable.value.copy(recoveredLive = true)
                    p.seekToDefaultPosition(); p.prepare(); p.play(); return
                }
                val failure = classify(error)
                mutable.value = mutable.value.copy(error = failure, buffering = false)
                val delayMs = RetryPolicy.delayMillis(failure, attempts++) ?: return
                val token = generation
                retry = scope.launch {
                    delay(delayMs)
                    if (token == generation && player === p) { mutable.value = mutable.value.copy(error = null); p.prepare(); p.play() }
                }
            }
        })
        session = MediaSession.Builder(context, p).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val result = super.onConnect(session, controller)
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(result.availablePlayerCommands.buildUpon()
                        .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS).remove(Player.COMMAND_SET_MEDIA_ITEM).build()).build()
            }
            override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: android.content.Intent): Boolean {
                @Suppress("DEPRECATION") val event = intent.getParcelableExtra<KeyEvent>(android.content.Intent.EXTRA_KEY_EVENT)
                if (event?.action == KeyEvent.ACTION_DOWN && event.keyCode in setOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
                    channelKey?.invoke(if (event.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) 1 else -1); return true
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }
        }).build()
        return p
    }
    fun togglePause() {
        val p = player ?: return
        if (p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)) p.pause() else {
            // No promise of persistent timeshift. Non-seekable live inputs reconnect at the edge.
            if (!p.isCurrentMediaItemSeekable) {
                p.stop(); p.seekToDefaultPosition(); p.prepare()
                mutable.value = mutable.value.copy(recoveredLive = true)
            }
            p.play()
        }
    }
    fun resume() { player?.let { if (!it.playWhenReady) togglePause() else it.play() } }
    fun halt() {
        retry?.cancel(); generation++; firstFrame = null
        player?.stop(); player?.clearMediaItems()
        mutable.value = PlaybackState(buffering = true)
    }
    fun liveEdge() { player?.let { it.seekToDefaultPosition(); it.prepare(); it.play() }; mutable.value = mutable.value.copy(recoveredLive = true) }
    fun choose(type: Int, track: TrackChoice?) { player?.let { p ->
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
            .setTrackTypeDisabled(type, track == null && type == C.TRACK_TYPE_TEXT)
            .apply { track?.let { setOverrideForType(TrackSelectionOverride(it.group, it.index)) } }.build()
    } }
    fun release() {
        retry?.cancel(); retry = null; generation++; firstFrame = null
        session?.release(); session = null
        player?.stop(); player?.clearMediaItems(); player?.release(); player = null
        mutable.value = PlaybackState()
    }
    companion object {
        fun classify(error: PlaybackException): Failure {
            var cause: Throwable? = error
            while (cause != null) {
                if (cause is HttpDataSource.InvalidResponseCodeException) return Network.httpFailure(cause.responseCode)
                if (cause is javax.net.ssl.SSLException) return Failure.TLS
                cause = cause.cause
            }
            return when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> Failure.NETWORK
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> Failure.TIMEOUT
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED -> Failure.DECODER
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> Failure.UNSUPPORTED
                else -> Failure.UNSUPPORTED
            }
        }
    }
}
