package com.hypermusic.core.extractor.parsers

import com.hypermusic.core.extractor.common.ItemParsers
import com.hypermusic.core.extractor.common.JsonUtils
import com.hypermusic.core.extractor.errors.ParsingException
import com.hypermusic.core.extractor.models.BrowseShelf
import com.hypermusic.core.extractor.models.ExtractedTrack
import com.hypermusic.core.extractor.models.PlaylistDetails
import com.hypermusic.core.utils.Logger
import org.json.JSONArray
import org.json.JSONObject

/** Parses playlist metadata, tracks and additional shelves. */
object PlaylistParser {

    @Throws(ParsingException::class)
    fun parsePlaylistDetails(jsonString: String): PlaylistDetails {
        val tracks = mutableListOf<ExtractedTrack>()
        var playlistTitle = "Unknown Playlist"
        var creator = "Unknown Creator"
        var creatorId: String? = null
        var trackCount = ""
        var artworkUrl = ""

        try {
            val root = JSONObject(jsonString)

            val microformat = root.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")
            if (microformat != null) {
                playlistTitle = microformat.optString("title", playlistTitle)
                val thumbnails = microformat.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails).ifEmpty { artworkUrl }
            }

            var header = root.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicHeaderRenderer")

            if (header == null) {
                val tabs = root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?: root.optJSONObject("contents")
                        ?.optJSONObject("singleColumnBrowseResultsRenderer")
                        ?.optJSONArray("tabs")

                if (tabs != null && tabs.length() > 0) {
                    val sectionContents = tabs.optJSONObject(0)
                        ?.optJSONObject("tabRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")

                    if (sectionContents != null) {
                        for (i in 0 until sectionContents.length()) {
                            val item = sectionContents.optJSONObject(i) ?: continue
                            val responsiveHeader = item.optJSONObject("musicResponsiveHeaderRenderer")
                            if (responsiveHeader != null) {
                                header = responsiveHeader
                                break
                            }
                        }
                    }
                }
            }

            if (header != null) {
                val headerTitle = header.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                if (headerTitle != null) playlistTitle = headerTitle

                val straplineRuns = header.optJSONObject("straplineTextOne")?.optJSONArray("runs")
                if (straplineRuns != null && straplineRuns.length() > 0) {
                    val firstRun = straplineRuns.optJSONObject(0)
                    creator = firstRun?.optString("text") ?: creator

                    val browseId = firstRun?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                    if (!browseId.isNullOrEmpty()) {
                        creatorId = browseId
                    }
                } else {
                    // Fall back to subtitle runs for creator metadata.
                    val subtitleRuns = header.optJSONObject("subtitle")?.optJSONArray("runs")
                    if (subtitleRuns != null && subtitleRuns.length() > 0) {
                        for (i in 0 until subtitleRuns.length()) {
                            val run = subtitleRuns.optJSONObject(i) ?: continue
                            val browseId = run.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")
                                ?.optString("browseId")
                            if (!browseId.isNullOrEmpty()) {
                                creator = run.optString("text")
                                creatorId = browseId
                                break
                            }
                        }

                        if (creator == "Unknown Creator" || creatorId.isNullOrEmpty()) {
                            val firstText = subtitleRuns.optJSONObject(0)?.optString("text") ?: ""
                            if (firstText.isNotEmpty() && firstText != "Playlist" && firstText != "Album") {
                                creator = firstText
                            }
                        }
                    }
                }

                // Fall back to facepile text when the creator name is missing.
                if (creator == "Unknown Creator" || creator == "Playlist" || creator == "") {
                    val facepileText = header.optJSONObject("facepile")
                        ?.optJSONObject("avatarStackViewModel")
                        ?.optJSONObject("text")
                        ?.optString("content")

                    if (!facepileText.isNullOrEmpty()) {
                        creator = facepileText
                    }
                }

                val secondSubtitleRuns = header.optJSONObject("secondSubtitle")?.optJSONArray("runs")
                if (secondSubtitleRuns != null && secondSubtitleRuns.length() > 0) {
                    val countText = JsonUtils.runsToText(secondSubtitleRuns)
                    if (countText.isNotEmpty()) {
                        trackCount = countText
                    }
                } else {
                    val subtitleRuns = header.optJSONObject("subtitle")?.optJSONArray("runs")
                    if (subtitleRuns != null && subtitleRuns.length() > 0) {
                        val countText = JsonUtils.runsToText(subtitleRuns)
                        if (countText.contains("song") || countText.contains("track")) {
                            trackCount = countText
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

            if (creator == "Unknown Creator" && microformat != null) {
                val microformatDesc = microformat.optString("description")
                if (microformatDesc.contains("•")) {
                    val parts = microformatDesc.split("•")
                    if (parts.size > 1 && parts[1].isNotBlank()) {
                        creator = parts[1].trim()
                    }
                }
            }

            val bottomShelves = mutableListOf<BrowseShelf>()
            val contents = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?: root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONObject("secondaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val section = contents.optJSONObject(i) ?: continue
                    val shelfContents = section.optJSONObject("musicPlaylistShelfRenderer")?.optJSONArray("contents")
                        ?: section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")

                    if (shelfContents != null) {
                        for (k in 0 until shelfContents.length()) {
                            val trackItem = shelfContents.optJSONObject(k) ?: continue
                            ItemParsers.parseTrackItem(trackItem, creator, creatorId, artworkUrl)?.let {
                                tracks.add(it)
                            }
                        }
                    } else if (section.optJSONObject("musicCarouselShelfRenderer") != null) {
                        val singleSectionArray = JSONArray().put(section)
                        ItemParsers.extractShelves(singleSectionArray, bottomShelves)
                    }
                }
            }

            // Infer a missing creator ID from an exact artist-name match among the first five tracks.
            if (creator.isNotEmpty() && creator != "Unknown Creator" && creator != "YouTube Music" && creatorId.isNullOrEmpty()) {
                if (tracks.isNotEmpty()) {
                    val limit = if (tracks.size > 5) 5 else tracks.size
                    for (i in 0 until limit) {
                        val track = tracks[i]
                        if (track.artist == creator && !track.artistId.isNullOrEmpty()) {
                            creatorId = track.artistId
                            break
                        }
                    }
                }
            }

            if (creator == "Unknown Creator" && playlistTitle != "Unknown Playlist") {
                creator = "YouTube Music"
            }

            if (trackCount.isEmpty() && tracks.isNotEmpty()) {
                trackCount = "${tracks.size} tracks"
            }

            return PlaylistDetails(
                title = playlistTitle,
                creator = creator,
                creatorId = creatorId,
                trackCount = trackCount,
                artworkUrl = artworkUrl,
                tracks = tracks.toTypedArray(),
                shelves = if (bottomShelves.isNotEmpty()) bottomShelves.toTypedArray() else null
            )
        } catch (e: Exception) {
            Logger.e("PlaylistParser: Failed to parse Playlist Details", e)
            throw ParsingException("Failed to parse Playlist Details", cause = e)
        }
    }
}
