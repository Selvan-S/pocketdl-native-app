package com.pocketdl.app.data.repository

import com.pocketdl.app.data.database.dao.CapturedMediaDao
import com.pocketdl.app.data.database.toDomain
import com.pocketdl.app.data.database.toEntity
import com.pocketdl.app.ui.mock.CapturedMediaMock
import com.pocketdl.app.ui.mock.MockDataProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Room-backed persistent implementation of [CapturedMediaRepository].
 */
class RoomCapturedMediaRepository(
    private val capturedMediaDao: CapturedMediaDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CapturedMediaRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        repositoryScope.launch {
            if (capturedMediaDao.count() == 0) {
                // Seed initial mock data on first launch
                val initialEntities = MockDataProvider.sampleCapturedList.mapIndexed { index, mock ->
                    mock.toEntity(createdAt = System.currentTimeMillis() - (index * 60_000L))
                }
                capturedMediaDao.insertAll(initialEntities)

                // Seed sample quality variants for the primary sample items
                val sampleVariants = MockDataProvider.sampleQualityOptions.map {
                    it.toEntity(capturedMediaId = "cap_1")
                }
                capturedMediaDao.insertVariants(sampleVariants)
            }
        }
    }

    override fun observeCapturedMedia(): Flow<List<CapturedMediaMock>> =
        capturedMediaDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getCapturedMediaById(id: String): Flow<CapturedMediaMock?> =
        capturedMediaDao.getById(id).map { it?.toDomain() }

    override fun deleteCapturedMedia(id: String) {
        repositoryScope.launch {
            capturedMediaDao.deleteById(id)
        }
    }

    override fun addCapturedMedia(item: CapturedMediaMock) {
        repositoryScope.launch {
            capturedMediaDao.insert(item.toEntity())
        }
    }

    override fun clearAll() {
        repositoryScope.launch {
            capturedMediaDao.deleteAll()
        }
    }
}
