package com.hypermusic.core.extractor.common

import com.hypermusic.core.extractor.models.BrowseItem
import com.hypermusic.core.extractor.models.BrowseShelf
import com.hypermusic.core.extractor.models.ExtractedEntity
import com.hypermusic.core.extractor.models.ExtractedTrack
import com.hypermusic.core.utils.Logger
import org.json.JSONArray
import org.json.JSONObject

/** Shared parsers for media items and shelves. */
object ItemParsers {
    private val durationPattern = Regex("^(?:\\d+:)?\\d+:\\d{2}$")

    private fun parseDurationSeconds(text: String): Double? {
        if (!durationPattern.matches(text)) return null
        return text.split(":").fold(0.0) { total, part ->
            total * 60 + (part.toDoubleOrNull() ?: return null)
        }
    }

    /**
     * Checks show, artist, playlist, episode and album branches in that order using page type,
     * ID prefixes or subtitle text, then falls back to a video ID or an album browse ID.
     * Adds VL to PL/RD/OLAK5uy playlist IDs; episodes prefer videoId, otherwise strip MPED.
     */
    fun resolveItemTypeAndId(
        rawBrowseId: String?,
        pageType: String?,
        videoId: String?,
        isVideoHint: Boolean = false,
        firstSubtitleWord: String? = null
    ): Pair<String, String>? {
        val bId = rawBrowseId ?: ""
        val pType = pageType ?: ""
        val vId = videoId ?: ""
        val subHint = firstSubtitleWord?.trim() ?: ""

        if (pType == "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" ||
            bId.startsWith("MPSP") || bId.startsWith("MPP") ||
            subHint.equals("Podcast", ignoreCase = true)
        ) {
            if (bId.isNotEmpty()) return Pair(bId, "podcast_show")
        }

        if (pType == "MUSIC_PAGE_TYPE_ARTIST" || pType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
            bId.startsWith("UC") || bId.startsWith("HC") || bId.startsWith("FEmusic_library_privately_owned_artist") ||
            subHint.equals("Artist", ignoreCase = true)
        ) {
            if (bId.isNotEmpty()) return Pair(bId, "artist")
        }

        if (pType == "MUSIC_PAGE_TYPE_PLAYLIST" ||
            bId.startsWith("VL") || bId.startsWith("PL") || bId.startsWith("RD") || bId.startsWith("OLAK5uy") ||
            subHint.equals("Playlist", ignoreCase = true)
        ) {
            if (bId.isNotEmpty()) {
                val normalizedId = if (!bId.startsWith("VL") && (bId.startsWith("PL") || bId.startsWith("RD") || bId.startsWith("OLAK5uy"))) {
                    "VL$bId"
                } else {
                    bId
                }
                return Pair(normalizedId, "playlist")
            }
        }

        if (pType == "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE" ||
            bId.startsWith("MPED") ||
            subHint.equals("Episode", ignoreCase = true)
        ) {
            val finalId = if (vId.isNotEmpty()) vId else bId.removePrefix("MPED")
            if (finalId.isNotEmpty()) return Pair(finalId, "podcast_episode")
        }

        if (pType == "MUSIC_PAGE_TYPE_ALBUM" ||
            bId.startsWith("MPREb") || bId.startsWith("MPRE") ||
            subHint.equals("Album", ignoreCase = true) ||
            subHint.equals("Single", ignoreCase = true) ||
            subHint.equals("EP", ignoreCase = true)
        ) {
            if (bId.isNotEmpty()) return Pair(bId, "album")
        }

        if (vId.isNotEmpty()) {
            val type = if (isVideoHint || subHint.equals("Video", ignoreCase = true)) "video" else "song"
            return Pair(vId, type)
        }

        // Unclassified browse IDs fall back to albums.
        if (bId.isNotEmpty()) {
            return Pair(bId, "album")
        }

        return null
    }

