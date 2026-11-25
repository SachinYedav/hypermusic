package com.hypermusic.core.data.repository

import android.app.NotificationManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hypermusic.core.data.local.dao.DownloadDao
import com.hypermusic.core.data.local.dao.TrackDao
import com.hypermusic.core.data.local.entity.DownloadEntity
import com.hypermusic.core.data.local.entity.DownloadQueueEntity
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import com.hypermusic.core.data.mapper.toEntity
import com.hypermusic.core.data.mapper.toDomain

import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.utils.NetworkUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hypermusic.core.lyrics.LyricsDiskCache
import java.io.File

enum class DownloadState { VALID, MISSING, INCOMPLETE, UNAVAILABLE }
data class AvailableDownload(val state: DownloadState, val file: File? = null)

class DownloadAvailability(private val tracks: TrackDao, private val downloads: DownloadDao) {
    suspend fun find(id: String): AvailableDownload {
        val record = downloads.getDownload(id) ?: return AvailableDownload(DownloadState.UNAVAILABLE)
        val path = tracks.getTrackById(id)?.localFilePath ?: return AvailableDownload(DownloadState.MISSING)
        return inspect(File(path), record.sizeBytes, record.status == "COMPLETED")
    }

    companion object {
        fun inspect(file: File, expectedBytes: Long, completed: Boolean): AvailableDownload = when {
            !file.isFile || !file.canRead() -> AvailableDownload(DownloadState.MISSING)
            !completed || expectedBytes <= 0 || file.length() != expectedBytes -> AvailableDownload(DownloadState.INCOMPLETE)
            else -> AvailableDownload(DownloadState.VALID, file)
        }
    }
}

