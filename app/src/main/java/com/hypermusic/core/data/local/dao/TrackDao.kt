package com.hypermusic.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hypermusic.core.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @androidx.room.Transaction
    suspend fun saveMetadata(track: TrackEntity) {
        val artwork = track.artworkUrl?.takeIf { it.isNotBlank() }
        insertTrack(track.copy(artworkUrl = artwork))
        enrichMetadata(track.id, track.title, track.artist, track.artistId, track.album,
            track.albumId, track.artistsJson, track.duration, artwork, track.trackType)
    }

    @androidx.room.Transaction
    suspend fun setLiked(track: TrackEntity, liked: Boolean) {
        saveMetadata(track)
        updateLikedStatus(track.id, liked)
    }

    @Query("""UPDATE Tracks SET
        title = CASE WHEN :title != '' THEN :title ELSE title END,
        artist = CASE WHEN :artist != '' THEN :artist ELSE artist END,
        artistId = COALESCE(:artistId, artistId), album = COALESCE(:album, album),
        albumId = COALESCE(:albumId, albumId), artistsJson = COALESCE(:artists, artistsJson),
        duration = COALESCE(:duration, duration),
        artworkUrl = CASE WHEN :artwork IS NOT NULL AND TRIM(:artwork, char(9,10,11,12,13,32)) != '' THEN :artwork ELSE artworkUrl END,
        trackType = :type WHERE id = :id""")
    suspend fun enrichMetadata(id: String, title: String, artist: String, artistId: String?,
        album: String?, albumId: String?, artists: String?, duration: Int?, artwork: String?, type: String)

    @Query("UPDATE Tracks SET localFilePath = :path, localArtworkPath = :artwork WHERE id = :id")
    suspend fun updateLocalPaths(id: String, path: String?, artwork: String?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: TrackEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<TrackEntity>): List<Long>
    
    @Update
    suspend fun updateTrack(track: TrackEntity)
    
    @Query("SELECT * FROM Tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): TrackEntity?
    
    @Query("SELECT * FROM Tracks WHERE isLiked = 1 ORDER BY addedAt DESC")
    fun getLikedTracks(): Flow<List<TrackEntity>>
    
    @Query("SELECT isLiked FROM Tracks WHERE id = :trackId")
    fun isTrackLiked(trackId: String): Flow<Boolean?>
    
    @Query("UPDATE Tracks SET isLiked = :isLiked WHERE id = :trackId")
    suspend fun updateLikedStatus(trackId: String, isLiked: Boolean)

    @Query("UPDATE Tracks SET localFilePath = NULL, localArtworkPath = NULL")
    suspend fun clearAllLocalPaths()

    @Query("""
        DELETE FROM Tracks 
        WHERE isLiked = 0 
          AND localFilePath IS NULL 
          AND id NOT IN (SELECT trackId FROM PlaybackHistory)
          AND id NOT IN (SELECT trackId FROM PlaylistTracks)
          AND id NOT IN (SELECT trackId FROM AlbumTracks)
          AND id NOT IN (SELECT trackId FROM Downloads)
          AND id NOT IN (SELECT trackId FROM DownloadQueue)
    """)
    suspend fun deleteOrphanTracks(): Int
}
