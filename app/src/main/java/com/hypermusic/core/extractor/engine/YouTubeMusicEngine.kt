package com.hypermusic.core.extractor.engine

import com.hypermusic.core.extractor.errors.EngineException
import com.hypermusic.core.extractor.errors.ExtractorException
import com.hypermusic.core.extractor.models.BrowseItem
import com.hypermusic.core.extractor.models.BrowseShelf
import com.hypermusic.core.extractor.models.ExtractedLyrics
import com.hypermusic.core.extractor.models.ExtractedTrack
import com.hypermusic.core.extractor.models.WatchNextResult
import com.hypermusic.core.extractor.network.NetworkClient
import com.hypermusic.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization

/** Coordinates direct InnerTube catalog requests and NewPipe stream extraction. */
object YouTubeMusicEngine {
    private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val lyricsBrowseIdCache = BoundedLruCache<String, String>(200)
    private val relatedBrowseIdCache = BoundedLruCache<String, String>(200)

    init {
        NewPipe.init(NewPipeDownloader.instance, Localization.DEFAULT)
    }

    fun search(query: String, hl: String = "en", gl: String = "IN"): Array<BrowseItem> {
        return try {
            Logger.d("Engine: Searching for '$query'")

            val payload = PayloadBuilder.buildSearchPayload(query, hl, gl)
            val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$BASE_URL/search?alt=json&hl=$hl&gl=$gl")
                .header("Accept-Language", "$hl-$gl,$hl;q=0.9")
                .post(requestBody)
                .build()

            val response = NetworkClient.executeSync(request)
            val responseBody = response.body?.string() ?: throw EngineException("Empty response body")

            Logger.d("Engine: Successfully fetched search response, parsing now...")
            return Parsers.parseSearchResponse(responseBody)
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: Search failed", e)
            throw EngineException("Failed to execute search for query: $query", e)
        }
    }

    suspend fun getSearchSuggestions(query: String, hl: String = "en", gl: String = "IN"): com.hypermusic.core.extractor.models.ExtractedSearchSuggestions {
        return try {
            Logger.d("Engine: Fetching search suggestions for '$query'")

            val payload = PayloadBuilder.buildSearchSuggestionsPayload(query, hl, gl)
            val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$BASE_URL/music/get_search_suggestions?alt=json&hl=$hl&gl=$gl")
                .header("Accept-Language", "$hl-$gl,$hl;q=0.9")
                .post(requestBody)
                .build()

            val response = NetworkClient.executeAsync(request)
            val responseBody = response.body?.string() ?: throw EngineException("Empty response body")

            Parsers.parseSearchSuggestions(responseBody)
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: getSearchSuggestions failed", e)
            throw EngineException("Failed to fetch search suggestions for query: $query", e)
        }
    }

    fun getStreamSource(videoId: String, quality: String, extractionType: String = "audio"): com.hypermusic.core.domain.StreamDescriptor {
        return try {
            Logger.d("Engine: Getting stream URL for '$videoId', quality '$quality', type '$extractionType'")

            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val info = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

            if (extractionType == "video" || extractionType == "podcast") {
                // Use progressive streams containing both audio and video.
                val videoStreams = info.videoStreams
                if (videoStreams.isNullOrEmpty()) {
                    throw EngineException("No progressive video streams found for $videoId")
                }

                // Prefer MP4 at 360p/480p, then another MP4, then another progressive stream.
                val selectedVideo = StreamSelector.progressiveVideo(videoStreams)
                    ?: throw EngineException("Failed to fallback video stream for $videoId")

                val url = selectedVideo.content ?: throw EngineException("Stream URL is empty")
                Logger.d("Engine: Extracted Video URL via NewPipe (Res: ${selectedVideo.getResolution()})")
                return describeStream(selectedVideo, true)
            }

            val bestAudio = StreamSelector.audio(info.audioStreams.orEmpty(), quality)
            if (bestAudio == null) {
                Logger.d("Engine: No audio streams found. Falling back to progressive video stream for audio playback.")
                val videoStreams = info.videoStreams
                if (!videoStreams.isNullOrEmpty()) {
                    // Reuse the progressive-stream preference when audio-only streams are unavailable.
                    val selectedVideo = StreamSelector.progressiveVideo(videoStreams)

                    val content = selectedVideo?.content
                    if (content != null) {
                        return describeStream(selectedVideo!!, true)
                    }
                }
                throw EngineException("No audio or video streams found for $videoId")
            }

            val url = bestAudio.content ?: throw EngineException("Stream URL is empty")
            Logger.d("Engine: Successfully extracted Audio URL via NewPipe (Bitrate: ${bestAudio.averageBitrate})")
            describeStream(bestAudio, false, bestAudio.averageBitrate)
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: getStreamUrl failed", e)
            throw EngineException("Failed to get stream URL for video: $videoId", e)
        }
    }

