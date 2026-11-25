package com.hypermusic.feature.shared.screens

import androidx.compose.runtime.Immutable

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.model.ArtistDetails
import com.hypermusic.core.utils.ErrorClassifier
import com.hypermusic.core.utils.FriendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed class ArtistUiState {
    @Immutable
    object Loading : ArtistUiState()
    @Immutable
    data class Success(val details: ArtistDetails) : ArtistUiState()
    @Immutable
    data class Error(val message: String, val friendlyError: FriendlyError? = null) : ArtistUiState()
}

class ArtistViewModel(
    private val musicService: MusicService,
    private val repository: com.hypermusic.core.data.repository.LocalMediaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val artistId: String = savedStateHandle.get<String>("id") ?: ""

    private val _uiState = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    init {
        loadArtist()
        checkIfSaved()
    }

    private fun checkIfSaved() {
        viewModelScope.launch {
            _isSaved.value = repository.isArtistSaved(artistId)
        }
    }

    fun toggleSave() {
        viewModelScope.launch {
            val state = uiState.value
            if (state is ArtistUiState.Success) {
                if (_isSaved.value) {
                    repository.unsaveArtist(artistId)
                    _isSaved.value = false
                } else {
                    repository.saveArtist(com.hypermusic.core.data.local.entity.ArtistEntity(
                        artistId = artistId,
                        name = state.details.title,
                        avatarUrl = state.details.artworkUrl
                    ))
                    _isSaved.value = true
                }
            }
        }
    }

    fun loadArtist() {
        if (artistId.isEmpty()) return
        
        _uiState.value = ArtistUiState.Loading
        viewModelScope.launch {
            val result = musicService.getArtistProfile(artistId)
            if (result.isSuccess) {
                val details = result.getOrThrow()
                _uiState.value = ArtistUiState.Success(details)
                recordVisitIfValid(details)
            } else {
                val exception = result.exceptionOrNull()
                val friendly = ErrorClassifier.classify(exception)
                _uiState.value = ArtistUiState.Error(friendly.title, friendly)
            }
        }
    }

    private fun recordVisitIfValid(details: ArtistDetails) {
        if (details.title.isNotBlank()) {
            viewModelScope.launch {
                repository.recordCollectionVisit(
                    id = artistId,
                    type = "artist",
                    title = details.title,
                    subtitle = "Artist",
                    artworkUrl = details.artworkUrl
                )
            }
        }
    }
}
