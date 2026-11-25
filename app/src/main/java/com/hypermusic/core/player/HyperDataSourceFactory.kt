@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.hypermusic.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.*
import com.hypermusic.core.data.repository.DownloadAvailability
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.domain.MusicService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Immutable resource for one loading attempt. URL renewal starts a new byte namespace. */
data class ResolvedSource(val uri: Uri, val cacheKey: String?, val local: Boolean,
    val localLength: Long? = null, val localModifiedAt: Long? = null,
    val representation: com.hypermusic.core.domain.StreamDescriptor? = null,
    val isCacheOnly: Boolean = false,
    val cachedLength: Long? = null) {
    companion object {
        fun localFile(file: File) = ResolvedSource(Uri.fromFile(file), null, true, file.length(), file.lastModified())
        fun cacheOnly(request: Uri, cacheKey: String, totalLength: Long) =
            ResolvedSource(request, cacheKey, false, isCacheOnly = true, cachedLength = totalLength)
        fun remote(url: String): ResolvedSource = ResolvedSource(Uri.parse(url), remoteKey(url), false)
        fun remote(source: com.hypermusic.core.domain.StreamDescriptor): ResolvedSource =
            ResolvedSource(Uri.parse(source.url), remoteKey(source.url), false, representation = source)
        fun remoteKey(url: String): String = "hyper:" + MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** Bounded lookup used only to repair the resource from a failed loading attempt. */
object SourceAttempts {
    private val sources = object : LinkedHashMap<String, ResolvedSource>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResolvedSource>?) = size > 200
    }
    @Synchronized fun remember(request: Uri, source: ResolvedSource) { sources[request.toString()] = source }
    @Synchronized fun find(request: Uri): ResolvedSource? = sources[request.toString()]
}

