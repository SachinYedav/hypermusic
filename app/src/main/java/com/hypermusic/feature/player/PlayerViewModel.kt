package com.hypermusic.feature.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.hypermusic.core.data.local.entity.SavedEpisodeEntity
import com.hypermusic.core.data.repository.DownloadRepository
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.core.model.UserSettings
import com.hypermusic.core.player.AutoplayStatus
import com.hypermusic.core.player.LyricsUiState
import com.hypermusic.core.player.PlayerOverlaySheet
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.core.player.RelatedUiState
import com.hypermusic.feature.shared.state.ToastAction
import com.hypermusic.feature.shared.state.ToastType
import com.hypermusic.navigation.NavigationEventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val totalDuration: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isVideoMode: Boolean = false,
    val mediaController: MediaController? = null,
    val activeOverlaySheet: PlayerOverlaySheet = PlayerOverlaySheet.NONE,
    val lyricsState: LyricsUiState = LyricsUiState.Idle,
    val relatedState: RelatedUiState = RelatedUiState.Idle,
    val isLiked: Boolean = false,
    val isSavedEpisode: Boolean = false,
    val isDownloaded: Boolean = false,
    val userSettings: UserSettings = UserSettings(),
    val queue: List<Track> = emptyList(),
    val currentEntryId: String? = null,
    val previousTrack: Track? = null,
    val nextTrack: Track? = null,
    val isFetchingRadio: Boolean = false,
    val autoplayStatus: AutoplayStatus = AutoplayStatus.Idle,
    val currentMediaItemIndex: Int = 0,
    val isTranslationEnabled: Boolean = false,
    val isTranslatingLyrics: Boolean = false,
    val targetTranslationLang: String = "en"
)

