package com.hypermusic.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hypermusic.core.data.local.entity.DownloadEntity
import com.hypermusic.core.data.local.entity.DownloadQueueEntity
import com.hypermusic.core.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("UPDATE Tracks SET localFilePath = :path, localArtworkPath = :artwork WHERE id = :id")
    suspend fun setLocalPaths(id: String, path: String, artwork: String?)

    @Transaction
    suspend fun completeDownload(download: DownloadEntity, path: String, artwork: String?, expectedQueuedAt: Long? = null, allowPending: Boolean = false): Boolean {
        val queued = getQueueItem(download.trackId) ?: return false
        if (expectedQueuedAt != null && queued.addedAt != expectedQueuedAt) return false
        if (queued.status != "DOWNLOADING" && !(allowPending && queued.status == "PENDING")) return false
        setLocalPaths(download.trackId, path, artwork)
        insertDownload(download)
        deleteQueueItemByTrackId(download.trackId)
        return true
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Query("DELETE FROM Downloads WHERE trackId = :trackId")
    suspend fun deleteDownload(trackId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQueueItem(queueItem: DownloadQueueEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQueueItems(queueItems: List<DownloadQueueEntity>)

    @Query("DELETE FROM DownloadQueue WHERE id = :id")
    suspend fun deleteQueueItem(id: String)
    
    @Query("DELETE FROM DownloadQueue WHERE trackId = :trackId")
    suspend fun deleteQueueItemByTrackId(trackId: String)

    @Query("UPDATE DownloadQueue SET progress = :progress, status = :status WHERE id = :id AND status = 'DOWNLOADING'")
    suspend fun updateQueueProgress(id: String, progress: Int, status: String)
    
    @Query("UPDATE DownloadQueue SET status = :status WHERE id = :id")
    suspend fun updateQueueStatus(id: String, status: String)

    @Query("UPDATE DownloadQueue SET status = 'PENDING' WHERE status = 'DOWNLOADING'")
    suspend fun resetDownloadingToPending()

    @Query("UPDATE DownloadQueue SET status = 'PAUSED' WHERE status IN ('PENDING', 'DOWNLOADING')")
    suspend fun pauseAllPending()

    @Query("UPDATE DownloadQueue SET status = 'PENDING' WHERE status = 'PAUSED'")
    suspend fun resumeAllPaused()

    @Query("UPDATE DownloadQueue SET status = 'PENDING', retryCount = CASE WHEN status = 'FAILED' THEN 0 ELSE retryCount END WHERE trackId IN (:trackIds) AND status IN ('PAUSED', 'FAILED')")
    suspend fun resumeQueueItems(trackIds: List<String>)

    @Query("SELECT * FROM DownloadQueue WHERE trackId = :trackId LIMIT 1")
    fun getQueueItemFlow(trackId: String): Flow<DownloadQueueEntity?>
    
    @Query("SELECT * FROM DownloadQueue WHERE trackId = :trackId LIMIT 1")
    suspend fun getQueueItem(trackId: String): DownloadQueueEntity?

    @Query("SELECT * FROM DownloadQueue WHERE status = 'PENDING' ORDER BY addedAt ASC LIMIT 1")
    suspend fun getNextPendingDownload(): DownloadQueueEntity?

    @Query("SELECT * FROM DownloadQueue WHERE status = 'PENDING' ORDER BY addedAt ASC LIMIT :limit")
    suspend fun getNextPendingDownloads(limit: Int): List<DownloadQueueEntity>

    @Query("UPDATE DownloadQueue SET retryCount = retryCount + 1, status = :status WHERE id = :id")
    suspend fun incrementRetryCount(id: String, status: String = "PENDING")

    @Query("SELECT COUNT(*) FROM DownloadQueue WHERE status IN ('PENDING', 'DOWNLOADING')")
    suspend fun getActiveQueueCount(): Int

    @Query("SELECT * FROM DownloadQueue ORDER BY addedAt ASC")
    fun getAllQueueItems(): Flow<List<DownloadQueueEntity>>

    @Transaction
    @Query("SELECT * FROM DownloadQueue ORDER BY addedAt ASC")
    fun getAllQueueItemsWithTracks(): Flow<List<com.hypermusic.core.data.local.entity.DownloadQueueWithTrack>>

    @Transaction
    @Query("""
        SELECT t.* FROM Tracks t 
        INNER JOIN Downloads d ON t.id = d.trackId 
        ORDER BY d.downloadedAt DESC
    """)
    fun getAllDownloadedTracks(): Flow<List<TrackEntity>>
    
    @Query("SELECT * FROM Downloads WHERE trackId = :trackId LIMIT 1")
    fun getDownloadFlow(trackId: String): Flow<DownloadEntity?>
    
    @Query("SELECT * FROM Downloads WHERE trackId = :trackId LIMIT 1")
    suspend fun getDownload(trackId: String): DownloadEntity?

    @Query("SELECT trackId FROM Downloads")
    suspend fun getAllDownloadedTrackIds(): List<String>

    @Query("DELETE FROM Downloads")
    suspend fun clearAllDownloads()

    @Query("DELETE FROM DownloadQueue")
    suspend fun clearQueue()
}