    /** Reads a responsive search row. */
    fun parseSearchItem(item: JSONObject): BrowseItem? {
        return try {
            val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: return null
            val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
            if (flexColumns.length() < 1) return null

            val titleCol = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
            val title = titleCol?.optString("text") ?: "Unknown Title"

            val subtitleRuns = flexColumns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")

            val subtitleBuilder = StringBuilder()
            var extractedArtistId: String? = null
            var extractedAlbumId: String? = null
            var extractedAlbumName: String? = null
            val extractedArtists = mutableListOf<ExtractedEntity>()

            if (subtitleRuns != null) {
                for (j in 0 until subtitleRuns.length()) {
                    val runObj = subtitleRuns.optJSONObject(j) ?: continue
                    val runText = runObj.optString("text") ?: ""
                    subtitleBuilder.append(runText)

                    val browseId = runObj.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                    if (!browseId.isNullOrEmpty()) {
                        if (browseId.startsWith("UC")) {
                            if (extractedArtistId == null) extractedArtistId = browseId
                            extractedArtists.add(ExtractedEntity(id = browseId, name = runText))
                        } else if (browseId.startsWith("MPREb")) {
                            if (extractedAlbumId == null) {
                                extractedAlbumId = browseId
                                extractedAlbumName = runText
                            }
                        }
                    }
                }
            }
            var subtitle = subtitleBuilder.toString()
            if (subtitle.startsWith("Song • ")) subtitle = subtitle.substring(7)
            if (subtitle.startsWith("Video • ")) subtitle = subtitle.substring(8)

            // Fallback: Check menu for "Go to album" browseId
            if (extractedAlbumId == null) {
                val menuAlbum = JsonUtils.extractAlbumFromMenu(renderer.optJSONObject("menu"))
                if (menuAlbum != null) {
                    extractedAlbumId = menuAlbum.second
                    if (menuAlbum.first != null) extractedAlbumName = menuAlbum.first
                }
            }

            val endpoint = renderer.optJSONObject("navigationEndpoint")
            val browseEndpoint = endpoint?.optJSONObject("browseEndpoint")
            val rawBrowseId = browseEndpoint?.optString("browseId")
            val pageType = browseEndpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")
                ?.optString("pageType")

            var videoId = endpoint?.optJSONObject("watchEndpoint")?.optString("videoId")
            if (videoId.isNullOrEmpty()) {
                videoId = renderer.optJSONObject("overlay")
                    ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicPlayButtonRenderer")
                    ?.optJSONObject("playNavigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId")
            }

            val fallbackBrowseId = if (rawBrowseId.isNullOrEmpty() && videoId.isNullOrEmpty()) {
                val titleEndpoint = titleCol?.optJSONObject("navigationEndpoint")
                titleEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId")
            } else null

            val finalBrowseId = if (!rawBrowseId.isNullOrEmpty()) rawBrowseId else fallbackBrowseId
            val firstSubWord = subtitleRuns?.optJSONObject(0)?.optString("text")?.trim()

            val resolved = resolveItemTypeAndId(
                rawBrowseId = finalBrowseId,
                pageType = pageType,
                videoId = videoId,
                isVideoHint = firstSubWord.equals("Video", ignoreCase = true),
                firstSubtitleWord = firstSubWord
            ) ?: return null

            val id = resolved.first
            val type = resolved.second

            val thumbnails = renderer.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            val artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails)

            BrowseItem(
                id = id,
                type = type,
                title = title,
                subtitle = subtitle,
                artworkUrl = artworkUrl,
                isExplicit = JsonUtils.isExplicit(renderer),
                artistId = extractedArtistId,
                album = extractedAlbumName,
                albumId = extractedAlbumId,
                artists = extractedArtists.toTypedArray()
            )
        } catch (e: Exception) {
            Logger.w("ItemParsers: Failed to parse search item", e)
            null
        }
    }

    /** Reads a spotlight music-card shelf. */
    fun parseMusicCardShelf(cardShelf: JSONObject): BrowseItem? {
        return try {
            val titleObj = cardShelf.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
            val title = titleObj?.optString("text") ?: "Unknown"

            val subtitleObj = cardShelf.optJSONObject("subtitle")?.optJSONArray("runs")
            val subtitleBuilder = StringBuilder()
            var firstRunText: String? = null
            var extractedArtistId: String? = null
            val extractedArtists = mutableListOf<ExtractedEntity>()

            if (subtitleObj != null) {
                if (subtitleObj.length() > 0) {
                    firstRunText = subtitleObj.optJSONObject(0)?.optString("text")?.trim()
                }
                for (k in 0 until subtitleObj.length()) {
                    val run = subtitleObj.optJSONObject(k) ?: continue
                    val text = run.optString("text") ?: ""
                    subtitleBuilder.append(text)

                    val browseEndpoint = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    val bId = browseEndpoint?.optString("browseId")
                    if (!bId.isNullOrEmpty() && (bId.startsWith("UC") || bId.startsWith("HC"))) {
                        if (extractedArtistId == null) extractedArtistId = bId
                        extractedArtists.add(ExtractedEntity(id = bId, name = text))
                    }
                }
            }
            val subtitle = subtitleBuilder.toString()

            val thumbnails = cardShelf.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            val artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails)

            val endpoint = titleObj?.optJSONObject("navigationEndpoint") ?: cardShelf.optJSONObject("onTap")
            val browseEndpoint = endpoint?.optJSONObject("browseEndpoint")
            var browseId = browseEndpoint?.optString("browseId") ?: ""
            val pageType = browseEndpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")
                ?.optString("pageType") ?: ""

            var videoId = endpoint?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""

            val buttons = cardShelf.optJSONArray("buttons")
            var buttonPlaylistId: String? = null
            if (buttons != null && buttons.length() > 0) {
                for (b in 0 until buttons.length()) {
                    val cmd = buttons.optJSONObject(b)?.optJSONObject("buttonRenderer")?.optJSONObject("command") ?: continue
                    val bWatch = cmd.optJSONObject("watchEndpoint")?.optString("videoId")
                    if (videoId.isEmpty() && !bWatch.isNullOrEmpty()) {
                        videoId = bWatch
                    }
                    val bPl = cmd.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
                    if (buttonPlaylistId == null && !bPl.isNullOrEmpty()) {
                        buttonPlaylistId = bPl
                    }
                }
            }

            val resolved = resolveItemTypeAndId(
                rawBrowseId = if (browseId.isNotEmpty()) browseId else buttonPlaylistId,
                pageType = pageType,
                videoId = videoId,
                isVideoHint = firstRunText.equals("Video", ignoreCase = true),
                firstSubtitleWord = firstRunText
            ) ?: return null

            val id = resolved.first
            val type = resolved.second

            BrowseItem(
                id = id,
                type = type,
                title = title,
                subtitle = subtitle,
                artworkUrl = artworkUrl,
                isExplicit = JsonUtils.isExplicit(cardShelf),
                artistId = extractedArtistId,
                album = null,
                albumId = null,
                artists = extractedArtists.toTypedArray()
            )
        } catch (e: Exception) {
            Logger.w("ItemParsers: Failed to parse musicCardShelf", e)
            null
        }
    }

    /** Reads a two-row card or responsive list item. */
    fun parseBrowseItem(itemContainer: JSONObject): BrowseItem? {
        return try {
            var item = itemContainer.optJSONObject("musicTwoRowItemRenderer")
            var isResponsive = false
            if (item == null) {
                item = itemContainer.optJSONObject("musicResponsiveListItemRenderer")
                if (item != null) isResponsive = true
            }
            if (item == null) return null

            var id = ""
            var type = "album"
            var itemTitle = ""
            var subtitle = ""
            var artworkUrl = ""
            var extractedArtistId: String? = null
            var extractedAlbumId: String? = null
            var extractedAlbumName: String? = null
            val extractedArtists = mutableListOf<ExtractedEntity>()
            var chartRank: String? = null
            var chartTrend: String? = null

            if (isResponsive) {
                val cic = item.optJSONObject("customIndexColumn")?.optJSONObject("musicCustomIndexColumnRenderer")
                if (cic != null) {
                    chartRank = cic.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    val iconType = cic.optJSONObject("icon")?.optString("iconType")
                    chartTrend = when (iconType) {
                        "ARROW_DROP_UP" -> "UP"
                        "ARROW_DROP_DOWN" -> "DOWN"
                        "ARROW_CHART_NEUTRAL" -> "NEUTRAL"
                        else -> null
                    }
                }

                val flexColumns = item.optJSONArray("flexColumns") ?: return null
                if (flexColumns.length() < 1) return null

                itemTitle = flexColumns.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "Unknown Title"

                val subtitleRuns = flexColumns.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                val subtitleBuilder = StringBuilder()
                if (subtitleRuns != null) {
                    for (k in 0 until subtitleRuns.length()) {
                        val runObj = subtitleRuns.optJSONObject(k)
                        val runText = runObj?.optString("text") ?: ""
                        subtitleBuilder.append(runText)
                        val browseId = runObj?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                        if (!browseId.isNullOrEmpty()) {
                            if (browseId.startsWith("UC")) {
                                if (extractedArtistId == null) extractedArtistId = browseId
                                extractedArtists.add(ExtractedEntity(id = browseId, name = runText))
                            } else if (extractedAlbumId == null && browseId.startsWith("MPREb")) {
                                extractedAlbumId = browseId
                                extractedAlbumName = runText
                            }
                        }
                    }
                }
                subtitle = subtitleBuilder.toString()
                if (subtitle.startsWith("Song • ")) subtitle = subtitle.substring(7)
                var hasVideoSubtitle = false
                if (subtitle.startsWith("Video • ")) {
                    subtitle = subtitle.substring(8)
                    hasVideoSubtitle = true
                }

                if (extractedAlbumId == null) {
                    val menuAlbum = JsonUtils.extractAlbumFromMenu(item.optJSONObject("menu"))
                    if (menuAlbum != null) {
                        extractedAlbumId = menuAlbum.second
                        if (menuAlbum.first != null) extractedAlbumName = menuAlbum.first
                    }
                }

                val endpoint = item.optJSONObject("navigationEndpoint")
                val browseEndpoint = endpoint?.optJSONObject("browseEndpoint")
                val browseId = browseEndpoint?.optString("browseId")
                val pageType = browseEndpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
                    ?.optJSONObject("browseEndpointContextMusicConfig")
                    ?.optString("pageType")

                val watchEndpointObj = endpoint?.optJSONObject("watchEndpoint")
                    ?: item.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                val videoId = watchEndpointObj?.optString("videoId")
                val musicVideoType = watchEndpointObj?.optJSONObject("watchEndpointMusicSupportedConfigs")
                    ?.optJSONObject("watchEndpointMusicConfig")
                    ?.optString("musicVideoType") ?: ""

                val isVideoHint = hasVideoSubtitle || musicVideoType.contains("OMV") || musicVideoType.contains("UGC") || musicVideoType.contains("OFFICIAL_SOURCE_MUSIC")
                val firstSubWord = subtitleRuns?.optJSONObject(0)?.optString("text")?.trim()

                val resolved = resolveItemTypeAndId(
                    rawBrowseId = browseId,
                    pageType = pageType,
                    videoId = videoId,
                    isVideoHint = isVideoHint,
                    firstSubtitleWord = firstSubWord
                )
                if (resolved != null) {
                    id = resolved.first
                    type = resolved.second
                }

                val thumbnails = item.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails)
            } else {
                val endpoint = item.optJSONObject("navigationEndpoint")
                val browseEndpoint = endpoint?.optJSONObject("browseEndpoint")
                val browseId = browseEndpoint?.optString("browseId")
                val pageType = browseEndpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
                    ?.optJSONObject("browseEndpointContextMusicConfig")
                    ?.optString("pageType")

                val watchEndpointObj = endpoint?.optJSONObject("watchEndpoint")
                    ?: item.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                val videoId = watchEndpointObj?.optString("videoId")
                val musicVideoType = watchEndpointObj?.optJSONObject("watchEndpointMusicSupportedConfigs")
                    ?.optJSONObject("watchEndpointMusicConfig")
                    ?.optString("musicVideoType") ?: ""
                val aspectRatio = item.optString("aspectRatio")

                itemTitle = item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""

                val subtitleRuns = item.optJSONObject("subtitle")?.optJSONArray("runs")
                val subtitleBuilder = StringBuilder()
                if (subtitleRuns != null) {
                    for (k in 0 until subtitleRuns.length()) {
                        val runObj = subtitleRuns.optJSONObject(k)
                        val runText = runObj?.optString("text") ?: ""
                        subtitleBuilder.append(runText)
                        val bId = runObj?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                        if (!bId.isNullOrEmpty()) {
                            if (bId.startsWith("UC")) {
                                if (extractedArtistId == null) extractedArtistId = bId
                                extractedArtists.add(ExtractedEntity(id = bId, name = runText))
                            } else if (extractedAlbumId == null && bId.startsWith("MPREb")) {
                                extractedAlbumId = bId
                                extractedAlbumName = runText
                            }
                        }
                    }
                }
                subtitle = subtitleBuilder.toString()

                val isVideoHint = aspectRatio.contains("16_BY_9") || musicVideoType.contains("OMV") || musicVideoType.contains("UGC") || musicVideoType.contains("OFFICIAL_SOURCE_MUSIC")
                val firstSubWord = subtitleRuns?.optJSONObject(0)?.optString("text")?.trim()

                val resolved = resolveItemTypeAndId(
                    rawBrowseId = browseId,
                    pageType = pageType,
                    videoId = videoId,
                    isVideoHint = isVideoHint,
                    firstSubtitleWord = firstSubWord
                )
                if (resolved != null) {
                    id = resolved.first
                    type = resolved.second
                }

                val thumbnails = item.optJSONObject("thumbnailRenderer")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails)
            }

            if (id.isEmpty()) return null

            BrowseItem(
                id = id,
                type = type,
                title = itemTitle,
                subtitle = subtitle,
                artworkUrl = artworkUrl,
                isExplicit = JsonUtils.isExplicit(item),
                artistId = extractedArtistId,
                album = extractedAlbumName,
                albumId = extractedAlbumId,
                artists = extractedArtists.toTypedArray(),
                chartRank = chartRank,
                chartTrend = chartTrend
            )
        } catch (e: Exception) {
            Logger.w("ItemParsers: Failed to parse browse item", e)
            null
        }
    }

    /** Reads a responsive track row, with parent metadata as fallback. */
    fun parseTrackItem(
        item: JSONObject,
        parentArtist: String? = null,
        parentArtistId: String? = null,
        parentArtworkUrl: String? = null,
        parentAlbum: String? = null,
        parentAlbumId: String? = null
    ): ExtractedTrack? {
        return try {
            val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: return null

            var videoId: String? = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            if (videoId.isNullOrEmpty()) {
                videoId = renderer.optJSONObject("overlay")
                    ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicPlayButtonRenderer")
                    ?.optJSONObject("playNavigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId")
            }
            if (videoId.isNullOrEmpty()) return null

            val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
            if (flexColumns.length() < 2) return null

            val titleCol = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
            val title = titleCol?.optString("text") ?: "Unknown Title"

            var duration = 0.0
            val fixedColumns = renderer.optJSONArray("fixedColumns")
            if (fixedColumns != null && fixedColumns.length() > 0) {
                val runs = fixedColumns.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                if (runs != null && runs.length() > 0) {
                    val runText = runs.optJSONObject(0)?.optString("text") ?: ""
                    parseDurationSeconds(runText)?.let { duration = it }
                }
            }

            // Read artist metadata; a matching subtitle duration overrides fixedColumns.
            val artistColRuns = flexColumns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")

            val artistBuilder = StringBuilder()
            var artistId: String? = null
            var albumId: String? = null
            var albumName: String? = null
            val extractedArtists = mutableListOf<ExtractedEntity>()

            if (artistColRuns != null) {
                for (j in 0 until artistColRuns.length()) {
                    val runObj = artistColRuns.optJSONObject(j) ?: continue
                    val runText = runObj.optString("text")

                    val browseId = runObj.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                    if (!browseId.isNullOrEmpty()) {
                        if (browseId.startsWith("UC")) {
                            if (artistId == null) artistId = browseId
                            extractedArtists.add(ExtractedEntity(id = browseId, name = runText))
                        } else if (albumId == null && browseId.startsWith("MPREb")) {
                            albumId = browseId
                            albumName = runText
                        }
                    }

                    val runDuration = parseDurationSeconds(runText)
                    if (runText == " • " || runText == " & ") {
                        artistBuilder.append(runText)
                    } else if (runDuration != null) {
                        duration = runDuration
                    } else if (runText != "Song" && runText != "Video") {
                        artistBuilder.append(runText)
                    }
                }
            }

            if (albumId == null && flexColumns.length() > 2) {
                val albumColRuns = flexColumns.optJSONObject(2)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                if (albumColRuns != null) {
                    for (j in 0 until albumColRuns.length()) {
                        val runObj = albumColRuns.optJSONObject(j) ?: continue
                        val bId = runObj.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                        if (!bId.isNullOrEmpty() && bId.startsWith("MPREb")) {
                            albumId = bId
                            albumName = runObj.optString("text")
                            break
                        }
                    }
                }
            }

            // Fallback: Check menu for "Go to album" browseId
            if (albumId == null) {
                val menuAlbum = JsonUtils.extractAlbumFromMenu(renderer.optJSONObject("menu"))
                if (menuAlbum != null) {
                    albumId = menuAlbum.second
                    if (menuAlbum.first != null) albumName = menuAlbum.first
                }
            }

            // Fallback to parentAlbum / parentAlbumId
            if (albumId == null) {
                albumId = parentAlbumId
                albumName = parentAlbum
            }

            var artist = artistBuilder.toString().replace(Regex("^( • )|( • )$"), "").trim()
            if (artist.isEmpty() || artist == "Unknown Artist") {
                artist = parentArtist ?: "Unknown Artist"
            }
            if (artist.startsWith("• ")) artist = artist.substring(2).trim()
            if (artistId == null) artistId = parentArtistId

            var artworkUrl = parentArtworkUrl ?: ""
            val thumbnails = renderer.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            if (thumbnails != null && thumbnails.length() > 0) {
                artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails).ifEmpty { artworkUrl }
            }

            ExtractedTrack(
                id = videoId,
                title = title,
                artist = artist,
                artistId = artistId,
                album = albumName,
                albumId = albumId,
                duration = duration,
                artworkUrl = artworkUrl,
                isExplicit = JsonUtils.isExplicit(renderer),
                artists = extractedArtists.toTypedArray()
            )
        } catch (e: Exception) {
            Logger.w("ItemParsers: Failed to parse track item", e)
            null
        }
    }

    /** Adds supported carousel, grid and list shelves to the supplied list. */
    fun extractShelves(contents: JSONArray, shelvesList: MutableList<BrowseShelf>) {
        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i) ?: continue

            var carousel = section.optJSONObject("musicCarouselShelfRenderer")
            if (carousel == null) {
                carousel = section.optJSONObject("musicImmersiveCarouselShelfRenderer")
            }

            val grid = section.optJSONObject("gridRenderer")
            var title = "Shelf"
            var shelfType = "carousel"
            var carouselContents: JSONArray? = null
            var moreEndpoint: String? = null
            var numItemsPerColumn = 1

            if (grid != null) {
                shelfType = "grid"
                val gridTitleObj = grid.optJSONObject("header")
                    ?.optJSONObject("gridHeaderRenderer")
                    ?.optJSONObject("title")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                title = gridTitleObj?.optString("text") ?: "Explore"
                carouselContents = grid.optJSONArray("items")
            } else if (carousel != null) {
                val headerRenderer = carousel.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?: carousel.optJSONObject("header")?.optJSONObject("musicImmersiveCarouselShelfBasicHeaderRenderer")
                val titleObj = headerRenderer?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
                val straplineObj = headerRenderer?.optJSONObject("straplinee")?.optJSONArray("runs")?.optJSONObject(0)
                val mainTitle = titleObj?.optString("text") ?: "Shelf"
                val straplineText = straplineObj?.optString("text") ?: ""
                title = if (straplineText.isNotEmpty()) "$straplineText • $mainTitle" else mainTitle
                val moreBtnEndpoint = headerRenderer?.optJSONObject("moreContentButton")
                    ?.optJSONObject("buttonRenderer")
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?: headerRenderer?.optJSONObject("title")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                if (moreBtnEndpoint != null) {
                    val bid = moreBtnEndpoint.optString("browseId")
                    val params = moreBtnEndpoint.optString("params")
                    val pageType = moreBtnEndpoint.optJSONObject("browseEndpointContextSupportedConfigs")
                        ?.optJSONObject("browseEndpointContextMusicConfig")
                        ?.optString("pageType")
                    if (bid.isNotEmpty() && pageType != "MUSIC_PAGE_TYPE_ARTIST" && !bid.startsWith("UC")) {
                        moreEndpoint = if (params.isNotEmpty()) "$bid|||$params" else bid
                    }
                }
                numItemsPerColumn = carousel.optInt("numItemsPerColumn", 1)
                carouselContents = carousel.optJSONArray("contents")
            } else {
                val musicShelf = section.optJSONObject("musicShelfRenderer")
                if (musicShelf != null) {
                    shelfType = "list"
                    title = musicShelf.optJSONObject("title")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: "Top Songs"
                    val bottomBrowse = musicShelf.optJSONObject("bottomEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?: musicShelf.optJSONObject("bottomButton")
                            ?.optJSONObject("buttonRenderer")
                            ?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                    if (bottomBrowse != null) {
                        val bid = bottomBrowse.optString("browseId")
                        val params = bottomBrowse.optString("params")
                        val pageType = bottomBrowse.optJSONObject("browseEndpointContextSupportedConfigs")
                            ?.optJSONObject("browseEndpointContextMusicConfig")
                            ?.optString("pageType")
                        if (bid.isNotEmpty() && pageType != "MUSIC_PAGE_TYPE_ARTIST" && !bid.startsWith("UC")) {
                            moreEndpoint = if (params.isNotEmpty()) "$bid|||$params" else bid
                        }
                    }
                    carouselContents = musicShelf.optJSONArray("contents")
                } else {
                    val itemSection = section.optJSONObject("itemSectionRenderer")
                    if (itemSection != null) {
                        val innerContents = itemSection.optJSONArray("contents")
                        if (innerContents != null && innerContents.length() > 0) {
                            val innerShelf = innerContents.optJSONObject(0)?.optJSONObject("musicCarouselShelfRenderer")
                            if (innerShelf != null) {
                                shelfType = "carousel"
                                val headerRenderer = innerShelf.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                                val titleObj = headerRenderer?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
                                val straplineObj = headerRenderer?.optJSONObject("straplinee")?.optJSONArray("runs")?.optJSONObject(0)
                                val mainTitle = titleObj?.optString("text") ?: "Shelf"
                                val straplineText = straplineObj?.optString("text") ?: ""
                                title = if (straplineText.isNotEmpty()) "$straplineText • $mainTitle" else mainTitle
                                carouselContents = innerShelf.optJSONArray("contents")
                            }
                        }
                    }
                }
            }

            if (carouselContents == null) continue

            val itemsList = mutableListOf<BrowseItem>()
            for (j in 0 until carouselContents.length()) {
                try {
                    val itemContainer = carouselContents.optJSONObject(j) ?: continue

                    val navButton = itemContainer.optJSONObject("musicNavigationButtonRenderer")
                    if (navButton != null) {
                        val buttonText = navButton.optJSONObject("buttonText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Explore"
                        val endpoint = navButton.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint")
                        val browseId = endpoint?.optString("browseId") ?: ""
                        val params = endpoint?.optString("params") ?: ""
                        val combinedId = if (params.isNotEmpty()) "$browseId|||$params" else browseId
                        if (combinedId.isNotEmpty()) {
                            itemsList.add(
                                BrowseItem(
                                    id = combinedId,
                                    type = "genre",
                                    title = buttonText,
                                    subtitle = "Explore",
                                    artworkUrl = "",
                                    artistId = null,
                                    album = null,
                                    albumId = null,
                                    artists = emptyArray()
                                )
                            )
                        }
                        continue
                    }

                    val multiRow = itemContainer.optJSONObject("musicMultiRowListItemRenderer")
                    if (multiRow != null) {
                        val titleObj = multiRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown Title"
                        var sub = multiRow.optJSONObject("secondTitle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                        if (sub.isEmpty()) {
                            val subtitleRuns = multiRow.optJSONObject("subtitle")?.optJSONArray("runs")
                            sub = JsonUtils.runsToText(subtitleRuns)
                        }
                        if (sub.startsWith("Podcast • ")) sub = sub.substring(10)
                        if (sub.startsWith("Episode • ")) sub = sub.substring(10)

                        val targetEndpoint = multiRow.optJSONObject("onTap") ?: multiRow.optJSONObject("navigationEndpoint")
                        val videoId = targetEndpoint?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                        val browseId = targetEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""

                        val id = if (videoId.isNotEmpty()) videoId else browseId
                        val itemType = if (browseId.startsWith("MPSP") || browseId.startsWith("MPP")) {
                            "podcast_show"
                        } else {
                            "podcast_episode"
                        }

                        val thumbnails = multiRow.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails)

                        if (id.isNotEmpty()) {
                            itemsList.add(
                                BrowseItem(
                                    id = id,
                                    type = itemType,
                                    title = titleObj,
                                    subtitle = sub,
                                    artworkUrl = artworkUrl,
                                    artistId = null,
                                    album = null,
                                    albumId = null,
                                    artists = emptyArray()
                                )
                            )
                        }
                        continue
                    }

                    parseBrowseItem(itemContainer)?.let { itemsList.add(it) }
                } catch (e: Exception) {
                    Logger.w("ItemParsers: Failed to parse item in shelf, skipping", e)
                }
            }

            if (itemsList.isNotEmpty()) {
                val resolvedType = if (numItemsPerColumn > 1 && itemsList.all { it.type == "artist" }) {
                    "chart_artists"
                } else {
                    shelfType
                }
                shelvesList.add(BrowseShelf(title = title, type = resolvedType, items = itemsList.toTypedArray(), moreEndpoint = moreEndpoint))
            }
        }
    }
}
