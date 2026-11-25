package com.hypermusic.core.extractor.parsers

import com.hypermusic.core.extractor.common.ItemParsers
import com.hypermusic.core.extractor.common.JsonUtils
import com.hypermusic.core.extractor.errors.ParsingException
import com.hypermusic.core.extractor.models.ArtistProfile
import com.hypermusic.core.extractor.models.BrowseShelf
import com.hypermusic.core.extractor.models.ExtractedTextRun
import com.hypermusic.core.utils.Logger
import org.json.JSONObject

/** Parses artist metadata, biography and shelves. */
object ArtistParser {

    @Throws(ParsingException::class)
    fun parseArtistProfile(jsonString: String): ArtistProfile {
        var name = "Unknown Artist"
        var subtitle = ""
        var artworkUrl = ""
        var artistBio: String? = null
        var artistBioRuns: List<ExtractedTextRun>? = null
        val shelvesList = mutableListOf<BrowseShelf>()

        try {
            val root = JSONObject(jsonString)

            val header = root.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicVisualHeaderRenderer")

            if (header != null) {
                name = header.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: name

                val headerDescRuns = header.optJSONObject("description")?.optJSONArray("runs")
                if (headerDescRuns != null && headerDescRuns.length() > 0) {
                    val runs = JsonUtils.runsToTextRuns(headerDescRuns)
                    val descText = runs.joinToString("") { it.text }.trim()
                    if (descText.isNotEmpty()) {
                        artistBio = descText
                        artistBioRuns = runs
                    }
                }

                var thumbnailObj = header.optJSONObject("thumbnail")
                    ?: header.optJSONObject("foregroundThumbnail")

                val thumbnails = thumbnailObj
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                artworkUrl = JsonUtils.extractArtworkFromThumbnails(thumbnails).ifEmpty { artworkUrl }

                val subscribeButton = header.optJSONObject("subscriptionButton")?.optJSONObject("subscribeButtonRenderer")

                var subText: String? = subscribeButton
                    ?.optJSONObject("subscriberCountWithSubscribeText")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text")

                if (subText.isNullOrEmpty()) {
                    subText = subscribeButton
                        ?.optJSONObject("subscriberCountText")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text")
                }

                if (subText.isNullOrEmpty()) {
                    subText = subscribeButton
                        ?.optJSONObject("subscriberCountText")
                        ?.optString("simpleText")
                }

                if (!subText.isNullOrEmpty()) {
                    subtitle = subText.toString().replace(" subscribers", "").replace(" subscriber", "").trim()
                }
            }

            val contents = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (contents != null) {
                // Check for musicDescriptionShelfRenderer if header didn't have bio
                for (i in 0 until contents.length()) {
                    val sec = contents.optJSONObject(i) ?: continue
                    val descShelf = sec.optJSONObject("musicDescriptionShelfRenderer")
                    if (descShelf != null && artistBio.isNullOrEmpty()) {
                        val descRuns = descShelf.optJSONObject("description")?.optJSONArray("runs")
                        if (descRuns != null && descRuns.length() > 0) {
                            val runs = JsonUtils.runsToTextRuns(descRuns)
                            val text = runs.joinToString("") { it.text }.trim()
                            if (text.isNotEmpty()) {
                                artistBio = text
                                artistBioRuns = runs
                            }
                        }
                    }
                }
                ItemParsers.extractShelves(contents, shelvesList)
            }

            return ArtistProfile(
                name = name,
                subtitle = subtitle,
                artworkUrl = artworkUrl,
                shelves = shelvesList.toTypedArray(),
                description = artistBio,
                descriptionRuns = artistBioRuns
            )
        } catch (e: Exception) {
            Logger.e("ArtistParser: Failed to parse Artist Profile", e)
            throw ParsingException("Failed to parse Artist Profile", cause = e)
        }
    }
}
