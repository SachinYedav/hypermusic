package com.hypermusic.core.data.local.dao

import androidx.room.*
import com.hypermusic.core.data.local.entity.PlaylistEntity
import com.hypermusic.core.data.local.entity.PlaylistTrackCrossRef
import com.hypermusic.core.data.local.entity.PlaylistWithTracks
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM Playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM Playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackCrossRefs(crossRefs: List<PlaylistTrackCrossRef>)

    @Query("SELECT COALESCE(MAX(position), -1) FROM PlaylistTracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Int

    @Delete
    suspend fun deletePlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM PlaylistTracks WHERE playlistId = :playlistId")
    suspend fun deleteAllPlaylistTracks(playlistId: String)

    @Transaction
    @Query("SELECT * FROM Playlists WHERE playlistId = :playlistId")
    fun getPlaylistWithTracks(playlistId: String): Flow<PlaylistWithTracks?>
}
