@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.hypermusic.core.player

import android.content.Context
import androidx.core.util.AtomicFile
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Process- and disk-persistent lookup for complete, contiguous media resources in SimpleCache. */
object CompleteCopyLookup {

    data class Entry(
        val cacheKey: String,
        val totalLength: Long
    )

    private const val MAX_ENTRIES = 300
    private const val FILE_NAME = "complete_copies.json"

    private val lock = Any()

    @Volatile
    var generation: Long = 0L
        private set

    @Volatile
    private var isLoaded = false

    private val map = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    private fun buildKey(trackId: String, type: String, quality: String): String =
        "${trackId}|${type}|${quality}"

    fun loadFromDisk(context: Context) {
        synchronized(lock) {
            if (isLoaded) return
            isLoaded = true
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (!file.exists()) return
                val atomic = AtomicFile(file)
                val jsonStr = atomic.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(jsonStr)
                if (root.optInt("version", 1) != 1) return
                val entries = root.optJSONObject("entries") ?: return
                val keys = entries.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = entries.getJSONObject(k)
                    val cacheKey = obj.getString("cacheKey")
                    val totalLength = obj.getLong("totalLength")
                    if (totalLength > 0 && cacheKey.isNotBlank()) {
                        map[k] = Entry(cacheKey, totalLength)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun persistLocked(context: Context) {
        try {
            val root = JSONObject()
            root.put("version", 1)
            val entries = JSONObject()
            for ((k, entry) in map) {
                val obj = JSONObject()
                obj.put("cacheKey", entry.cacheKey)
                obj.put("totalLength", entry.totalLength)
                entries.put(k, obj)
            }
            root.put("entries", entries)

            val file = File(context.filesDir, FILE_NAME)
            val atomic = AtomicFile(file)
            var stream: FileOutputStream? = null
            try {
                stream = atomic.startWrite()
                stream.write(root.toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(stream)
            } catch (e: Exception) {
                if (stream != null) atomic.failWrite(stream)
            }
        } catch (_: Exception) {}
    }

    /** Registers a complete copy only after contiguous cache coverage and generation checks pass. */
    fun register(
        trackId: String, type: String, quality: String,
        cacheKey: String, totalLength: Long, context: Context,
        expectedGen: Long
    ) {
        if (totalLength <= 0 && cacheKey.isBlank()) return
        ensureLoaded(context)
        val cache = HyperCache.getInstance(context)
        val metaLen = ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey))
        val targetLen = if (metaLen > 0L) metaLen else totalLength
        if (targetLen <= 0L) return
        if (!cache.isCached(cacheKey, 0L, targetLen)) return

        synchronized(lock) {
            if (generation != expectedGen) return
            try {
                val mutations = ContentMetadataMutations()
                ContentMetadataMutations.setContentLength(mutations, targetLen)
                cache.applyContentMetadataMutations(cacheKey, mutations)
            } catch (_: Exception) {}
            map[buildKey(trackId, type, quality)] = Entry(cacheKey, targetLen)
            persistLocked(context)
        }
    }

    /** Verifies and returns the cached resource entry if full byte coverage is present. */
    fun verify(trackId: String, type: String, quality: String, context: Context): Entry? {
        ensureLoaded(context)
        val entry = synchronized(lock) { map[buildKey(trackId, type, quality)] } ?: return null
        val cache = HyperCache.getInstance(context)
        if (!cache.isCached(entry.cacheKey, 0L, entry.totalLength)) {
            invalidate(trackId, type, quality, context)
            return null
        }
        return entry
    }

    fun invalidate(trackId: String, type: String? = null, quality: String? = null, context: Context? = null) {
        synchronized(lock) {
            generation++
            if (type != null && quality != null) {
                map.remove(buildKey(trackId, type, quality))
            } else {
                val prefix = "${trackId}|"
                map.keys.removeAll { it.startsWith(prefix) }
            }
            if (context != null) {
                persistLocked(context)
            }
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            generation++
            map.clear()
            try {
                AtomicFile(File(context.filesDir, FILE_NAME)).delete()
            } catch (_: Exception) {}
        }
    }

    private fun ensureLoaded(context: Context) {
        if (!isLoaded) {
            loadFromDisk(context)
        }
    }
}
