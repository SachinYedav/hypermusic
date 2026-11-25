package com.hypermusic.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hypermusic.core.data.local.entity.RecentCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentCollectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentCollectionEntity)

    @Query("SELECT * FROM RecentCollections ORDER BY lastVisitedAt DESC LIMIT :limit")
    fun getRecentCollections(limit: Int = 10): Flow<List<RecentCollectionEntity>>

    @Query("SELECT * FROM RecentCollections WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RecentCollectionEntity?

    @Query("SELECT COUNT(*) FROM RecentCollections")
    suspend fun getCount(): Int

    @Query("DELETE FROM RecentCollections WHERE id NOT IN (SELECT id FROM RecentCollections ORDER BY lastVisitedAt DESC LIMIT 20)")
    suspend fun pruneOld()

    @Query("DELETE FROM RecentCollections WHERE id = :id")
    suspend fun deleteById(id: String)
}
