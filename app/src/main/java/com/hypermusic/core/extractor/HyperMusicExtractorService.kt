package com.hypermusic.core.extractor

import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.extractor.engine.YouTubeMusicEngine
import com.hypermusic.core.model.BrowseShelf
import com.hypermusic.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.hypermusic.core.datastore.PreferencesManager
import kotlinx.coroutines.flow.first

/** Applies content preferences and maps extractor results to app models. */
class HyperMusicExtractorService(private val preferencesManager: PreferencesManager) : MusicService {

    private suspend fun isExplicitAllowed(): Boolean {
        return preferencesManager.userSettingsFlow.first().allowExplicitContent
    }

    private suspend fun getLanguage(): String {
        val lang = preferencesManager.userSettingsFlow.first().contentLanguage
        return if (lang == "auto") java.util.Locale.getDefault().language else lang
    }

    private suspend fun getRegion(): String {
        val region = preferencesManager.userSettingsFlow.first().contentRegion
        return if (region == "auto") java.util.Locale.getDefault().country else region
    }

    override suspend fun getHomeFeed(params: String?): Result<com.hypermusic.core.model.HomeData> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val feed = YouTubeMusicEngine.getHomeFeed(params, hl, gl)
            val explicitAllowed = isExplicitAllowed()
            
            val fallbackOccurrences = mutableMapOf<String, Int>()
            val uiShelves = feed.shelves.map { shelf ->
                val stableId = shelf.moreEndpoint?.takeIf { it.isNotBlank() }?.let { "home_endpoint:$it" }
                    ?: fallbackShelfId(shelf.title, shelf.type, fallbackOccurrences)
                BrowseShelf(
                    id = stableId,
                    title = shelf.title,
                    type = shelf.type ?: "carousel",
                    items = shelf.items.filter { explicitAllowed || !it.isExplicit }.map { item ->
                        Track(
                            id = item.id,
                            title = item.title,
                            artist = item.subtitle,
                            artwork = item.artworkUrl,
                            trackType = item.type,
                            isExplicitContent = item.isExplicit,
                            artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                            album = item.album,
                            albumId = item.albumId,
                            chartRank = item.chartRank,
                            chartTrend = item.chartTrend
                        )
                    },
                    moreEndpoint = shelf.moreEndpoint
                )
            }.filter { it.items.isNotEmpty() }
            
            val uiChips = feed.chips?.map { chip ->
                com.hypermusic.core.model.FilterChip(
                    title = chip.title,
                    params = chip.params
                )
            } ?: emptyList()
            
