package com.hypermusic.feature.shared.screens

import androidx.compose.runtime.Immutable

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.core.domain.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import com.hypermusic.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.hypermusic.core.data.mapper.toDomain
import com.hypermusic.core.utils.ErrorClassifier
import com.hypermusic.core.utils.FriendlyError

@Immutable
sealed interface CollectionUiState {
    val details: com.hypermusic.core.model.CollectionDetails?
    @Immutable
    data class Loading(override val details: com.hypermusic.core.model.CollectionDetails?) : CollectionUiState
    @Immutable
    data class Success(override val details: com.hypermusic.core.model.CollectionDetails) : CollectionUiState
    @Immutable
    data class Error(
        val message: String,
        override val details: com.hypermusic.core.model.CollectionDetails?,
        val friendlyError: FriendlyError? = null
    ) : CollectionUiState
}

class CollectionViewModel(
    savedStateHandle: SavedStateHandle,
    private val musicService: MusicService,
    private val repository: com.hypermusic.core.data.repository.LocalMediaRepository
) : ViewModel() {

    private val collectionId: String = checkNotNull(savedStateHandle["id"])
    private val collectionType: String = checkNotNull(savedStateHandle["type"])
    
    private val optimisticTitle: String? = savedStateHandle["title"]
    private val optimisticSubtitle: String? = savedStateHandle["subtitle"]
    private val optimisticArtworkUrl: String? = savedStateHandle["artworkUrl"]

    private val initialOptimisticDetails = if (optimisticTitle != null && optimisticArtworkUrl != null) {
        com.hypermusic.core.model.CollectionDetails(
            id = collectionId,
            title = optimisticTitle,
            subtitle = optimisticSubtitle ?: "",
            artworkUrl = optimisticArtworkUrl,
            tracks = emptyList()
        )
    } else null

    private val _uiState = MutableStateFlow<CollectionUiState>(CollectionUiState.Loading(initialOptimisticDetails))
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    val savedEpisodeIds: StateFlow<Set<String>> = if (collectionType == "podcast") {
        repository.getAllSavedEpisodes()
            .map { episodes -> episodes.map { it.episodeId }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    } else {
        MutableStateFlow(emptySet())
    }

    init {
        loadCollection()
        checkIfSaved()
    }

    private fun checkIfSaved() {
        viewModelScope.launch {
            if (collectionType == "album") {
                repository.isAlbumSaved(collectionId).collect { saved ->
                    _isSaved.value = saved
                }
            } else if (collectionType == "playlist") {
                repository.isPlaylistSaved(collectionId).collect { saved ->
                    _isSaved.value = saved
                }
            } else if (collectionType == "podcast") {
                repository.isPodcastSaved(collectionId).collect { saved ->
                    _isSaved.value = saved
                }
            }
        }
    }

    fun toggleSave() {
        viewModelScope.launch {
            val state = uiState.value
            if (state is CollectionUiState.Success) {
                if (_isSaved.value) {
                    if (collectionType == "album") repository.unsaveAlbum(collectionId)
                    else if (collectionType == "playlist") repository.unsavePlaylist(collectionId)
                    else if (collectionType == "podcast") repository.unsavePodcast(collectionId)
                } else {
                    if (collectionType == "album") {
                        repository.saveAlbum(com.hypermusic.core.data.local.entity.AlbumEntity(
                            albumId = collectionId,
                            title = state.details.title,
                            artistName = state.details.subtitle,
                            artworkUrl = state.details.artworkUrl
                        ))
                    } else if (collectionType == "playlist") {
                        repository.savePlaylist(com.hypermusic.core.data.local.entity.PlaylistEntity(
                            playlistId = collectionId,
                            name = state.details.title,
                            description = state.details.subtitle,
                            artworkUrl = state.details.artworkUrl,
                            isLocal = false
                        ))
                    } else if (collectionType == "podcast") {
                        repository.savePodcast(com.hypermusic.core.data.local.entity.PodcastEntity(
                            podcastId = collectionId,
                            title = state.details.title,
                            author = state.details.subtitle,
                            authorId = state.details.artistId,
                            artworkUrl = state.details.artworkUrl,
                            episodeCount = state.details.tracks.size
                        ))
                    }
                    
                    state.details.tracks.forEachIndexed { index, track ->
                        if (collectionType == "album") {
                            repository.addTrackToAlbum(collectionId, track, index)
                        } else if (collectionType == "playlist") {
                            repository.addTrackToPlaylist(collectionId, track, index)
                        }
                    }
                }
            }
        }
    }

    fun isEpisodeSaved(episodeId: String): Flow<Boolean> {
        return repository.isEpisodeSaved(episodeId)
    }

    fun toggleSaveEpisode(track: Track, showTitle: String? = null) {
        viewModelScope.launch {
            repository.isEpisodeSaved(track.id).first().let { isSaved ->
                if (isSaved) {
                    repository.unsaveEpisodeForLater(track.id)
                } else {
                    repository.saveEpisodeForLater(
                        com.hypermusic.core.data.local.entity.SavedEpisodeEntity(
                            episodeId = track.id,
                            showId = collectionId,
                            showTitle = showTitle ?: (uiState.value as? CollectionUiState.Success)?.details?.title,
                            title = track.title,
                            author = track.artist,
                            artworkUrl = track.artwork,
                            duration = track.duration ?: 0L,
                            description = track.description,
                            releaseDate = track.releaseDate
                        )
                    )
                }
            }
        }
    }

    fun loadCollection() {
        val retainedDetails = _uiState.value.details ?: initialOptimisticDetails
        _uiState.value = CollectionUiState.Loading(retainedDetails)
        viewModelScope.launch {
            var hadLocal = false
            try {
                if (collectionType == "album") {
                    val localAlbum = repository.getAlbumWithTracks(collectionId).firstOrNull()
                    if (localAlbum != null) {
                        hadLocal = true
                        val localArtwork = when {
                            !localAlbum.album.artworkUrl.isNullOrBlank() -> localAlbum.album.artworkUrl
                            !retainedDetails?.artworkUrl.isNullOrBlank() -> retainedDetails.artworkUrl
                            else -> ""
                        }
                        val details = com.hypermusic.core.model.CollectionDetails(
                            id = localAlbum.album.albumId,
                            title = localAlbum.album.title,
                            subtitle = localAlbum.album.artistName,
                            artworkUrl = localArtwork,
                            year = localAlbum.album.year,
                            tracks = localAlbum.tracks.map { trackRef ->
                                trackRef.toDomain()
                            }
                        )
                        _uiState.value = CollectionUiState.Success(details)
                        recordVisitIfValid(details)
                    }
                } else if (collectionType == "playlist") {
                    val localPlaylist = repository.getPlaylistWithTracks(collectionId).firstOrNull()
                    if (localPlaylist != null) {
                        hadLocal = true
                        val localArtwork = when {
                            !localPlaylist.playlist.artworkUrl.isNullOrBlank() -> localPlaylist.playlist.artworkUrl
                            !retainedDetails?.artworkUrl.isNullOrBlank() -> retainedDetails.artworkUrl
                            else -> ""
                        }
                        val details = com.hypermusic.core.model.CollectionDetails(
                            id = localPlaylist.playlist.playlistId,
                            title = localPlaylist.playlist.name,
                            subtitle = localPlaylist.playlist.description ?: "",
                            artworkUrl = localArtwork,
                            year = null,
                            tracks = localPlaylist.tracks.map { trackRef ->
                                trackRef.toDomain()
                            }
                        )
                        _uiState.value = CollectionUiState.Success(details)
                        recordVisitIfValid(details)
                    }
                }
            } catch (e: Exception) {
            }

            // Refresh remotely after the local lookup; retain local data if the refresh fails.
            val result = when (collectionType) {
                "album" -> musicService.getAlbumDetails(collectionId)
                "playlist" -> musicService.getPlaylistDetails(collectionId)
                "podcast" -> musicService.getPodcastDetails(collectionId)
                else -> Result.failure(Exception("Unknown collection type"))
            }

            if (result.isSuccess) {
                val remoteDetails = result.getOrNull()!!
                val currentArtwork = when (val state = _uiState.value) {
                    is CollectionUiState.Success -> state.details.artworkUrl
                    is CollectionUiState.Loading -> state.details?.artworkUrl
                    else -> null
                }
                val finalArtwork = when {
                    !remoteDetails.artworkUrl.isNullOrBlank() -> remoteDetails.artworkUrl
                    !currentArtwork.isNullOrBlank() -> currentArtwork
                    !retainedDetails?.artworkUrl.isNullOrBlank() -> retainedDetails.artworkUrl
                    else -> ""
                }
                val mergedDetails = remoteDetails.copy(artworkUrl = finalArtwork)
                _uiState.value = CollectionUiState.Success(mergedDetails)
                recordVisitIfValid(mergedDetails)
            } else if (!hadLocal) {
                val exception = result.exceptionOrNull()
                val friendly = ErrorClassifier.classify(exception)
                _uiState.value = CollectionUiState.Error(
                    message = friendly.title,
                    details = retainedDetails,
                    friendlyError = friendly
                )
            }
        }
    }

    private fun recordVisitIfValid(details: com.hypermusic.core.model.CollectionDetails) {
        if (details.title.isNotBlank()) {
            viewModelScope.launch {
                repository.recordCollectionVisit(
                    id = details.id.ifBlank { collectionId },
                    type = collectionType,
                    title = details.title,
                    subtitle = details.subtitle.ifBlank { null },
                    artworkUrl = details.artworkUrl.ifBlank { null },
                    previewTracks = details.tracks.take(4)
                )
            }
        }
    }
}
