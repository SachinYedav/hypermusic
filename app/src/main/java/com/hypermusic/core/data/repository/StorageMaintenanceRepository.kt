package com.hypermusic.core.data.repository

import android.app.Application
import com.hypermusic.core.data.local.AppDatabase
import com.hypermusic.core.data.local.dao.DownloadDao
import com.hypermusic.core.data.local.dao.HistoryDao
import com.hypermusic.core.data.local.dao.TrackDao
import com.hypermusic.core.lyrics.LyricsDiskCache
import com.hypermusic.core.player.HyperCache
import com.hypermusic.core.player.StreamUrlCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageSizes(
    val cacheSize: Long,
    val downloadsSize: Long,
    val databaseSize: Long,
    val totalDeviceStorage: Long,
    val freeDeviceStorage: Long,
    val historyCount: Int
)

class StorageMaintenanceRepository(
    private val context: Application,
    private val historyDao: HistoryDao,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val appDatabase: AppDatabase,
    private val lyricsDiskCache: LyricsDiskCache = LyricsDiskCache(context),
    private val historyWriter: PlaybackHistoryWriter
) {

    suspend fun getStorageSizes(): StorageSizes = withContext(Dispatchers.IO) {
        val cacheSize = getCacheSize()
        val downloadsSize = getDownloadsSize()
        val databaseSize = getDatabaseSize()
        val historyCount = historyDao.getHistoryCount()

        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalDeviceStorage = totalBlocks * blockSize
        val freeDeviceStorage = availableBlocks * blockSize

        StorageSizes(
            cacheSize = cacheSize,
            downloadsSize = downloadsSize,
            databaseSize = databaseSize,
            totalDeviceStorage = totalDeviceStorage,
            freeDeviceStorage = freeDeviceStorage,
            historyCount = historyCount
        )
    }

    private fun getCacheSize(): Long {
        var size = 0L
        val cacheDir = context.cacheDir
        if (cacheDir != null && cacheDir.exists()) {
            size += getFolderSize(cacheDir)
        }
        val externalCacheDir = context.externalCacheDir
        if (externalCacheDir != null && externalCacheDir.exists()) {
            size += getFolderSize(externalCacheDir)
        }
        val lyricsDir = File(context.filesDir, "lyrics_cache")
        if (lyricsDir.exists()) {
            size += getFolderSize(lyricsDir)
        }
        return size
    }

    private fun getDownloadsSize(): Long {
        var size = 0L
        val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
        if (musicDir != null && musicDir.exists()) {
            size += getFolderSize(musicDir)
        }
        val picturesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        if (picturesDir != null && picturesDir.exists()) {
            size += getFolderSize(picturesDir)
        }
        return size
    }

    private fun getDatabaseSize(): Long {
        var size = 0L
        val dbFile = context.getDatabasePath("hypermusic.db")
        if (dbFile != null && dbFile.exists()) {
            size += dbFile.length()
        }
        val walFile = File(dbFile.path + "-wal")
        if (walFile.exists()) {
            size += walFile.length()
        }
        val shmFile = File(dbFile.path + "-shm")
        if (shmFile.exists()) {
            size += shmFile.length()
        }
        return size
    }

    private fun getFolderSize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                size += getFolderSize(child)
            }
        } else {
            size += file.length()
        }
        return size
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        // Clear media and URL caches under the preloader writer barrier.
        HyperCache.clearCache(context)


        withContext(Dispatchers.Main) {
            coil.Coil.imageLoader(context).memoryCache?.clear()
        }

        // Clear internal cache contents while preserving the root directory.
        context.cacheDir?.listFiles()?.forEach { child ->
            if (child.name != "hyper_media_cache") {
                child.deleteRecursively()
            }
        }

        context.externalCacheDir?.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }

        // Preserve offline lyrics for downloaded tracks when clearing the lyrics cache.
        try {
            val downloadedTrackIds = downloadDao.getAllDownloadedTrackIds().toSet()
            lyricsDiskCache.clearNonDownloaded(downloadedTrackIds)
        } catch (_: Exception) {}
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyWriter.clear()
        try {
            appDatabase.openHelper.writableDatabase.execSQL("VACUUM")
        } catch (_: Exception) {}
    }

    suspend fun clearDownloads() = withContext(Dispatchers.IO) {
        com.hypermusic.core.player.BytePreloader.cancel()
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("download_queue_processor").result.get()
        DownloadFinalizer.withExclusiveFiles {
        downloadDao.clearAllDownloads()
        downloadDao.clearQueue()

        trackDao.clearAllLocalPaths()
        trackDao.deleteOrphanTracks()

        val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
        musicDir?.listFiles()?.forEach { it.deleteRecursively() }

        val picturesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        picturesDir?.listFiles()?.forEach { it.deleteRecursively() }

        lyricsDiskCache.clearAll()

        try {
            appDatabase.openHelper.writableDatabase.execSQL("VACUUM")
        } catch (_: Exception) {}
        }
    }
}
