package com.hypermusic.core.domain

import com.hypermusic.core.model.BrowseShelf
import com.hypermusic.core.model.Track

/** Remote music catalog, stream and metadata operations. */
interface MusicService {
    suspend fun getHomeFeed(params: String? = null): Result<com.hypermusic.core.model.HomeData>
    suspend fun getHomeContinuation(continuationToken: String, visitorData: String? = null): Result<com.hypermusic.core.model.HomeContinuationData>
    suspend fun getStreamUrl(videoId: String, quality: String, extractionType: String = "audio"): Result<String>
    suspend fun getStreamSource(videoId: String, quality: String, extractionType: String = "audio"): Result<StreamDescriptor> =
        getStreamUrl(videoId, quality, extractionType).map { StreamDescriptor(it) }
    suspend fun search(query: String): Result<List<Track>>
    suspend fun getSearchSuggestions(query: String): Result<com.hypermusic.core.model.SearchSuggestions>
    suspend fun getExplorePage(browseId: String, countryCode: String? = null): Result<List<BrowseShelf>>
    suspend fun getAlbumDetails(browseId: String): Result<com.hypermusic.core.model.CollectionDetails>
    suspend fun getPlaylistDetails(browseId: String): Result<com.hypermusic.core.model.CollectionDetails>
    suspend fun getPodcastDetails(browseId: String): Result<com.hypermusic.core.model.CollectionDetails>
    suspend fun getArtistProfile(browseId: String): Result<com.hypermusic.core.model.ArtistDetails>
    suspend fun getRadioQueue(videoId: String): Result<List<Track>>
    suspend fun getLyrics(videoId: String): Result<com.hypermusic.core.model.Lyrics?>
    suspend fun getRelated(videoId: String): Result<List<BrowseShelf>>
}
