package com.hypermusic.feature.shared.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.core.data.local.entity.AlbumEntity
import com.hypermusic.core.data.local.entity.ArtistEntity
import com.hypermusic.core.data.local.entity.PlaylistEntity
import com.hypermusic.core.data.local.entity.PodcastEntity
import com.hypermusic.core.data.local.entity.SavedEpisodeEntity
import com.hypermusic.core.data.repository.DownloadEnqueueResult
import com.hypermusic.core.data.repository.DownloadRepository
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.model.ArtistInfo
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.navigation.NavigationEventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed class ActionContext {
    data class TrackContext(val track: Track) : ActionContext()
    data class DeviceLocalTrackContext(val track: Track) : ActionContext()
    data class PlaylistTrackContext(val playlistId: String, val track: Track, val isLocalPlaylist: Boolean) : ActionContext()
    data class HistoryTrackContext(val track: Track) : ActionContext()
    data class DownloadTrackContext(val track: Track) : ActionContext()
    data class PlaylistContext(val id: String, val title: String, val artworkUrl: String?, val isLocal: Boolean = false) : ActionContext()
    data class AlbumContext(val id: String, val title: String, val artist: String, val artworkUrl: String?, val isLocal: Boolean = false) : ActionContext()
    data class ArtistContext(val id: String, val name: String, val avatarUrl: String?) : ActionContext()
    data class PodcastContext(val id: String, val title: String, val author: String, val artworkUrl: String?) : ActionContext()
    data class PodcastEpisodeContext(val track: Track, val showId: String? = null, val showTitle: String? = null) : ActionContext()
}

