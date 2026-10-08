package com.pocketdl.app.data.repository

import com.pocketdl.app.ui.mock.CapturedMediaMock
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing captured media.
 * Backed by in-memory mock data in Phase 3; will be backed by Room in Phase 5.
 */
interface CapturedMediaRepository : Repository {
    fun observeCapturedMedia(): Flow<List<CapturedMediaMock>>
    fun getCapturedMediaById(id: String): Flow<CapturedMediaMock?>
    fun deleteCapturedMedia(id: String)
    fun addCapturedMedia(item: CapturedMediaMock)
    fun clearAll()
}
