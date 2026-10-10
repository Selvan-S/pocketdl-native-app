package com.pocketdl.app.download

import com.pocketdl.app.data.database.dao.DownloadTaskDao
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import com.pocketdl.app.data.repository.InMemorySettingsRepository
import com.pocketdl.app.data.repository.RoomDownloadRepository
import com.pocketdl.app.data.repository.SettingsData
import com.pocketdl.app.download.service.ServiceForegroundResult
import com.pocketdl.app.ui.mock.QualityOptionMock
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueHardeningUnitTest {

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
        settingsRepo = InMemorySettingsRepository(
            initialSettings = SettingsData(maxParallelDownloads = 2)
        )
        tempDir = File.createTempFile("hardening_test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    }

    @Test
    fun fifoDispatch_ordersDeterministicallyByCreatedAtThenId() = runTest(testDispatcher) {
        val sameTimestamp = 1_700_000_000_000L
        val taskC = createTask("task_C", createdAt = sameTimestamp)
        val taskA = createTask("task_A", createdAt = sameTimestamp)
        val taskB = createTask("task_B", createdAt = sameTimestamp)

        fakeDao.insertAll(listOf(taskC, taskA, taskB))

        // Query oldest 3: secondary tie-breaker id ASC guarantees [task_A, task_B, task_C]
        val oldest = fakeDao.findOldestQueued(3)
        assertEquals(listOf("task_A", "task_B", "task_C"), oldest.map { it.id })

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo, // maxParallel = 2
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        coordinator.startAll()
        advanceUntilIdle()

        // Deterministically, task_A and task_B are started; task_C waits in QUEUED
        assertTrue("task_A should be running", coordinator.isRunning("task_A"))
        assertTrue("task_B should be running", coordinator.isRunning("task_B"))
        assertFalse("task_C should be queued", coordinator.isRunning("task_C"))
        assertEquals(TaskStatus.QUEUED.name, fakeDao.findById("task_C")?.status)

        // Complete task_A -> task_C is next in deterministic order
        fakeEngine.completeTask("task_A")
        advanceUntilIdle()

        assertFalse(coordinator.isRunning("task_A"))
        assertTrue("task_C should now be running", coordinator.isRunning("task_C"))
    }

    @Test
    fun racingQueueDispatch_maintainsMaxParallelInvariant() = runTest(testDispatcher) {
        val maxObservedActive = AtomicInteger(0)
        val tasks = (1..6).map { createTask("t$it") }
        fakeDao.insertAll(tasks)

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo, // maxParallel = 2
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        // Monitor active jobs count continuously
        val monitorJob = testScope.launch {
            coordinator.activeJobsCount.collect { count ->
                maxObservedActive.updateAndGet { current -> maxOf(current, count) }
            }
        }

        // 10 concurrent coroutines hammering startTask and dispatchQueue
        val racingJobs = (1..10).map { idx ->
            testScope.launch {
                val targetTaskId = "t${(idx % 6) + 1}"
                coordinator.startTask(targetTaskId)
                coordinator.dispatchQueue()
            }
        }

        racingJobs.joinAll()
        advanceUntilIdle()

        assertEquals("Active jobs must never exceed maxParallel (2)", 2, maxObservedActive.get())
        assertEquals(2, coordinator.activeJobsCount.value)

        val activeIds1 = tasks.map { it.id }.filter { coordinator.isRunning(it) }
        assertEquals(2, activeIds1.size)
        activeIds1.forEach { fakeEngine.completeTask(it) }
        advanceUntilIdle()

        val activeIds2 = tasks.map { it.id }.filter { coordinator.isRunning(it) }
        assertEquals(2, activeIds2.size)
        activeIds2.forEach { fakeEngine.completeTask(it) }
        advanceUntilIdle()

        val activeIds3 = tasks.map { it.id }.filter { coordinator.isRunning(it) }
        assertEquals(2, activeIds3.size)
        activeIds3.forEach { fakeEngine.completeTask(it) }
        advanceUntilIdle()

        assertEquals(0, coordinator.activeJobsCount.value)
        assertEquals(2, maxObservedActive.get())

        monitorJob.cancel()
    }

    @Test
    fun reservationInvalidation_byPauseBeforeHandoff_releasesCapacityAndStartsNextTask() = runTest(testDispatcher) {
        var serviceLauncherCalled = false
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo, // maxParallel = 2
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            startServiceLauncher = {
                serviceLauncherCalled = true
            }
        )

        val task1 = createTask("t1")
        val task2 = createTask("t2")
        fakeDao.insert(task1)

        // Start task1 -> enters Starting state
        coordinator.startTask("t1")
        advanceTimeBy(100)

        assertTrue(serviceLauncherCalled)
        assertTrue(coordinator.serviceForegroundState.value is ServiceForegroundResult.Starting)
        val sessionId = (coordinator.serviceForegroundState.value as ServiceForegroundResult.Starting).sessionId

        // Pause task1 while reservation is pending on the barrier
        coordinator.pauseTask("t1")
        advanceTimeBy(50)

        // Verify task1 is set to PAUSED in Room and not running
        assertEquals(TaskStatus.PAUSED.name, fakeDao.findById("t1")?.status)
        assertFalse(coordinator.isRunning("t1"))

        // Signal barrier success
        coordinator.notifyServiceForegroundResult(ServiceForegroundResult.Success(sessionId), sessionId)
        advanceUntilIdle()

        // task1 never executed; engine download was never called for task1
        assertFalse(coordinator.isRunning("t1"))
        assertEquals(0, fakeEngine.startCount)

        // Queue is unblocked and task2 can be started
        fakeDao.insert(task2)
        coordinator.startTask("t2")
        advanceUntilIdle()

        assertTrue(coordinator.isRunning("t2"))
        assertEquals(1, fakeEngine.startCount)
    }

    @Test
    fun reservationInvalidation_byCancelBeforeHandoff_cleansFilesAndDeletesTask() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            startServiceLauncher = { /* hangs in starting */ }
        )

        val destFile = DownloadFileUtils.resolveDestinationFile(tempDir, "Pending Task", "t_cancel_res")
        val partFile = DownloadFileUtils.resolvePartFile(destFile).apply { writeText("stale partial data") }
        val task = createTask("t_cancel_res").copy(title = "Pending Task", localPath = destFile.absolutePath)
        fakeDao.insert(task)

        coordinator.startTask("t_cancel_res")
        advanceTimeBy(100)

        assertTrue(partFile.exists())

        // Cancel task while reservation is held waiting on barrier
        coordinator.cancelTask("t_cancel_res")
        advanceUntilIdle()

        assertFalse("Part file must be deleted upon cancel", partFile.exists())
        assertNull("Task entity must be deleted from Room", fakeDao.findById("t_cancel_res"))
        assertFalse(coordinator.isRunning("t_cancel_res"))
        assertEquals(0, coordinator.activeJobsCount.value)
    }

    @Test
    fun preStartLazyJobCancellation_releasesCapacityViaInvokeOnCompletion() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo, // maxParallel = 2
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        val task1 = createTask("t1")
        val task2 = createTask("t2")
        val task3 = createTask("t3")
        fakeDao.insertAll(listOf(task1, task2, task3))

        coordinator.startTask("t1")
        coordinator.startTask("t2")
        coordinator.startTask("t3")
        advanceUntilIdle()

        assertTrue(coordinator.isRunning("t1"))
        assertTrue(coordinator.isRunning("t2"))
        assertFalse(coordinator.isRunning("t3"))

        // Simulate cancellation of all active jobs for timeout
        coordinator.cancelAllJobsForTimeout()
        advanceUntilIdle()

        // All active jobs cancelled and cleared immediately
        assertFalse(coordinator.isRunning("t1"))
        assertFalse(coordinator.isRunning("t2"))
        assertEquals(0, coordinator.activeJobsCount.value)

        // Queue dispatch can resume fresh tasks without capacity leaks
        coordinator.startTask("t3")
        advanceUntilIdle()

        assertTrue("t3 should be running without being blocked", coordinator.isRunning("t3"))
        assertEquals(1, coordinator.activeJobsCount.value)
    }

    @Test
    fun duplicateEnqueue_concurrentCallsWithSameUrl_createsSingleActiveTask() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        val repository = RoomDownloadRepository(
            downloadTaskDao = fakeDao,
            coordinator = coordinator,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()
        fakeDao.deleteAll()

        val sharedUrl = "https://example.com/unique_file.mp4"

        // 5 concurrent enqueue requests with the exact same URL
        val enqueueJobs = (1..5).map {
            testScope.launch {
                repository.enqueueDownload(
                    url = sharedUrl,
                    title = "Unique File",
                    sourceDomain = "example.com",
                    quality = QualityOptionMock("1080p", "1080p MP4", "H.264", "mp4", "120 MB", "5.2 Mbps")
                )
            }
        }

        enqueueJobs.joinAll()
        advanceUntilIdle()

        // Verify only 1 task entity exists for this URL in Room
        val existingTasks = fakeDao.findByUrl(sharedUrl)
        assertEquals("Exactly one task should be persisted for duplicate enqueue", 1, existingTasks.size)

        val singleTask = existingTasks.first()
        assertTrue(coordinator.isRunning(singleTask.id))
        assertEquals(1, coordinator.activeJobsCount.value)
    }

    @Test
    fun enqueueDownload_pausedTaskReEnqueuedWithoutCreatingDuplicate() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        val repository = RoomDownloadRepository(
            downloadTaskDao = fakeDao,
            coordinator = coordinator,
            ioDispatcher = testDispatcher
        )

        val url = "https://example.com/paused_task.mp4"
        val pausedTask = createTask("t_paused").copy(
            sourceUrl = url,
            status = TaskStatus.PAUSED.name,
            statusText = "Paused"
        )
        fakeDao.insert(pausedTask)

        // Enqueueing the same URL when task is paused should re-enqueue the existing task
        repository.enqueueDownload(
            url = url,
            title = "Paused Task",
            sourceDomain = "example.com",
            quality = QualityOptionMock("1080p", "1080p MP4", "H.264", "mp4", "120 MB", "5.2 Mbps")
        )
        advanceUntilIdle()

        val allForUrl = fakeDao.findByUrl(url)
        assertEquals("Should not create duplicate entity in Room", 1, allForUrl.size)
        assertEquals("t_paused", allForUrl.first().id)
        assertTrue("Existing task should be resumed and running", coordinator.isRunning("t_paused"))
    }

    @Test
    fun lazyJobCompletion_whileCoordinatorScopeIsCancelling_cleansUpState() = runTest(testDispatcher) {
        val coordinatorJob = Job()
        val customScope = CoroutineScope(coordinatorJob + testDispatcher)
        val startedGate = CompletableDeferred<Unit>()

        fakeEngine.onDownloadStarted = { id ->
            if (id == "t_shutdown") startedGate.complete(Unit)
        }

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = customScope,
            ioDispatcher = testDispatcher
        )

        val task = createTask("t_shutdown")
        fakeDao.insert(task)

        coordinator.startTask("t_shutdown")
        advanceUntilIdle()

        assertTrue(startedGate.isCompleted)
        assertTrue(coordinator.isRunning("t_shutdown"))
        assertEquals(1, coordinator.activeJobsCount.value)

        // Cancel coordinator scope
        coordinatorJob.cancel()
        advanceUntilIdle()

        // Verify state is completely cleared and reset
        assertEquals(0, coordinator.activeJobsCount.value)
        assertFalse(coordinator.isRunning("t_shutdown"))
        assertEquals(ServiceForegroundResult.Idle, coordinator.serviceForegroundState.value)
        assertNull(coordinator.activeNotificationState.value)
    }

    @Test
    fun oldWorkerUnwinding_afterReplacementWorkerRegistered_doesNotOverwriteRoomState() = runTest(testDispatcher) {
        val startedGate = CompletableDeferred<Unit>()
        val engineResultDeferred = CompletableDeferred<DownloadResult>()

        val customEngine = object : DownloadEngine {
            override fun isRunning(taskId: String): Boolean = true
            override suspend fun download(
                taskId: String,
                url: String,
                destinationFile: File,
                existingEtag: String?,
                existingLastModified: String?,
                onProgress: suspend (DownloadProgressUpdate) -> Unit
            ): DownloadResult {
                startedGate.complete(Unit)
                return engineResultDeferred.await()
            }
            override fun pause(taskId: String) {}
            override fun cancel(taskId: String) {}
        }

        val coordinator = DownloadCoordinator(
            engine = customEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        val task = createTask("t_replace")
        fakeDao.insert(task)

        coordinator.startTask("t_replace")
        advanceUntilIdle()

        assertTrue("Old worker started", startedGate.isCompleted)
        assertTrue(coordinator.isRunning("t_replace"))

        // Register a replacement worker job for "t_replace"
        val replacementJob = Job()
        coordinator.registerActiveJobForTest("t_replace", replacementJob)

        // Update task in Room to simulate replacement worker active state
        fakeDao.updateProgress(
            id = "t_replace",
            status = TaskStatus.DOWNLOADING.name,
            statusText = "Downloading by replacement",
            progress = 0.85f,
            downloadedSizeText = "85 MB",
            totalSizeText = "100 MB",
            speedText = "5 MB/s",
            etaText = "00:03",
            localPath = null,
            etag = null,
            lastModified = null,
            completedAt = null
        )

        // Now old worker unwinds with a Failure result
        engineResultDeferred.complete(DownloadResult.Failure(RuntimeException("Old worker socket aborted"), canResume = false))
        advanceUntilIdle()

        // Verify: The old worker did NOT overwrite the replacement worker's Room status or progress
        val inDb = fakeDao.findById("t_replace")
        assertNotNull(inDb)
        assertEquals(TaskStatus.DOWNLOADING.name, inDb?.status)
        assertEquals("Downloading by replacement", inDb?.statusText)
        assertEquals(0.85f, inDb?.progress)

        // Verify: replacement job is still active in coordinator
        assertTrue(coordinator.isRunning("t_replace"))
        assertEquals(1, coordinator.activeJobsCount.value)
    }

    @Test
    fun pauseThenImmediateQueueDispatch_ensuresNoConcurrentWriters() = runTest(testDispatcher) {
        val startedGate = CompletableDeferred<Unit>()
        val pauseGate = CompletableDeferred<Unit>()
        val activeWriters = AtomicInteger(0)
        var maxObservedWriters = 0

        val customEngine = object : DownloadEngine {
            val paused = AtomicInteger(0)
            override fun isRunning(taskId: String): Boolean = activeWriters.get() > 0
            override suspend fun download(
                taskId: String,
                url: String,
                destinationFile: File,
                existingEtag: String?,
                existingLastModified: String?,
                onProgress: suspend (DownloadProgressUpdate) -> Unit
            ): DownloadResult {
                val current = activeWriters.incrementAndGet()
                if (current > maxObservedWriters) maxObservedWriters = current
                startedGate.complete(Unit)
                try {
                    pauseGate.await()
                    return DownloadResult.Paused
                } finally {
                    activeWriters.decrementAndGet()
                }
            }
            override fun pause(taskId: String) {
                paused.incrementAndGet()
            }
            override fun cancel(taskId: String) {}
        }

        val coordinator = DownloadCoordinator(
            engine = customEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        val task = createTask("t_pause_writer")
        fakeDao.insert(task)

        coordinator.startTask("t_pause_writer")
        advanceUntilIdle()

        assertTrue(startedGate.isCompleted)
        assertEquals(1, activeWriters.get())

        // Pause task - pauseTask moves active job to retiringJobs and awaits jobToCancel.join()
        coordinator.pauseTask("t_pause_writer")

        // Trigger immediate queue dispatch while old worker is still suspended in pauseGate
        coordinator.dispatchQueue()
        coordinator.startTask("t_pause_writer")
        advanceUntilIdle()

        // While pauseGate is not completed, t_pause_writer is retiring and cannot be started concurrently
        assertEquals("Never more than 1 concurrent writer", 1, maxObservedWriters)

        // Now let old worker unblock and finish unwinding
        pauseGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("Old writer fully terminated", 0, activeWriters.get())
        assertFalse(coordinator.isRetiring("t_pause_writer"))
        assertEquals(TaskStatus.PAUSED.name, fakeDao.findById("t_pause_writer")?.status)
    }

    @Test
    fun completionCleanup_preservesNewerJobForSameTaskId() = runTest(testDispatcher) {
        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher
        )

        val oldJob = Job()
        val replacementJob = Job()

        // Register oldJob first
        coordinator.registerActiveJobForTest("t_preserve", oldJob)
        assertEquals(1, coordinator.activeJobsCount.value)
        assertTrue(coordinator.isRunning("t_preserve"))

        // Replace with newer job
        coordinator.registerActiveJobForTest("t_preserve", replacementJob)
        assertEquals(1, coordinator.activeJobsCount.value)

        // Stale completion callback for oldJob executes
        val removedOld = coordinator.releaseActiveJob("t_preserve", expectedJob = oldJob)
        assertFalse("Stale completion must not remove newer job", removedOld)
        assertTrue("Replacement job must still be running", coordinator.isRunning("t_preserve"))
        assertEquals("Active jobs count must remain 1", 1, coordinator.activeJobsCount.value)

        // Matching completion callback for replacementJob executes
        val removedNew = coordinator.releaseActiveJob("t_preserve", expectedJob = replacementJob)
        assertTrue("Matching completion must remove replacement job", removedNew)
        assertFalse("Task should no longer be running", coordinator.isRunning("t_preserve"))
        assertEquals("Active jobs count must become 0", 0, coordinator.activeJobsCount.value)
    }

    private fun createTask(id: String, createdAt: Long = System.currentTimeMillis()) = DownloadTaskEntity(
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
        createdAt = createdAt
    )

    private class FakeDownloadEngine : DownloadEngine {
        var startCount = 0
        private val runningDeferreds = ConcurrentHashMap<String, CompletableDeferred<DownloadResult>>()
        var onDownloadStarted: ((String) -> Unit)? = null

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
            onDownloadStarted?.invoke(taskId)
            val result = deferred.await()
            runningDeferreds.remove(taskId)
            return result
        }

        fun completeTask(taskId: String) {
            runningDeferreds[taskId]?.complete(
                DownloadResult.Success(File("/dummy/$taskId.mp4"), 1000L)
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

        override suspend fun retryTask(id: String, status: String, statusText: String, progress: Float, speedText: String, etaText: String) {
            tasks[id]?.let {
                tasks[id] = it.copy(status = status, statusText = statusText, progress = progress, speedText = speedText, etaText = etaText)
                tasksFlow.value = tasks.values.toList()
            }
        }

        override suspend fun findOldestQueued(limit: Int): List<DownloadTaskEntity> {
            return tasks.values
                .filter { it.status == TaskStatus.QUEUED.name }
                .sortedWith(compareBy<DownloadTaskEntity> { it.createdAt }.thenBy { it.id })
                .take(limit)
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
