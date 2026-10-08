package com.pocketdl.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for download tasks, history, and queue state.
 */
@Dao
interface DownloadTaskDao {

    @Query("SELECT * FROM download_tasks ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    fun getById(id: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<DownloadTaskEntity>)

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET status = :status, status_text = :statusText, speed_text = :speedText, eta_text = :etaText WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, statusText: String, speedText: String, etaText: String)

    @Query("UPDATE download_tasks SET status = :toStatus, status_text = :statusText, speed_text = :speedText, eta_text = :etaText WHERE status IN (:fromStatuses)")
    suspend fun updateAllStatus(fromStatuses: List<String>, toStatus: String, statusText: String, speedText: String, etaText: String)

    @Query("UPDATE download_tasks SET status = :status, status_text = :statusText, progress = :progress, speed_text = :speedText, eta_text = :etaText WHERE id = :id")
    suspend fun retryTask(id: String, status: String, statusText: String, progress: Float, speedText: String, etaText: String)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM download_tasks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM download_tasks")
    suspend fun count(): Int
}
