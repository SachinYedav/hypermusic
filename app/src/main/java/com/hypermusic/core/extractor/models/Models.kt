package com.hypermusic.core.extractor.models

data class ExtractedEntity(val id: String, val name: String)

data class ExtractedTextRun(val text: String, val url: String? = null)

data class ExtractedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val duration: Double,
    val artworkUrl: String,
    val isExplicit: Boolean = false,
    val artists: Array<ExtractedEntity>? = null,
    val description: String? = null,
    val releaseDate: String? = null
)

data class BrowseItem(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val isExplicit: Boolean = false,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val artists: Array<ExtractedEntity>? = null,
    val chartRank: String? = null,
    val chartTrend: String? = null
)

data class BrowseShelf(
    val title: String,
    val type: String? = null,
    val items: Array<BrowseItem>,
    val description: String? = null,
    val descriptionRuns: List<ExtractedTextRun>? = null,
    val footer: String? = null,
    val moreEndpoint: String? = null
)

data class FilterChip(
    val title: String,
    val params: String
)

data class HomeFeed(
    val shelves: Array<BrowseShelf>,
    val chips: Array<FilterChip>? = null,
    val continuationToken: String? = null,
    val visitorData: String? = null
)

data class AlbumDetails(
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val year: String,
    val artworkUrl: String,
    val tracks: Array<ExtractedTrack>,
    val artists: Array<ExtractedEntity>? = null,
    val shelves: Array<BrowseShelf>? = null
)

data class PlaylistDetails(
    val title: String,
    val creator: String,
    val creatorId: String? = null,
    val trackCount: String,
    val artworkUrl: String,
    val tracks: Array<ExtractedTrack>,
    val shelves: Array<BrowseShelf>? = null
)

data class PodcastShowDetails(
    val title: String,
    val creator: String,
    val creatorId: String? = null,
    val artworkUrl: String,
    val episodes: Array<ExtractedTrack>
)

data class ArtistProfile(
    val name: String,
    val subtitle: String,
    val artworkUrl: String,
    val shelves: Array<BrowseShelf>,
    val description: String? = null,
    val descriptionRuns: List<ExtractedTextRun>? = null
)

data class ExtractedLyrics(
    val text: String,
    val source: String? = null
)

data class WatchNextResult(
    val tracks: Array<ExtractedTrack>,
    val lyricsBrowseId: String? = null,
    val relatedBrowseId: String? = null
)

data class ExtractedSearchSuggestions(
    val queries: Array<String>,
    val richItems: Array<BrowseItem> = emptyArray()
)

