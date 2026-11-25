package com.hypermusic.core.lyrics

import android.content.Context
import androidx.core.util.AtomicFile
import com.hypermusic.core.model.LyricLine
import com.hypermusic.core.model.Lyrics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class LyricsDiskCache(private val context: Context) {
    companion object {
        private val epoch = java.util.concurrent.atomic.AtomicLong()
        val generation: Long get() = epoch.get()
    }

    private val cacheDir = File(context.filesDir, "lyrics_cache")
    private val lock = Any()

    private fun getFileForTrack(trackId: String): File {
        val safeId = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(cacheDir, "$safeId.json")
    }

    fun save(trackId: String, lyrics: Lyrics) {
        if (trackId.isBlank() || lyrics.text.isBlank()) return
        synchronized(lock) {
            try {
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val targetFile = getFileForTrack(trackId)
                val atomicFile = AtomicFile(targetFile)
                val root = JSONObject()
                root.put("trackId", trackId)
                root.put("text", lyrics.text)
                lyrics.source?.let { root.put("source", it) }
                root.put("isSynced", lyrics.isSynced)
                lyrics.translatedPlainText?.let { root.put("translatedPlainText", it) }
                lyrics.translatedLang?.let { root.put("translatedLang", it) }

                if (lyrics.lines.isNotEmpty()) {
                    val linesArray = JSONArray()
                    for (line in lyrics.lines) {
                        val lineObj = JSONObject()
                        lineObj.put("timeMs", line.timeMs)
                        lineObj.put("text", line.text)
                        line.translatedText?.let { lineObj.put("translatedText", it) }
                        linesArray.put(lineObj)
                    }
                    root.put("lines", linesArray)
                }

                val content = root.toString()
                var stream: FileOutputStream? = null
                try {
                    stream = atomicFile.startWrite()
                    stream.write(content.toByteArray(Charsets.UTF_8))
                    atomicFile.finishWrite(stream)
                } catch (e: Exception) {
                    if (stream != null) {
                        atomicFile.failWrite(stream)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun load(trackId: String): Lyrics? {
        if (trackId.isBlank()) return null
        synchronized(lock) {
            try {
                val targetFile = getFileForTrack(trackId)
                if (!targetFile.exists()) return null
                val atomicFile = AtomicFile(targetFile)

                val content = atomicFile.openRead().use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                val root = JSONObject(content)
                val text = root.optString("text", "")
                if (text.isBlank()) return null

                val source = if (root.has("source")) root.optString("source") else null
                val isSynced = root.optBoolean("isSynced", false)
                val translatedPlainText = if (root.has("translatedPlainText")) root.optString("translatedPlainText").ifEmpty { null } else null
                val translatedLang = if (root.has("translatedLang")) root.optString("translatedLang").ifEmpty { null } else null

                val lines = mutableListOf<LyricLine>()
                if (root.has("lines")) {
                    val linesArray = root.getJSONArray("lines")
                    for (i in 0 until linesArray.length()) {
                        val lineObj = linesArray.optJSONObject(i) ?: continue
                        val timeMs = lineObj.optLong("timeMs", 0L)
                        val lineText = lineObj.optString("text", "")
                        val translatedText = if (lineObj.has("translatedText")) lineObj.optString("translatedText").ifEmpty { null } else null
                        lines.add(LyricLine(timeMs = timeMs, text = lineText, translatedText = translatedText))
                    }
                }

                return Lyrics(
                    text = text,
                    source = source,
                    lines = lines,
                    isSynced = isSynced && lines.isNotEmpty(),
                    translatedPlainText = translatedPlainText,
                    translatedLang = translatedLang
                )
            } catch (_: Exception) {
                return null
            }
        }
    }

    fun delete(trackId: String) {
        epoch.incrementAndGet()
        if (trackId.isBlank()) return
        synchronized(lock) {
            try {
                val targetFile = getFileForTrack(trackId)
                if (targetFile.exists()) {
                    AtomicFile(targetFile).delete()
                }
            } catch (_: Exception) {}
        }
    }

    fun clearNonDownloaded(downloadedTrackIds: Set<String>) {
        epoch.incrementAndGet()
        synchronized(lock) {
            try {
                val safeDownloadedIds = downloadedTrackIds.map {
                    it.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                }.toSet()

                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".json")) {
                        val fileId = file.name.removeSuffix(".json")
                        val isSafeIdMatch = fileId in safeDownloadedIds || fileId in downloadedTrackIds
                        if (!isSafeIdMatch) {
                            var isInternalIdMatch = false
                            try {
                                val content = file.readText()
                                val root = JSONObject(content)
                                val internalId = root.optString("trackId", "")
                                if (internalId.isNotEmpty() && internalId in downloadedTrackIds) {
                                    isInternalIdMatch = true
                                }
                            } catch (_: Exception) {}

                            if (!isInternalIdMatch) {
                                file.delete()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun clearAll() {
        epoch.incrementAndGet()
        synchronized(lock) {
            try {
                cacheDir.listFiles()?.forEach { file ->
                    file.delete()
                }
            } catch (_: Exception) {}
        }
    }
}