    /** Maps the selected stream to transport metadata. */
    private fun describeStream(stream: org.schabi.newpipe.extractor.stream.Stream, video: Boolean, bitrate: Int? = null) =
        com.hypermusic.core.domain.StreamDescriptor(stream.content, stream.id, stream.format?.mimeType,
            stream.format?.suffix, bitrate, video)

    private fun fetchBrowse(
        browseId: String,
        params: String? = null,
        hl: String = "en",
        gl: String = "IN",
        formDataValues: List<String>? = null
    ): String {
        val effectiveGl = if (gl == "ZZ") "US" else gl
        val payload = PayloadBuilder.buildBrowsePayload(browseId, params, hl, effectiveGl, formDataValues)
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/browse?alt=json&hl=$hl&gl=$effectiveGl")
            .header("Accept-Language", "$hl-$effectiveGl,$hl;q=0.9")
            .post(requestBody)
            .build()

        val response = NetworkClient.executeSync(request)
        return response.body?.string() ?: throw EngineException("Empty response body")
    }

    private fun fetchContinuation(continuationToken: String, visitorData: String? = null, hl: String = "en", gl: String = "IN"): String {
        val payload = PayloadBuilder.buildContinuationPayload(continuationToken, visitorData, hl, gl)
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/browse?continuation=$continuationToken&ctoken=$continuationToken&alt=json&hl=$hl&gl=$gl")
            .header("Accept-Language", "$hl-$gl,$hl;q=0.9")
            .post(requestBody)
            .build()

        val response = NetworkClient.executeSync(request)
        return response.body?.string() ?: throw EngineException("Empty response body for continuation")
    }

    private suspend fun fetchBrowseAsync(
        browseId: String,
        params: String? = null,
        hl: String = "en",
        gl: String = "IN",
        formDataValues: List<String>? = null
    ): String {
        val effectiveGl = if (gl == "ZZ") "US" else gl
        val payload = PayloadBuilder.buildBrowsePayload(browseId, params, hl, effectiveGl, formDataValues)
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/browse?alt=json&hl=$hl&gl=$effectiveGl")
            .header("Accept-Language", "$hl-$effectiveGl,$hl;q=0.9")
            .post(requestBody)
            .build()

        val response = NetworkClient.executeAsync(request)
        return response.body?.string() ?: throw EngineException("Empty response body")
    }

    private suspend fun fetchContinuationAsync(
        continuationToken: String,
        visitorData: String? = null,
        hl: String = "en",
        gl: String = "IN"
    ): String {
        val payload = PayloadBuilder.buildContinuationPayload(continuationToken, visitorData, hl, gl)
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/browse?continuation=$continuationToken&ctoken=$continuationToken&alt=json&hl=$hl&gl=$gl")
            .header("Accept-Language", "$hl-$gl,$hl;q=0.9")
            .post(requestBody)
            .build()

        val response = NetworkClient.executeAsync(request)
        return response.body?.string() ?: throw EngineException("Empty response body for continuation")
    }

