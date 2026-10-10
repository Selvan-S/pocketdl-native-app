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

    @Query("SELECT * FROM download_tasks WHERE status = 'QUEUED' ORDER BY created_at ASC, id ASC LIMIT :limit")
    suspend fun findOldestQueued(limit: Int): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE source_url = :url ORDER BY created_at DESC")
    suspend fun findByUrl(url: String): List<DownloadTaskEntity>

    @Query("UPDATE download_tasks SET status = :status, status_text = :statusText, progress = :progress, downloaded_size_text = :downloadedSizeText, total_size_text = :totalSizeText, speed_text = :speedText, eta_text = :etaText, local_path = COALESCE(:localPath, local_path), etag = COALESCE(:etag, etag), last_modified = COALESCE(:lastModified, last_modified), completed_at = COALESCE(:completedAt, completed_at) WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        status: String,
        statusText: String,
        progress: Float,
        downloadedSizeText: String,
        totalSizeText: String,
        speedText: String,
        etaText: String,
        localPath: String? = null,
        etag: String? = null,
        lastModified: String? = null,
        completedAt: Long? = null
    )

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM download_tasks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM download_tasks")
    suspend fun count(): Int
}
