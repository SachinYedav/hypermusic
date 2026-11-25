package com.hypermusic.core.extractor.engine

import com.hypermusic.core.extractor.errors.ParsingException
import com.hypermusic.core.extractor.models.*
import com.hypermusic.core.extractor.parsers.*

/** Delegates response parsing to the corresponding parser. */
object Parsers {

    @Throws(ParsingException::class)
    fun parseSearchSuggestions(jsonString: String): ExtractedSearchSuggestions =
        SearchParser.parseSearchSuggestions(jsonString)

    @Throws(ParsingException::class)
    fun parseSearchResponse(jsonString: String): Array<BrowseItem> =
        SearchParser.parseSearchResponse(jsonString)

    @Throws(ParsingException::class)
    fun parseHomeFeed(jsonString: String): HomeFeed =
        HomeParser.parseHomeFeed(jsonString)

    @Throws(ParsingException::class)
    fun parseContinuationFeed(jsonString: String): Pair<Array<BrowseShelf>, String?> =
        HomeParser.parseContinuationFeed(jsonString)

    @Throws(ParsingException::class)
    fun parseAlbumDetails(jsonString: String, albumBrowseId: String? = null): AlbumDetails =
        AlbumParser.parseAlbumDetails(jsonString, albumBrowseId)

    @Throws(ParsingException::class)
    fun parsePlaylistDetails(jsonString: String): PlaylistDetails =
        PlaylistParser.parsePlaylistDetails(jsonString)

    @Throws(ParsingException::class)
    fun parseArtistProfile(jsonString: String): ArtistProfile =
        ArtistParser.parseArtistProfile(jsonString)

    @Throws(ParsingException::class)
    fun parsePodcastDetails(jsonString: String): PodcastShowDetails =
        PodcastParser.parsePodcastDetails(jsonString)

    @Throws(ParsingException::class)
    fun parseWatchNext(jsonString: String, parentVideoId: String): WatchNextResult =
        RadioQueueParser.parseWatchNext(jsonString, parentVideoId)

    @Throws(ParsingException::class)
    fun parseLyrics(jsonString: String): ExtractedLyrics? =
        LyricsParser.parseLyrics(jsonString)

    @Throws(ParsingException::class)
    fun parseRelated(jsonString: String): Array<BrowseShelf> =
        RelatedParser.parseRelated(jsonString)
}