    suspend fun getHomeFeed(params: String? = null, hl: String = "en", gl: String = "IN"): com.hypermusic.core.extractor.models.HomeFeed {
        return try {
            Logger.d("Engine: Fetching initial Home Feed (params: $params)")
            val homeBody = fetchBrowseAsync("FEmusic_home", params, hl, gl)
            val homeParsed = Parsers.parseHomeFeed(homeBody)

            val shelves = if (homeParsed.shelves.isEmpty() && homeParsed.continuationToken == null && params == null) {
                try {
                    val chartsBody = fetchBrowseAsync("FEmusic_charts", null, hl, gl)
                    Parsers.parseHomeFeed(chartsBody).shelves
                } catch (e: Exception) {
                    Logger.w("Engine: Fallback charts fetch failed", e)
                    homeParsed.shelves
                }
            } else {
                homeParsed.shelves
            }

            com.hypermusic.core.extractor.models.HomeFeed(
                shelves = shelves,
                chips = homeParsed.chips,
                continuationToken = homeParsed.continuationToken,
                visitorData = homeParsed.visitorData
            )
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: getHomeFeed failed", e)
            throw EngineException("Failed to fetch Home Feed", e)
        }
    }

    suspend fun getHomeContinuation(
        continuationToken: String,
        visitorData: String? = null,
        hl: String = "en",
        gl: String = "IN"
    ): Pair<Array<com.hypermusic.core.extractor.models.BrowseShelf>, String?> {
        return try {
            val contBody = fetchContinuationAsync(continuationToken, visitorData, hl, gl)
            Parsers.parseContinuationFeed(contBody)
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: getHomeContinuation failed", e)
            throw EngineException("Failed to fetch continuation feed", e)
        }
    }

    fun getAlbumDetails(browseId: String, hl: String = "en", gl: String = "IN"): com.hypermusic.core.extractor.models.AlbumDetails {
        return try {
            Logger.d("Engine: Fetching Album Details for $browseId")
            val responseBody = fetchBrowse(browseId, null, hl, gl)
            Parsers.parseAlbumDetails(responseBody, browseId)
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: getAlbumDetails failed", e)
            throw EngineException("Failed to fetch Album Details", e)
        }
    }

    /** Adds VL to supported raw playlist IDs before browsing. */
    fun getPlaylistDetails(browseId: String, hl: String = "en", gl: String = "IN"): com.hypermusic.core.extractor.models.PlaylistDetails {
        return try {
            val targetBrowseId = if (!browseId.startsWith("VL") && (browseId.startsWith("PL") || browseId.startsWith("RD") || browseId.startsWith("OLAK5uy_"))) {
                "VL$browseId"
            } else {
                browseId
            }
            Logger.d("Engine: Fetching Playlist Details for $targetBrowseId")
            val responseBody = fetchBrowse(targetBrowseId, null, hl, gl)
            Parsers.parsePlaylistDetails(responseBody)
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: getPlaylistDetails failed", e)
            throw EngineException("Failed to fetch Playlist Details", e)
        }
    }

    fun getPodcastDetails(browseId: String, hl: String = "en", gl: String = "IN"): com.hypermusic.core.extractor.models.PodcastShowDetails {
        return try {
            Logger.d("Engine: Fetching Podcast Details for $browseId")
            val responseBody = fetchBrowse(browseId, null, hl, gl)
            Parsers.parsePodcastDetails(responseBody)
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: getPodcastDetails failed", e)
            throw EngineException("Failed to fetch Podcast Details", e)
        }
    }

    fun getArtistProfile(browseId: String, hl: String = "en", gl: String = "IN"): com.hypermusic.core.extractor.models.ArtistProfile {
        return try {
            Logger.d("Engine: Fetching Artist Profile for $browseId")
            val responseBody = fetchBrowse(browseId, null, hl, gl)
            Parsers.parseArtistProfile(responseBody)
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: getArtistProfile failed", e)
            throw EngineException("Failed to fetch Artist Profile", e)
        }
    }

