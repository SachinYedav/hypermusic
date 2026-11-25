package com.hypermusic.core.extractor.parsers

import com.hypermusic.core.extractor.common.JsonUtils
import com.hypermusic.core.extractor.errors.ParsingException
import com.hypermusic.core.extractor.models.ExtractedLyrics
import com.hypermusic.core.utils.Logger
import org.json.JSONObject

object LyricsParser {

    /** Reads text and optional attribution from description shelves or timed lines; returns null for missing content. */
    @Throws(ParsingException::class)
    fun parseLyrics(jsonString: String): ExtractedLyrics? {
        try {
            val root = JSONObject(jsonString)
            val contents = root.optJSONObject("contents") ?: return null

            val sectionListContents = contents.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            if (sectionListContents != null) {
                for (i in 0 until sectionListContents.length()) {
                    val section = sectionListContents.optJSONObject(i) ?: continue
                    val shelf = section.optJSONObject("musicDescriptionShelfRenderer") ?: continue

                    val descriptionRuns = shelf.optJSONObject("description")?.optJSONArray("runs")
                    val footerRuns = shelf.optJSONObject("footer")?.optJSONArray("runs")

                    val lyricsText = JsonUtils.runsToText(descriptionRuns)
                    val sourceText = JsonUtils.runsToText(footerRuns).ifEmpty { null }

                    if (lyricsText.isNotBlank()) {
                        return ExtractedLyrics(
                            text = lyricsText,
                            source = sourceText
                        )
                    }
                }
            }

            // Timed-lyrics layout; flatten lyric lines to plain text.
            val elementRenderer = contents.optJSONObject("elementRenderer")
            if (elementRenderer != null) {
                val timedLyricsModel = elementRenderer.optJSONObject("newElement")
                    ?.optJSONObject("type")
                    ?.optJSONObject("componentType")
                    ?.optJSONObject("model")
                    ?.optJSONObject("timedLyricsModel")
                val lyricsData = timedLyricsModel?.optJSONObject("lyricsData")
                val timedLines = lyricsData?.optJSONArray("timedLyricsData")
                if (timedLines != null && timedLines.length() > 0) {
                    val lineList = mutableListOf<String>()
                    for (i in 0 until timedLines.length()) {
                        val lineObj = timedLines.optJSONObject(i) ?: continue
                        val text = lineObj.optString("lyricLine").trim()
                        if (text.isNotEmpty()) {
                            lineList.add(text)
                        }
                    }
                    val fullText = lineList.joinToString("\n")
                    val sourceText = lyricsData.optString("sourceMessage").ifEmpty { null }
                    if (fullText.isNotBlank()) {
                        return ExtractedLyrics(
                            text = fullText,
                            source = sourceText
                        )
                    }
                }
            }

            return null
        } catch (e: Exception) {
            Logger.e("LyricsParser: Failed to parse lyrics", e)
            throw ParsingException("Failed to parse lyrics response", cause = e)
        }
    }
}
