package com.hypermusic.core.model

import androidx.compose.runtime.Immutable

@Immutable
@kotlinx.serialization.Serializable
data class Track(
    val id: String,
    val url: String = "",
    val title: String,
    val artist: String,
    val artwork: String? = null,
    val duration: Long? = null,
    val isExtracted: Boolean? = false,
    val extractedAt: Long? = null,
    val trackType: String? = "song", // Media kind used for rendering and routing, including collection and podcast types.
    val isImplicit: Boolean = false, // Defines if track is from Autoplay (Radio) or Explicit (User)
    val isExplicitContent: Boolean = false,
    val isDeviceLocal: Boolean = false,
    val artists: List<ArtistInfo>? = null,
    val album: String? = null,
    val albumId: String? = null,
    val lastPlayedAt: Long? = null,
    val queueEntryId: String? = null,
    val description: String? = null,
    val releaseDate: String? = null,
    val previewTracks: List<Track>? = null,
    val chartRank: String? = null,
    val chartTrend: String? = null
)

@Immutable
@kotlinx.serialization.Serializable
data class ArtistInfo(val id: String, val name: String)

fun isDeviceUri(id: String?): Boolean =
    !id.isNullOrBlank() && (id.startsWith("content://") || id.startsWith("file://"))

fun Track.isDeviceOrigin(): Boolean = isDeviceLocal || isDeviceUri(id)

@Immutable
data class TextRun(
    val text: String,
    val url: String? = null
)

@Immutable
data class BrowseShelf(
    val id: String,
    val title: String,
    val type: String = "carousel", // Layout types include grid, carousel, list, description, chart_artists, and collection_cards.
    val items: List<Track>,
    val description: String? = null,
    val descriptionRuns: List<TextRun>? = null,
    val footer: String? = null,
    val moreEndpoint: String? = null
)

@Immutable
data class FilterChip(
    val title: String,
    val params: String
)

@Immutable
data class HomeData(
    val shelves: List<BrowseShelf>,
    val chips: List<FilterChip>,
    val continuationToken: String? = null,
    val visitorData: String? = null
)

@Immutable
data class HomeContinuationData(
    val shelves: List<BrowseShelf>,
    val continuationToken: String? = null
)

@Immutable
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translatedText: String? = null
)

@Immutable
data class Lyrics(
    val text: String,
    val source: String? = null,
    val lines: List<LyricLine> = emptyList(),
    val isSynced: Boolean = false,
    val translatedPlainText: String? = null,
    val translatedLang: String? = null
)

@Immutable
data class SearchSuggestions(
    val queries: List<String> = emptyList(),
    val richItems: List<Track> = emptyList()
)