class PlayerViewModel(
    private val playerRepository: PlayerRepository,
    private val localMediaRepository: LocalMediaRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesManager: PreferencesManager,
    private val navigationEventBus: NavigationEventBus
) : ViewModel() {

    // High-frequency 4Hz position tick is kept completely decoupled from PlayerUiState
    // so that full-screen recomposition is not triggered every 250ms.
    val playbackPosition: StateFlow<Long> = playerRepository.playbackPosition

    @OptIn(ExperimentalCoroutinesApi::class)
    private val isLikedFlow: Flow<Boolean> = playerRepository.currentTrack.flatMapLatest { track ->
        if (track != null) {
            localMediaRepository.isTrackLiked(track.id)
        } else {
            flowOf(false)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val isSavedEpisodeFlow: Flow<Boolean> = playerRepository.currentTrack.flatMapLatest { track ->
        if (track != null) {
            localMediaRepository.isEpisodeSaved(track.id)
        } else {
            flowOf(false)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val isDownloadedFlow: Flow<Boolean> = playerRepository.currentTrack.flatMapLatest { track ->
        if (track != null) {
            downloadRepository.getDownloadFlow(track.id).map { it != null }
        } else {
            flowOf(false)
        }
    }

    private data class CorePlaybackState(
        val currentTrack: Track?,
        val isPlaying: Boolean,
        val playbackState: Int,
        val totalDuration: Long,
        val shuffleModeEnabled: Boolean,
        val repeatMode: Int
    )

    private val corePlaybackFlow: Flow<CorePlaybackState> = combine(
        playerRepository.currentTrack,
        playerRepository.isPlaying,
        playerRepository.playbackState,
        playerRepository.totalDuration,
        combine(playerRepository.shuffleModeEnabled, playerRepository.repeatMode, ::Pair)
    ) { track, isPlaying, playbackState, duration, (shuffle, repeat) ->
        CorePlaybackState(track, isPlaying, playbackState, duration, shuffle, repeat)
    }

    private data class DisplayState(
        val isVideoMode: Boolean,
        val mediaController: MediaController?,
        val activeOverlaySheet: PlayerOverlaySheet,
        val lyricsState: LyricsUiState,
        val relatedState: RelatedUiState,
        val isTranslationEnabled: Boolean,
        val isTranslatingLyrics: Boolean,
        val targetTranslationLang: String
    )

    private val translationFlow = combine(
        playerRepository.isTranslationEnabled,
        playerRepository.isTranslatingLyrics,
        playerRepository.targetTranslationLang
    ) { enabled, translating, lang ->
        Triple(enabled, translating, lang)
    }

    private val displayFlow: Flow<DisplayState> = combine(
        playerRepository.isVideoMode,
        playerRepository.mediaController,
        playerRepository.activeOverlaySheet,
        combine(playerRepository.lyricsState, playerRepository.relatedState, translationFlow) { lyrics, related, trans ->
            Triple(lyrics, related, trans)
        }
    ) { video, controller, overlay, (lyrics, related, trans) ->
        DisplayState(
            video, controller, overlay, lyrics, related,
            trans.first, trans.second, trans.third
        )
    }

    private data class ExtrasState(
        val isLiked: Boolean,
        val isSavedEpisode: Boolean,
        val isDownloaded: Boolean,
        val userSettings: UserSettings,
        val queue: List<Track>,
        val currentEntryId: String?,
        val previousTrack: Track?,
        val nextTrack: Track?,
        val isFetchingRadio: Boolean,
        val autoplayStatus: AutoplayStatus,
        val currentMediaItemIndex: Int
    )

    private val trackStatusFlow = combine(
        isLikedFlow,
        isSavedEpisodeFlow,
        isDownloadedFlow
    ) { liked, saved, downloaded ->
        Triple(liked, saved, downloaded)
    }

    private val extrasFlow: Flow<ExtrasState> = combine(
        trackStatusFlow,
        preferencesManager.userSettingsFlow,
        playerRepository.queuePresentation,
        combine(playerRepository.autoplayStatus, playerRepository.currentMediaItemIndex, ::Pair)
    ) { (liked, saved, downloaded), settings, presentation, (autoplayStatus, mediaIndex) ->
        ExtrasState(
            isLiked = liked,
            isSavedEpisode = saved,
            isDownloaded = downloaded,
            userSettings = settings,
            queue = presentation.tracks,
            currentEntryId = presentation.currentEntryId,
            previousTrack = presentation.previousTrack,
            nextTrack = presentation.nextTrack,
            isFetchingRadio = autoplayStatus is AutoplayStatus.Loading,
            autoplayStatus = autoplayStatus,
            currentMediaItemIndex = mediaIndex
        )
    }

    val uiState: StateFlow<PlayerUiState> = combine(
        corePlaybackFlow,
        displayFlow,
        extrasFlow
    ) { core, display, extras ->
        PlayerUiState(
            currentTrack = core.currentTrack,
            isPlaying = core.isPlaying,
            playbackState = core.playbackState,
            totalDuration = core.totalDuration,
            shuffleModeEnabled = core.shuffleModeEnabled,
            repeatMode = core.repeatMode,
            isVideoMode = display.isVideoMode,
            mediaController = display.mediaController,
            activeOverlaySheet = display.activeOverlaySheet,
            lyricsState = display.lyricsState,
            relatedState = display.relatedState,
            isLiked = extras.isLiked,
            isSavedEpisode = extras.isSavedEpisode,
            isDownloaded = extras.isDownloaded,
            userSettings = extras.userSettings,
            queue = extras.queue,
            currentEntryId = extras.currentEntryId,
            previousTrack = extras.previousTrack,
            nextTrack = extras.nextTrack,
            isFetchingRadio = extras.isFetchingRadio,
            autoplayStatus = extras.autoplayStatus,
            currentMediaItemIndex = extras.currentMediaItemIndex,
            isTranslationEnabled = display.isTranslationEnabled,
            isTranslatingLyrics = display.isTranslatingLyrics,
            targetTranslationLang = display.targetTranslationLang
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState(
            currentTrack = playerRepository.currentTrack.value,
            isPlaying = playerRepository.isPlaying.value,
            playbackState = playerRepository.playbackState.value,
            totalDuration = playerRepository.totalDuration.value,
            shuffleModeEnabled = playerRepository.shuffleModeEnabled.value,
            repeatMode = playerRepository.repeatMode.value,
            isVideoMode = playerRepository.isVideoMode.value,
            mediaController = playerRepository.mediaController.value,
            activeOverlaySheet = playerRepository.activeOverlaySheet.value,
            lyricsState = playerRepository.lyricsState.value,
            relatedState = playerRepository.relatedState.value,
            queue = playerRepository.queuePresentation.value.tracks,
            currentEntryId = playerRepository.queuePresentation.value.currentEntryId,
            previousTrack = playerRepository.queuePresentation.value.previousTrack,
            nextTrack = playerRepository.queuePresentation.value.nextTrack,
            isFetchingRadio = playerRepository.isFetchingRadio.value,
            autoplayStatus = playerRepository.autoplayStatus.value,
            currentMediaItemIndex = playerRepository.currentMediaItemIndex.value,
            isTranslationEnabled = playerRepository.isTranslationEnabled.value,
            isTranslatingLyrics = playerRepository.isTranslatingLyrics.value,
            targetTranslationLang = playerRepository.targetTranslationLang.value
        )
    )

    fun retryAutoplay() = playerRepository.retryAutoplay()

    val sleepTimerActiveOption: StateFlow<String> = playerRepository.sleepTimerManager.activeOption
    val sleepTimerRemainingMs: StateFlow<Long?> = playerRepository.sleepTimerManager.remainingMs
    fun setSleepTimer(option: String) = playerRepository.sleepTimerManager.set(option)
    fun cancelSleepTimer() = playerRepository.sleepTimerManager.cancel()

    fun togglePlayPause() = playerRepository.togglePlayPause()
    fun play() {
        if (!playerRepository.isPlaying.value) {
            playerRepository.togglePlayPause()
        }
    }
    fun pause() = playerRepository.pause()
    fun skipNext() = playerRepository.skipNext()
    fun skipPrevious() = playerRepository.skipPrevious()
    fun seekTo(position: Long) = playerRepository.seekTo(position)
    fun seekForward(offsetMs: Long = 10_000L) = playerRepository.seekForward(offsetMs)
    fun seekBackward(offsetMs: Long = 10_000L) = playerRepository.seekBackward(offsetMs)
    fun toggleShuffle() = playerRepository.toggleShuffle()
    fun toggleRepeat() = playerRepository.toggleRepeat()
    fun playTrack(track: Track) = playerRepository.playTrack(track)

    fun moveQueueItem(entryId: String, beforeEntryId: String?) = playerRepository.moveQueueEntry(entryId, beforeEntryId)
    fun removeQueueItem(queueEntryId: String, index: Int) = playerRepository.removeQueueEntry(queueEntryId)
    fun clearQueue() = playerRepository.clearQueue()

    fun closeOverlaySheet() = playerRepository.closeOverlaySheet()
    fun openLyrics(trackId: String? = null) = playerRepository.openLyrics(trackId)
    fun openRelated(trackId: String? = null) = playerRepository.openRelated(trackId)
    fun fetchLyrics(trackId: String, forceRefresh: Boolean = false) = playerRepository.fetchLyrics(trackId, forceRefresh)
    fun fetchRelated(trackId: String, forceRefresh: Boolean = false) = playerRepository.fetchRelated(trackId, forceRefresh)
    fun toggleLyricsTranslation() = playerRepository.toggleLyricsTranslation()
    fun setLyricsTranslationLanguage(langCode: String) = playerRepository.setLyricsTranslationLanguage(langCode)

    fun toggleAutoplay(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.toggleAutoplay(enabled)
        }
    }

    fun toggleLike(
        track: Track,
        isCurrentlyLiked: Boolean,
        onToast: (String, ToastType) -> Unit = { _, _ -> }
    ) {
        if (!isCurrentlyLiked && track.isDeviceOrigin()) {
            onToast("Unsupported for local files", ToastType.INFO)
            return
        }
        viewModelScope.launch {
            localMediaRepository.setLiked(track, !isCurrentlyLiked)
        }
    }

    fun toggleSaveEpisode(
        track: Track,
        isCurrentlySaved: Boolean,
        onToast: (String, ToastType) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            if (isCurrentlySaved) {
                localMediaRepository.unsaveEpisodeForLater(track.id)
                onToast("Removed from Saved Episodes", ToastType.INFO)
            } else {
                localMediaRepository.saveEpisodeForLater(
                    SavedEpisodeEntity(
                        episodeId = track.id,
                        showId = track.albumId,
                        showTitle = track.album,
                        title = track.title,
                        author = track.artist,
                        artworkUrl = track.artwork,
                        duration = track.duration ?: 0L,
                        description = track.description,
                        releaseDate = track.releaseDate
                    )
                )
                onToast("Saved for Later", ToastType.LIKED)
            }
        }
    }

    fun downloadTrack(
        track: Track,
        onToast: (String, ToastType, ToastAction?) -> Unit = { _, _, _ -> }
    ) {
        if (track.isDeviceOrigin()) {
            onToast("Unsupported for local files", ToastType.INFO, null)
            return
        }
        viewModelScope.launch {
            if (downloadRepository.isDownloaded(track.id)) {
                onToast("Already Downloaded", ToastType.INFO, null)
            } else {
                val queueItem = downloadRepository.getQueueItem(track.id)
                if (queueItem != null && (queueItem.status == "PENDING" || queueItem.status == "DOWNLOADING")) {
                    onToast(
                        "Already Downloading",
                        ToastType.INFO,
                        ToastAction("View") {
                            navigationEventBus.navigateTo("downloads")
                        }
                    )
                } else {
                    val result = downloadRepository.enqueueDownload(track)
                    if (result == com.hypermusic.core.data.repository.DownloadEnqueueResult.UNSUPPORTED) {
                        onToast("Unsupported for local files", ToastType.INFO, null)
                        return@launch
                    }
                    val message = if (result == com.hypermusic.core.data.repository.DownloadEnqueueResult.WAITING_FOR_WIFI) {
                        "Queued • Waiting for Wi-Fi"
                    } else {
                        "Download Started"
                    }
                    onToast(
                        message,
                        ToastType.INFO,
                        ToastAction("View") {
                            navigationEventBus.navigateTo("downloads")
                        }
                    )
                }
            }
        }
    }
}
