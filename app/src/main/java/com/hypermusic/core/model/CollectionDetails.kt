package com.hypermusic.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class CollectionDetails(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val year: String? = null,
    val artistId: String? = null,
    val artists: List<ArtistInfo>? = null,
    val tracks: List<Track>,
    val shelves: List<BrowseShelf> = emptyList()
)

@Immutable
data class ArtistDetails(
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val shelves: List<BrowseShelf>,
    val description: String? = null,
    val descriptionRuns: List<TextRun>? = null
)
