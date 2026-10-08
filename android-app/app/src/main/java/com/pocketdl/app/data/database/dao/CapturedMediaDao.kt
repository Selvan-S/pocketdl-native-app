package com.pocketdl.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketdl.app.data.database.entity.CapturedMediaEntity
import com.pocketdl.app.data.database.entity.MediaVariantEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for captured media and associated quality stream variants.
 */
@Dao
interface CapturedMediaDao {

    @Query("SELECT * FROM captured_media ORDER BY created_at DESC")
    fun observeAll(): Flow<List<CapturedMediaEntity>>

    @Query("SELECT * FROM captured_media WHERE id = :id LIMIT 1")
    fun getById(id: String): Flow<CapturedMediaEntity?>

    @Query("SELECT * FROM captured_media WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CapturedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CapturedMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CapturedMediaEntity>)

    @Query("DELETE FROM captured_media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM captured_media")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM captured_media")
    suspend fun count(): Int

    // Media Variants
    @Query("SELECT * FROM media_variants WHERE captured_media_id = :capturedMediaId")
    fun observeVariantsForMedia(capturedMediaId: String): Flow<List<MediaVariantEntity>>

    @Query("SELECT * FROM media_variants WHERE captured_media_id = :capturedMediaId")
    suspend fun getVariantsForMedia(capturedMediaId: String): List<MediaVariantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<MediaVariantEntity>)

    @Query("DELETE FROM media_variants WHERE captured_media_id = :capturedMediaId")
    suspend fun deleteVariantsForMedia(capturedMediaId: String)
}
