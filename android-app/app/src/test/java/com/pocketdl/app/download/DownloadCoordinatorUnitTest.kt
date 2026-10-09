package com.pocketdl.app.download

import com.pocketdl.app.data.database.dao.DownloadTaskDao
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import com.pocketdl.app.data.repository.InMemorySettingsRepository
import com.pocketdl.app.data.repository.SettingsData
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadCoordinatorUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)
    private lateinit var fakeDao: FakeDownloadTaskDao
    private lateinit var fakeEngine: FakeDownloadEngine
    private lateinit var settingsRepo: InMemorySettingsRepository
    private lateinit var coordinator: DownloadCoordinator
    private lateinit var tempDir: File

    @Before
    fun setup() {
        fakeDao = FakeDownloadTaskDao()
        fakeEngine = FakeDownloadEngine()
        settingsRepo = InMemorySettingsRepository(
            initialSettings = SettingsData(maxParallelDownloads = 2)
        )
        tempDir = File.createTempFile("coord_test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun coordinator_respectsMaxParallelLimit() = runTest(testDispatcher) {
        // Given 3 tasks
        val task1 = createTask("t1")
        val task2 = createTask("t2")
        val task3 = createTask("t3")
        fakeDao.insertAll(listOf(task1, task2, task3))

        // When starting all 3 with maxParallel = 2
        coordinator.startTask("t1")
        coordinator.startTask("t2")
        coordinator.startTask("t3")
        advanceUntilIdle()

        // Then only 2 should be actively running, and 3rd remains in QUEUED
        assertTrue(coordinator.isRunning("t1"))
        assertTrue(coordinator.isRunning("t2"))
        assertFalse(coordinator.isRunning("t3"))
        assertEquals(TaskStatus.QUEUED.name, fakeDao.findById("t3")?.status)
    }

    @Test
    fun coordinator_preventsDuplicateExecution() = runTest(testDispatcher) {
        val task1 = createTask("t1")
        fakeDao.insert(task1)

        coordinator.startTask("t1")
        advanceUntilIdle()
        assertTrue(coordinator.isRunning("t1"))
        assertEquals(1, fakeEngine.startCount)

        // Starting again while already running should be a no-op
        coordinator.startTask("t1")
        advanceUntilIdle()
        assertEquals(1, fakeEngine.startCount)
    }

    @Test
    fun coordinator_autoAdvancesQueueWhenActiveTaskFinishes() = runTest(testDispatcher) {
        val task1 = createTask("t1")
        val task2 = createTask("t2")
        val task3 = createTask("t3")
        fakeDao.insertAll(listOf(task1, task2, task3))

        coordinator.startTask("t1")
        coordinator.startTask("t2")
        coordinator.startTask("t3")
        advanceUntilIdle()

        // Complete task1
        fakeEngine.completeTask("t1")
        advanceUntilIdle()

        // task1 is now completed, task3 should auto-start!
        assertFalse(coordinator.isRunning("t1"))
        assertEquals(TaskStatus.COMPLETED.name, fakeDao.findById("t1")?.status)
        assertTrue(coordinator.isRunning("t3"))
        assertEquals(TaskStatus.DOWNLOADING.name, fakeDao.findById("t3")?.status)
    }

    @Test
    fun coordinator_pauseAll_pausesAllActiveTasks() = runTest(testDispatcher) {
        val task1 = createTask("t1")
        val task2 = createTask("t2")
        fakeDao.insertAll(listOf(task1, task2))

        coordinator.startTask("t1")
        coordinator.startTask("t2")
        advanceUntilIdle()

        assertTrue(coordinator.isRunning("t1"))
        assertTrue(coordinator.isRunning("t2"))

        coordinator.pauseAll()
        advanceUntilIdle()

        assertFalse(coordinator.isRunning("t1"))
        assertFalse(coordinator.isRunning("t2"))
        assertEquals(TaskStatus.PAUSED.name, fakeDao.findById("t1")?.status)
        assertEquals(TaskStatus.PAUSED.name, fakeDao.findById("t2")?.status)
    }

    @Test
    fun coordinator_taskFailure_marksStatusFailedAndAdvancesQueue() = runTest(testDispatcher) {
        val task1 = createTask("t1")
        val task2 = createTask("t2")
        fakeDao.insertAll(listOf(task1, task2))

        coordinator.startTask("t1")
        coordinator.startTask("t2")
        advanceUntilIdle()

        assertTrue(coordinator.isRunning("t1"))
        assertTrue(coordinator.isRunning("t2"))

        // Fail task1 with HTTP 403
        fakeEngine.failTask("t1", java.io.IOException("HTTP error 403: Forbidden"))
        advanceUntilIdle()

        // task1 is marked FAILED and no longer running
        assertFalse(coordinator.isRunning("t1"))
        val failedTask = fakeDao.findById("t1")
        assertEquals(TaskStatus.FAILED.name, failedTask?.status)
        assertTrue(failedTask?.statusText?.contains("403") == true)

        // task2 continues running
        assertTrue(coordinator.isRunning("t2"))
    }

    private fun createTask(id: String) = DownloadTaskEntity(
        id = id,
        title = "Video $id",
        sourceDomain = "example.com",
        sourceUrl = "https://example.com/$id.mp4",
        status = TaskStatus.QUEUED.name,
        statusText = "Queued",
        progress = 0f,
        downloadedSizeText = "0 MB",
        totalSizeText = "100 MB",
        speedText = "0 KB/s",
        etaText = "--:--",
        resolutionBadge = "1080p",
        codecBadge = "H.264",
        createdAt = System.currentTimeMillis()
    )

    private class FakeDownloadEngine : DownloadEngine {
        var startCount = 0
        private val runningDeferreds = mutableMapOf<String, CompletableDeferred<DownloadResult>>()

        override fun isRunning(taskId: String): Boolean = runningDeferreds.containsKey(taskId)

        override suspend fun download(
            taskId: String,
            url: String,
            destinationFile: File,
            existingEtag: String?,
            existingLastModified: String?,
            onProgress: suspend (DownloadProgressUpdate) -> Unit
        ): DownloadResult {
            startCount++
            val deferred = CompletableDeferred<DownloadResult>()
            runningDeferreds[taskId] = deferred
            val result = deferred.await()
            runningDeferreds.remove(taskId)
            return result
        }

        fun completeTask(taskId: String) {
            runningDeferreds[taskId]?.complete(
                DownloadResult.Success(File("/dummy/$taskId.mp4"), 1000L)
            )
        }

        fun failTask(taskId: String, exception: Exception = java.io.IOException("HTTP error 403: Forbidden")) {
            runningDeferreds[taskId]?.complete(
                DownloadResult.Failure(exception)
            )
        }

        override fun pause(taskId: String) {
            runningDeferreds[taskId]?.complete(DownloadResult.Paused)
        }

        override fun cancel(taskId: String) {
            runningDeferreds[taskId]?.complete(DownloadResult.Cancelled)
        }
    }

    private class FakeDownloadTaskDao : DownloadTaskDao {
        private val tasks = mutableMapOf<String, DownloadTaskEntity>()
        private val tasksFlow = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())

        override fun observeAll(): Flow<List<DownloadTaskEntity>> = tasksFlow.asStateFlow()
        override fun getById(id: String): Flow<DownloadTaskEntity?> = MutableStateFlow(tasks[id])
        override suspend fun findById(id: String): DownloadTaskEntity? = tasks[id]

        override suspend fun insert(task: DownloadTaskEntity) {
            tasks[task.id] = task
            tasksFlow.value = tasks.values.toList()
        }

        override suspend fun insertAll(tasks: List<DownloadTaskEntity>) {
            tasks.forEach { this.tasks[it.id] = it }
            tasksFlow.value = this.tasks.values.toList()
        }

        override suspend fun update(task: DownloadTaskEntity) {
            tasks[task.id] = task
            tasksFlow.value = tasks.values.toList()
        }

        override suspend fun updateStatus(id: String, status: String, statusText: String, speedText: String, etaText: String) {
            tasks[id]?.let { existing ->
                tasks[id] = existing.copy(status = status, statusText = statusText, speedText = speedText, etaText = etaText)
                tasksFlow.value = tasks.values.toList()
            }
        }

        override suspend fun updateAllStatus(fromStatuses: List<String>, toStatus: String, statusText: String, speedText: String, etaText: String) {
            tasks.values.filter { it.status in fromStatuses }.forEach {
                tasks[it.id] = it.copy(status = toStatus, statusText = statusText, speedText = speedText, etaText = etaText)
            }
            tasksFlow.value = tasks.values.toList()
        }

        override suspend fun retryTask(id: String, status: String, statusText: String, progress: Float, speedText: String, etaText: String) {
            tasks[id]?.let {
                tasks[id] = it.copy(status = status, statusText = statusText, progress = progress, speedText = speedText, etaText = etaText)
                tasksFlow.value = tasks.values.toList()
            }
        }

        override suspend fun findOldestQueued(limit: Int): List<DownloadTaskEntity> {
            return tasks.values
                .filter { it.status == TaskStatus.QUEUED.name }
                .sortedBy { it.createdAt }
                .take(limit)
        }

        override suspend fun updateProgress(
            id: String,
            status: String,
            statusText: String,
            progress: Float,
            downloadedSizeText: String,
            totalSizeText: String,
            speedText: String,
            etaText: String,
            localPath: String?,
            etag: String?,
            lastModified: String?,
            completedAt: Long?
        ) {
            tasks[id]?.let {
                tasks[id] = it.copy(
                    status = status,
                    statusText = statusText,
                    progress = progress,
                    downloadedSizeText = downloadedSizeText,
                    totalSizeText = totalSizeText,
                    speedText = speedText,
                    etaText = etaText,
                    localPath = localPath ?: it.localPath,
                    etag = etag ?: it.etag,
                    lastModified = lastModified ?: it.lastModified,
                    completedAt = completedAt ?: it.completedAt
                )
                tasksFlow.value = tasks.values.toList()
            }
        }

        override suspend fun deleteById(id: String) {
            tasks.remove(id)
            tasksFlow.value = tasks.values.toList()
        }

        override suspend fun deleteByIds(ids: List<String>) {
            ids.forEach { tasks.remove(it) }
            tasksFlow.value = tasks.values.toList()
        }

        override suspend fun deleteAll() {
            tasks.clear()
            tasksFlow.value = emptyList()
        }

        override suspend fun count(): Int = tasks.size
    }
}
