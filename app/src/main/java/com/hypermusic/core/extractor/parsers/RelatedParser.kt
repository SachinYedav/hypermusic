package com.hypermusic.core.extractor.parsers

import com.hypermusic.core.extractor.common.ItemParsers
import com.hypermusic.core.extractor.errors.ParsingException
import com.hypermusic.core.extractor.models.BrowseShelf
import com.hypermusic.core.utils.Logger
import org.json.JSONObject

object RelatedParser {

    /** Reads related shelves and optional artist biography text. */
    @Throws(ParsingException::class)
    fun parseRelated(jsonString: String): Array<BrowseShelf> {
        val shelvesList = mutableListOf<BrowseShelf>()
        try {
            val root = JSONObject(jsonString)
            val contents = root.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyArray()

            ItemParsers.extractShelves(contents, shelvesList)

            // Isolate biography parsing so failures do not discard other shelves.
            try {
                for (i in 0 until contents.length()) {
                    val section = contents.optJSONObject(i) ?: continue
                    val descShelf = section.optJSONObject("musicDescriptionShelfRenderer") ?: continue

                    val headerObj = descShelf.optJSONObject("header")
                    val headerRuns = headerObj?.optJSONArray("runs")
                        ?: headerObj?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title")?.optJSONArray("runs")
                        ?: descShelf.optJSONObject("title")?.optJSONArray("runs")
                    val rawHeader = com.hypermusic.core.extractor.common.JsonUtils.runsToText(headerRuns).trim()

                    val descriptionRuns = descShelf.optJSONObject("description")?.optJSONArray("runs")
                    val runs = com.hypermusic.core.extractor.common.JsonUtils.runsToTextRuns(descriptionRuns)
                    val descriptionText = runs.joinToString("") { it.text }.trim()

                    if (descriptionText.isNotEmpty()) {
                        val shelfTitle = rawHeader.ifEmpty { "About the Artist" }
                        shelvesList.add(
                            BrowseShelf(
                                title = shelfTitle,
                                type = "artist_bio",
                                items = emptyArray(),
                                description = descriptionText,
                                descriptionRuns = runs,
                                footer = null
                            )
                        )
                    }
                }
            } catch (descEx: Exception) {
                Logger.w("RelatedParser: Non-fatal error while parsing description shelves", descEx)
            }

            Logger.i("RelatedParser: Successfully extracted ${shelvesList.size} related shelves.")
            return shelvesList.toTypedArray()
        } catch (e: Exception) {
            Logger.e("RelatedParser: Failed to parse related content", e)
            throw ParsingException("Failed to parse related content response", cause = e)
        }
    }
}
