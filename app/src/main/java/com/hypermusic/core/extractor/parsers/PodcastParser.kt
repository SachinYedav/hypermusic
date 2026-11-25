package com.hypermusic.core.extractor.parsers

import com.hypermusic.core.extractor.common.JsonUtils
import com.hypermusic.core.extractor.errors.ParsingException
import com.hypermusic.core.extractor.models.ExtractedTrack
import com.hypermusic.core.extractor.models.PodcastShowDetails
import com.hypermusic.core.utils.Logger
import org.json.JSONArray
import org.json.JSONObject

/** Parses podcast show metadata and episode rows. */
object PodcastParser {

    @Throws(ParsingException::class)
    fun parsePodcastDetails(jsonString: String): PodcastShowDetails {
        val tracks = mutableListOf<ExtractedTrack>()
        var podcastTitle = "Unknown Podcast"
        var creator = "Unknown Creator"
        var creatorId: String? = null
        var artworkUrl = ""

        try {
            val root = JSONObject(jsonString)

            var header = root.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicPodcastHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")

            // Fallback for podcast playlists where header is inside tabs
            if (header == null) {
                header = root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
                    ?.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveHeaderRenderer")
            }

            if (header != null) {
                val headerTitle = header.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                if (headerTitle != null) podcastTitle = headerTitle

                val authorRuns = header.optJSONObject("author")?.optJSONArray("runs")
                    ?: header.optJSONObject("subtitle")?.optJSONArray("runs")
                    ?: header.optJSONObject("straplineTextOne")?.optJSONArray("runs")

                if (authorRuns != null && authorRuns.length() > 0) {
                    for (i in 0 until authorRuns.length()) {
                        val run = authorRuns.optJSONObject(i) ?: continue
                        val text = run.optString("text")
                        if (text.isNotEmpty() && text != "Podcast") {
                            creator = text
                            val browseId = run.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")
                                ?.optString("browseId")
                            if (!browseId.isNullOrEmpty()) {
                                creatorId = browseId
                            }
                            break
                        }
                    }
                }

                val thumbnails = header.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                    ?: header.optJSONObject("thumbnail")
                        ?.optJSONObject("croppedSquareThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")

                artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails).ifEmpty { artworkUrl }
            }

            val secondaryContents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONObject("secondaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (secondaryContents != null) {
                for (i in 0 until secondaryContents.length()) {
                    val section = secondaryContents.optJSONObject(i) ?: continue
                    val musicShelf = section.optJSONObject("musicShelfRenderer")
                    if (musicShelf != null) {
                        val shelfContents = musicShelf.optJSONArray("contents")
                        if (shelfContents != null) {
                            extractPodcastEpisodes(shelfContents, tracks, creator, creatorId, artworkUrl)
                        }
                    }
                }
            }

            return PodcastShowDetails(
                title = podcastTitle,
                creator = creator,
                creatorId = creatorId,
                artworkUrl = artworkUrl,
                episodes = tracks.toTypedArray()
            )
        } catch (e: Exception) {
            Logger.e("PodcastParser: Failed to parse Podcast Details", e)
            throw ParsingException("Failed to parse Podcast Details", cause = e)
        }
    }

    private fun extractPodcastEpisodes(
        contents: JSONArray,
        tracks: MutableList<ExtractedTrack>,
        parentArtist: String?,
        parentArtistId: String?,
        parentArtworkUrl: String?
    ) {
        for (i in 0 until contents.length()) {
            try {
                val item = contents.optJSONObject(i) ?: continue
                val renderer = item.optJSONObject("musicMultiRowListItemRenderer") ?: continue

                val videoId: String? = renderer.optJSONObject("onTap")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId")

                if (videoId.isNullOrEmpty()) continue

                val title = renderer.optJSONObject("title")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "Unknown Episode"

                var durationStr = ""
                val durationRuns = renderer.optJSONObject("playbackProgress")
                    ?.optJSONObject("musicPlaybackProgressRenderer")
                    ?.optJSONObject("durationText")
                    ?.optJSONArray("runs")

                if (durationRuns != null && durationRuns.length() > 0) {
                    val lastRun = durationRuns.optJSONObject(durationRuns.length() - 1)?.optString("text") ?: ""
                    durationStr = lastRun.replace(" • ", "").trim()
                }

                val artist = parentArtist ?: "Unknown Creator"
                val artistId = parentArtistId

                var artworkUrl = parentArtworkUrl ?: ""
                val thumbnails = renderer.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                if (thumbnails != null && thumbnails.length() > 0) {
                    artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails).ifEmpty { artworkUrl }
                }

                var durationSeconds = 0.0
                if (durationStr.isNotEmpty()) {
                    if (durationStr.contains(":")) {
                        val parts = durationStr.split(":")
                        if (parts.size == 2) {
                            durationSeconds = (parts[0].toDoubleOrNull() ?: 0.0) * 60 + (parts[1].toDoubleOrNull() ?: 0.0)
                        } else if (parts.size == 3) {
                            durationSeconds = (parts[0].toDoubleOrNull() ?: 0.0) * 3600 + (parts[1].toDoubleOrNull() ?: 0.0) * 60 + (parts[2].toDoubleOrNull() ?: 0.0)
                        }
                    } else {
                        val hrMatch = Regex("""(\d+)\s*hr""").find(durationStr)
                        val minMatch = Regex("""(\d+)\s*min""").find(durationStr)
                        val secMatch = Regex("""(\d+)\s*sec""").find(durationStr)
                        val hours = hrMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                        val mins = minMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                        val secs = secMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                        durationSeconds = hours * 3600 + mins * 60 + secs
                    }
                }

                val subtitleRuns = renderer.optJSONObject("subtitle")?.optJSONArray("runs")
                val releaseDate = if (subtitleRuns != null && subtitleRuns.length() > 0) {
                    JsonUtils.runsToText(subtitleRuns)
                } else null

                tracks.add(
                    ExtractedTrack(
                        id = videoId,
                        title = title,
                        artist = artist,
                        artistId = artistId,
                        album = null,
                        albumId = null,
                        duration = durationSeconds,
                        artworkUrl = artworkUrl,
                        isExplicit = JsonUtils.isExplicit(renderer),
                        artists = emptyArray(),
                        description = null,
                        releaseDate = releaseDate
                    )
                )
            } catch (e: Exception) {
                Logger.e("PodcastParser: Failed to parse podcast episode, skipping", e)
            }
        }
    }
}
