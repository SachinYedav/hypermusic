package com.hypermusic.feature.shared.screens

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.model.Track
import com.hypermusic.core.utils.ErrorClassifier
import com.hypermusic.core.utils.FriendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
enum class CatalogLayoutType {
    TRACK_LIST,
    PODCAST_EPISODES,
    GRID
}

@Immutable
data class CatalogUiState(
    val title: String = "",
    val subtitle: String? = null,
    val items: List<Track> = emptyList(),
    val layoutType: CatalogLayoutType = CatalogLayoutType.GRID,
    val isLoading: Boolean = true,
    val error: String? = null,
    val friendlyError: FriendlyError? = null
) {
    val isTrackList: Boolean get() = layoutType == CatalogLayoutType.TRACK_LIST
}

class CatalogViewModel(
    savedStateHandle: SavedStateHandle,
    private val musicService: MusicService
) : ViewModel() {

    private val endpoint: String = checkNotNull(savedStateHandle["endpoint"])
    private val initialTitle: String? = savedStateHandle["title"]
    private val initialSubtitle: String? = savedStateHandle["subtitle"]

    private val _uiState = MutableStateFlow(
        CatalogUiState(
            title = initialTitle ?: "Catalog",
            subtitle = initialSubtitle,
            isLoading = true
        )
    )
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        _uiState.update { it.copy(isLoading = true, error = null, friendlyError = null) }
        viewModelScope.launch {
            try {
                val isPlaylist = com.hypermusic.feature.shared.utils.MediaActionHandler.isPlaylistEndpoint(endpoint)
                if (isPlaylist) {
                    val result = musicService.getPlaylistDetails(endpoint)
                    result.onSuccess { details ->
                        val isPodcastEpisodeList = details.tracks.isNotEmpty() && details.tracks.all {
                            com.hypermusic.feature.shared.utils.MediaActionHandler.isPodcastEpisode(it)
                        }
                        val layout = if (isPodcastEpisodeList) CatalogLayoutType.PODCAST_EPISODES else CatalogLayoutType.TRACK_LIST
                        _uiState.value = CatalogUiState(
                            title = initialTitle ?: details.title,
                            subtitle = initialSubtitle ?: details.subtitle.ifBlank { null },
                            items = details.tracks,
                            layoutType = layout,
                            isLoading = false
                        )
                    }.onFailure { error ->
                        val friendly = ErrorClassifier.classify(error.message)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to load playlist",
                                friendlyError = friendly
                            )
                        }
                    }
                } else {
                    val result = musicService.getExplorePage(endpoint)
                    result.onSuccess { shelves ->
                        val allItems = shelves.flatMap { it.items }.distinctBy { it.id }
                        val isPodcastEpisodeList = allItems.isNotEmpty() && allItems.all {
                            com.hypermusic.feature.shared.utils.MediaActionHandler.isPodcastEpisode(it)
                        }
                        val isAllPlayable = allItems.isNotEmpty() && allItems.all {
                            com.hypermusic.feature.shared.utils.MediaActionHandler.isPlayable(it)
                        }
                        val layout = when {
                            isPodcastEpisodeList -> CatalogLayoutType.PODCAST_EPISODES
                            isAllPlayable -> CatalogLayoutType.TRACK_LIST
                            else -> CatalogLayoutType.GRID
                        }
                        val resolvedTitle = initialTitle?.ifBlank { null }
                            ?: (shelves.firstOrNull()?.title?.takeIf { it != "Explore" && it.isNotBlank() } ?: "Catalog")
                        _uiState.value = CatalogUiState(
                            title = resolvedTitle,
                            subtitle = initialSubtitle,
                            items = allItems,
                            layoutType = layout,
                            isLoading = false
                        )
                    }.onFailure { error ->
                        val friendly = ErrorClassifier.classify(error.message)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to load catalog",
                                friendlyError = friendly
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                val friendly = ErrorClassifier.classify(e.message)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An unexpected error occurred",
                        friendlyError = friendly
                    )
                }
            }
        }
    }
}
