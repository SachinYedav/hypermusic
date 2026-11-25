package com.hypermusic.core.extractor.parsers

import com.hypermusic.core.extractor.common.JsonUtils
import com.hypermusic.core.extractor.errors.ParsingException
import com.hypermusic.core.extractor.models.ExtractedEntity
import com.hypermusic.core.extractor.models.ExtractedTrack
import com.hypermusic.core.extractor.models.WatchNextResult
import com.hypermusic.core.utils.Logger
import org.json.JSONObject

object RadioQueueParser {

    /**
     * Reads queue tracks by renderer structure or the English title, excluding the seed video.
     * Lyrics and related tabs also use browse-ID/page-type checks.
     */
    @Throws(ParsingException::class)
    fun parseWatchNext(jsonString: String, parentVideoId: String): WatchNextResult {
        val tracks = mutableListOf<ExtractedTrack>()
        var lyricsBrowseId: String? = null
        var relatedBrowseId: String? = null

        try {
            val root = JSONObject(jsonString)

            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs")

            if (tabs != null) {
                for (i in 0 until tabs.length()) {
                    val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                    val title = tab.optString("title")

                    val isQueueTab = tab.optJSONObject("content")?.optJSONObject("musicQueueRenderer") != null ||
                        title.equals("Up next", ignoreCase = true)
                    if (isQueueTab) {
                        val contents = tab.optJSONObject("content")
                            ?.optJSONObject("musicQueueRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("playlistPanelRenderer")
                            ?.optJSONArray("contents")

                        if (contents != null) {
                            for (j in 0 until contents.length()) {
                                try {
                                    val item = contents.optJSONObject(j) ?: continue
                                    val renderer = item.optJSONObject("playlistPanelVideoRenderer") ?: continue

                                    val videoId = renderer.optString("videoId")
                                    if (videoId.isNullOrEmpty() || videoId == parentVideoId) continue

                                    val itemTitle = renderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown Title"

                                    val longBylineTextRuns = renderer.optJSONObject("longBylineText")?.optJSONArray("runs")
                                    val artistBuilder = StringBuilder()
                                    var artistId: String? = null
                                    var albumId: String? = null
                                    var albumName: String? = null
                                    val extractedArtists = mutableListOf<ExtractedEntity>()

                                    if (longBylineTextRuns != null) {
                                        for (k in 0 until longBylineTextRuns.length()) {
                                            val runObj = longBylineTextRuns.optJSONObject(k) ?: continue
                                            val runText = runObj.optString("text")

                                            val browseId = runObj.optJSONObject("navigationEndpoint")
                                                ?.optJSONObject("browseEndpoint")
                                                ?.optString("browseId")

                                            if (!browseId.isNullOrEmpty()) {
                                                if (browseId.startsWith("UC")) {
                                                    if (artistId == null) artistId = browseId
                                                    artistBuilder.append(runText)
                                                    extractedArtists.add(ExtractedEntity(id = browseId, name = runText))
                                                } else if (browseId.startsWith("MPREb")) {
                                                    if (albumId == null) {
                                                        albumId = browseId
                                                        albumName = runText
                                                    }
                                                    break
                                                } else {
                                                    break
                                                }
                                            } else if (runText == " • " || runText == " & ") {
                                                artistBuilder.append(runText)
                                            }
                                        }
                                    }

                                    if (albumId == null) {
                                        val menuAlbum = JsonUtils.extractAlbumFromMenu(renderer.optJSONObject("menu"))
                                        if (menuAlbum != null) {
                                            albumId = menuAlbum.second
                                            if (menuAlbum.first != null) albumName = menuAlbum.first
                                        }
                                    }

                                    var artist = artistBuilder.toString().replace(Regex("^( • )|( • )$"), "").trim()
                                    if (artist.isEmpty()) artist = "Unknown Artist"

                                    val durationStr = renderer.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                                    var duration = 0.0
                                    if (durationStr.isNotEmpty()) {
                                        val parts = durationStr.split(":")
                                        if (parts.size == 2) {
                                            duration = (parts[0].toDoubleOrNull() ?: 0.0) * 60 + (parts[1].toDoubleOrNull() ?: 0.0)
                                        } else if (parts.size == 3) {
                                            duration = (parts[0].toDoubleOrNull() ?: 0.0) * 3600 + (parts[1].toDoubleOrNull() ?: 0.0) * 60 + (parts[2].toDoubleOrNull() ?: 0.0)
                                        }
                                    }

                                    val thumbnails = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                    val artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails)

                                    tracks.add(
                                        ExtractedTrack(
                                            id = videoId,
                                            title = itemTitle,
                                            artist = artist,
                                            artistId = artistId,
                                            album = albumName,
                                            albumId = albumId,
                                            duration = duration,
                                            artworkUrl = artworkUrl,
                                            isExplicit = JsonUtils.isExplicit(renderer),
                                            artists = extractedArtists.toTypedArray()
                                        )
                                    )
                                } catch (e: Exception) {
                                    Logger.w("RadioQueueParser: Failed to parse radio track", e)
                                }
                            }
                        }
                    } else {
                        val isUnselectable = tab.optBoolean("unselectable", false)
                        val endpoint = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")
                        val id = endpoint?.optString("browseId")
                        val pageType = endpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
                            ?.optJSONObject("browseEndpointContextMusicConfig")
                            ?.optString("pageType")

                        val isLyricsTab = !id.isNullOrEmpty() && (
                            id.startsWith("MPLYt") ||
                            pageType == "MUSIC_PAGE_TYPE_TRACK_LYRICS" ||
                            title.equals("Lyrics", ignoreCase = true)
                        )
                        val isRelatedTab = !id.isNullOrEmpty() && (
                            id.startsWith("MPTRt") ||
                            pageType == "MUSIC_PAGE_TYPE_TRACK_RELATED" ||
                            title.equals("Related", ignoreCase = true)
                        )

                        if (isLyricsTab) {
                            if (!isUnselectable) {
                                lyricsBrowseId = id
                            }
                        } else if (isRelatedTab) {
                            if (!isUnselectable) {
                                relatedBrowseId = id
                            }
                        }
                    }
                }
            }

            Logger.i("RadioQueueParser: Extracted ${tracks.size} tracks, lyricsId: $lyricsBrowseId, relatedId: $relatedBrowseId")
            return WatchNextResult(
                tracks = tracks.toTypedArray(),
                lyricsBrowseId = lyricsBrowseId,
                relatedBrowseId = relatedBrowseId
            )
        } catch (e: Exception) {
            Logger.e("RadioQueueParser: Failed to parse Watch Next / Radio response", e)
            throw ParsingException("Failed to parse Watch Next response", cause = e)
        }
    }

}
