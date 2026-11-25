@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.hypermusic.core.player

import android.content.Context
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Preloads the first 512 KiB of the service-selected upcoming remote track. */
object BytePreloader {
    private const val PRELOAD_BYTES = 512 * 1024L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handover = Mutex()
    private var job: Job? = null
    private var key: String? = null
    private var maintenance = 0

    @Synchronized fun preload(context: Context, streamUrl: String) {
        if (maintenance > 0) return
        val source = ResolvedSource.remote(streamUrl)
        val cacheKey = source.cacheKey ?: return
        val cache = HyperCache.getInstance(context)
        if (cache.isCached(cacheKey, 0L, PRELOAD_BYTES)) return

        if (key == cacheKey && job?.isActive == true) return
        cancel()
        key = cacheKey
        job = scope.launch {
            try {
                handover.withLock {
                    ensureActive()
                    val data = HyperDataSource.cacheFactory(context.applicationContext).createDataSource()
                    try {
                        // Bound manual reads to PRELOAD_BYTES so optional preloading stays within its byte budget.
                        data.open(DataSpec.Builder().setUri(source.uri).setKey(source.cacheKey).setLength(PRELOAD_BYTES).build())
                        var remaining = PRELOAD_BYTES
                        val buffer = ByteArray(32 * 1024)
                        while (remaining > 0) {
                            ensureActive()
                            val count = data.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (count < 0) break
                            remaining -= count
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: java.io.IOException) { /* Foreground playback remains authoritative. */ }
                    finally { data.close() }
                }
            } finally {
                synchronized(this@BytePreloader) {
                    if (key == cacheKey) {
                        key = null
                        job = null
                    }
                }
            }
        }
    }

    @Synchronized fun cancel() {
        job?.cancel()
        job = null
        key = null
        // Cancellation does not wait for a read; the handover mutex serializes subsequent writers.
    }

    internal suspend fun awaitIdle() { synchronized(this) { job }?.join() }

    /** Stops admission and waits for every old writer's finally/close before maintenance. */
    suspend fun <T> withSuspendedWrites(action: suspend () -> T): T {
        synchronized(this) { maintenance++; cancel() }
        return try { handover.withLock { action() } }
        finally { synchronized(this) { maintenance-- } }
    }
}
