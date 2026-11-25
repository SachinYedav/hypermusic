package com.hypermusic.core.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.hypermusic.core.utils.ArtworkFileStore
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.core.utils.ArtworkUrlResolver
import com.hypermusic.core.utils.Logger
import coil.size.Precision
import com.hypermusic.core.data.local.dao.DownloadDao
import com.hypermusic.core.data.local.dao.TrackDao
import com.hypermusic.core.data.local.entity.DownloadEntity
import com.hypermusic.core.data.local.entity.DownloadQueueEntity
import com.hypermusic.core.data.local.entity.TrackEntity
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.domain.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class LowStorageException(message: String) : java.io.IOException(message)

class DownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val trackDao: TrackDao by inject()
    private val downloadDao: DownloadDao by inject()
    private val musicService: MusicService by inject()
    private val preferencesManager: PreferencesManager by inject()

    companion object {
        private const val CHANNEL_ID = "hypermusic_downloads"
        private const val GROUP_KEY_DOWNLOADS = "com.hypermusic.DOWNLOADS"
        private const val MAX_CONCURRENT_DOWNLOADS = 2
        private val extractionMutex = Mutex()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(2001) // Dismiss the paused-summary notification.
        notificationManager.cancel(2002) // Dismiss the completed-summary notification.
        setupNotificationChannel(notificationManager)

        appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let {
            com.hypermusic.core.data.repository.DownloadFinalizer(it, trackDao, downloadDao).recover()
        }
        downloadDao.resetDownloadingToPending()

        // Set initial foreground info so worker is safely promoted as group summary
        setForeground(createSummaryForegroundInfo("Preparing downloads...", 0, 0))

        val activeJobs = ConcurrentHashMap<String, Job>()
        val activeProgress = ConcurrentHashMap<String, Int>()
        val activeTitles = ConcurrentHashMap<String, String>()
        val activeTrackIds = ConcurrentHashMap<String, String>()

        var totalProcessed = 0
        var totalFailed = 0
        var wasPaused = false
        var lastNotificationTime = 0L

        suspend fun updateSummaryNotification(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastNotificationTime < 1000L) return
            lastNotificationTime = now

            val activeCount = activeJobs.size
            if (activeCount == 0) return

            val avgProgress = if (activeProgress.isNotEmpty()) {
                activeProgress.values.average().toInt().coerceIn(0, 100)
            } else 0

            val contentText = if (activeCount > 1) {
                "$avgProgress% • $activeCount active downloads"
            } else {
                "$avgProgress% • ${activeTitles.values.firstOrNull() ?: "Downloading"}"
            }

            try {
                setForeground(createSummaryForegroundInfo(contentText, avgProgress, activeCount))
            } catch (_: Exception) {}
        }

        coroutineScope {
            while (!isStopped) {
                val totalActiveAndPending = downloadDao.getActiveQueueCount()
                if (totalActiveAndPending == 0 && activeJobs.isEmpty()) {
                    break
                }

                val availableSlots = MAX_CONCURRENT_DOWNLOADS - activeJobs.size
                if (availableSlots > 0) {
                    val pendingItems = downloadDao.getNextPendingDownloads(availableSlots)
                    if (pendingItems.isNotEmpty()) {
                        for (item in pendingItems) {
                            if (isStopped) break
                            val queueId = item.id
                            val trackId = item.trackId

                            downloadDao.updateQueueStatus(queueId, "DOWNLOADING")
                            activeProgress[queueId] = item.progress
                            activeTrackIds[queueId] = trackId

                            val job = launch {
                                try {
                                    val track = trackDao.getTrackById(trackId)
                                    if (track == null) {
                                        downloadDao.updateQueueStatus(queueId, "FAILED")
                                        totalFailed++
                                        return@launch
                                    }
                                    activeTitles[queueId] = track.title
                                    updateSummaryNotification(force = true)

                                    val success = processSingleTrack(
                                        queueItem = item,
                                        track = track,
                                        notificationManager = notificationManager
                                    ) { progress ->
                                        activeProgress[queueId] = progress
                                        launch { updateSummaryNotification() }
                                    }

                                    if (success) {
                                        totalProcessed++
                                    } else {
                                        totalFailed++
                                    }
                                } catch (e: Exception) {
                                    if (e is LowStorageException) {
                                        wasPaused = true
                                        downloadDao.updateQueueStatus(queueId, "PAUSED")
                                    } else if (isStopped) {
                                        wasPaused = true
                                        downloadDao.updateQueueStatus(queueId, "PAUSED")
                                    } else {
                                        totalFailed++
                                    }
                                } finally {
                                    activeJobs.remove(queueId)
                                    activeProgress.remove(queueId)
                                    activeTitles.remove(queueId)
                                    activeTrackIds.remove(queueId)
                                    notificationManager.cancel(trackId.hashCode())
                                    updateSummaryNotification(force = true)
                                }
                            }
                            activeJobs[queueId] = job
                        }
                    } else if (activeJobs.isEmpty()) {
                        break
                    }
                }

                delay(300)
            }

            if (isStopped) {
                activeJobs.values.forEach { it.cancel() }
                wasPaused = true
                downloadDao.resetDownloadingToPending()
            }
        }

        activeTrackIds.values.forEach { tId ->
            notificationManager.cancel(tId.hashCode())
        }

        if (!wasPaused && totalProcessed > 0) {
            val failedText = if (totalFailed > 0) " ($totalFailed failed)" else ""
            val trackWord = if (totalProcessed == 1) "track" else "tracks"
            showStaticNotification(
                "Download Complete",
                "Successfully downloaded $totalProcessed $trackWord$failedText • Tap to view downloads"
            )
        }

        Result.success()
    }

    private suspend fun processSingleTrack(
        queueItem: DownloadQueueEntity,
        track: TrackEntity,
        notificationManager: NotificationManager,
        onProgress: (Int) -> Unit
    ): Boolean {
        val trackId = queueItem.trackId
        val queueId = queueItem.id
        var tempAudioFile: File? = null

        val artworkBitmap = loadArtworkBitmap(appContext, track.localArtworkPath ?: track.artworkUrl)

        val cancelIntent = Intent(appContext, DownloadActionReceiver::class.java).apply {
            action = "ACTION_CANCEL_TRACK"
            putExtra("track_id", trackId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            appContext,
            trackId.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val manageIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val managePendingIntent = PendingIntent.getActivity(
            appContext,
            CHANNEL_ID.hashCode(),
            manageIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openDownloadsIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "downloads")
        }
        val openDownloadsPendingIntent = openDownloadsIntent?.let {
            PendingIntent.getActivity(
                appContext,
                11,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun postOrUpdateChildNotification(progress: Int) {
            try {
                val childNotification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setContentTitle(track.title)
                    .setContentText("${progress}% • ${track.artist}")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setProgress(100, progress, false)
                    .setGroup(GROUP_KEY_DOWNLOADS)
                    .setGroupSummary(false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .apply {
                        if (artworkBitmap != null) {
                            setLargeIcon(artworkBitmap)
                        }
                        if (openDownloadsPendingIntent != null) {
                            setContentIntent(openDownloadsPendingIntent)
                        }
                    }
                    .addAction(0, "Cancel", cancelPendingIntent)
                    .addAction(0, "Manage notification", managePendingIntent)
                    .build()

                notificationManager.notify(trackId.hashCode(), childNotification)
            } catch (_: Exception) {}
        }

        try {
            val settings = preferencesManager.userSettingsFlow.first()
            val isPodcastVideo = (track.trackType == "podcast" || track.trackType == "podcast_episode") && settings.downloadPodcastsAsVideo
            val extractionType = if (isPodcastVideo) "video" else "audio"

            postOrUpdateChildNotification(queueItem.progress)

            // Serialize download stream resolution to reduce request bursts.
            val source = extractionMutex.withLock {
                delay(250) // Space requests to reduce rate-limit pressure.
                val result = com.hypermusic.core.player.StreamResolutionCoordinator.resolveStreamSource(
                    trackId, settings.downloadQuality, extractionType, musicService, backgroundDownload = true)
                result.getOrNull()
            }

            if (source == null) {
                if (queueItem.retryCount < 2) {
                    downloadDao.incrementRetryCount(queueId, "PENDING")
                } else {
                    downloadDao.updateQueueStatus(queueId, "FAILED")
                }
                return false
            }

            val musicDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            if (musicDir != null && !musicDir.exists()) {
                musicDir.mkdirs()
            }
            val fileExtension = source.suffix?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "media"
            val resourceVersion = com.hypermusic.core.player.ResolvedSource.remoteKey(source.url).substringAfter(":").take(16)
            val currentTemp = File(musicDir, "${trackId}.${resourceVersion}.${fileExtension}.tmp")
            tempAudioFile = currentTemp
            val audioFile = File(musicDir, "${trackId}.${resourceVersion}.${fileExtension}")

            val picturesDir = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (picturesDir != null && !picturesDir.exists()) {
                picturesDir.mkdirs()
            }
            val artworkFile = picturesDir?.let { File(it, "${trackId}_art.jpg") }

            var lastChildNotifTime = 0L
            val downloadedBytes = downloadFileChunkedResumable(
                urlString = source.url,
                destFile = currentTemp,
                queueId = queueId,
                onProgress = { progress ->
                    onProgress(progress)
                    val now = System.currentTimeMillis()
                    if (now - lastChildNotifTime > 1000L || progress == 100) {
                        lastChildNotifTime = now
                        postOrUpdateChildNotification(progress)
                    }
                }
            )

            if (isStopped) {
                downloadDao.updateQueueStatus(queueId, "PAUSED")
                return false
            }

            val currentStatus = downloadDao.getQueueItem(queueId)?.status
            if (currentStatus == null || currentStatus == "PAUSED" || currentStatus == "CANCELLED") {
                return false
            }

            var savedArtworkPath = track.localArtworkPath?.let(::File)
                ?.takeIf { it.isFile && it.length() > 0L }?.absolutePath
                ?: artworkFile?.takeIf { it.isFile && it.length() > 0L }?.absolutePath
            if (artworkFile != null && !track.artworkUrl.isNullOrBlank()) {
                var tempArtworkFile: File? = null
                try {
                    val request = ImageRequest.Builder(appContext)
                        .data(ArtworkUrlResolver.resolveUrl(track.artworkUrl, ArtworkQuality.HERO))
                        .size(1200, 1200)
                        .precision(Precision.INEXACT)
                        .allowHardware(false)
                        .build()
                    val result = Coil.imageLoader(appContext).execute(request)
                    val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                        ?: throw java.io.IOException("Artwork could not be decoded")
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (isStopped) {
                        downloadDao.updateQueueStatus(queueId, "PAUSED")
                        return false
                    }
                    tempArtworkFile = File.createTempFile("${trackId}_art_", ".tmp", artworkFile.parentFile)
                    FileOutputStream(tempArtworkFile).use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                            throw java.io.IOException("Artwork could not be saved")
                        }
                        output.flush()
                        output.fd.sync()
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    savedArtworkPath = ArtworkFileStore.install(tempArtworkFile, artworkFile)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w("Keeping existing artwork after an optional cover download failed", e)
                    if (isStopped) {
                        downloadDao.updateQueueStatus(queueId, "PAUSED")
                        return false
                    }
                } finally {
                    tempArtworkFile?.delete()
                }
            }

            return com.hypermusic.core.data.repository.DownloadFinalizer(
                musicDir ?: throw java.io.IOException("Download storage unavailable"), trackDao, downloadDao
            ).finish(currentTemp, audioFile,
                record = DownloadEntity(
                    trackId = trackId,
                    downloadedAt = System.currentTimeMillis(),
                    sizeBytes = downloadedBytes,
                    status = "COMPLETED",
                    mimeType = source.mimeType,
                    formatId = source.formatId,
                    hasVideo = source.hasVideo
                ),
                queue = queueItem,
                artwork = savedArtworkPath
            )

        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            if (e is LowStorageException) {
                downloadDao.updateQueueStatus(queueId, "PAUSED")
                throw e
            } else if (isStopped) {
                downloadDao.updateQueueStatus(queueId, "PAUSED")
                return false
            } else {
                val currentStatus = downloadDao.getQueueItem(queueId)?.status
                if (currentStatus == null || currentStatus == "PAUSED" || currentStatus == "CANCELLED") {
                    return false
                }
                if (queueItem.retryCount < 2) {
                    downloadDao.incrementRetryCount(queueId, "PENDING")
                } else {
                    downloadDao.updateQueueStatus(queueId, "FAILED")
                }
                return false
            }
        } finally {
            notificationManager.cancel(trackId.hashCode())
        }
    }

    private suspend fun downloadFileChunkedResumable(
        urlString: String,
        destFile: File,
        queueId: String,
        onProgress: (Int) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val url = URL(urlString)

        val initialConn = url.openConnection() as HttpURLConnection
        initialConn.requestMethod = "HEAD"
        initialConn.connectTimeout = 15000
        initialConn.readTimeout = 15000
        initialConn.connect()
        val totalLength = initialConn.contentLengthLong
        initialConn.disconnect()
        if (totalLength <= 0) throw java.io.IOException("Download length unavailable")

        // Require the resource size plus a 50 MiB free-space reserve.
        val targetDir = destFile.parentFile ?: appContext.filesDir
        val stat = android.os.StatFs(targetDir.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val requiredBytes = (if (totalLength > 0) totalLength else 10L * 1024 * 1024) + (50L * 1024 * 1024)
        if (availableBytes < requiredBytes) {
            throw LowStorageException("Not enough storage space available")
        }

        var downloadedBytes = 0L
        if (destFile.exists()) {
            downloadedBytes = destFile.length()
            if (downloadedBytes > totalLength) { destFile.outputStream().use { }; downloadedBytes = 0 }
            if (downloadedBytes == totalLength) {
                return@withContext downloadedBytes
            }
        } else {
            destFile.createNewFile()
        }

        val chunkSize = 2L * 1024 * 1024 // 2 MiB chunks.
        var lastDbUpdate = 0L

        RandomAccessFile(destFile, "rw").use { raf ->
            raf.seek(downloadedBytes)

            while (downloadedBytes < totalLength) {
                if (isStopped) {
                    throw Exception("Worker stopped")
                }

                val qItem = downloadDao.getQueueItem(queueId)
                if (qItem == null || qItem.status == "PAUSED" || qItem.status == "CANCELLED") {
                    throw Exception("Paused/Cancelled by user or queue cleared")
                }

                val endByte = minOf(downloadedBytes + chunkSize - 1, totalLength - 1)

                val chunkConn = url.openConnection() as HttpURLConnection
                chunkConn.setRequestProperty("Range", "bytes=$downloadedBytes-$endByte")
                chunkConn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                )
                chunkConn.connectTimeout = 15000
                chunkConn.readTimeout = 15000
                chunkConn.connect()

                try {
                val status = chunkConn.responseCode
                if (status != 200 && status != 206) throw java.io.IOException("Download HTTP request failed: $status")
                if (status == 200) {
                    // A range-ignoring server sends the entire resource: never append it to a partial file.
                    raf.setLength(0); raf.seek(0); downloadedBytes = 0
                } else {
                    val range = chunkConn.getHeaderField("Content-Range")
                    if (range != "bytes $downloadedBytes-$endByte/$totalLength") throw java.io.IOException("Mismatched download range")
                }
                val responseEnd = if (status == 200) totalLength else endByte + 1
                chunkConn.inputStream.use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (downloadedBytes + bytesRead > responseEnd) throw java.io.IOException("Oversized download response")
                        raf.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalLength > 0) {
                            ((downloadedBytes * 100) / totalLength).toInt().coerceIn(0, 100)
                        } else 0

                        onProgress(progress)

                        val now = System.currentTimeMillis()
                        // Persist transfer progress at most once per second, plus its final 100% update.
                        if (now - lastDbUpdate > 1000L || progress == 100) {
                            lastDbUpdate = now
                            downloadDao.updateQueueProgress(queueId, progress, "DOWNLOADING")
                        }
                    }
                    if (downloadedBytes != responseEnd) throw java.io.IOException("Truncated download response")
                }
                } finally { chunkConn.disconnect() }
            }
        }

        return@withContext downloadedBytes
    }

    private suspend fun loadArtworkBitmap(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(192, 192)
                .precision(Precision.INEXACT)
                .allowHardware(false)
                .build()
            val result = Coil.imageLoader(context).execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun setupNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "HyperMusic Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Download progress and status notifications"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun showStaticNotification(title: String, content: String) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        setupNotificationChannel(notificationManager)

        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "library")
            putExtra("library_filter", "Downloads")
        }
        val viewPendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                appContext, 2002, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val manageIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val managePendingIntent = PendingIntent.getActivity(
            appContext,
            CHANNEL_ID.hashCode() + 1,
            manageIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (viewPendingIntent != null) {
                    setContentIntent(viewPendingIntent)
                    addAction(0, "View", viewPendingIntent)
                }
                addAction(0, "Manage notification", managePendingIntent)
            }

        notificationManager.notify(2002, notificationBuilder.build())
    }

    private fun createSummaryForegroundInfo(
        content: String,
        progress: Int,
        activeCount: Int
    ): ForegroundInfo {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "downloads")
        }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                appContext, 10, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val summaryNotification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("HyperMusic Downloads")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setGroup(GROUP_KEY_DOWNLOADS)
            .setGroupSummary(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (pendingIntent != null) {
                    setContentIntent(pendingIntent)
                }
            }
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(
                this.id.hashCode(),
                summaryNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            return ForegroundInfo(this.id.hashCode(), summaryNotification)
        }
    }
}
