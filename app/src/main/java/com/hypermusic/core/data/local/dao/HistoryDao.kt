package com.hypermusic.core.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hypermusic.core.data.local.entity.HistoryEntity
import com.hypermusic.core.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

data class HistoryTrackItem(
    @Embedded val track: TrackEntity,
    val lastPlayedAt: Long
)

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(history: HistoryEntity)

    @Query("UPDATE PlaybackHistory SET playCount = playCount + :count, totalListenTimeMs = totalListenTimeMs + :duration, lastPlayedAt = :at WHERE trackId = :id")
    suspend fun increment(id: String, duration: Long, count: Int, at: Long)

    @androidx.room.Transaction
    suspend fun credit(id: String, duration: Long, count: Int, at: Long) {
        insertIfMissing(HistoryEntity(trackId = id, playCount = 0, lastPlayedAt = at, totalListenTimeMs = 0))
        increment(id, duration, count, at)
    }

    @Query("SELECT * FROM PlaybackCheckpoints WHERE sessionId = :session")
    suspend fun getCheckpoint(session: String): com.hypermusic.core.data.local.entity.PlaybackCheckpoint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCheckpoint(checkpoint: com.hypermusic.core.data.local.entity.PlaybackCheckpoint)

    @Query("DELETE FROM PlaybackCheckpoints")
    suspend fun clearCheckpoints()

    @Query("DELETE FROM PlaybackCheckpoints WHERE trackId = :id")
    suspend fun deleteCheckpointsForTrack(id: String)

    @Query("DELETE FROM PlaybackCheckpoints WHERE updatedAt < :before")
    suspend fun pruneCheckpoints(before: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHistory(history: HistoryEntity)
    
    @Query("SELECT * FROM PlaybackHistory WHERE trackId = :trackId")
    suspend fun getHistoryByTrackId(trackId: String): HistoryEntity?
    
    @Query("""
        SELECT Tracks.*, PlaybackHistory.lastPlayedAt AS lastPlayedAt FROM Tracks 
        INNER JOIN PlaybackHistory ON Tracks.id = PlaybackHistory.trackId 
        ORDER BY PlaybackHistory.lastPlayedAt DESC 
        LIMIT :limit
    """)
    fun getRecentHistory(limit: Int = 20): Flow<List<HistoryTrackItem>>

    @Query("SELECT COUNT(*) FROM PlaybackHistory")
    suspend fun getHistoryCount(): Int

    @Query("DELETE FROM PlaybackHistory WHERE trackId = :trackId")
    suspend fun deleteHistoryByTrackId(trackId: String)

    @Query("UPDATE PlaybackHistory SET totalListenTimeMs = totalListenTimeMs + :additionalMs WHERE trackId = :trackId")
    suspend fun addListeningDuration(trackId: String, additionalMs: Long)

    @Query("DELETE FROM PlaybackHistory")
    suspend fun clearHistory()
}
