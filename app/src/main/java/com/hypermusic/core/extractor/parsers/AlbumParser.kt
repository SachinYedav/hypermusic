package com.hypermusic.core.extractor.parsers

import com.hypermusic.core.extractor.common.ItemParsers
import com.hypermusic.core.extractor.common.JsonUtils
import com.hypermusic.core.extractor.errors.ParsingException
import com.hypermusic.core.extractor.models.AlbumDetails
import com.hypermusic.core.extractor.models.BrowseShelf
import com.hypermusic.core.extractor.models.ExtractedEntity
import com.hypermusic.core.extractor.models.ExtractedTrack
import com.hypermusic.core.utils.Logger
import org.json.JSONArray
import org.json.JSONObject

/** Parses album metadata and tracks from supported browse layouts. */
object AlbumParser {

    @Throws(ParsingException::class)
    fun parseAlbumDetails(jsonString: String, albumBrowseId: String? = null): AlbumDetails {
        val tracks = mutableListOf<ExtractedTrack>()
        var albumTitle = "Unknown Album"
        var albumArtist = "Unknown Artist"
        var artistId: String? = null
        var year = ""
        var artworkUrl = ""
        val extractedArtists = mutableListOf<ExtractedEntity>()

        try {
            val root = JSONObject(jsonString)

            val microformat = root.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")
            if (microformat != null) {
                albumTitle = microformat.optString("title", albumTitle)
                val thumbnails = microformat.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails).ifEmpty { artworkUrl }
            }

            var header = root.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")

            // Look for a responsive header inside tab sections when the root header is absent.
            if (header == null) {
                val tabs = root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
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
                if (headerTitle != null) albumTitle = headerTitle

                val straplineRuns = header.optJSONObject("straplineTextOne")?.optJSONArray("runs")
                if (straplineRuns != null && straplineRuns.length() > 0) {
                    for (i in 0 until straplineRuns.length()) {
                        val run = straplineRuns.optJSONObject(i) ?: continue
                        val bId = run.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                        val runText = run.optString("text") ?: ""
                        if (!bId.isNullOrEmpty() && (bId.startsWith("UC") || bId.startsWith("UCI"))) {
                            if (artistId == null) {
                                artistId = bId
                                albumArtist = runText
                            }
                            extractedArtists.add(ExtractedEntity(id = bId, name = runText))
                        } else if (runText.isNotEmpty() && runText != " • " && runText != " & ") {
                            if (albumArtist == "Unknown Artist" || albumArtist == "Album") {
                                albumArtist = runText
                            }
                        }
                    }
                }

                val subtitleRuns = header.optJSONObject("subtitle")?.optJSONArray("runs")
                if (subtitleRuns != null && subtitleRuns.length() > 0) {
                    for (i in 0 until subtitleRuns.length()) {
                        val run = subtitleRuns.optJSONObject(i) ?: continue
                        val text = run.optString("text") ?: ""
                        val browseId = run.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                        if (!browseId.isNullOrEmpty() && (browseId.startsWith("UC") || browseId.startsWith("UCI"))) {
                            if (artistId == null) {
                                artistId = browseId
                                albumArtist = text
                            }
                            extractedArtists.add(ExtractedEntity(id = browseId, name = text))
                        }
                        if (text.matches(Regex("\\d{4}"))) {
                            year = text
                        }
                    }

                    if (year.isEmpty() && subtitleRuns.length() >= 3) {
                        year = subtitleRuns.optJSONObject(subtitleRuns.length() - 1)?.optString("text") ?: ""
                    }
                }

                val thumbnails = header.optJSONObject("thumbnail")
                    ?.optJSONObject("croppedSquareThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                    ?: header.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")

                artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails).ifEmpty { artworkUrl }
            }

            var contents = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (contents == null) {
                contents = root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONObject("secondaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
            }

            val bottomShelves = mutableListOf<BrowseShelf>()
            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val section = contents.optJSONObject(i) ?: continue

                    var shelfContents = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                    if (shelfContents == null) {
                        shelfContents = section.optJSONObject("musicPlaylistShelfRenderer")?.optJSONArray("contents")
                    }

                    if (shelfContents != null) {
                        for (k in 0 until shelfContents.length()) {
                            val trackItem = shelfContents.optJSONObject(k) ?: continue
                            ItemParsers.parseTrackItem(trackItem, albumArtist, artistId, artworkUrl, albumTitle, albumBrowseId)?.let {
                                tracks.add(it)
                            }
                        }
                    } else {
                        val singleSectionArray = JSONArray().put(section)
                        ItemParsers.extractShelves(singleSectionArray, bottomShelves)
                    }
                }
            }

            if (albumArtist == "Unknown Artist" || albumArtist == "Album") {
                if (tracks.isNotEmpty()) {
                    albumArtist = tracks[0].artist ?: "Unknown Artist"
                }
            }

            return AlbumDetails(
                title = albumTitle,
                artist = albumArtist,
                artistId = artistId,
                year = year,
                artworkUrl = artworkUrl,
                tracks = tracks.toTypedArray(),
                artists = extractedArtists.toTypedArray(),
                shelves = if (bottomShelves.isNotEmpty()) bottomShelves.toTypedArray() else null
            )
        } catch (e: Exception) {
            Logger.e("AlbumParser: Failed to parse Album Details", e)
            throw ParsingException("Failed to parse Album Details", cause = e)
        }
    }
}
