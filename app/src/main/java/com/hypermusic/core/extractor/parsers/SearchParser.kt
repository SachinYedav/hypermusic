package com.hypermusic.core.extractor.parsers

import com.hypermusic.core.extractor.common.ItemParsers
import com.hypermusic.core.extractor.common.JsonUtils
import com.hypermusic.core.extractor.errors.ParsingException
import com.hypermusic.core.extractor.models.BrowseItem
import com.hypermusic.core.extractor.models.ExtractedSearchSuggestions
import com.hypermusic.core.utils.Logger
import org.json.JSONArray
import org.json.JSONObject

/** Parses autocomplete suggestions and search result layouts. */
object SearchParser {

    @Throws(ParsingException::class)
    fun parseSearchSuggestions(jsonString: String): ExtractedSearchSuggestions {
        val suggestions = mutableListOf<String>()
        val richItems = mutableListOf<BrowseItem>()
        try {
            val root = JSONObject(jsonString)
            val contentsArray = root.optJSONArray("contents")

            if (contentsArray != null) {
                for (cIndex in 0 until contentsArray.length()) {
                    val section = contentsArray.optJSONObject(cIndex)
                        ?.optJSONObject("searchSuggestionsSectionRenderer")
                        ?: continue
                    val items = section.optJSONArray("contents") ?: continue

                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue

                        val runs = item.optJSONObject("searchSuggestionRenderer")?.optJSONObject("suggestion")?.optJSONArray("runs")
                            ?: item.optJSONObject("historySuggestionRenderer")?.optJSONObject("suggestion")?.optJSONArray("runs")

                        if (runs != null) {
                            val text = JsonUtils.runsToText(runs)
                            if (text.isNotBlank()) {
                                suggestions.add(text)
                            }
                            continue
                        }

                        if (item.has("musicResponsiveListItemRenderer")) {
                            val parsed = ItemParsers.parseSearchItem(item)
                            if (parsed != null) {
                                richItems.add(parsed)
                            }
                        }
                    }
                }
            }
            return ExtractedSearchSuggestions(
                queries = suggestions.toTypedArray(),
                richItems = richItems.toTypedArray()
            )
        } catch (e: Exception) {
            Logger.e("SearchParser: Failed to parse search suggestions", e)
            throw ParsingException("Failed to parse search suggestions", cause = e)
        }
    }

    @Throws(ParsingException::class)
    fun parseSearchResponse(jsonString: String): Array<BrowseItem> {
        val items = mutableListOf<BrowseItem>()
        try {
            val root = JSONObject(jsonString)

            var contents = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (contents == null) {
                contents = root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnSearchResultsRenderer")
                    ?.optJSONObject("primaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
            }

            if (contents == null) {
                contents = root.optJSONObject("contents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
            }

            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val section = contents.optJSONObject(i) ?: continue

                    val musicShelf = section.optJSONObject("musicShelfRenderer")
                    if (musicShelf != null) {
                        val shelfContents = musicShelf.optJSONArray("contents")
                        if (shelfContents != null) {
                            extractSearchItems(shelfContents, items)
                        }
                    }

                    val itemSection = section.optJSONObject("itemSectionRenderer")
                    if (itemSection != null) {
                        val sectionContents = itemSection.optJSONArray("contents")
                        if (sectionContents != null) {
                            extractSearchItems(sectionContents, items)
                        }
                    }

                    val cardShelf = section.optJSONObject("musicCardShelfRenderer")
                    if (cardShelf != null) {
                        ItemParsers.parseMusicCardShelf(cardShelf)?.let { items.add(0, it) }
                    }
                }
            }

            Logger.i("SearchParser: Successfully extracted ${items.size} search items.")
            return items.toTypedArray()
        } catch (e: Exception) {
            Logger.e("SearchParser: Failed to parse search response JSON", e)
            throw ParsingException("Failed to parse search response JSON", cause = e)
        }
    }

    private fun extractSearchItems(contents: JSONArray, items: MutableList<BrowseItem>) {
        for (i in 0 until contents.length()) {
            val item = contents.optJSONObject(i) ?: continue
            ItemParsers.parseSearchItem(item)?.let { items.add(it) }
        }
    }
}
