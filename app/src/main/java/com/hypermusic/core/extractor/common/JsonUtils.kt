package com.hypermusic.core.extractor.common

import com.hypermusic.core.extractor.models.ExtractedTextRun
import org.json.JSONArray
import org.json.JSONObject

/** Shared text, artwork and navigation helpers for InnerTube responses. */
object JsonUtils {

    /**
     * Preserves highest-resolution candidate URL from InnerTube without speculative parameter alteration.
     * Viewport-specific sizing is applied deterministically by ArtworkUrlResolver at display time.
     */
    fun extractArtworkFromThumbnails(thumbnails: JSONArray?): String {
        if (thumbnails == null || thumbnails.length() == 0) return ""
        var bestUrl: String? = null
        var maxArea = -1
        for (i in 0 until thumbnails.length()) {
            val obj = thumbnails.optJSONObject(i) ?: continue
            val width = obj.optInt("width", 0)
            val height = obj.optInt("height", 0)
            val area = width * height
            val url = obj.optString("url")
            if (area >= maxArea && !url.isNullOrEmpty()) {
                maxArea = area
                bestUrl = url
            }
        }
        if (bestUrl.isNullOrEmpty()) {
            val lastObj = thumbnails.optJSONObject(thumbnails.length() - 1)
            bestUrl = lastObj?.optString("url")
        }
        val candidate = bestUrl?.let { if (it.startsWith("//")) "https:$it" else it } ?: ""
        return candidate
    }

    /** Resolves high-resolution presentation URL using the central ArtworkUrlResolver. */
    fun getHighResArtworkUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        return com.hypermusic.core.utils.ArtworkUrlResolver.resolveUrl(url, com.hypermusic.core.utils.ArtworkQuality.HERO) ?: url
    }

    /** Reads text and endpoint URLs, expanding truncated URL display text when available. */
    fun runsToTextRuns(runsJson: JSONArray?): List<ExtractedTextRun> {
        if (runsJson == null || runsJson.length() == 0) return emptyList()
        val list = mutableListOf<ExtractedTextRun>()
        for (i in 0 until runsJson.length()) {
            val run = runsJson.optJSONObject(i) ?: continue
            val rawText = run.optString("text", "")
            if (rawText.isEmpty()) continue

            val navEndpoint = run.optJSONObject("navigationEndpoint")
            var url = navEndpoint?.optJSONObject("urlEndpoint")?.optString("url")
            if (url.isNullOrEmpty()) {
                url = navEndpoint?.optJSONObject("webNavigationEndpointData")?.optString("url")
            }
            if (url.isNullOrEmpty() && (rawText.startsWith("http://") || rawText.startsWith("https://"))) {
                url = rawText
            }

            val displayText = if (!url.isNullOrEmpty() && (rawText.endsWith("...") || rawText.endsWith("…"))) {
                url
            } else {
                rawText
            }

            list.add(ExtractedTextRun(text = displayText, url = if (!url.isNullOrEmpty()) url else null))
        }
        return list
    }

    /** Concatenates text from an array of runs. */
    fun runsToText(runsJson: JSONArray?): String {
        val runs = runsToTextRuns(runsJson)
        if (runs.isEmpty()) return ""
        val sb = StringBuilder()
        for (run in runs) {
            sb.append(run.text)
        }
        return sb.toString()
    }

    /** Reads runs or simpleText from a text container. */
    fun getTextFromContainer(container: JSONObject?, fieldName: String = "title"): String {
        if (container == null) return ""
        val target = container.optJSONObject(fieldName) ?: container
        val runs = target.optJSONArray("runs")
        if (runs != null) return runsToText(runs)
        return target.optString("simpleText", "")
    }

    fun isExplicit(renderer: JSONObject): Boolean {
        val badges = renderer.optJSONArray("badges")
        if (badges != null) {
            for (i in 0 until badges.length()) {
                val badge = badges.optJSONObject(i)?.optJSONObject("musicInlineBadgeRenderer")
                val iconType = badge?.optJSONObject("icon")?.optString("iconType")
                if (iconType == "MUSIC_EXPLICIT_BADGE") return true
            }
        }
        val subtitleBadges = renderer.optJSONArray("subtitleBadges")
        if (subtitleBadges != null) {
            for (i in 0 until subtitleBadges.length()) {
                val badge = subtitleBadges.optJSONObject(i)?.optJSONObject("musicInlineBadgeRenderer")
                val iconType = badge?.optJSONObject("icon")?.optString("iconType")
                if (iconType == "MUSIC_EXPLICIT_BADGE") return true
            }
        }
        return false
    }

    /** Finds an MPREb album menu destination; returns (null, albumId), or null if absent. */
    fun extractAlbumFromMenu(menuObj: JSONObject?): Pair<String?, String?>? {
        if (menuObj == null) return null
        val items = menuObj.optJSONObject("menuRenderer")?.optJSONArray("items")
            ?: menuObj.optJSONArray("items")
            ?: return null

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val navItem = item.optJSONObject("menuNavigationItemRenderer") ?: continue
            val endpoint = navItem.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            val browseId = endpoint?.optString("browseId")
            if (!browseId.isNullOrEmpty() && browseId.startsWith("MPREb")) {
                val label = runsToText(navItem.optJSONObject("text")?.optJSONArray("runs"))
                return Pair(null, browseId)
            }
        }
        return null
    }
}
