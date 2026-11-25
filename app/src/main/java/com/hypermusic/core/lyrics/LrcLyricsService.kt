package com.hypermusic.core.lyrics

import com.hypermusic.BuildConfig
import com.hypermusic.core.model.LyricLine
import com.hypermusic.core.model.Lyrics
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Service for fetching synchronized (karaoke-style) and plain lyrics from LRCLIB.
 */
object LrcLyricsService {

    private const val USER_AGENT = "HyperMusic/${BuildConfig.VERSION_NAME} (${BuildConfig.PROJECT_REPOSITORY_URL})"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val lrcRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\](.*)""")

    fun fetchLyrics(
        title: String,
        artist: String,
        durationSeconds: Long? = null
    ): Lyrics? {
        val cleanTitle = cleanTitle(title, artist)
        val cleanArtist = cleanArtist(artist)

        // Step 1: Exact lookup via /api/get
        val exactLookup = runCatching { queryLrcLibGet(cleanTitle, cleanArtist, durationSeconds) }
        val exactResult = exactLookup.getOrNull()
        if (exactResult != null && exactResult.isSynced) {
            return exactResult
        }

        // Step 2: Fuzzy search fallback via /api/search
        val searchLookup = runCatching { queryLrcLibSearch(cleanTitle, cleanArtist, durationSeconds) }
        val searchResult = searchLookup.getOrNull()
        if (searchResult != null && searchResult.isSynced) {
            return searchResult
        }

        // Fallback to plain lyrics if synced lyrics were unavailable
        if (exactResult != null && exactResult.text.isNotBlank()) {
            return exactResult
        }
        if (searchResult != null && searchResult.text.isNotBlank()) {
            return searchResult
        }

        exactLookup.exceptionOrNull()?.let { throw it }
        searchLookup.exceptionOrNull()?.let { throw it }
        return null
    }

    private fun queryLrcLibGet(title: String, artist: String, durationSeconds: Long?): Lyrics? {
        val queryParams = mutableListOf<String>()
        queryParams.add("track_name=${URLEncoder.encode(title, "UTF-8")}")
        queryParams.add("artist_name=${URLEncoder.encode(artist, "UTF-8")}")
        if (durationSeconds != null && durationSeconds > 0) {
            queryParams.add("duration=$durationSeconds")
        }

        val url = "https://lrclib.net/api/get?" + queryParams.joinToString("&")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("LRCLIB HTTP ${response.code}")
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            parseLyricsJson(json)
        }
    }

    private fun queryLrcLibSearch(title: String, artist: String, durationSeconds: Long?): Lyrics? {
        val query = "$title $artist".trim()
        val url = "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("LRCLIB HTTP ${response.code}")
            val body = response.body?.string() ?: return null
            val array = JSONArray(body)

            var bestPlainCandidate: Lyrics? = null

            for (i in 0 until array.length()) {
                val candidate = array.optJSONObject(i) ?: continue
                if (candidate.optBoolean("instrumental", false)) continue

                val candidateDur = candidate.optDouble("duration", 0.0)
                if (durationSeconds != null && durationSeconds > 0 && candidateDur > 0) {
                    if (abs(candidateDur - durationSeconds) > 5.0) continue
                }

                val lyrics = parseLyricsJson(candidate) ?: continue
                if (lyrics.isSynced) {
                    return lyrics
                } else if (bestPlainCandidate == null) {
                    bestPlainCandidate = lyrics
                }
            }
            bestPlainCandidate
        }
    }

    private fun parseLyricsJson(json: JSONObject): Lyrics? {
        val syncedLyrics = json.optString("syncedLyrics", "").trim()
        val plainLyrics = json.optString("plainLyrics", "").trim()

        if (syncedLyrics.isNotEmpty()) {
            val lines = parseLrc(syncedLyrics)
            val rawText = if (plainLyrics.isNotEmpty()) plainLyrics else lines.joinToString("\n") { it.text }
            if (isValidLyricsContent(rawText, lines)) {
                return Lyrics(
                    text = rawText,
                    source = "LRCLIB",
                    lines = lines,
                    isSynced = true
                )
            }
        }

        if (plainLyrics.isNotEmpty() && isValidLyricsContent(plainLyrics, emptyList())) {
            return Lyrics(
                text = plainLyrics,
                source = "LRCLIB",
                lines = emptyList(),
                isSynced = false
            )
        }

        return null
    }

    fun isValidLyricsContent(text: String, lines: List<LyricLine>): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 25) return false
        val lower = trimmed.lowercase()
        if (lower == "probe" || lower == "test" || lower == "instrumental") return false
        if (lines.isNotEmpty() && lines.size < 3) return false
        return true
    }

    fun cleanTitle(rawTitle: String, artist: String? = null): String {
        var title = rawTitle.trim()

        // Strip leading "Artist - " prefix if present in the video title
        if (!artist.isNullOrBlank()) {
            val cleanArt = cleanArtist(artist)
            if (cleanArt.isNotEmpty() && title.startsWith("$cleanArt - ", ignoreCase = true)) {
                title = title.substring(cleanArt.length + 3).trim()
            } else if (title.startsWith("$artist - ", ignoreCase = true)) {
                title = title.substring(artist.length + 3).trim()
            }
        }

        val dashIndex = title.indexOf(" - ")
        if (dashIndex in 1..40 && !artist.isNullOrBlank()) {
            val prefix = title.substring(0, dashIndex).trim()
            if (artist.contains(prefix, ignoreCase = true) || prefix.contains(artist, ignoreCase = true)) {
                title = title.substring(dashIndex + 3).trim()
            }
        }

        // Strip common YouTube noise suffixes and brackets
        title = title
            .replace(Regex("""\s*[\(\[](official\s*(music\s*)?video|official\s*audio|lyric\s*video|lyrics?|audio|video|visualizer|remastered|remaster|hd|4k|hq)[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[\(\[](ft\.|feat\.)\s+[^\)\]]+[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*(ft\.|feat\.)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .trim()

        return title.ifEmpty { rawTitle.trim() }
    }

    fun cleanArtist(rawArtist: String): String {
        return rawArtist.split(",", "&", "•", " feat.", " ft.", "/")[0].trim()
    }

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        lrcContent.lines().forEach { line ->
            val trimmed = line.trim()
            lrcRegex.find(trimmed)?.let { match ->
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues[3]
                val ms = when (msStr.length) {
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timeMs = (min * 60 + sec) * 1000 + ms
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(timeMs = timeMs, text = text))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
