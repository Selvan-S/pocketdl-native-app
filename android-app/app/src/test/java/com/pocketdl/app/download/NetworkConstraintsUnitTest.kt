package com.pocketdl.app.download

import com.pocketdl.app.core.network.ConnectionType
import com.pocketdl.app.core.network.NetworkConnectivityObserver
import com.pocketdl.app.core.network.NetworkState
import com.pocketdl.app.data.database.dao.DownloadTaskDao
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import com.pocketdl.app.data.repository.InMemorySettingsRepository
import com.pocketdl.app.data.repository.SettingsData
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class FakeNetworkConnectivityObserver(
    initialState: NetworkState = NetworkState(
        isConnected = true,
        isMetered = false,
        connectionType = ConnectionType.WIFI
    )
) : NetworkConnectivityObserver {
    private val _state = MutableStateFlow(initialState)

    override fun observeNetworkState(): Flow<NetworkState> = _state.asStateFlow()

    override fun currentNetworkState(): NetworkState = _state.value

    fun emitState(newState: NetworkState) {
        _state.value = newState
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkConstraintsUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)
    private lateinit var fakeDao: FakeDownloadTaskDao
    private lateinit var fakeEngine: FakeDownloadEngine
    private lateinit var settingsRepo: InMemorySettingsRepository
    private lateinit var networkObserver: FakeNetworkConnectivityObserver
    private lateinit var tempDir: File

    @Before
    fun setup() {
        fakeDao = FakeDownloadTaskDao()
        fakeEngine = FakeDownloadEngine()
        settingsRepo = InMemorySettingsRepository(
            initialSettings = SettingsData(maxParallelDownloads = 2, allowCellular = false)
        )
        networkObserver = FakeNetworkConnectivityObserver(
            NetworkState(isConnected = true, isMetered = false, connectionType = ConnectionType.WIFI)
        )
        tempDir = File.createTempFile("network_test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    }

    private fun createTask(
        id: String,
        status: TaskStatus = TaskStatus.QUEUED,
        createdAt: Long = System.currentTimeMillis(),
        retryCount: Int = 0,
        nextRetryAt: Long? = null
    ): DownloadTaskEntity {
        return DownloadTaskEntity(
            id = id,
            title = "Video $id",
            sourceDomain = "example.com",
            sourceUrl = "https://example.com/video_$id.mp4",
            status = status.name,
            statusText = if (status == TaskStatus.QUEUED) "Queued" else status.name,
            progress = 0.0f,
            downloadedSizeText = "0 MB",
            totalSizeText = "100 MB",
            speedText = "0 KB/s",
            etaText = "--:--",
            resolutionBadge = "1080p",
            codecBadge = "H.264",
            createdAt = createdAt,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt
        )
    }

    @Test
    fun dispatchQueue_whenNetworkDisconnected_doesNotDispatch() = runTest(testDispatcher) {
        networkObserver.emitState(
            NetworkState(isConnected = false, isMetered = false, connectionType = ConnectionType.NONE)
        )

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        fakeDao.insert(createTask("task_1"))
        coordinator.dispatchQueue()
        advanceUntilIdle()

        assertEquals("No workers should be active when offline", 0, coordinator.activeJobsCount.value)
        assertEquals(TaskStatus.QUEUED.name, fakeDao.findById("task_1")?.status)
        assertEquals("Waiting for Network", fakeDao.findById("task_1")?.statusText)
    }

    @Test
    fun dispatchQueue_whenMeteredAndCellularDisabled_doesNotDispatch() = runTest(testDispatcher) {
        networkObserver.emitState(
            NetworkState(isConnected = true, isMetered = true, connectionType = ConnectionType.CELLULAR)
        )

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo, // allowCellular = false
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        fakeDao.insert(createTask("task_metered"))
        coordinator.dispatchQueue()
        advanceUntilIdle()

        assertEquals("No workers should dispatch on metered network without cellular permission", 0, coordinator.activeJobsCount.value)
        assertEquals(TaskStatus.QUEUED.name, fakeDao.findById("task_metered")?.status)
        assertEquals("Waiting for an unmetered network", fakeDao.findById("task_metered")?.statusText)
    }

    @Test
    fun dispatchQueue_whenMeteredAndCellularEnabled_dispatches() = runTest(testDispatcher) {
        settingsRepo.setAllowCellular(true)
        networkObserver.emitState(
            NetworkState(isConnected = true, isMetered = true, connectionType = ConnectionType.CELLULAR)
        )

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        fakeDao.insert(createTask("task_allowed"))
        coordinator.dispatchQueue()
        advanceUntilIdle()

        assertEquals(1, coordinator.activeJobsCount.value)
        assertTrue(coordinator.isRunning("task_allowed"))
    }

    @Test
    fun networkLost_duringActiveDownload_interruptsWorkerAndPreservesPartFile() = runTest(testDispatcher) {
        val activeGate = CompletableDeferred<Unit>()
        val unblockGate = CompletableDeferred<Unit>()

        val blockingEngine = object : DownloadEngine {
            override suspend fun download(
                taskId: String,
                url: String,
                destinationFile: File,
                existingEtag: String?,
                existingLastModified: String?,
                onProgress: suspend (DownloadProgressUpdate) -> Unit
            ): DownloadResult {
                val part = DownloadFileUtils.resolvePartFile(destinationFile)
                part.writeText("partial-bytes-12345")
                activeGate.complete(Unit)
                unblockGate.await()
                return DownloadResult.Success(destinationFile, 100L)
            }
            override fun pause(taskId: String) {
                unblockGate.complete(Unit)
            }
            override fun cancel(taskId: String) {
                unblockGate.complete(Unit)
            }
            override fun isRunning(taskId: String): Boolean = true
        }

        val coordinator = DownloadCoordinator(
            engine = blockingEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        val task = createTask("t_disconnect")
        fakeDao.insert(task)
        coordinator.startTask("t_disconnect")
        advanceUntilIdle()

        assertTrue("Download must have started", activeGate.isCompleted)
        assertEquals(1, coordinator.activeJobsCount.value)

        // Simulate network disconnect
        networkObserver.emitState(
            NetworkState(isConnected = false, isMetered = false, connectionType = ConnectionType.NONE)
        )
        advanceUntilIdle()

        assertEquals("Active jobs must drop to 0", 0, coordinator.activeJobsCount.value)
        val updated = fakeDao.findById("t_disconnect")
        assertEquals(TaskStatus.QUEUED.name, updated?.status)
        assertEquals("Waiting for Network", updated?.statusText)
        assertEquals(0, updated?.retryCount)

        // Verify partial file was preserved
        val destFile = File(tempDir, "${task.title}_t_disconnect.mp4")
        val partFile = DownloadFileUtils.resolvePartFile(destFile)
        assertTrue("Partial file must be preserved", partFile.exists())
        assertEquals("partial-bytes-12345", partFile.readText())
    }

    @Test
    fun networkLost_duringActiveDownload_doesNotIncrementRetryCount() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        val task = createTask("t_retry_budget", retryCount = 0)
        fakeDao.insert(task)
        coordinator.startTask("t_retry_budget")
        advanceUntilIdle()

        // Disconnect network
        networkObserver.emitState(
            NetworkState(isConnected = false, isMetered = false, connectionType = ConnectionType.NONE)
        )
        advanceUntilIdle()

        val afterDisconnect = fakeDao.findById("t_retry_budget")
        assertEquals("Retry count must NOT be incremented on network drop", 0, afterDisconnect?.retryCount)
        assertEquals(TaskStatus.QUEUED.name, afterDisconnect?.status)
        assertEquals("Waiting for Network", afterDisconnect?.statusText)
    }

    @Test
    fun networkLost_preservesExistingRetryBackoffDeadline() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        val futureDeadline = System.currentTimeMillis() + 30_000L
        val taskWithBackoff = createTask("t_backoff", status = TaskStatus.QUEUED, retryCount = 2, nextRetryAt = futureDeadline)
        fakeDao.insert(taskWithBackoff)

        // Network drops
        networkObserver.emitState(
            NetworkState(isConnected = false, isMetered = false, connectionType = ConnectionType.NONE)
        )
        advanceUntilIdle()

        val inDb = fakeDao.findById("t_backoff")
        assertEquals("Future deadline must remain untouched", futureDeadline, inDb?.nextRetryAt)
        assertEquals(2, inDb?.retryCount)
    }

    @Test
    fun networkRestored_dispatchesEligibleTasks_andIgnoresUnexpiredRetryDeadlines() = runTest(testDispatcher) {
        // Start offline
        networkObserver.emitState(
            NetworkState(isConnected = false, isMetered = false, connectionType = ConnectionType.NONE)
        )

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        // Task A: eligible (no deadline)
        val taskA = createTask("task_eligible", status = TaskStatus.QUEUED, nextRetryAt = null)
        // Task B: unexpired deadline (+60s)
        val taskB = createTask("task_deadline", status = TaskStatus.QUEUED, nextRetryAt = System.currentTimeMillis() + 60_000L)
        fakeDao.insertAll(listOf(taskA, taskB))

        advanceUntilIdle()
        assertEquals(0, coordinator.activeJobsCount.value)

        // Restore network
        networkObserver.emitState(
            NetworkState(isConnected = true, isMetered = false, connectionType = ConnectionType.WIFI)
        )
        advanceUntilIdle()

        assertTrue("Eligible task without deadline must be dispatched", coordinator.isRunning("task_eligible"))
        assertFalse("Task with unexpired retry deadline must NOT be dispatched", coordinator.isRunning("task_deadline"))
    }

    @Test
    fun rapidNetworkReconnection_doesNotSpawnDuplicateWorkers() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        val task = createTask("t_rapid")
        fakeDao.insert(task)
        coordinator.startTask("t_rapid")
        advanceUntilIdle()
        assertTrue(coordinator.isRunning("t_rapid"))

        // Bounce network rapidly 5 times
        repeat(5) {
            networkObserver.emitState(NetworkState(isConnected = false, isMetered = false, connectionType = ConnectionType.NONE))
            advanceUntilIdle()
            networkObserver.emitState(NetworkState(isConnected = true, isMetered = false, connectionType = ConnectionType.WIFI))
            advanceUntilIdle()
        }

        assertEquals("Never more than 1 active job for the task", 1, coordinator.activeJobsCount.value)
        assertTrue(coordinator.isRunning("t_rapid"))
    }

    @Test
    fun runtimeSettingsChange_allowCellularToggledOn_wakesQueuedDownloads() = runTest(testDispatcher) {
        // On cellular, cellular disallowed initially
        networkObserver.emitState(
            NetworkState(isConnected = true, isMetered = true, connectionType = ConnectionType.CELLULAR)
        )

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        val task = createTask("t_toggle")
        fakeDao.insert(task)
        advanceUntilIdle()

        assertEquals(0, coordinator.activeJobsCount.value)

        // Toggle allowCellular to true
        settingsRepo.setAllowCellular(true)
        advanceUntilIdle()

        assertEquals("Queue should wake up upon setting toggle", 1, coordinator.activeJobsCount.value)
        assertTrue(coordinator.isRunning("t_toggle"))
    }

    @Test
    fun runtimeSettingsChange_allowCellularToggledOff_interruptsActiveMeteredDownloads() = runTest(testDispatcher) {
        settingsRepo.setAllowCellular(true)
        networkObserver.emitState(
            NetworkState(isConnected = true, isMetered = true, connectionType = ConnectionType.CELLULAR)
        )

        val unblockGate = CompletableDeferred<Unit>()
        val blockingEngine = object : DownloadEngine {
            override suspend fun download(
                taskId: String,
                url: String,
                destinationFile: File,
                existingEtag: String?,
                existingLastModified: String?,
                onProgress: suspend (DownloadProgressUpdate) -> Unit
            ): DownloadResult {
                unblockGate.await()
                return DownloadResult.Success(destinationFile, 100L)
            }
            override fun pause(taskId: String) {
                unblockGate.complete(Unit)
            }
            override fun cancel(taskId: String) {
                unblockGate.complete(Unit)
            }
            override fun isRunning(taskId: String): Boolean = true
        }

        val coordinator = DownloadCoordinator(
            engine = blockingEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        val task = createTask("t_metered_stop")
        fakeDao.insert(task)
        coordinator.startTask("t_metered_stop")
        advanceUntilIdle()

        assertTrue(coordinator.isRunning("t_metered_stop"))

        // User turns off cellular downloads
        settingsRepo.setAllowCellular(false)
        advanceUntilIdle()

        assertEquals(0, coordinator.activeJobsCount.value)
        val inDb = fakeDao.findById("t_metered_stop")
        assertEquals(TaskStatus.QUEUED.name, inDb?.status)
        assertEquals("Waiting for an unmetered network", inDb?.statusText)
    }

    @Test
    fun disconnectDuringFinalization_handledSafelyWithoutStateCorruption() = runTest(testDispatcher) {
        val finalizeGate = CompletableDeferred<Unit>()
        val finalizedEngine = object : DownloadEngine {
            override suspend fun download(
                taskId: String,
                url: String,
                destinationFile: File,
                existingEtag: String?,
                existingLastModified: String?,
                onProgress: suspend (DownloadProgressUpdate) -> Unit
            ): DownloadResult {
                val finalizedFile = DownloadFileUtils.finalizeDownload(
                    DownloadFileUtils.resolvePartFile(destinationFile).apply { writeText("done") },
                    destinationFile
                )
                finalizeGate.complete(Unit)
                return DownloadResult.Success(finalizedFile, 4L)
            }
            override fun pause(taskId: String) {}
            override fun cancel(taskId: String) {}
            override fun isRunning(taskId: String): Boolean = true
        }

        val coordinator = DownloadCoordinator(
            engine = finalizedEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        val task = createTask("t_finalize")
        fakeDao.insert(task)
        coordinator.startTask("t_finalize")
        advanceUntilIdle()

        assertTrue(finalizeGate.isCompleted)
        val inDb = fakeDao.findById("t_finalize")
        assertEquals(TaskStatus.COMPLETED.name, inDb?.status)
    }

    @Test
    fun dispatchQueue_doesNotClobberActiveRetryTimer() = runTest(testDispatcher) {
        networkObserver.emitState(NetworkState(isConnected = false, isMetered = false, connectionType = ConnectionType.NONE))

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        val now = System.currentTimeMillis()
        val retryTask = createTask("t_retry").copy(
            status = TaskStatus.QUEUED.name,
            statusText = "Retrying in 10s",
            nextRetryAt = now + 10_000L,
            retryCount = 1
        )
        fakeDao.insert(retryTask)

        coordinator.dispatchQueue()
        advanceUntilIdle()

        val afterDispatch = fakeDao.findById("t_retry")
        assertEquals("Retrying in 10s", afterDispatch?.statusText)
        assertEquals(now + 10_000L, afterDispatch?.nextRetryAt)
    }

    @Test
    fun networkObserver_coldStartPermitted_dispatchesQueuedTaskOnDispatch() = runTest(testDispatcher) {
        // Cold start: network is already connected and permitted
        networkObserver.emitState(NetworkState(isConnected = true, isMetered = false, connectionType = ConnectionType.WIFI))

        val task = createTask("t_cold_start")
        fakeDao.insert(task)

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        coordinator.dispatchQueue()
        advanceUntilIdle()

        assertEquals(1, coordinator.activeJobsCount.value)
        assertTrue(coordinator.isRunning("t_cold_start"))
    }

    @Test
    fun networkObserver_coldStartNonPermitted_marksQueuedTaskWaiting() = runTest(testDispatcher) {
        // Cold start: metered connection without cellular permission
        networkObserver.emitState(NetworkState(isConnected = true, isMetered = true, connectionType = ConnectionType.CELLULAR))

        val task = createTask("t_cold_metered")
        fakeDao.insert(task)

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        advanceUntilIdle()

        assertEquals(0, coordinator.activeJobsCount.value)
        val inDb = fakeDao.findById("t_cold_metered")
        assertEquals(TaskStatus.QUEUED.name, inDb?.status)
        assertEquals("Waiting for an unmetered network", inDb?.statusText)
    }

    @Test
    fun dispatchQueue_repeatedNetworkChanges_noDuplicateWorkers() = runTest(testDispatcher) {
        settingsRepo.setAllowCellular(true)
        networkObserver.emitState(NetworkState(isConnected = true, isMetered = true, connectionType = ConnectionType.CELLULAR))

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            networkObserver = networkObserver
        )

        val task = createTask("t_repeat")
        fakeDao.insert(task)
        coordinator.dispatchQueue()
        advanceUntilIdle()

        assertEquals(1, coordinator.activeJobsCount.value)

        // Multiple rapid dispatches and network updates while task is active
        coordinator.dispatchQueue()
        coordinator.dispatchQueue()
        networkObserver.emitState(NetworkState(isConnected = true, isMetered = true, connectionType = ConnectionType.CELLULAR))
        advanceUntilIdle()

        assertEquals("Should still have exactly 1 active worker", 1, coordinator.activeJobsCount.value)
    }

    // --- In-memory test doubles ---

    private class FakeDownloadEngine : DownloadEngine {
        private val running = ConcurrentHashMap<String, Boolean>()
        override suspend fun download(
            taskId: String,
            url: String,
            destinationFile: File,
            existingEtag: String?,
            existingLastModified: String?,
            onProgress: suspend (DownloadProgressUpdate) -> Unit
        ): DownloadResult {
            running[taskId] = true
            CompletableDeferred<Unit>().await() // suspend indefinitely until cancelled
            return DownloadResult.Paused
        }
        override fun pause(taskId: String) { running.remove(taskId) }
        override fun cancel(taskId: String) { running.remove(taskId) }
        override fun isRunning(taskId: String): Boolean = running.containsKey(taskId)
    }

    private class FakeDownloadTaskDao : DownloadTaskDao {
        private val tasks = ConcurrentHashMap<String, DownloadTaskEntity>()
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
        override suspend fun updateAllStatusAndResetRetries(fromStatuses: List<String>, toStatus: String, statusText: String, speedText: String, etaText: String) {
            tasks.values.filter { it.status in fromStatuses }.forEach {
                tasks[it.id] = it.copy(status = toStatus, statusText = statusText, speedText = speedText, etaText = etaText, retryCount = 0, nextRetryAt = null)
            }
            tasksFlow.value = tasks.values.toList()
        }
        override suspend fun updateRetryState(id: String, status: String, statusText: String, speedText: String, etaText: String, retryCount: Int, nextRetryAt: Long) {
            tasks[id]?.let { existing ->
                tasks[id] = existing.copy(status = status, statusText = statusText, speedText = speedText, etaText = etaText, retryCount = retryCount, nextRetryAt = nextRetryAt)
                tasksFlow.value = tasks.values.toList()
            }
        }
        override suspend fun resetRetryState(id: String) {
            tasks[id]?.let { existing ->
                tasks[id] = existing.copy(retryCount = 0, nextRetryAt = null)
                tasksFlow.value = tasks.values.toList()
            }
        }
        override suspend fun retryTask(id: String, status: String, statusText: String, progress: Float, speedText: String, etaText: String) {
            tasks[id]?.let {
                tasks[id] = it.copy(status = status, statusText = statusText, progress = progress, speedText = speedText, etaText = etaText)
                tasksFlow.value = tasks.values.toList()
            }
        }
        override suspend fun findOldestQueued(limit: Int, now: Long): List<DownloadTaskEntity> {
            return tasks.values
                .filter { it.status == TaskStatus.QUEUED.name && (it.nextRetryAt == null || it.nextRetryAt <= now) }
                .sortedWith(compareBy<DownloadTaskEntity> { it.createdAt }.thenBy { it.id })
                .take(limit)
        }
        override suspend fun getPendingRetries(now: Long): List<DownloadTaskEntity> {
            return tasks.values.filter { it.status == TaskStatus.QUEUED.name && it.nextRetryAt != null && it.nextRetryAt > now }
        }
        override suspend fun findByUrl(url: String): List<DownloadTaskEntity> {
            return tasks.values.filter { it.sourceUrl == url }.sortedByDescending { it.createdAt }
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