/** Prefers a valid download or complete cached copy before remote resolution; pins the selected resource per attempt. */
class HyperDataSource(
    private val context: Context,
    private val preferences: PreferencesManager,
    private val music: MusicService,
    private val downloads: DownloadAvailability
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var opened: DataSource? = null
    private var pinned: ResolvedSource? = null
    private var pinnedRequest: Uri? = null
    /** Set when this instance opened via a complete-copy cache-only path. */
    private var cacheOnly = false
    /** Track identity, type, effective quality for complete-copy registration. */
    private var openedTrackId: String? = null
    private var openedType: String? = null
    private var openedQuality: String? = null
    /** Content-length reported by the delegate on open. */
    private var openedContentLength: Long = -1L
    /** Generation at the time open was initiated. */
    private var openedGeneration: Long = 0L

    override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }

    override fun open(dataSpec: DataSpec): Long {
        val request = dataSpec.uri
        openedContentLength = -1L
        openedTrackId = null
        openedType = null
        openedQuality = null
        cacheOnly = false
        openedGeneration = CompleteCopyLookup.generation

        val source = if (request.scheme != "hyper") ResolvedSource(request, null, true) else {
            val id = request.host ?: throw IOException("Missing media identity")
            val type = request.pathSegments.firstOrNull() ?: "audio"
            val settings = runBlocking { preferences.userSettingsFlow.first() }
            val quality = if (settings.dataSaverEnabled) "data_saver" else settings.streamQuality

            openedTrackId = id
            openedType = type
            openedQuality = quality

            if (pinnedRequest != request || pinned == null) {
                val selected = try {
                    runBlocking {
                        val local = downloads.find(id).file
                        if (local != null) {
                            ResolvedSource.localFile(local)
                        } else {
                            val completeCopy = CompleteCopyLookup.verify(id, type, quality, context)
                            if (completeCopy != null) {
                                ResolvedSource.cacheOnly(request, completeCopy.cacheKey, completeCopy.totalLength)
                            } else {
                                val source = StreamResolutionCoordinator.resolveStreamSource(id, quality, type, music).getOrElse {
                                    throw IOException("Stream resolution failed", it)
                                }
                                ResolvedSource.remote(source)
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException("Stream resolution interrupted").apply { initCause(e) }
                }
                pinned = selected
                pinnedRequest = request
            } else if (pinned?.isCacheOnly == true) {
                val stillValid = CompleteCopyLookup.verify(id, type, quality, context)
                if (stillValid == null) {
                    val fallback = try {
                        runBlocking {
                            val source = StreamResolutionCoordinator.resolveStreamSource(id, quality, type, music).getOrElse {
                                throw IOException("Stream resolution failed", it)
                            }
                            ResolvedSource.remote(source)
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw java.io.InterruptedIOException("Stream resolution interrupted").apply { initCause(e) }
                    }
                    pinned = fallback
                    pinnedRequest = request
                }
            }
            pinned!!
        }

        cacheOnly = source.isCacheOnly

        if (source.localLength != null) {
            val file = File(source.uri.path ?: throw IOException("Local media path unavailable"))
            if (!file.isFile || file.length() != source.localLength || file.lastModified() != source.localModifiedAt) {
                throw java.io.FileNotFoundException("Selected local resource changed; a new playback attempt is required")
            }
        }
        val delegate = if (source.local) upstreamFactory(context).createDataSource()
            else if (cacheOnly) cacheOnlyFactory(context).createDataSource()
            else cacheFactory(context).createDataSource()
        SourceAttempts.remember(request, source)
        listeners.forEach(delegate::addTransferListener)
        opened = delegate
        try {
            val boundedLength = if (cacheOnly && source.cachedLength != null) {
                source.cachedLength - dataSpec.position
            } else -1L
            val targetSpec = if (cacheOnly && dataSpec.length == androidx.media3.common.C.LENGTH_UNSET.toLong() && boundedLength > 0L) {
                dataSpec.buildUpon().setUri(source.uri).setKey(source.cacheKey).setLength(boundedLength).build()
            } else {
                dataSpec.buildUpon().setUri(source.uri).setKey(source.cacheKey).build()
            }

            val length = delegate.open(targetSpec)
            if (dataSpec.position == 0L && length > 0L) {
                openedContentLength = length
            } else if (dataSpec.position > 0L && length > 0L && openedContentLength <= 0L) {
                openedContentLength = dataSpec.position + length
            } else {
                val metaLen = source.cacheKey?.let { ContentMetadata.getContentLength(HyperCache.getInstance(context).getContentMetadata(it)) } ?: -1L
                if (metaLen > 0L) openedContentLength = metaLen
            }
            return length
        } catch (e: Exception) {
            if (cacheOnly) {
                val tId = openedTrackId; val tType = openedType; val tQuality = openedQuality
                if (tId != null && tType != null && tQuality != null) {
                    CompleteCopyLookup.invalidate(tId, tType, tQuality, context)
                }
                pinned = null
                pinnedRequest = null
                cacheOnly = false
                runCatching { delegate.close() }
                opened = null
                throw Cache.CacheException("Cache-only open failed for track $tId", e)
            }
            runCatching { delegate.close() }
            opened = null
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        try {
            return opened?.read(buffer, offset, length) ?: -1
        } catch (e: Exception) {
            if (cacheOnly) {
                val tId = openedTrackId; val tType = openedType; val tQuality = openedQuality
                if (tId != null && tType != null && tQuality != null) {
                    CompleteCopyLookup.invalidate(tId, tType, tQuality, context)
                }
                pinned = null
                pinnedRequest = null
                cacheOnly = false
                runCatching { opened?.close() }
                opened = null
                throw Cache.CacheException("Cache-only read failed for track $tId", e)
            }
            throw e
        }
    }
    override fun getUri(): Uri? = opened?.uri ?: pinned?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = opened?.responseHeaders ?: emptyMap()
    override fun close() {
        try { opened?.close() }
        finally {
            if (!cacheOnly) {
                val tId = openedTrackId; val tType = openedType; val tQuality = openedQuality
                val key = pinned?.cacheKey
                if (tId != null && tType != null && tQuality != null && key != null) {
                    val metaLen = ContentMetadata.getContentLength(HyperCache.getInstance(context).getContentMetadata(key))
                    val finalLen = if (metaLen > 0L) metaLen else openedContentLength
                    if (finalLen > 0L) {
                        CompleteCopyLookup.register(tId, tType, tQuality, key, finalLen, context, openedGeneration)
                    }
                }
            }
            openedContentLength = -1L
            openedTrackId = null
            openedType = null
            openedQuality = null
            cacheOnly = false
            opened = null
        }
    }

    companion object {
        fun upstreamFactory(context: Context) = DefaultDataSource.Factory(context,
            DefaultHttpDataSource.Factory().setConnectTimeoutMs(8_000).setReadTimeoutMs(8_000)
                .setAllowCrossProtocolRedirects(false))
        fun cacheFactory(context: Context) = CacheDataSource.Factory()
            .setCache(HyperCache.getInstance(context))
            .setUpstreamDataSourceFactory(upstreamFactory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        /** Cache-only factory: no upstream, reads only from cached spans. */
        fun cacheOnlyFactory(context: Context) = CacheDataSource.Factory()
            .setCache(HyperCache.getInstance(context))
            .setFlags(0)
    }
}

object HyperCache {
    @Volatile private var instance: SimpleCache? = null
    /** Releases the shared cache after foreground data sources have closed. */
    internal suspend fun release() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        BytePreloader.withSuspendedWrites {
            synchronized(this@HyperCache) { instance?.release(); instance = null }
        }
    }
    fun getInstance(context: Context): SimpleCache = instance ?: synchronized(this) {
        instance ?: SimpleCache(File(context.cacheDir, "hyper_media_cache"),
            LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024),
            StandaloneDatabaseProvider(context.applicationContext)).also { instance = it }
    }
    suspend fun clearCache(context: Context) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        BytePreloader.withSuspendedWrites {
            StreamUrlCache.clear()
            CompleteCopyLookup.clear(context)
            val cache = getInstance(context)
            cache.keys.toList().forEach { cache.removeResource(it) }
        }
    }
}

class HyperDataSourceFactory(
    private val context: Context,
    private val preferences: PreferencesManager = getKoin().get(),
    private val music: MusicService = getKoin().get(),
    private val downloads: DownloadAvailability = getKoin().get()
) : DataSource.Factory {
    override fun createDataSource(): DataSource = HyperDataSource(context.applicationContext, preferences, music, downloads)
}