            Result.success(
                com.hypermusic.core.model.HomeData(
                    shelves = uiShelves,
                    chips = uiChips,
                    continuationToken = feed.continuationToken,
                    visitorData = feed.visitorData
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getHomeContinuation(
        continuationToken: String,
        visitorData: String?
    ): Result<com.hypermusic.core.model.HomeContinuationData> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val (shelves, nextToken) = YouTubeMusicEngine.getHomeContinuation(continuationToken, visitorData, hl, gl)
            val explicitAllowed = isExplicitAllowed()

            val fallbackOccurrences = mutableMapOf<String, Int>()
            val uiShelves = shelves.map { shelf ->
                val stableId = shelf.moreEndpoint?.takeIf { it.isNotBlank() }?.let { "home_endpoint:$it" }
                    ?: fallbackShelfId(shelf.title, shelf.type, fallbackOccurrences)
                BrowseShelf(
                    id = stableId,
                    title = shelf.title,
                    type = shelf.type ?: "carousel",
                    items = shelf.items.filter { explicitAllowed || !it.isExplicit }.map { item ->
                        Track(
                            id = item.id,
                            title = item.title,
                            artist = item.subtitle,
                            artwork = item.artworkUrl,
                            trackType = item.type,
                            isExplicitContent = item.isExplicit,
                            artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                            album = item.album,
                            albumId = item.albumId,
                            chartRank = item.chartRank,
                            chartTrend = item.chartTrend
                        )
                    },
                    moreEndpoint = shelf.moreEndpoint
                )
            }.filter { it.items.isNotEmpty() }

            Result.success(com.hypermusic.core.model.HomeContinuationData(uiShelves, nextToken))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fallbackShelfId(title: String, type: String?, occurrences: MutableMap<String, Int>): String {
        val base = "home_shelf:${type ?: "carousel"}:${title.ifBlank { "shelf" }}"
        val occurrence = occurrences.getOrDefault(base, 0) + 1
        occurrences[base] = occurrence
        return "$base:$occurrence"
    }

    override suspend fun getStreamUrl(videoId: String, quality: String, extractionType: String): Result<String> =
        getStreamSource(videoId, quality, extractionType).map { it.url }

    override suspend fun getStreamSource(videoId: String, quality: String, extractionType: String): Result<com.hypermusic.core.domain.StreamDescriptor> {
        return try {
            Result.success(com.hypermusic.core.extractor.engine.ExtractionRequest.execute {
                YouTubeMusicEngine.getStreamSource(videoId, quality, extractionType)
            })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun search(query: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val items = YouTubeMusicEngine.search(query, hl, gl)
            val explicitAllowed = isExplicitAllowed()
            
            val uiTracks = items.filter { explicitAllowed || !it.isExplicit }.map { item ->
                Track(
                    id = item.id,
                    title = item.title,
                    artist = item.subtitle,
                    artwork = item.artworkUrl,
                    trackType = item.type,
                    isExplicitContent = item.isExplicit,
                    artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) }
                )
            }
            Result.success(uiTracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSearchSuggestions(query: String): Result<com.hypermusic.core.model.SearchSuggestions> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val extracted = YouTubeMusicEngine.getSearchSuggestions(query, hl, gl)
            val explicitAllowed = isExplicitAllowed()
            val richTracks = extracted.richItems
                .filter { explicitAllowed || !it.isExplicit }
                .map { item ->
                    Track(
                        id = item.id,
                        title = item.title,
                        artist = item.subtitle,
                        artwork = item.artworkUrl,
                        trackType = item.type,
                        isExplicitContent = item.isExplicit,
                        artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) }
                    )
                }
            Result.success(
                com.hypermusic.core.model.SearchSuggestions(
                    queries = extracted.queries.toList(),
                    richItems = richTracks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getExplorePage(browseId: String, countryCode: String?): Result<List<BrowseShelf>> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val region = getRegion()
            val gl = countryCode ?: region
            val formData = if (browseId == "FEmusic_charts" && !gl.isNullOrBlank()) listOf(gl) else null
            val shelves = YouTubeMusicEngine.getExplorePage(browseId, hl, gl, formData)
            val explicitAllowed = isExplicitAllowed()
            
            val uiShelves = shelves.mapIndexed { index, shelf ->
                val stableId = shelf.moreEndpoint ?: "${shelf.title.ifBlank { "shelf" }}_${index}_${java.util.UUID.randomUUID().toString().take(6)}"
                BrowseShelf(
                    id = stableId,
                    title = shelf.title,
                    type = shelf.type ?: "grid",
                    items = shelf.items.filter { explicitAllowed || !it.isExplicit }.map { item ->
                        Track(
                            id = item.id,
                            title = item.title,
                            artist = item.subtitle,
                            artwork = item.artworkUrl,
                            trackType = item.type,
                            isExplicitContent = item.isExplicit,
                            artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                            album = item.album,
                            albumId = item.albumId,
                            chartRank = item.chartRank,
                            chartTrend = item.chartTrend
                        )
                    },
                    moreEndpoint = shelf.moreEndpoint
                )
            }.filter { it.items.isNotEmpty() }
            Result.success(uiShelves)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAlbumDetails(browseId: String): Result<com.hypermusic.core.model.CollectionDetails> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val album = YouTubeMusicEngine.getAlbumDetails(browseId, hl, gl)
            val explicitAllowed = isExplicitAllowed()
            
            val bottomShelves = album.shelves?.map { shelf ->
                BrowseShelf(
                    id = shelf.title,
                    title = shelf.title,
                    type = shelf.type ?: "carousel",
                    items = shelf.items.filter { explicitAllowed || !it.isExplicit }.map { item ->
                        Track(
                            id = item.id,
                            title = item.title,
                            artist = item.subtitle,
                            artwork = item.artworkUrl,
                            trackType = item.type,
                            isExplicitContent = item.isExplicit,
                            artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                            album = item.album,
                            albumId = item.albumId,
                            chartRank = item.chartRank,
                            chartTrend = item.chartTrend
                        )
                    },
                    moreEndpoint = shelf.moreEndpoint
                )
            }?.filter { it.items.isNotEmpty() } ?: emptyList()

            val uiCollection = com.hypermusic.core.model.CollectionDetails(
                id = browseId,
                title = album.title,
                subtitle = album.artist,
                artworkUrl = album.artworkUrl,
                year = album.year,
                artistId = album.artistId,
                artists = album.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) }
                    ?: if (!album.artistId.isNullOrBlank()) listOf(com.hypermusic.core.model.ArtistInfo(album.artistId, album.artist)) else null,
                tracks = album.tracks.filter { explicitAllowed || !it.isExplicit }.map { item ->
                    Track(
                        id = item.id,
                        title = item.title,
                        artist = item.artist,
                        artwork = item.artworkUrl,
                        trackType = "song",
                        isExplicitContent = item.isExplicit,
                        artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                        album = item.album ?: album.title,
                        albumId = item.albumId ?: browseId
                    )
                },
                shelves = bottomShelves
            )
            Result.success(uiCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPlaylistDetails(browseId: String): Result<com.hypermusic.core.model.CollectionDetails> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val playlist = YouTubeMusicEngine.getPlaylistDetails(browseId, hl, gl)
            val explicitAllowed = isExplicitAllowed()
            
            val bottomShelves = playlist.shelves?.map { shelf ->
                BrowseShelf(
                    id = shelf.title,
                    title = shelf.title,
                    type = shelf.type ?: "carousel",
                    items = shelf.items.filter { explicitAllowed || !it.isExplicit }.map { item ->
                        Track(
                            id = item.id,
                            title = item.title,
                            artist = item.subtitle,
                            artwork = item.artworkUrl,
                            trackType = item.type,
                            isExplicitContent = item.isExplicit,
                            artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                            album = item.album,
                            albumId = item.albumId,
                            chartRank = item.chartRank,
                            chartTrend = item.chartTrend
                        )
                    },
                    moreEndpoint = shelf.moreEndpoint
                )
            }?.filter { it.items.isNotEmpty() } ?: emptyList()

            val uiCollection = com.hypermusic.core.model.CollectionDetails(
                id = browseId,
                title = playlist.title,
                subtitle = playlist.creator,
                artworkUrl = playlist.artworkUrl,
                year = playlist.trackCount,
                artistId = playlist.creatorId,
                artists = if (!playlist.creatorId.isNullOrBlank()) listOf(com.hypermusic.core.model.ArtistInfo(playlist.creatorId, playlist.creator)) else null,
                tracks = playlist.tracks.filter { explicitAllowed || !it.isExplicit }.map { item ->
                    Track(
                        id = item.id,
                        title = item.title,
                        artist = item.artist,
                        artwork = item.artworkUrl,
                        trackType = "song",
                        isExplicitContent = item.isExplicit,
                        artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                        album = item.album,
                        albumId = item.albumId
                    )
                },
                shelves = bottomShelves
            )
            Result.success(uiCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPodcastDetails(browseId: String): Result<com.hypermusic.core.model.CollectionDetails> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val podcast = YouTubeMusicEngine.getPodcastDetails(browseId, hl, gl)
            val explicitAllowed = isExplicitAllowed()
            
            val uiCollection = com.hypermusic.core.model.CollectionDetails(
                id = browseId,
                title = podcast.title,
                subtitle = podcast.creator,
                artworkUrl = podcast.artworkUrl,
                year = null,
                artistId = podcast.creatorId,
                artists = if (!podcast.creatorId.isNullOrBlank()) listOf(com.hypermusic.core.model.ArtistInfo(podcast.creatorId, podcast.creator)) else null,
                tracks = podcast.episodes.filter { explicitAllowed || !it.isExplicit }.map { item ->
                    Track(
                        id = item.id,
                        title = item.title,
                        artist = item.artist,
                        artwork = item.artworkUrl,
                        duration = item.duration.toLong(),
                        trackType = "podcast_episode",
                        isExplicitContent = item.isExplicit,
                        artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                        description = item.description,
                        releaseDate = item.releaseDate,
                        album = podcast.title,
                        albumId = browseId
                    )
                }
            )
            Result.success(uiCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getArtistProfile(browseId: String): Result<com.hypermusic.core.model.ArtistDetails> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val artist = YouTubeMusicEngine.getArtistProfile(browseId, hl, gl)
            val explicitAllowed = isExplicitAllowed()
            
            val uiShelves = artist.shelves.map { shelf ->
                BrowseShelf(
                    id = shelf.title,
                    title = shelf.title,
                    type = shelf.type ?: "carousel",
                    items = shelf.items.filter { explicitAllowed || !it.isExplicit }.map { item ->
                        Track(
                            id = item.id,
                            title = item.title,
                            artist = item.subtitle,
                            artwork = item.artworkUrl,
                            trackType = item.type,
                            isExplicitContent = item.isExplicit,
                            artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                            album = item.album,
                            albumId = item.albumId,
                            chartRank = item.chartRank,
                            chartTrend = item.chartTrend
                        )
                    },
                    moreEndpoint = shelf.moreEndpoint
                )
            }.filter { it.items.isNotEmpty() }
            
            val uiCollection = com.hypermusic.core.model.ArtistDetails(
                title = artist.name,
                subtitle = artist.subtitle,
                artworkUrl = artist.artworkUrl,
                shelves = uiShelves,
                description = artist.description,
                descriptionRuns = artist.descriptionRuns?.map { com.hypermusic.core.model.TextRun(it.text, it.url) }
            )
            Result.success(uiCollection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRadioQueue(videoId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val radioTracks = YouTubeMusicEngine.getRadioQueue(videoId, hl, gl)
            val explicitAllowed = isExplicitAllowed()
            
            val uiTracks = radioTracks.filter { explicitAllowed || !it.isExplicit }.map { item ->
                Track(
                    id = item.id,
                    title = item.title,
                    artist = item.artist,
                    artwork = item.artworkUrl,
                    trackType = "song",
                    isImplicit = true,
                    isExplicitContent = item.isExplicit,
                    artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                    album = item.album,
                    albumId = item.albumId
                )
            }
            Result.success(uiTracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLyrics(videoId: String): Result<com.hypermusic.core.model.Lyrics?> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val lyrics = YouTubeMusicEngine.getLyrics(videoId, hl, gl)
            if (lyrics != null) {
                Result.success(com.hypermusic.core.model.Lyrics(text = lyrics.text, source = lyrics.source))
            } else {
                Result.success(null)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRelated(videoId: String): Result<List<BrowseShelf>> = withContext(Dispatchers.IO) {
        try {
            val hl = getLanguage()
            val gl = getRegion()
            val shelves = YouTubeMusicEngine.getRelated(videoId, hl, gl)
            val explicitAllowed = isExplicitAllowed()

            val uiShelves = shelves.map { shelf ->
                BrowseShelf(
                    id = shelf.title.lowercase().replace(" ", "_"),
                    title = shelf.title,
                    type = shelf.type ?: "carousel",
                    items = shelf.items.filter { explicitAllowed || !it.isExplicit }.map { item ->
                        Track(
                            id = item.id,
                            title = item.title,
                            artist = item.subtitle,
                            artwork = item.artworkUrl,
                            trackType = item.type,
                            isExplicitContent = item.isExplicit,
                            artists = item.artists?.map { com.hypermusic.core.model.ArtistInfo(it.id, it.name) },
                            album = item.album,
                            albumId = item.albumId,
                            chartRank = item.chartRank,
                            chartTrend = item.chartTrend
                        )
                    },
                    description = shelf.description,
                    descriptionRuns = shelf.descriptionRuns?.map { com.hypermusic.core.model.TextRun(it.text, it.url) },
                    footer = shelf.footer
                )
            }.filter { it.items.isNotEmpty() || !it.description.isNullOrBlank() }

            Result.success(uiShelves)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

