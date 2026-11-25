package com.hypermusic.core.player

import android.net.Uri
import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.domain.StreamDescriptor
import com.hypermusic.core.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

data class CachedStreamUrl(
    val source: StreamDescriptor,
    val expiresAtMs: Long,
    val cachedAtMs: Long = System.currentTimeMillis()
)

/** LRU cache keyed by video ID, extraction type and quality, with CDN URL expiry handling. */
object StreamUrlCache {
    private const val MAX_ENTRIES = 200
    private const val DEFAULT_TTL_MS = 5 * 60 * 60 * 1000L // 5 hours fallback

    private val lock = Any()
    private val epoch = java.util.concurrent.atomic.AtomicLong()
    val generation: Long get() = epoch.get()
    private val lruMap = object : LinkedHashMap<String, CachedStreamUrl>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStreamUrl>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private fun buildKey(videoId: String, extractionType: String, quality: String): String =
        "${videoId}_${extractionType}_${quality}"

    fun put(videoId: String, extractionType: String, quality: String = "normal", source: StreamDescriptor, expectedGeneration: Long = generation) {
        val expiresAtMs = parseExpiry(source.url)
        val entry = CachedStreamUrl(source, expiresAtMs)
        synchronized(lock) {
            if (epoch.get() == expectedGeneration) lruMap[buildKey(videoId, extractionType, quality)] = entry
        }
    }

    fun get(videoId: String, extractionType: String, quality: String = "normal"): StreamDescriptor? {
        synchronized(lock) {
            val key = buildKey(videoId, extractionType, quality)
            val entry = lruMap[key] ?: return null
            if (System.currentTimeMillis() >= entry.expiresAtMs) {
                lruMap.remove(key)
                return null
            }
            return entry.source
        }
    }

    fun remove(videoId: String, extractionType: String? = null) {
        synchronized(lock) {
            epoch.incrementAndGet()
            val prefix = if (extractionType != null) "${videoId}_${extractionType}_" else "${videoId}_"
            val keysToRemove = lruMap.keys.filter { it.startsWith(prefix) || it == "${videoId}_$extractionType" }
            keysToRemove.forEach { lruMap.remove(it) }
        }
    }

    fun clear() {
        synchronized(lock) {
            epoch.incrementAndGet()
            lruMap.clear()
        }
    }

    private fun parseExpiry(url: String): Long {
        return try {
            val uri = Uri.parse(url)
            val expireSec = uri.getQueryParameter("expire")?.toLongOrNull()
            if (expireSec != null && expireSec > 0) {
                // 10 minutes safety buffer before actual CDN expiry
                (expireSec * 1000L) - (10 * 60 * 1000L)
            } else {
                System.currentTimeMillis() + DEFAULT_TTL_MS
            }
        } catch (_: Exception) {
            System.currentTimeMillis() + DEFAULT_TTL_MS
        }
    }
}

/** One extraction per key. Queued background work can be promoted without duplicating it. */
object StreamResolutionCoordinator {
    private enum class Lane(val limit: Int) { FOREGROUND(2), DOWNLOAD(1), SPECULATIVE(1) }
    private class Flight(var priority: Lane) {
        val result = CompletableDeferred<Result<StreamDescriptor>>()
        val admitted = CompletableDeferred<Unit>()
        var subscribers = 0
        var assigned: Lane? = null
        lateinit var job: kotlinx.coroutines.Job
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private val inFlight = mutableMapOf<String, Flight>()
    private val waiting = mutableListOf<Flight>()
    private val active = Lane.entries.associateWith { 0 }.toMutableMap()

    /** Called under lock. An assigned flight keeps its slot until its operation terminates. */
    private fun dispatch() {
        for (lane in Lane.entries) {
            while (active.getValue(lane) < lane.limit) {
                val next = waiting.firstOrNull { it.priority == lane && !it.job.isCancelled && it.subscribers > 0 } ?: break
                waiting.remove(next)
                next.assigned = lane
                active[lane] = active.getValue(lane) + 1
                next.admitted.complete(Unit)
            }
        }
    }

    suspend fun resolveStreamUrl(videoId: String, quality: String, extractionType: String,
        musicService: MusicService, prefetch: Boolean = false): Result<String> =
        resolveStreamSource(videoId, quality, extractionType, musicService, prefetch).map { it.url }

    suspend fun resolveStreamSource(videoId: String, quality: String, extractionType: String,
        musicService: MusicService, prefetch: Boolean = false, backgroundDownload: Boolean = false): Result<StreamDescriptor> {
        val epoch = StreamUrlCache.generation
        val key = "$videoId|$extractionType|$quality|$epoch"
        val priority = if (backgroundDownload) Lane.DOWNLOAD else if (prefetch) Lane.SPECULATIVE else Lane.FOREGROUND
        val flight = synchronized(lock) {
            StreamUrlCache.get(videoId, extractionType, quality)?.let {
                return Result.success(it)
            }
            val shared = inFlight[key] ?: Flight(priority).also { created ->
                inFlight[key] = created
                created.job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    try {
                        val result = withTimeout(15_000) {
                            synchronized(lock) { waiting += created; dispatch() }
                            created.admitted.await()
                            StreamUrlCache.get(videoId, extractionType, quality)?.let { Result.success(it) }
                                ?: musicService.getStreamSource(videoId, quality, extractionType)
                        }
                        result.onSuccess {
                            StreamUrlCache.put(videoId, extractionType, quality, it, epoch)
                        }
                        created.result.complete(result)
                    } catch (e: TimeoutCancellationException) {
                        created.result.complete(Result.failure(java.net.SocketTimeoutException("Stream resolution exceeded 15 seconds").apply { initCause(e) }))
                    } catch (e: CancellationException) {
                        created.result.complete(Result.failure(e))
                        throw e
                    } catch (e: Exception) { created.result.complete(Result.failure(e)) }
                }
                // Also runs for cancellation before the lazy coroutine enters its body.
                created.job.invokeOnCompletion { cause ->
                    if (!created.result.isCompleted) created.result.complete(Result.failure(cause ?: CancellationException("Resolution stopped")))
                    synchronized(lock) {
                        waiting.remove(created)
                        created.assigned?.let { active[it] = active.getValue(it) - 1 }
                        if (inFlight[key] === created) inFlight.remove(key)
                        dispatch()
                    }
                }
            }
            shared.subscribers++
            if (priority == Lane.FOREGROUND && shared.assigned == null && shared.priority != Lane.FOREGROUND) {
                shared.priority = Lane.FOREGROUND
                dispatch()
            }
            shared
        }
        flight.job.start()
        try {
            return withTimeout(15_000) { flight.result.await() }
        } catch (e: TimeoutCancellationException) {
            return Result.failure(java.net.SocketTimeoutException("Stream resolution exceeded 15 seconds").apply { initCause(e) })
        } finally {
            synchronized(lock) {
                flight.subscribers--
                if (flight.subscribers == 0 && !flight.result.isCompleted) {
                    if (inFlight[key] === flight) inFlight.remove(key)
                    flight.job.cancel()
                }
            }
        }
    }
}