enum class DownloadEnqueueResult {
    ALREADY_EXISTS,
    STARTED,
    WAITING_FOR_WIFI,
    UNSUPPORTED
}

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val preferencesManager: PreferencesManager,
    private val lyricsDiskCache: LyricsDiskCache = LyricsDiskCache(context)
) {
    private val availability = DownloadAvailability(trackDao, downloadDao)
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    val downloadQueue: StateFlow<Map<String, DownloadQueueEntity>> = downloadDao.getAllQueueItems()
        .map { items -> items.associateBy { it.trackId } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    private val _isCellularBypassActive = MutableStateFlow(false)
    val isCellularBypassActive: StateFlow<Boolean> = _isCellularBypassActive.asStateFlow()

    init {
        scope.launch {
            downloadDao.getAllQueueItems().collect { items ->
                if (items.isEmpty() && _isCellularBypassActive.value) {
                    _isCellularBypassActive.value = false
                }
            }
        }
    }

    suspend fun enqueueDownload(track: Track): DownloadEnqueueResult {
        if (track.isDeviceOrigin()) {
            return DownloadEnqueueResult.UNSUPPORTED
        }
        if (isDownloaded(track.id)) {
            return DownloadEnqueueResult.ALREADY_EXISTS
        }

        val trackEntity = track.toEntity()
        trackDao.insertTrack(trackEntity)

        val queueItem = DownloadQueueEntity(
            id = track.id,
            trackId = track.id,
            status = "PENDING"
        )
        downloadDao.insertQueueItem(queueItem)

        startQueueProcessor()

        CoroutineScope(Dispatchers.IO).launch {
            if (lyricsDiskCache.load(track.id) == null) {
                try {
                    val lyrics = com.hypermusic.core.lyrics.LrcLyricsService.fetchLyrics(
                        title = track.title,
                        artist = track.artist,
                        durationSeconds = track.duration
                    )
                    if (lyrics != null && lyrics.text.isNotBlank()) {
                        lyricsDiskCache.save(track.id, lyrics)
                    }
                } catch (_: Exception) {}
            }
        }

        val settings = preferencesManager.userSettingsFlow.first()
        return if (settings.downloadOverWifiOnly && !NetworkUtils.isUnmeteredConnection(context) && !_isCellularBypassActive.value) {
            DownloadEnqueueResult.WAITING_FOR_WIFI
        } else {
            DownloadEnqueueResult.STARTED
        }
    }

    suspend fun enqueueBulkDownload(tracks: List<Track>): DownloadEnqueueResult {
        val eligibleTracks = tracks.filter { !it.isDeviceOrigin() }
        if (eligibleTracks.isEmpty()) {
            return if (tracks.isNotEmpty()) DownloadEnqueueResult.UNSUPPORTED else DownloadEnqueueResult.ALREADY_EXISTS
        }
        val newTracks = eligibleTracks.filter { track ->
            !isDownloaded(track.id)
        }
        if (newTracks.isEmpty()) return DownloadEnqueueResult.ALREADY_EXISTS

        val trackEntities = newTracks.map { track ->
            track.toEntity()
        }
        trackDao.insertTracks(trackEntities)

        val queueItems = newTracks.map { track ->
            DownloadQueueEntity(
                id = track.id,
                trackId = track.id,
                status = "PENDING"
            )
        }
        downloadDao.insertQueueItems(queueItems)

        startQueueProcessor()

        CoroutineScope(Dispatchers.IO).launch {
            for (track in newTracks) {
                if (lyricsDiskCache.load(track.id) == null) {
                    try {
                        val lyrics = com.hypermusic.core.lyrics.LrcLyricsService.fetchLyrics(
                            title = track.title,
                            artist = track.artist,
                            durationSeconds = track.duration
                        )
                        if (lyrics != null && lyrics.text.isNotBlank()) {
                            lyricsDiskCache.save(track.id, lyrics)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        val settings = preferencesManager.userSettingsFlow.first()
        return if (settings.downloadOverWifiOnly && !NetworkUtils.isUnmeteredConnection(context) && !_isCellularBypassActive.value) {
            DownloadEnqueueResult.WAITING_FOR_WIFI
        } else {
            DownloadEnqueueResult.STARTED
        }
    }

    suspend fun startQueueProcessor(forceReplace: Boolean = false, overrideWifiConstraint: Boolean = false) {
        val effectiveOverride = overrideWifiConstraint || _isCellularBypassActive.value
        val settings = preferencesManager.userSettingsFlow.first()
        val networkType = if (settings.downloadOverWifiOnly && !effectiveOverride) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<com.hypermusic.core.worker.DownloadWorker>()
            .setConstraints(constraints)
            .build()
            
        // Preserve active tasks unless explicit replacement is requested
        val policy = if (forceReplace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(
            "download_queue_processor",
            policy,
            workRequest
        )
    }

    suspend fun resumeOverCellularNow() = withContext(Dispatchers.IO) {
        downloadDao.resetDownloadingToPending()
        _isCellularBypassActive.value = true
        startQueueProcessor(forceReplace = true, overrideWifiConstraint = true)
    }

    suspend fun isWaitingForWifi(): Boolean {
        if (_isCellularBypassActive.value) return false
        val settings = preferencesManager.userSettingsFlow.first()
        return settings.downloadOverWifiOnly && !NetworkUtils.isUnmeteredConnection(context)
    }

    fun getQueueItemsWithTracks(): Flow<List<com.hypermusic.core.data.local.entity.DownloadQueueWithTrack>> {
        return downloadDao.getAllQueueItemsWithTracks()
    }

    suspend fun getQueueItem(trackId: String): DownloadQueueEntity? {
        return downloadDao.getQueueItem(trackId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getDownloadFlow(trackId: String): Flow<DownloadEntity?> {
        return downloadDao.getDownloadFlow(trackId).flatMapLatest { record ->
            kotlinx.coroutines.flow.flow {
                do {
                    emit(record?.takeIf { availability.find(trackId).state == DownloadState.VALID })
                    kotlinx.coroutines.delay(5_000)
                } while (true)
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun isDownloaded(trackId: String): Boolean {
        return withContext(Dispatchers.IO) { availability.find(trackId).state == DownloadState.VALID }
    }

    suspend fun getAllDownloadedTrackIds(): List<String> = withContext(Dispatchers.IO) {
        downloadDao.getAllDownloadedTrackIds().filter { availability.find(it).state == DownloadState.VALID }
    }

    suspend fun cancelDownload(trackId: String) = withContext(Dispatchers.IO) {
        downloadDao.deleteQueueItemByTrackId(trackId)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(trackId.hashCode())
        try {
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            musicDir?.listFiles()?.forEach { file ->
                if (file.name.startsWith(trackId) && file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun removeDownload(trackId: String) = withContext(Dispatchers.IO) {
        DownloadFinalizer.withExclusiveFiles {
        downloadDao.deleteQueueItemByTrackId(trackId)
        downloadDao.deleteDownload(trackId)
        
        val track = trackDao.getTrackById(trackId)
        if (track != null) {
            track.localFilePath?.let { java.io.File(it).delete() }
            track.localArtworkPath?.let { java.io.File(it).delete() }
            
            trackDao.updateLocalPaths(trackId, null, null)
        }

        lyricsDiskCache.delete(trackId)
        }
    }

    suspend fun pauseAllDownloads() = withContext(Dispatchers.IO) {
        _isCellularBypassActive.value = false
        downloadDao.pauseAllPending()
        workManager.cancelUniqueWork("download_queue_processor")
    }

    suspend fun resumeAllDownloads() = withContext(Dispatchers.IO) {
        downloadDao.resumeAllPaused()
        downloadDao.resetDownloadingToPending()
        startQueueProcessor(forceReplace = true)
    }

    suspend fun clearAllDownloads() = withContext(Dispatchers.IO) {
        _isCellularBypassActive.value = false
        // Request cancellation of the queue processor before clearing its state.
        workManager.cancelUniqueWork("download_queue_processor")

        downloadDao.clearQueue()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(2001) // Paused
        notificationManager.cancel(2002) // Complete

        try {
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            musicDir?.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    suspend fun pauseDownload(trackId: String) {
        downloadDao.updateQueueStatus(trackId, "PAUSED")
    }
    
    suspend fun resumeDownload(trackId: String) {
        downloadDao.updateQueueStatus(trackId, "PENDING")
        startQueueProcessor()
    }

    suspend fun resumeDownloads(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        downloadDao.resumeQueueItems(trackIds)
        startQueueProcessor()
    }

    fun getQueueStatus(trackId: String): Flow<DownloadQueueEntity?> {
        return downloadDao.getQueueItemFlow(trackId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getAllDownloadedTracks(): Flow<List<Track>> {
        return downloadDao.getAllDownloadedTracks().flatMapLatest { entities ->
            kotlinx.coroutines.flow.flow {
                do {
                    emit(entities.filter { availability.find(it.id).state == DownloadState.VALID }.map { entity ->
                        entity.toDomain().copy(artwork = entity.localArtworkPath ?: entity.artworkUrl)
                    })
                    kotlinx.coroutines.delay(5_000)
                } while (true)
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun cleanOrphanTempFiles() = withContext(Dispatchers.IO) {
        try {
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let {
                DownloadFinalizer(it, trackDao, downloadDao).recover()
            }
            downloadDao.getAllDownloadedTrackIds().forEach { id ->
                if (availability.find(id).state != DownloadState.VALID) {
                    downloadDao.deleteDownload(id)
                    trackDao.updateLocalPaths(id, null, null)
                }
            }
            // Reset interrupted DOWNLOADING rows before restarting the queue.
            downloadDao.resetDownloadingToPending()
            
            // Preserve partial files for tracks still queued, including paused and failed items.
            val activeQueueIds = downloadDao.getAllQueueItems().first().map { it.trackId }.toSet()
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            musicDir?.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    val trackId = file.name.substringBefore(".")
                    if (trackId !in activeQueueIds) {
                        file.delete()
                    }
                }
            }
            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            picturesDir?.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    val trackId = file.name.substringBefore("_")
                    if (trackId !in activeQueueIds) {
                        file.delete()
                    }
                }
            }
            
            // Auto-resume if there are pending items on app startup
            val pendingCount = downloadDao.getActiveQueueCount()
            if (pendingCount > 0) {
                startQueueProcessor(forceReplace = false)
            }
        } catch (_: Exception) {

        }
    }
}
