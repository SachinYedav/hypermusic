package com.hypermusic.core.extractor.parsers

import com.hypermusic.core.extractor.common.ItemParsers
import com.hypermusic.core.extractor.errors.ParsingException
import com.hypermusic.core.extractor.models.BrowseShelf
import com.hypermusic.core.extractor.models.FilterChip
import com.hypermusic.core.extractor.models.HomeFeed
import com.hypermusic.core.utils.Logger
import org.json.JSONObject

/** Parses home shelves, filter chips and continuation metadata. */
object HomeParser {

    @Throws(ParsingException::class)
    fun parseHomeFeed(jsonString: String): HomeFeed {
        val shelvesList = mutableListOf<BrowseShelf>()

        try {
            val root = JSONObject(jsonString)

            var sectionListRenderer = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")

            if (sectionListRenderer == null) {
                sectionListRenderer = root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
            }

            var chipsArray: Array<FilterChip>? = null
            if (sectionListRenderer != null) {
                val header = sectionListRenderer.optJSONObject("header")
                if (header != null) {
                    val rawChips = header.optJSONObject("chipCloudRenderer")?.optJSONArray("chips")
                    if (rawChips != null) {
                        val parsedChips = mutableListOf<FilterChip>()
                        for (i in 0 until rawChips.length()) {
                            val chipRenderer = rawChips.optJSONObject(i)?.optJSONObject("chipCloudChipRenderer") ?: continue
                            val text = chipRenderer.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                            val params = chipRenderer.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")
                                ?.optString("params") ?: ""

                            if (text.isNotEmpty() && params.isNotEmpty()) {
                                parsedChips.add(FilterChip(text, params))
                            }
                        }
                        if (parsedChips.isNotEmpty()) {
                            chipsArray = parsedChips.toTypedArray()
                        }
                    }
                }

                val contents = sectionListRenderer.optJSONArray("contents")
                if (contents != null) {
                    ItemParsers.extractShelves(contents, shelvesList)
                }

                val continuations = sectionListRenderer.optJSONArray("continuations")
                val nextContinuationData = continuations?.optJSONObject(0)?.optJSONObject("nextContinuationData")
                val continuationToken = nextContinuationData?.optString("continuation")
                val visitorData = root.optJSONObject("responseContext")?.optString("visitorData")

                return HomeFeed(
                    shelves = shelvesList.toTypedArray(),
                    chips = chipsArray,
                    continuationToken = if (continuationToken.isNullOrEmpty()) null else continuationToken,
                    visitorData = if (visitorData.isNullOrEmpty()) null else visitorData
                )
            } else {
                Logger.e("HomeParser: Could not find sectionListRenderer in Home Feed response. Keys: ${root.keys().asSequence().joinToString()}")
            }

            return HomeFeed(shelves = shelvesList.toTypedArray(), chips = chipsArray)
        } catch (e: Exception) {
            Logger.e("HomeParser: Failed to parse Home Feed", e)
            throw ParsingException("Failed to parse Home Feed", cause = e)
        }
    }

    /** Returns continuation shelves and the next token; parsing failures throw [ParsingException]. */
    @Throws(ParsingException::class)
    fun parseContinuationFeed(jsonString: String): Pair<Array<BrowseShelf>, String?> {
        val shelvesList = mutableListOf<BrowseShelf>()
        var nextContinuation: String? = null
        try {
            val root = JSONObject(jsonString)
            val continuationContents = root.optJSONObject("continuationContents")
            val sectionListContinuation = continuationContents?.optJSONObject("sectionListContinuation")
            if (sectionListContinuation != null) {
                val contents = sectionListContinuation.optJSONArray("contents")
                if (contents != null) {
                    ItemParsers.extractShelves(contents, shelvesList)
                }
                val continuations = sectionListContinuation.optJSONArray("continuations")
                val nextToken = continuations?.optJSONObject(0)
                    ?.optJSONObject("nextContinuationData")
                    ?.optString("continuation")
                if (!nextToken.isNullOrEmpty()) {
                    nextContinuation = nextToken
                }
            }
            return Pair(shelvesList.toTypedArray(), nextContinuation)
        } catch (e: Exception) {
            Logger.e("HomeParser: Failed to parse Continuation Feed", e)
            throw ParsingException("Failed to parse Continuation Feed", cause = e)
        }
    }
}
