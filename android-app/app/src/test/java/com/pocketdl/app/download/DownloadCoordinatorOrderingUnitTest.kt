package com.pocketdl.app.download

import com.pocketdl.app.data.database.dao.DownloadTaskDao
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import com.pocketdl.app.data.repository.InMemorySettingsRepository
import com.pocketdl.app.download.service.ServiceForegroundResult
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadCoordinatorOrderingUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)
    private lateinit var fakeDao: FakeDownloadTaskDao
    private lateinit var fakeEngine: FakeDownloadEngine
    private lateinit var settingsRepo: InMemorySettingsRepository
    private lateinit var tempDir: File

    @Before
    fun setup() {
        fakeDao = FakeDownloadTaskDao()
        fakeEngine = FakeDownloadEngine()
        settingsRepo = InMemorySettingsRepository()
        tempDir = File.createTempFile("coord_order_test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    }

    @Test
    fun startTask_awaitsServiceForegroundBarrier_beforeStartingEngineDownload() = runTest(testDispatcher) {
        var serviceLauncherCalled = false
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            applicationContext = null,
            startServiceLauncher = {
                serviceLauncherCalled = true
            }
        )

        val task = createTask("t_barrier")
        fakeDao.insert(task)

        // Start task - barrier initially in Starting state
        coordinator.startTask("t_barrier")
        advanceTimeBy(100)

        assertTrue("Service launcher should have been triggered", serviceLauncherCalled)
        assertTrue(coordinator.serviceForegroundState.value is ServiceForegroundResult.Starting)
        assertEquals(0, fakeEngine.startCount) // Engine download NOT started yet!

        val startingState = coordinator.serviceForegroundState.value as ServiceForegroundResult.Starting
        val sessionId = startingState.sessionId

        // Signal Success on barrier
        coordinator.notifyServiceForegroundResult(ServiceForegroundResult.Success(sessionId), sessionId)
        advanceUntilIdle()

        // Engine download should now start!
        assertEquals(1, fakeEngine.startCount)
        assertTrue(coordinator.isRunning("t_barrier"))
    }

    @Test
    fun startTask_serviceStartFails_rollsBackRoomStatusToPausedWithoutOrphanWorker() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            applicationContext = null,
            startServiceLauncher = {
                throw SecurityException("Foreground service start restricted by OS")
            }
        )

        val task = createTask("t_fail")
        fakeDao.insert(task)

        coordinator.startTask("t_fail")
        advanceUntilIdle()

        // Engine must NOT be running
        assertFalse(coordinator.isRunning("t_fail"))
        assertEquals(0, fakeEngine.startCount)

        // Room status rolled back to PAUSED with failure message
        val updatedInDb = fakeDao.findById("t_fail")
        assertEquals(TaskStatus.PAUSED.name, updatedInDb?.status)
        assertTrue(updatedInDb?.statusText?.contains("Blocked") == true || updatedInDb?.statusText?.contains("Failed") == true)
    }

    @Test
    fun concurrentStarts_shareSingleServiceStartupAttempt() = runTest(testDispatcher) {
        var launcherCallCount = 0
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            applicationContext = null,
            startServiceLauncher = {
                launcherCallCount++
            }
        )

        val task1 = createTask("t1")
        val task2 = createTask("t2")
        fakeDao.insertAll(listOf(task1, task2))

        // Start both simultaneously while activeJobs is empty
        coordinator.startTask("t1")
        coordinator.startTask("t2")
        advanceTimeBy(100)

        // Launcher must be called EXACTLY ONCE for the shared attempt
        assertEquals(1, launcherCallCount)

        val startingState = coordinator.serviceForegroundState.value as ServiceForegroundResult.Starting
        val sessionId = startingState.sessionId

        // Signal success for that session
        coordinator.notifyServiceForegroundResult(ServiceForegroundResult.Success(sessionId), sessionId)
        advanceUntilIdle()

        // Both tasks should start executing cleanly
        assertTrue(coordinator.isRunning("t1"))
        assertTrue(coordinator.isRunning("t2"))
        assertEquals(2, coordinator.activeJobsCount.value)
    }

    @Test
    fun staleServiceResultCallback_ignoredByNewerStartupSession() = runTest(testDispatcher) {
        var launcherCallCount = 0
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            applicationContext = null,
            startServiceLauncher = {
                launcherCallCount++
            }
        )

        val task1 = createTask("t1")
        fakeDao.insert(task1)

        coordinator.startTask("t1")
        advanceTimeBy(100)

        val firstState = coordinator.serviceForegroundState.value as ServiceForegroundResult.Starting
        val session1 = firstState.sessionId

        // Simulate session 1 failing / timing out
        coordinator.notifyServiceForegroundResult(ServiceForegroundResult.Failed(session1, IOException("Session 1 Failed")), session1)
        advanceUntilIdle()
        assertFalse(coordinator.isRunning("t1"))

        // Start a new task, initiating session 2
        val task2 = createTask("t2")
        fakeDao.insert(task2)
        coordinator.startTask("t2")
        advanceTimeBy(100)

        assertEquals(2, launcherCallCount)
        val secondState = coordinator.serviceForegroundState.value as ServiceForegroundResult.Starting
        val session2 = secondState.sessionId
        assertTrue("Session 2 ID must be strictly greater than Session 1", session2 > session1)

        // Late callback from session 1 arrives!
        coordinator.notifyServiceForegroundResult(ServiceForegroundResult.Success(session1), session1)
        advanceTimeBy(100)

        // Session 2 state must NOT be mutated by late session 1 callback!
        assertEquals(secondState, coordinator.serviceForegroundState.value)
        assertFalse("Task 2 should not start on stale session 1 callback", coordinator.isRunning("t2"))

        // Valid session 2 callback arrives
        coordinator.notifyServiceForegroundResult(ServiceForegroundResult.Success(session2), session2)
        advanceUntilIdle()
        assertTrue("Task 2 should start on valid session 2 callback", coordinator.isRunning("t2"))
    }

    @Test
    fun sessionTeardown_allowsFreshServiceStartupSession() = runTest(testDispatcher) {
        var launcherCallCount = 0
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            applicationContext = null,
            startServiceLauncher = {
                launcherCallCount++
            }
        )

        val task1 = createTask("t1")
        fakeDao.insert(task1)

        coordinator.startTask("t1")
        advanceTimeBy(100)

        val session1 = (coordinator.serviceForegroundState.value as ServiceForegroundResult.Starting).sessionId
        coordinator.notifyServiceForegroundResult(ServiceForegroundResult.Success(session1), session1)
        advanceUntilIdle()

        assertTrue(coordinator.isRunning("t1"))
        fakeEngine.completeTask("t1")
        advanceUntilIdle()

        // Session 1 finished, activeJobs drops to 0, state resets to Idle
        assertFalse(coordinator.isRunning("t1"))
        assertEquals(0, coordinator.activeJobsCount.value)
        assertEquals(ServiceForegroundResult.Idle, coordinator.serviceForegroundState.value)

        // Start task 2 -> fresh session must be launched
        val task2 = createTask("t2")
        fakeDao.insert(task2)
        coordinator.startTask("t2")
        advanceTimeBy(100)

        assertEquals(2, launcherCallCount)
        val session2 = (coordinator.serviceForegroundState.value as ServiceForegroundResult.Starting).sessionId
        assertTrue(session2 > session1)
    }

    @Test
    fun startupTimeout_rollsBackWaitingTasksToPausedWithoutOrphanJobs() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            applicationContext = null,
            startServiceLauncher = {
                // Service launched but never calls notifyServiceForegroundResult (hangs)
            }
        )

        val task = createTask("t_timeout")
        fakeDao.insert(task)

        coordinator.startTask("t_timeout")
        // Advance time past 8,000 ms timeout
        advanceTimeBy(9_000)
        advanceUntilIdle()

        // Task must NOT be running
        assertFalse(coordinator.isRunning("t_timeout"))
        assertEquals(0, coordinator.activeJobsCount.value)

        // Task in DB rolled back to PAUSED
        val inDb = fakeDao.findById("t_timeout")
        assertEquals(TaskStatus.PAUSED.name, inDb?.status)
        assertTrue(inDb?.statusText?.contains("Timeout") == true || inDb?.statusText?.contains("Paused") == true)
    }

    @Test
    fun pauseRacingWithCompletion_preservesCompletedStatus() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        val task = createTask("t_race_pause")
        fakeDao.insert(task)

        coordinator.startTask("t_race_pause")
        advanceUntilIdle()

        assertTrue(coordinator.isRunning("t_race_pause"))

        // Complete task in engine AND attempt to pause simultaneously
        fakeEngine.completeTask("t_race_pause")
        coordinator.pauseTask("t_race_pause")
        advanceUntilIdle()

        // Task status in DB must remain COMPLETED, not overwritten to PAUSED
        val inDb = fakeDao.findById("t_race_pause")
        assertEquals(TaskStatus.COMPLETED.name, inDb?.status)
    }

    @Test
    fun cancelTaskRacingWithCompletion_preventsTaskResurrection() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        val task = createTask("t_race_cancel")
        fakeDao.insert(task)

        coordinator.startTask("t_race_cancel")
        advanceUntilIdle()

        // Cancel task (which deletes DB row) right as engine completes
        fakeEngine.completeTask("t_race_cancel")
        coordinator.cancelTask("t_race_cancel")
        advanceUntilIdle()

        // Task must be deleted and NOT re-inserted into DB
        assertNull(fakeDao.findById("t_race_cancel"))
        assertFalse(coordinator.isRunning("t_race_cancel"))
    }

    @Test
    fun unexpectedExceptionInWorker_alwaysCleansUpActiveJobsRegistry() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        val task = createTask("t_exc")
        fakeDao.insert(task)

        coordinator.startTask("t_exc")
        advanceUntilIdle()

        assertTrue(coordinator.isRunning("t_exc"))

        // Fail task with unexpected exception
        fakeEngine.failTask("t_exc", IOException("Disk I/O Error"))
        advanceUntilIdle()

        // Task must be cleaned up from activeJobs map
        assertFalse(coordinator.isRunning("t_exc"))
        assertEquals(0, coordinator.activeJobsCount.value)
        assertEquals(TaskStatus.FAILED.name, fakeDao.findById("t_exc")?.status)
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
        val destinationFiles = mutableMapOf<String, File>()
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
            destinationFiles[taskId] = destinationFile
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

        fun failTask(taskId: String, exception: Exception = IOException("HTTP error 403: Forbidden")) {
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
            id: String, status: String, statusText: String, progress: Float,
            downloadedSizeText: String, totalSizeText: String, speedText: String,
            etaText: String, localPath: String?, etag: String?, lastModified: String?,
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
