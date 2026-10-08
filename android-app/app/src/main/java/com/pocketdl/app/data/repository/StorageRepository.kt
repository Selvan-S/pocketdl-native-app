package com.pocketdl.app.data.repository

import com.pocketdl.app.ui.mock.StorageUsageMock
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing device storage calculations and cleanup allocations.
 */
interface StorageRepository : Repository {
    fun observeStorageUsage(): Flow<StorageUsageMock>
    fun recalculateStorage(freedGb: Double)
}