    fun getExplorePage(
        browseId: String,
        hl: String = "en",
        gl: String = "IN",
        formDataValues: List<String>? = null
    ): Array<com.hypermusic.core.extractor.models.BrowseShelf> {
        return try {
            Logger.d("Engine: Fetching Explore Page for $browseId (gl=$gl, formData=$formDataValues)")
            val finalFormData = formDataValues ?: if (browseId == "FEmusic_charts" && !gl.isNullOrBlank()) listOf(gl) else null
            val responseBody = fetchBrowse(browseId, null, hl, gl, finalFormData)
            val feed = Parsers.parseHomeFeed(responseBody)
            feed.shelves
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: getExplorePage failed", e)
            throw EngineException("Failed to fetch Explore Page for $browseId", e)
        }
    }

    /** Loads watch-next tracks and caches available lyrics/related browse IDs. */
    fun getWatchNext(videoId: String, hl: String = "en", gl: String = "IN"): WatchNextResult {
        return try {
            Logger.d("Engine: Fetching Watch Next for '$videoId'")

            val payload = PayloadBuilder.buildRadioPayload(videoId, hl, gl)
            val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$BASE_URL/next?alt=json&hl=$hl&gl=$gl")
                .header("Accept-Language", "$hl-$gl,$hl;q=0.9")
                .post(requestBody)
                .build()

            val response = NetworkClient.executeSync(request)
            val responseBody = response.body?.string() ?: throw EngineException("Empty response body")

            val result = Parsers.parseWatchNext(responseBody, videoId)

            result.lyricsBrowseId?.let { lyricsBrowseIdCache[videoId] = it }
            result.relatedBrowseId?.let { relatedBrowseIdCache[videoId] = it }

            result
        } catch (e: ExtractorException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Engine: getWatchNext failed", e)
            throw EngineException("Failed to fetch Watch Next for video: $videoId", e)
        }
    }

    /** Returns radio tracks from one watch-next response. */
    fun getRadioQueue(videoId: String, hl: String = "en", gl: String = "IN"): Array<ExtractedTrack> {
        return getWatchNext(videoId, hl, gl).tracks
    }

    /** Returns null for missing lyrics; retrieval and parsing failures propagate to the caller. */
    fun getLyrics(videoId: String, hl: String = "en", gl: String = "IN"): ExtractedLyrics? {
        return try {
            Logger.d("Engine: Getting lyrics for '$videoId'")

            var browseId = lyricsBrowseIdCache[videoId]
            if (browseId == null) {
                val watchNext = getWatchNext(videoId, hl, gl)
                browseId = watchNext.lyricsBrowseId
            }

            if (browseId.isNullOrEmpty()) {
                Logger.d("Engine: No lyrics browseId found for video: $videoId")
                return null
            }

            val responseBody = fetchBrowse(browseId, hl = hl, gl = gl)
            Parsers.parseLyrics(responseBody)
        } catch (e: Exception) {
            Logger.w("Engine: Failed to get lyrics for $videoId", e)
            throw e
        }
    }

    /** Returns an empty array for missing content; retrieval and parsing failures propagate. */
    fun getRelated(videoId: String, hl: String = "en", gl: String = "IN"): Array<BrowseShelf> {
        return try {
            Logger.d("Engine: Getting related content for '$videoId'")

            var browseId = relatedBrowseIdCache[videoId]
            if (browseId == null) {
                val watchNext = getWatchNext(videoId, hl, gl)
                browseId = watchNext.relatedBrowseId
            }

            if (browseId.isNullOrEmpty()) {
                Logger.d("Engine: No related browseId found for video: $videoId")
                return emptyArray()
            }

            val responseBody = fetchBrowse(browseId, hl = hl, gl = gl)
            Parsers.parseRelated(responseBody)
        } catch (e: Exception) {
            Logger.w("Engine: Failed to get related content for $videoId", e)
            throw e
        }
    }
}