class ActionSheetViewModel(
    private val toastViewModel: GlobalToastViewModel,
    private val playerRepository: PlayerRepository,
    private val localMediaRepository: LocalMediaRepository,
    private val downloadRepository: DownloadRepository,
    private val musicService: MusicService,
    private val navigationEventBus: NavigationEventBus
) : ViewModel() {
    private val _currentContext = MutableStateFlow<ActionContext?>(null)
    val currentContext = _currentContext.asStateFlow()

    private val _playlistSelectorTracks = MutableStateFlow<List<Track>?>(null)
    val playlistSelectorTracks = _playlistSelectorTracks.asStateFlow()

    private val _artistSelectorArtists = MutableStateFlow<List<ArtistInfo>?>(null)
    val artistSelectorArtists = _artistSelectorArtists.asStateFlow()

    fun openSheet(context: ActionContext) {
        val isLocalPlaylistCleanup = context is ActionContext.PlaylistTrackContext && context.isLocalPlaylist
        val track = when (context) {
            is ActionContext.TrackContext -> context.track
            is ActionContext.PlaylistTrackContext -> context.track
            is ActionContext.HistoryTrackContext -> context.track
            is ActionContext.DownloadTrackContext -> context.track
            is ActionContext.PodcastEpisodeContext -> context.track
            else -> null
        }
        if (!isLocalPlaylistCleanup && track?.isDeviceOrigin() == true) {
            toastViewModel.showToast("Unsupported for local files", ToastType.INFO)
            return
        }
        _currentContext.value = context
    }

    fun closeSheet() {
        _currentContext.value = null
    }

    fun openPlaylistSelector(tracks: List<Track>) {
        val eligibleTracks = tracks.filter { !it.isDeviceOrigin() }
        if (eligibleTracks.isEmpty()) {
            if (tracks.any { it.isDeviceOrigin() }) {
                toastViewModel.showToast("Unsupported for local files", ToastType.INFO)
            }
            return
        }
        _currentContext.value = null
        _playlistSelectorTracks.value = eligibleTracks
    }

    fun closePlaylistSelector() {
        _playlistSelectorTracks.value = null
    }

    fun openArtistSelector(artists: List<ArtistInfo>) {
        _currentContext.value = null
        _artistSelectorArtists.value = artists
    }

    fun closeArtistSelector() {
        _artistSelectorArtists.value = null
    }

    fun isTrackLikedFlow(trackId: String): Flow<Boolean> = localMediaRepository.isTrackLiked(trackId)
    fun isEpisodeSavedFlow(episodeId: String): Flow<Boolean> = localMediaRepository.isEpisodeSaved(episodeId)
    fun isAlbumSavedFlow(albumId: String): Flow<Boolean> = localMediaRepository.isAlbumSaved(albumId)
    fun isPlaylistSavedFlow(playlistId: String): Flow<Boolean> = localMediaRepository.isPlaylistSaved(playlistId)
    fun isPodcastSavedFlow(podcastId: String): Flow<Boolean> = localMediaRepository.isPodcastSaved(podcastId)
    suspend fun isArtistSaved(artistId: String): Boolean = localMediaRepository.isArtistSaved(artistId)

    fun playTrack(track: Track) {
        playerRepository.playTrack(track)
        closeSheet()
    }

    fun playNext(track: Track, isEpisode: Boolean = false) {
        playerRepository.insertNext(track)
        toastViewModel.showToast(if (isEpisode) "Episode playing next" else "Added to play next", ToastType.INFO)
        closeSheet()
    }

    fun appendToQueue(track: Track, isEpisode: Boolean = false) {
        playerRepository.appendToQueue(track)
        toastViewModel.showToast("Added to queue", ToastType.INFO)
        closeSheet()
    }

    fun toggleLike(track: Track, isLiked: Boolean) {
        if (!isLiked && track.isDeviceOrigin()) {
            toastViewModel.showToast("Unsupported for local files", ToastType.INFO)
            closeSheet()
            return
        }
        viewModelScope.launch {
            localMediaRepository.setLiked(track, !isLiked)
            toastViewModel.showToast(
                if (isLiked) "Removed from Liked Songs" else "Added to Liked Songs",
                if (isLiked) ToastType.INFO else ToastType.LIKED
            )
            closeSheet()
        }
    }

    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            if (downloadRepository.isDownloaded(track.id)) {
                toastViewModel.showToast("Already Downloaded", ToastType.INFO)
            } else {
                val queueItem = downloadRepository.getQueueItem(track.id)
                if (queueItem != null && (queueItem.status == "PENDING" || queueItem.status == "DOWNLOADING")) {
                    toastViewModel.showToast(
                        message = "Already Downloading",
                        type = ToastType.INFO,
                        action = ToastAction("View") {
                            navigationEventBus.navigateTo("downloads")
                        }
                    )
                } else {
                    val result = downloadRepository.enqueueDownload(track)
                    if (result == DownloadEnqueueResult.UNSUPPORTED) {
                        toastViewModel.showToast("Unsupported for local files", ToastType.INFO)
                        closeSheet()
                        return@launch
                    }
                    val message = if (result == DownloadEnqueueResult.WAITING_FOR_WIFI) {
                        "Queued • Waiting for Wi-Fi"
                    } else {
                        "Download Started"
                    }
                    toastViewModel.showToast(
                        message = message,
                        type = ToastType.INFO,
                        action = ToastAction("View") {
                            navigationEventBus.navigateTo("downloads")
                        }
                    )
                }
            }
            closeSheet()
        }
    }

    fun removeFromHistory(trackId: String) {
        viewModelScope.launch {
            localMediaRepository.removeTrackFromHistory(trackId)
            toastViewModel.showToast("Removed from history", ToastType.INFO)
            closeSheet()
        }
    }

    fun removeDownload(trackId: String) {
        viewModelScope.launch {
            downloadRepository.removeDownload(trackId)
            toastViewModel.showToast("Download removed", ToastType.INFO)
            closeSheet()
        }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            localMediaRepository.removeTrackFromPlaylist(playlistId, trackId)
            toastViewModel.showToast("Removed from playlist", ToastType.INFO)
            closeSheet()
        }
    }

    fun toggleSaveEpisode(
        track: Track,
        isSaved: Boolean,
        showId: String? = null,
        showTitle: String? = null
    ) {
        viewModelScope.launch {
            if (isSaved) {
                localMediaRepository.unsaveEpisodeForLater(track.id)
                toastViewModel.showToast("Removed from Saved Episodes", ToastType.INFO)
            } else {
                localMediaRepository.saveEpisodeForLater(
                    SavedEpisodeEntity(
                        episodeId = track.id,
                        showId = showId ?: track.albumId,
                        showTitle = showTitle ?: track.album,
                        title = track.title,
                        author = track.artist,
                        artworkUrl = track.artwork,
                        duration = track.duration ?: 0L,
                        description = track.description,
                        releaseDate = track.releaseDate
                    )
                )
                toastViewModel.showToast("Saved for Later", ToastType.LIKED)
            }
            closeSheet()
        }
    }

    fun playContainer(
        id: String,
        isAlbum: Boolean,
        shuffle: Boolean = false,
        appendToQueue: Boolean = false
    ) {
        val typeName = if (isAlbum) "album" else "playlist"
        toastViewModel.showToast(
            if (appendToQueue) "Adding to queue..." else "Fetching $typeName...",
            ToastType.INFO
        )
        closeSheet()
        viewModelScope.launch {
            val localTracks = if (isAlbum) {
                localMediaRepository.getAlbumWithTracks(id).firstOrNull()?.tracks
            } else {
                localMediaRepository.getPlaylistWithTracks(id).firstOrNull()?.tracks
            }
            if (!localTracks.isNullOrEmpty()) {
                val tracks = localTracks.map { trackRef ->
                    Track(
                        id = trackRef.id,
                        title = trackRef.title,
                        artist = trackRef.artist,
                        artwork = trackRef.artworkUrl,
                        trackType = trackRef.trackType
                    )
                }
                when {
                    appendToQueue -> {
                        tracks.forEach { playerRepository.appendToQueue(it) }
                        toastViewModel.showToast("${tracks.size} tracks added", ToastType.INFO)
                    }
                    shuffle -> playerRepository.playTracksShuffled(tracks)
                    else -> playerRepository.playTracks(tracks, 0)
                }
                return@launch
            }

            val result = if (isAlbum) musicService.getAlbumDetails(id) else musicService.getPlaylistDetails(id)
            result.onSuccess { details ->
                if (details.tracks.isNotEmpty()) {
                    when {
                        appendToQueue -> {
                            details.tracks.forEach { playerRepository.appendToQueue(it) }
                            toastViewModel.showToast("${details.tracks.size} tracks added", ToastType.INFO)
                        }
                        shuffle -> playerRepository.playTracksShuffled(details.tracks)
                        else -> playerRepository.playTracks(details.tracks, 0)
                    }
                } else {
                    toastViewModel.showToast("$typeName has no tracks", ToastType.ERROR)
                }
            }.onFailure {
                toastViewModel.showToast("Failed to load $typeName", ToastType.ERROR)
            }
        }
    }

    private fun bulkDownloadMessage(result: DownloadEnqueueResult, typeName: String): String = when (result) {
        DownloadEnqueueResult.WAITING_FOR_WIFI -> "Queued $typeName • Waiting for Wi-Fi"
        DownloadEnqueueResult.STARTED -> "Downloading $typeName..."
        DownloadEnqueueResult.ALREADY_EXISTS -> "Already Downloaded"
        DownloadEnqueueResult.UNSUPPORTED -> "Unsupported for local files"
    }

    fun downloadContainer(id: String, isAlbum: Boolean) {
        val typeName = if (isAlbum) "album" else "playlist"
        toastViewModel.showToast("Fetching $typeName for download...", ToastType.INFO)
        closeSheet()
        viewModelScope.launch {
            val localTracks = if (isAlbum) {
                localMediaRepository.getAlbumWithTracks(id).firstOrNull()?.tracks
            } else {
                localMediaRepository.getPlaylistWithTracks(id).firstOrNull()?.tracks
            }
            if (!localTracks.isNullOrEmpty()) {
                val tracks = localTracks.map { trackRef ->
                    Track(
                        id = trackRef.id,
                        title = trackRef.title,
                        artist = trackRef.artist,
                        artwork = trackRef.artworkUrl,
                        trackType = trackRef.trackType
                    )
                }
                val result = downloadRepository.enqueueBulkDownload(tracks)
                toastViewModel.showToast(bulkDownloadMessage(result, typeName), ToastType.INFO)
                return@launch
            }

            val result = if (isAlbum) musicService.getAlbumDetails(id) else musicService.getPlaylistDetails(id)
            result.onSuccess { details ->
                val enqueueResult = downloadRepository.enqueueBulkDownload(details.tracks)
                toastViewModel.showToast(bulkDownloadMessage(enqueueResult, typeName), ToastType.INFO)
            }.onFailure {
                toastViewModel.showToast("Failed to fetch $typeName for download", ToastType.ERROR)
            }
        }
    }

    fun toggleSaveAlbum(id: String, isSaved: Boolean) {
        viewModelScope.launch {
            if (isSaved) {
                localMediaRepository.unsaveAlbum(id)
                toastViewModel.showToast("Removed from Library", ToastType.INFO)
            } else {
                toastViewModel.showToast("Saving to Library...", ToastType.INFO)
                val result = musicService.getAlbumDetails(id)
                result.onSuccess { details ->
                    localMediaRepository.saveAlbum(
                        AlbumEntity(
                            albumId = id,
                            title = details.title,
                            artistName = details.subtitle,
                            artworkUrl = details.artworkUrl
                        )
                    )
                    details.tracks.forEachIndexed { index, track ->
                        localMediaRepository.addTrackToAlbum(id, track, index)
                    }
                    toastViewModel.showToast("Saved to Library", ToastType.LIKED)
                }.onFailure {
                    toastViewModel.showToast("Failed to save album", ToastType.ERROR)
                }
            }
            closeSheet()
        }
    }

    fun toggleSavePlaylist(id: String, isSaved: Boolean) {
        viewModelScope.launch {
            if (isSaved) {
                localMediaRepository.unsavePlaylist(id)
                toastViewModel.showToast("Removed from Library", ToastType.INFO)
            } else {
                toastViewModel.showToast("Saving to Library...", ToastType.INFO)
                val result = musicService.getPlaylistDetails(id)
                result.onSuccess { details ->
                    localMediaRepository.savePlaylist(
                        PlaylistEntity(
                            playlistId = id,
                            name = details.title,
                            description = details.subtitle,
                            artworkUrl = details.artworkUrl,
                            isLocal = false
                        )
                    )
                    details.tracks.forEachIndexed { index, track ->
                        localMediaRepository.addTrackToPlaylist(id, track, index)
                    }
                    toastViewModel.showToast("Saved to Library", ToastType.LIKED)
                }.onFailure {
                    toastViewModel.showToast("Failed to save playlist", ToastType.ERROR)
                }
            }
            closeSheet()
        }
    }

    fun playPodcastShow(id: String) {
        toastViewModel.showToast("Loading podcast...", ToastType.INFO)
        closeSheet()
        viewModelScope.launch {
            musicService.getPodcastDetails(id).onSuccess { details ->
                if (details.tracks.isNotEmpty()) {
                    playerRepository.playTracks(details.tracks, 0)
                } else {
                    toastViewModel.showToast("No episodes available", ToastType.INFO)
                }
            }.onFailure {
                toastViewModel.showToast("Failed to load podcast", ToastType.ERROR)
            }
        }
    }

    fun queuePodcastShowNext(id: String) {
        closeSheet()
        viewModelScope.launch {
            musicService.getPodcastDetails(id).onSuccess { details ->
                if (details.tracks.isNotEmpty()) {
                    details.tracks.reversed().forEach { playerRepository.insertNext(it) }
                    toastViewModel.showToast("${details.tracks.size} episodes queued next", ToastType.INFO)
                }
            }
        }
    }

    fun toggleSavePodcastShow(
        id: String,
        isSaved: Boolean,
        title: String,
        author: String,
        artworkUrl: String?
    ) {
        viewModelScope.launch {
            if (isSaved) {
                localMediaRepository.unsavePodcast(id)
                toastViewModel.showToast("Removed from Library", ToastType.INFO)
            } else {
                toastViewModel.showToast("Saving to Library...", ToastType.INFO)
                val result = musicService.getPodcastDetails(id)
                result.onSuccess { details ->
                    localMediaRepository.savePodcast(
                        PodcastEntity(
                            podcastId = id,
                            title = details.title,
                            author = details.subtitle,
                            authorId = details.artistId,
                            artworkUrl = details.artworkUrl,
                            episodeCount = details.tracks.size
                        )
                    )
                    toastViewModel.showToast("Saved to Library", ToastType.LIKED)
                }.onFailure {
                    if (id.startsWith("MPSP") || id.startsWith("MPP")) {
                        localMediaRepository.savePodcast(
                            PodcastEntity(
                                podcastId = id,
                                title = title,
                                author = author,
                                authorId = null,
                                artworkUrl = artworkUrl,
                                episodeCount = 0
                            )
                        )
                        toastViewModel.showToast("Saved to Library", ToastType.LIKED)
                    } else {
                        toastViewModel.showToast("Failed to save podcast", ToastType.ERROR)
                    }
                }
            }
            closeSheet()
        }
    }

    fun toggleFollowArtist(id: String, name: String, avatarUrl: String?, isSaved: Boolean) {
        viewModelScope.launch {
            if (isSaved) {
                localMediaRepository.unsaveArtist(id)
                toastViewModel.showToast("Unfollowed artist", ToastType.INFO)
            } else {
                localMediaRepository.saveArtist(ArtistEntity(id, name, avatarUrl))
                toastViewModel.showToast("Following artist", ToastType.LIKED)
            }
            closeSheet()
        }
    }
}
