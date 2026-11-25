package com.hypermusic.feature.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.core.data.local.entity.AlbumEntity
import com.hypermusic.core.data.local.entity.ArtistEntity
import com.hypermusic.core.data.local.entity.PlaylistEntity
import com.hypermusic.core.data.local.entity.PodcastEntity
import com.hypermusic.core.data.local.entity.SavedEpisodeEntity
import com.hypermusic.core.data.repository.DeviceMediaRepository
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LibraryUiState(
    val activeChip: String? = null,
    val sortMode: String = "Recently played",
    val isGridView: Boolean = false,
    val likedTracks: List<Track> = emptyList(),
    val recentHistory: List<Track> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<ArtistEntity> = emptyList(),
    val podcasts: List<PodcastEntity> = emptyList(),
    val savedEpisodes: List<SavedEpisodeEntity> = emptyList(),
    val deviceTracks: List<Track> = emptyList()
)

class LibraryViewModel(
    private val repository: LocalMediaRepository,
    private val deviceMediaRepository: DeviceMediaRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _activeChip = MutableStateFlow<String?>(null)
    val activeChip: StateFlow<String?> = _activeChip.asStateFlow()
    private var deviceScanJob: kotlinx.coroutines.Job? = null
    private var deviceScanRevision = 0L

    val sortMode: StateFlow<String> = preferencesManager.librarySortMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Recently played")

    val isGridView: StateFlow<Boolean> = preferencesManager.libraryIsGridView
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val likedTracks: StateFlow<List<Track>> = repository.getLikedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentHistory: StateFlow<List<Track>> = repository.getRecentHistory(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<AlbumEntity>> = repository.getAllAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<ArtistEntity>> = repository.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val podcasts: StateFlow<List<PodcastEntity>> = repository.getAllPodcasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedEpisodes: StateFlow<List<SavedEpisodeEntity>> = repository.getAllSavedEpisodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deviceTracks = MutableStateFlow<List<Track>>(emptyList())
    val deviceTracks: StateFlow<List<Track>> = _deviceTracks.asStateFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        combine(_activeChip, sortMode, isGridView, ::Triple),
        combine(likedTracks, recentHistory, playlists, ::Triple),
        combine(albums, artists, podcasts, ::Triple),
        combine(savedEpisodes, _deviceTracks, ::Pair)
    ) { (chip, sort, grid), (liked, history, pl), (alb, art, pod), (ep, dev) ->
        LibraryUiState(
            activeChip = chip,
            sortMode = sort,
            isGridView = grid,
            likedTracks = liked,
            recentHistory = history,
            playlists = pl,
            albums = alb,
            artists = art,
            podcasts = pod,
            savedEpisodes = ep,
            deviceTracks = dev
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun unsaveEpisode(episodeId: String) {
        viewModelScope.launch {
            repository.unsaveEpisodeForLater(episodeId)
        }
    }

    fun loadDeviceMedia(debounceMs: Long = 0L) {
        val revision = ++deviceScanRevision
        deviceScanJob?.cancel()
        deviceScanJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                kotlinx.coroutines.delay(debounceMs)
            }
            try {
                val scanned = deviceMediaRepository.getDeviceAudioFiles()
                if (revision == deviceScanRevision) {
                    _deviceTracks.value = scanned
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun isDeviceTrackReadable(uri: String): Boolean =
        deviceMediaRepository.isDeviceUriReadable(uri)

    fun setActiveChip(chip: String?) {
        _activeChip.value = chip
    }

    fun setSortMode(newMode: String) {
        viewModelScope.launch {
            preferencesManager.setLibrarySortMode(newMode)
        }
    }

    fun toggleSortMode() {
        viewModelScope.launch {
            val newMode = when (sortMode.value) {
                "Recently played" -> "Recently added"
                "Recently added" -> "Alphabetical"
                else -> "Recently played"
            }
            preferencesManager.setLibrarySortMode(newMode)
        }
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            preferencesManager.setLibraryGridView(!isGridView.value)
        }
    }
}
