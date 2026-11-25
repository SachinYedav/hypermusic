package com.hypermusic.core.player

import com.hypermusic.core.model.BrowseShelf
import com.hypermusic.core.model.Lyrics

/** The single active overlay on the player screen. */
enum class PlayerOverlaySheet {
    NONE,
    LYRICS,
    RELATED
}

sealed class LyricsUiState {
    object Idle : LyricsUiState()
    object Loading : LyricsUiState()
    data class Success(val lyrics: Lyrics) : LyricsUiState()
    object Empty : LyricsUiState()
    data class Error(val message: String) : LyricsUiState()
}

sealed class RelatedUiState {
    object Idle : RelatedUiState()
    object Loading : RelatedUiState()
    data class Success(val shelves: List<BrowseShelf>) : RelatedUiState()
    object Empty : RelatedUiState()
    data class Error(val message: String) : RelatedUiState()
}

@androidx.compose.runtime.Immutable
sealed interface AutoplayStatus {
    data object Idle : AutoplayStatus
    data object Loading : AutoplayStatus
    data object LocalOnly : AutoplayStatus
    data class Error(val message: String) : AutoplayStatus
}
