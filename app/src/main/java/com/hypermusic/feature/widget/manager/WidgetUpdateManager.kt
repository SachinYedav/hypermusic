package com.hypermusic.feature.widget.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.Coil
import coil.request.ImageRequest
import coil.size.Precision
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.core.data.repository.PlaybackStateStore
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.core.utils.ArtworkUrlResolver
import com.hypermusic.core.utils.ColorExtractor
import com.hypermusic.core.utils.DynamicColors
import com.hypermusic.feature.widget.data.WidgetPlaybackState
import com.hypermusic.feature.widget.renderer.WidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetUpdateManager(
    private val context: Context,
    private val playerRepository: PlayerRepository,
    private val localMediaRepository: LocalMediaRepository,
    private val playbackStateStore: PlaybackStateStore,
    private val preferencesManager: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _playbackState = MutableStateFlow(WidgetPlaybackState())
    val playbackState: StateFlow<WidgetPlaybackState> = _playbackState.asStateFlow()

    private var currentCachedArtwork: Bitmap? = null
    private var lastArtworkUrl: String? = null
    private var observeJob: Job? = null
    private var artworkRevision = 0L
    private var immediateJob: Job? = null

    init {
        startObserving()
    }

    fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            launch {
                playerRepository.currentTrack.collectLatest { track ->
                    updateTrack(track)
                }
            }

            launch {
                playerRepository.isPlaying.collect { isPlaying ->
                    if (_playbackState.value.isPlaying != isPlaying) {
                        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                        pushWidgetUpdates()
                    }
                }
            }

            launch {
                playerRepository.repeatMode.collect { repeatMode ->
                    if (_playbackState.value.repeatMode != repeatMode) {
                        _playbackState.value = _playbackState.value.copy(repeatMode = repeatMode)
                        pushWidgetUpdates()
                    }
                }
            }

            launch {
                playerRepository.totalDuration.collect { duration ->
                    if (duration > 0 && duration != _playbackState.value.durationMs) {
                        _playbackState.value = _playbackState.value.copy(durationMs = duration)
                        pushWidgetUpdates()
                    }
                }
            }

            launch {
                @OptIn(ExperimentalCoroutinesApi::class)
                playerRepository.currentTrack
                    .flatMapLatest { track ->
                        if (track != null && !track.isDeviceOrigin()) {
                            localMediaRepository.isTrackLiked(track.id)
                        } else {
                            flowOf(false)
                        }
                    }
                    .collect { isLiked ->
                        if (_playbackState.value.isLiked != isLiked) {
                            _playbackState.value = _playbackState.value.copy(isLiked = isLiked)
                            pushWidgetUpdates()
                        }
                    }
            }
        }
    }

    private suspend fun updateTrack(track: Track?) {
        val revision = ++artworkRevision
        if (track == null) {
            currentCachedArtwork = null
            lastArtworkUrl = null
            _playbackState.value = WidgetPlaybackState(hasTrack = false, canLike = false)
            pushWidgetUpdates()
            return
        }

        val canLike = !track.isDeviceOrigin()
        val isLiked = if (canLike) {
            try {
                localMediaRepository.isTrackLiked(track.id).first()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        if (revision != artworkRevision || playerRepository.currentTrack.value?.id != track.id) return

        val canReuseArtwork = !track.artwork.isNullOrBlank() && track.artwork == lastArtworkUrl && currentCachedArtwork != null
        val initialArtwork = if (canReuseArtwork) currentCachedArtwork else null
        if (!canReuseArtwork) {
            currentCachedArtwork = null
            lastArtworkUrl = null
        }
        val initialColors = if (canReuseArtwork && initialArtwork != null) {
            withContext(Dispatchers.Default) { ColorExtractor.extract(initialArtwork) }
        } else {
            DynamicColors.DEFAULT
        }
        if (revision != artworkRevision || playerRepository.currentTrack.value?.id != track.id) return

        _playbackState.value = WidgetPlaybackState(
            title = track.title,
            artist = track.artist,
            album = track.album ?: "",
            artwork = initialArtwork,
            isPlaying = playerRepository.isPlaying.value,
            durationMs = playerRepository.totalDuration.value.takeIf { it > 0 } ?: ((track.duration ?: 0L) * 1000L),
            positionMs = playerRepository.playbackPosition.value,
            isLiked = isLiked,
            canLike = canLike,
            repeatMode = playerRepository.repeatMode.value,
            hasTrack = true,
            dynamicColors = initialColors
        )
        pushWidgetUpdates()

        if (!canReuseArtwork && !track.artwork.isNullOrBlank()) {
            val loaded = loadDownsampledBitmap(track.artwork)
            if (revision != artworkRevision || playerRepository.currentTrack.value?.queueEntryId != track.queueEntryId) return
            if (loaded != null) {
                currentCachedArtwork = loaded
                lastArtworkUrl = track.artwork
            }
            val dynamicColors = if (loaded != null) {
                withContext(Dispatchers.Default) { ColorExtractor.extract(loaded) }
            } else {
                DynamicColors.DEFAULT
            }
            if (revision != artworkRevision || playerRepository.currentTrack.value?.id != track.id) return
            _playbackState.value = _playbackState.value.copy(
                artwork = loaded,
                dynamicColors = dynamicColors
            )
            pushWidgetUpdates()
        }
    }

    fun requestImmediateUpdate() {
        immediateJob?.cancel()
        immediateJob = scope.launch {
            val track = playerRepository.currentTrack.value
            updateTrack(track)
        }
    }

    fun pushWidgetUpdates() {
        try {
            WidgetRenderer.updateAllWidgets(context, _playbackState.value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadDownsampledBitmap(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        try {
            val resolvedUrl = ArtworkUrlResolver.resolveUrl(url, ArtworkQuality.CARD)
            val request = ImageRequest.Builder(context)
                .data(resolvedUrl)
                .size(512, 512)
                .precision(Precision.INEXACT)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .allowHardware(false) // Must be software bitmap for RemoteViews
                .build()
            val result = Coil.imageLoader(context).execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }
}
