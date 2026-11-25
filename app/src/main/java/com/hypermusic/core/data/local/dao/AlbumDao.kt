package com.hypermusic.core.data.local.dao

import androidx.room.*
import com.hypermusic.core.data.local.entity.AlbumEntity
import com.hypermusic.core.data.local.entity.AlbumTrackCrossRef
import com.hypermusic.core.data.local.entity.AlbumWithTracks
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity)

    @Delete
    suspend fun deleteAlbum(album: AlbumEntity)

    @Query("SELECT * FROM Albums ORDER BY savedAt DESC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumTrackCrossRef(crossRef: AlbumTrackCrossRef)

    @Query("DELETE FROM AlbumTracks WHERE albumId = :albumId")
    suspend fun deleteAllAlbumTracks(albumId: String)

    @Transaction
    @Query("SELECT * FROM Albums WHERE albumId = :albumId")
    fun getAlbumWithTracks(albumId: String): Flow<AlbumWithTracks?>
}
