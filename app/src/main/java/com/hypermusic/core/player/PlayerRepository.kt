@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.hypermusic.core.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.hypermusic.core.data.repository.PlaybackStateSnapshot
import com.hypermusic.core.data.repository.PlaybackStateStore
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.core.model.isDeviceUri
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.core.utils.ArtworkUrlResolver
import com.hypermusic.core.utils.ColorExtractor
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.hypermusic.core.lyrics.LyricsDiskCache
import com.hypermusic.core.lyrics.LyricsTranslationService

data class QueuePresentation(
    val tracks: List<Track> = emptyList(),
    val currentEntryId: String? = null,
    val previousTrack: Track? = null,
    val nextTrack: Track? = null
)

class PlayerRepository(
    private val context: Context,
    private val musicService: MusicService,
    val sleepTimerManager: SleepTimerManager = SleepTimerManager(),
    private val playbackStateStore: PlaybackStateStore = PlaybackStateStore(context),
    private val lyricsDiskCache: LyricsDiskCache = LyricsDiskCache(context),
    private val lyricsTranslationService: LyricsTranslationService = LyricsTranslationService(),
    private val runtime: PlaybackRuntimeState
) {

    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController: StateFlow<MediaController?> = _mediaController.asStateFlow()

    private val controller: MediaController?
        get() = _mediaController.value

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()

    private val _queuePresentation = MutableStateFlow(QueuePresentation())
    val queuePresentation: StateFlow<QueuePresentation> = _queuePresentation.asStateFlow()

    private val _currentMediaItemIndex = MutableStateFlow(0)
    val currentMediaItemIndex: StateFlow<Int> = _currentMediaItemIndex.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration: StateFlow<Long> = _totalDuration.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()
    
    private val _playerSheetState = MutableStateFlow(0) // 0: Hidden, 1: Mini, 2: Fullscreen, 3: QueueHalf, 4: QueueFull
    val playerSheetState: StateFlow<Int> = _playerSheetState.asStateFlow()

    fun updatePlayerSheetState(state: Int) {
        _playerSheetState.value = state
    }
    
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var pendingPlaybackAction: (() -> Unit)? = null
    private var reconnectAttempts = 0
    private var released = false
    private var reconnectJob: Job? = null
    private var connectionDeadline: Job? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    enum class Connection { DISCONNECTED, CONNECTING, RESTORING, READY, FAILED }
    private val _connection = MutableStateFlow(Connection.DISCONNECTED)
    val connection: StateFlow<Connection> = _connection.asStateFlow()
    val autoplayStatus: StateFlow<AutoplayStatus> = runtime.autoplay
    private val _isFetchingRadio = MutableStateFlow(false)
    val isFetchingRadio: StateFlow<Boolean> = _isFetchingRadio.asStateFlow()

    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()


    private val _activeOverlaySheet = MutableStateFlow(PlayerOverlaySheet.NONE)
    val activeOverlaySheet: StateFlow<PlayerOverlaySheet> = _activeOverlaySheet.asStateFlow()

    private val content = PlayerContentRepository(musicService, lyricsDiskCache, lyricsTranslationService)
    val lyricsState: StateFlow<LyricsUiState> = content.lyrics
    val relatedState: StateFlow<RelatedUiState> = content.related
    val isTranslationEnabled: StateFlow<Boolean> = content.translationEnabled
    val isTranslatingLyrics: StateFlow<Boolean> = content.translating
    val targetTranslationLang: StateFlow<String> = content.language

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()


    init {
        initializeController()
        scope.launch { runtime.error.collect { _playerError.value = it } }
        scope.launch { runtime.autoplay.collect { _isFetchingRadio.value = it is AutoplayStatus.Loading } }
    }

    private fun initializeController() {
        if (released) return
        _connection.value = Connection.CONNECTING
        val token = SessionToken(context, ComponentName(context, HyperMediaService::class.java))
        val future = MediaController.Builder(context, token).setListener(object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                if (released || this@PlayerRepository.controller !== controller) return
                connectionDeadline?.cancel()
                controller.removeListener(listener)
                _mediaController.value = null
                stopProgressUpdate()
                _connection.value = Connection.DISCONNECTED
                controller.release()
                scheduleReconnect()
            }
        }).buildAsync()
        controllerFuture = future
        connectionDeadline?.cancel()
        connectionDeadline = scope.launch {
            delay(10_000)
            if (controllerFuture !== future || _connection.value == Connection.READY) return@launch
            controllerFuture = null
            controller?.removeListener(listener)
            _mediaController.value = null
            MediaController.releaseFuture(future)
            _connection.value = Connection.FAILED
            _playerError.value = "Playback connection timed out"
            scheduleReconnect()
        }
        future.addListener({
            if (released || controllerFuture !== future) return@addListener
            try {
                val ctrl = future.get()
                _mediaController.value = ctrl
                ctrl.addListener(listener)
                publish(ctrl)
                _connection.value = Connection.RESTORING
                scope.launch {
                    val ready = withTimeoutOrNull(10_000) { runtime.ready.first { it } }
                    if (ready != true) {
                        return@launch
                    }
                    if (controller !== ctrl) return@launch
                    connectionDeadline?.cancel()
                    _connection.value = Connection.READY
                    reconnectAttempts = 0
                    publish(ctrl)
                    val action = pendingPlaybackAction
                    pendingPlaybackAction = null
                    action?.invoke()
                }
            } catch (e: Exception) {
                connectionDeadline?.cancel()
                _connection.value = Connection.FAILED
                _playerError.value = "Unable to connect to playback"
                scheduleReconnect()
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    private fun scheduleReconnect() {
        if (released || reconnectJob?.isActive == true) return
        if (reconnectAttempts >= 3) { _connection.value = Connection.FAILED; return }
        val attempt = ++reconnectAttempts
        reconnectJob = scope.launch { delay(attempt * 500L); reconnectJob = null; initializeController() }
    }

    fun close() {
        released = true
        pendingPlaybackAction = null
        content.cancel()
        controller?.removeListener(listener)
        controllerFuture?.let(MediaController::releaseFuture)
        _mediaController.value = null
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        _connection.value = Connection.DISCONNECTED
    }

    private val listener by lazy { object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { publish(player) }
    } }

    private fun publish(player: Player) {
        val physicalTracks = (0 until player.mediaItemCount).map { TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(it)) }
        _currentQueue.value = physicalTracks
        _currentMediaItemIndex.value = player.currentMediaItemIndex.coerceAtLeast(0)
        val previous = _currentTrack.value?.id
        val track = player.currentMediaItem?.let(TrackMediaItemMapper::fromMediaItem)
        _currentTrack.value = track
        _isVideoMode.value = if (player.playbackState == Player.STATE_READY)
            player.currentTracks.isTypeSelected(androidx.media3.common.C.TRACK_TYPE_VIDEO)
        else track?.let(::isVideoTrack) ?: false
        _isPlaying.value = player.isPlaying
        _playbackState.value = if (pendingPlaybackAction != null && player.playbackState == Player.STATE_IDLE)
            Player.STATE_BUFFERING
        else
            player.playbackState
        _shuffleModeEnabled.value = player.shuffleModeEnabled
        _repeatMode.value = player.repeatMode

        val timeline = player.currentTimeline
        val traversalTracks = if (player.shuffleModeEnabled && !timeline.isEmpty) {
            val indices = mutableListOf<Int>()
            var idx = timeline.getFirstWindowIndex(true)
            while (idx != androidx.media3.common.C.INDEX_UNSET && indices.size < timeline.windowCount) {
                indices += idx
                idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, true)
            }
            indices.mapNotNull { physicalTracks.getOrNull(it) }
        } else {
            physicalTracks
        }

        val nextIndex = player.nextMediaItemIndex.takeIf { it >= 0 }
        val prevIndex = player.previousMediaItemIndex.takeIf { it >= 0 }
        val nextTrack = nextIndex?.let { physicalTracks.getOrNull(it) }
        val prevTrack = prevIndex?.let { physicalTracks.getOrNull(it) }

        _queuePresentation.value = QueuePresentation(
            tracks = traversalTracks,
            currentEntryId = track?.queueEntryId,
            previousTrack = prevTrack,
            nextTrack = nextTrack
        )

        updateProgressImmediate()
        if (player.isPlaying) startProgressUpdate() else stopProgressUpdate()
        if (previous != track?.id) {
            content.onTrackChanged()
            if (track != null) when (_activeOverlaySheet.value) {
                PlayerOverlaySheet.LYRICS -> fetchLyrics(track.id)
                PlayerOverlaySheet.RELATED -> {
                    if (track.isDeviceOrigin()) {
                        _activeOverlaySheet.value = PlayerOverlaySheet.NONE
                    } else {
                        fetchRelated(track.id)
                    }
                }
                else -> {}
            }
        }
    }

    fun isVideoTrack(track: Track): Boolean = !track.isDeviceOrigin() && track.trackType in setOf("video", "podcast", "podcast_episode")
    private fun trackToMediaItem(track: Track) = TrackMediaItemMapper.toMediaItem(track,
        if (track.isDeviceOrigin()) track.id else "hyper://${track.id}/${if (isVideoTrack(track)) "video" else "audio"}")

    private fun startProgressUpdate() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch { while (true) { updateProgressImmediate(); delay(250) } }
    }
    private fun stopProgressUpdate() { progressJob?.cancel(); progressJob = null; updateProgressImmediate() }
    private fun updateProgressImmediate() {
        controller?.let {
            _playbackPosition.value = it.currentPosition.coerceAtLeast(0)
            _totalDuration.value = it.duration.takeIf { d -> d > 0 } ?: ((_currentTrack.value?.duration ?: 0) * 1000)
        }
    }
    private fun whenReady(action: () -> Unit) {
        if (_connection.value == Connection.READY) action() else pendingPlaybackAction = action
    }

    fun playTrack(track: Track) = whenReady {
        controller?.sendCustomCommand(androidx.media3.session.SessionCommand("PLAY_TRACK", android.os.Bundle.EMPTY),
            android.os.Bundle().apply {
                putBundle("item", trackToMediaItem(track).toBundle())
                putString("entryId", track.queueEntryId)
            })
    }
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) = whenReady {
        if (tracks.isEmpty()) { clearQueue(); return@whenReady }
        val ctrl = controller ?: return@whenReady
        ctrl.setMediaItems(tracks.map { trackToMediaItem(it.copy(isImplicit = false, queueEntryId = java.util.UUID.randomUUID().toString())) },
            startIndex.coerceIn(tracks.indices), 0)
        ctrl.prepare(); ctrl.play()
    }
    fun playTracksShuffled(tracks: List<Track>) = whenReady {
        if (tracks.isEmpty()) { clearQueue(); return@whenReady }
        val ctrl = controller ?: return@whenReady
        ctrl.shuffleModeEnabled = true
        ctrl.setMediaItems(tracks.map { trackToMediaItem(it.copy(isImplicit = false, queueEntryId = java.util.UUID.randomUUID().toString())) },
            androidx.media3.common.C.INDEX_UNSET, androidx.media3.common.C.TIME_UNSET)
        ctrl.prepare(); ctrl.play()
    }
    fun moveQueueEntry(entryId: String, beforeEntryId: String?) {
        controller?.sendCustomCommand(androidx.media3.session.SessionCommand("MOVE_ENTRY", android.os.Bundle.EMPTY),
            android.os.Bundle().apply { putString("entryId", entryId); putString("beforeEntryId", beforeEntryId) })
    }
    fun removeQueueEntry(queueEntryId: String) {
        controller?.sendCustomCommand(androidx.media3.session.SessionCommand("REMOVE_ENTRY", android.os.Bundle.EMPTY),
            android.os.Bundle().apply { putString("entryId", queueEntryId) })
    }
    fun removeTrack(track: Track) { track.queueEntryId?.let { removeQueueEntry(it) } }
    fun removeMediaItem(index: Int) {
        val ctrl = controller ?: return
        if (index !in 0 until ctrl.mediaItemCount) return
        ctrl.getMediaItemAt(index).mediaMetadata.extras?.getString("queueEntryId")?.let(::removeQueueEntry)
    }
    fun clearQueue() {
        pendingPlaybackAction = null
        controller?.let { it.stop(); it.clearMediaItems() }
        playbackStateStore.clearState()
        sleepTimerManager.cancel()
        closeOverlaySheet()
    }
    fun clearSavedPlaybackState() { playbackStateStore.clearState() }
    fun pause() {
        pendingPlaybackAction = null
        if (_connection.value != Connection.READY && _playbackState.value == Player.STATE_BUFFERING) {
            _playbackState.value = Player.STATE_IDLE
        }
        controller?.pause()
    }
    fun togglePlayPause() {
        val ctrl = controller
        if (_connection.value == Connection.FAILED) {
            pendingPlaybackAction = null
            return
        }
        if (_connection.value != Connection.READY) {
            if (pendingPlaybackAction != null) {
                pendingPlaybackAction = null
                _playbackState.value = Player.STATE_IDLE
            } else {
                pendingPlaybackAction = { _mediaController.value?.let { if (!it.playWhenReady) it.play() } }
                _playbackState.value = Player.STATE_BUFFERING
            }
            return
        }
        if (ctrl == null) {
            pendingPlaybackAction = null
            return
        }
        if (ctrl.playWhenReady) {
            ctrl.pause()
        } else {
            ctrl.play()
        }
    }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0)); updateProgressImmediate() }
    fun seekRelative(offsetMs: Long) {
        val ctrl = controller ?: return
        seekTo((ctrl.currentPosition + offsetMs).coerceIn(0, _totalDuration.value.coerceAtLeast(0)))
    }
    fun seekForward(offsetMs: Long = 10_000) = seekRelative(offsetMs)
    fun seekBackward(offsetMs: Long = 10_000) = seekRelative(-offsetMs)
    fun skipNext() {
        val ctrl = controller ?: return
        ctrl.seekToNextMediaItem()
        if (!ctrl.playWhenReady) {
            ctrl.play()
        }
    }
    fun skipPrevious() {
        val ctrl = controller ?: return
        ctrl.seekToPreviousMediaItem()
        if (!ctrl.playWhenReady) {
            ctrl.play()
        }
    }
    fun retryPlayback() { clearPlayerError(); controller?.sendCustomCommand(androidx.media3.session.SessionCommand("RETRY_PLAYBACK", android.os.Bundle.EMPTY), android.os.Bundle.EMPTY) }
    fun clearPlayerError() { runtime.error.value = null; _playerError.value = null }
    fun retryAutoplay() { controller?.sendCustomCommand(androidx.media3.session.SessionCommand("RETRY_AUTOPLAY", android.os.Bundle.EMPTY), android.os.Bundle.EMPTY) }
    fun toggleShuffle() {
        controller?.sendCustomCommand(androidx.media3.session.SessionCommand("TOGGLE_SHUFFLE", android.os.Bundle.EMPTY), android.os.Bundle.EMPTY)
    }
    fun toggleRepeat() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }

    suspend fun executeWidgetActionWhenReady(action: () -> Unit): Boolean = withTimeoutOrNull(6_000) {
        connection.first { it == Connection.READY || it == Connection.FAILED }
        if (connection.value != Connection.READY) return@withTimeoutOrNull false
        withContext(Dispatchers.Main) { action() }
        true
    } ?: false

    fun insertNext(track: Track) {
        enqueue(track, "INSERT_NEXT")
    }
    fun appendToQueue(track: Track) {
        enqueue(track, "APPEND_QUEUE")
    }
    private fun enqueue(track: Track, action: String) {
        controller?.sendCustomCommand(androidx.media3.session.SessionCommand(action, android.os.Bundle.EMPTY),
            android.os.Bundle().apply { putBundle("item", trackToMediaItem(track).toBundle()) })
    }
    fun openLyrics(trackId: String? = null) {
        _activeOverlaySheet.value = PlayerOverlaySheet.LYRICS
        val id = trackId ?: _currentTrack.value?.id ?: return
        fetchLyrics(id)
    }

    fun openRelated(trackId: String? = null) {
        val current = _currentTrack.value
        val id = trackId ?: current?.id ?: return
        if ((current?.id == id && current.isDeviceOrigin()) || isDeviceUri(id)) return
        _activeOverlaySheet.value = PlayerOverlaySheet.RELATED
        fetchRelated(id)
    }

    fun closeOverlaySheet() {
        content.cancel()
        _activeOverlaySheet.value = PlayerOverlaySheet.NONE
    }

    fun fetchLyrics(trackId: String, forceRefresh: Boolean = false) = content.fetchLyrics(trackId, _currentTrack.value, forceRefresh)
    fun fetchRelated(trackId: String, forceRefresh: Boolean = false) {
        val isLocal = _currentTrack.value?.takeIf { it.id == trackId }?.isDeviceOrigin() == true
        content.fetchRelated(trackId, isDeviceLocal = isLocal, force = forceRefresh)
    }
    fun toggleLyricsTranslation() = content.toggleTranslation()
    fun setLyricsTranslationLanguage(langCode: String) = content.setLanguage(langCode)
    fun ensureLyricsTranslated(force: Boolean = false) = content.translate(force)
}
