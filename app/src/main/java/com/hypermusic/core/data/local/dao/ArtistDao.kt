package com.hypermusic.core.data.local.dao

import androidx.room.*
import com.hypermusic.core.data.local.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtist(artist: ArtistEntity)

    @Delete
    suspend fun deleteArtist(artist: ArtistEntity)

    @Query("SELECT * FROM Artists ORDER BY savedAt DESC")
    fun getAllArtists(): Flow<List<ArtistEntity>>
    
    @Query("SELECT * FROM Artists WHERE artistId = :artistId")
    suspend fun getArtistById(artistId: String): ArtistEntity?
}
