package com.pocketdl.app.download

import com.pocketdl.app.core.network.ConnectionType
import com.pocketdl.app.core.network.NetworkConnectivityObserver
import com.pocketdl.app.core.network.NetworkState
import com.pocketdl.app.core.storage.StoragePolicy
import com.pocketdl.app.core.storage.StorageSpaceChecker
import com.pocketdl.app.data.database.dao.DownloadTaskDao
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import com.pocketdl.app.data.repository.InMemorySettingsRepository
import com.pocketdl.app.data.repository.SettingsData
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class FakeStorageSpaceChecker(
    var usableSpaceBytes: Long = 10L * 1024L * 1024L * 1024L // 10 GiB by default
) : StorageSpaceChecker {
    override fun getUsableSpace(directory: File): Long = usableSpaceBytes
}

@OptIn(ExperimentalCoroutinesApi::class)
class StorageConstraintsUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)
    private lateinit var fakeDao: FakeDownloadTaskDao
    private lateinit var fakeEngine: FakeStorageEngine
    private lateinit var settingsRepo: InMemorySettingsRepository
    private lateinit var fakeStorageChecker: FakeStorageSpaceChecker
    private lateinit var tempDir: File
    private var mockWebServer: MockWebServer? = null

    @Before
    fun setup() {
        fakeDao = FakeDownloadTaskDao()
        fakeEngine = FakeStorageEngine()
        settingsRepo = InMemorySettingsRepository(
            initialSettings = SettingsData(maxParallelDownloads = 2, allowCellular = true)
        )
        fakeStorageChecker = FakeStorageSpaceChecker()
        tempDir = File.createTempFile("storage_test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    }

    @After
    fun teardown() {
        mockWebServer?.shutdown()
        tempDir.deleteRecursively()
    }

    private fun createTask(
        id: String,
        totalSizeText: String = "100 MB",
        status: TaskStatus = TaskStatus.QUEUED,
        progress: Float = 0.0f
    ): DownloadTaskEntity {
        return DownloadTaskEntity(
            id = id,
            title = "Video $id",
            sourceDomain = "example.com",
            sourceUrl = "https://example.com/video_$id.mp4",
            status = status.name,
            statusText = if (status == TaskStatus.QUEUED) "Queued" else status.name,
            progress = progress,
            downloadedSizeText = "0 MB",
            totalSizeText = totalSizeText,
            speedText = "0 KB/s",
            etaText = "--:--",
            resolutionBadge = "1080p",
            codecBadge = "H.264",
            createdAt = System.currentTimeMillis()
        )
    }

    @Test
    fun preflightCheck_whenKnownSizeExceedsUsableSpace_failsImmediatelyWithoutNetwork() = runTest(testDispatcher) {
        // 100 MB file requires 100 MB + 25 MB safety buffer = 125 MB
        // Available is only 50 MB
        fakeStorageChecker.usableSpaceBytes = 50L * 1024L * 1024L

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            storageChecker = fakeStorageChecker
        )

        fakeDao.insert(createTask("task_no_space", totalSizeText = "100 MB"))
        coordinator.dispatchQueue()
        advanceUntilIdle()

        assertEquals("Engine must never be invoked if storage preflight fails", 0, fakeEngine.downloadCallCount.get())
        val task = fakeDao.findById("task_no_space")
        assertNotNull(task)
        assertEquals(TaskStatus.FAILED.name, task?.status)
        assertEquals("Failed: Insufficient storage", task?.statusText)
    }

    @Test
    fun preflightCheck_accountsForExistingPartFile_avoidsDoubleCounting() = runTest(testDispatcher) {
        // 100 MB total file, but 80 MB is already downloaded in .part file
        // Remaining bytes = 20 MB. Required = 20 MB + 25 MB safety buffer = 45 MB.
        val destFile = File(tempDir, "video_task_resume.mp4")
        val partFile = File(tempDir, "video_task_resume.mp4.part")
        partFile.writeBytes(ByteArray(80 * 1024 * 1024)) // 80 MB

        // Available space is 50 MB.
        // If double-counting total size (100 MB + 25 MB = 125 MB), it would fail.
        // Accounting for existing part file (20 MB + 25 MB = 45 MB), 50 MB >= 45 MB succeeds!
        fakeStorageChecker.usableSpaceBytes = 50L * 1024L * 1024L

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            storageChecker = fakeStorageChecker
        )

        fakeDao.insert(
            createTask("task_resume", totalSizeText = "100 MB").copy(localPath = destFile.absolutePath)
        )
        coordinator.dispatchQueue()
        advanceUntilIdle()

        assertEquals("Engine should be invoked because 50 MB > 45 MB required remaining headroom", 1, fakeEngine.downloadCallCount.get())
        val task = fakeDao.findById("task_resume")
        assertNotNull(task)
        assertEquals(TaskStatus.COMPLETED.name, task?.status)
    }

    @Test
    fun preflightCheck_whenSizeUnknown_enforcesMinimumSafetyBuffer() = runTest(testDispatcher) {
        // When size is unknown ("0 MB"), required is 50 MB unknown headroom + 25 MB safety buffer = 75 MB
        // When available is 60 MB (< 75 MB), preflight should fail!
        fakeStorageChecker.usableSpaceBytes = 60L * 1024L * 1024L

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            storageChecker = fakeStorageChecker
        )

        fakeDao.insert(createTask("task_unknown_fail", totalSizeText = "0 MB"))
        coordinator.dispatchQueue()
        advanceUntilIdle()

        assertEquals(0, fakeEngine.downloadCallCount.get())
        val taskFail = fakeDao.findById("task_unknown_fail")
        assertEquals(TaskStatus.FAILED.name, taskFail?.status)
        assertEquals("Failed: Insufficient storage", taskFail?.statusText)

        // Now with 80 MB available (>= 75 MB), it succeeds
        fakeStorageChecker.usableSpaceBytes = 80L * 1024L * 1024L
        fakeDao.insert(createTask("task_unknown_pass", totalSizeText = "0 MB"))
        coordinator.dispatchQueue()
        advanceUntilIdle()

        assertEquals(1, fakeEngine.downloadCallCount.get())
        val taskPass = fakeDao.findById("task_unknown_pass")
        assertEquals(TaskStatus.COMPLETED.name, taskPass?.status)
    }

    @Test
    fun engineHeaderValidation_whenContentLengthExceedsStorage_abortsBeforeStreaming() = runBlocking {
        mockWebServer = MockWebServer().apply { start() }
        val bodyContent = "A".repeat(100 * 1024) // 100 KB mock
        mockWebServer!!.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(bodyContent)
                .setHeader("Content-Length", (100L * 1024L * 1024L).toString()) // Claims 100 MB, set after setBody
        )

        // Storage checker allows only 50 MB (needed: 100 MB + 25 MB = 125 MB)
        val checker = FakeStorageSpaceChecker(usableSpaceBytes = 50L * 1024L * 1024L)
        val okHttpEngine = OkHttpDownloadEngine(
            client = OkHttpClient.Builder().build(),
            ioDispatcher = Dispatchers.IO,
            storageChecker = checker
        )

        val destFile = File(tempDir, "video_insufficient.mp4")
        val partFile = File(tempDir, "video_insufficient.mp4.part")

        val result = okHttpEngine.download(
            taskId = "engine_storage_task",
            url = mockWebServer!!.url("/video.mp4").toString(),
            destinationFile = destFile,
            onProgress = {}
        )

        assertTrue("Result must be Failure", result is DownloadResult.Failure)
        val failure = result as DownloadResult.Failure
        assertTrue("Error must be InsufficientStorageException", failure.error is InsufficientStorageException)
        val insufficientException = failure.error as InsufficientStorageException
        assertEquals(125L * 1024L * 1024L, insufficientException.requiredBytes)
        assertEquals(50L * 1024L * 1024L, insufficientException.availableBytes)

        // Part file should not have body streamed into it
        assertEquals(0L, if (partFile.exists()) partFile.length() else 0L)
    }

    @Test
    fun engineRangeResponse_calculatesStorageFromContentLengthNotFullSize() = runBlocking {
        mockWebServer = MockWebServer().apply { start() }
        val bodyContent = "B".repeat(10 * 1024)
        mockWebServer!!.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 52428800-104857599/104857600")
                .setBody(bodyContent)
                .setHeader("Content-Length", (50L * 1024L * 1024L).toString()) // 50 MB remaining, set after setBody
        )

        val destFile = File(tempDir, "video_range.mp4")
        val partFile = File(tempDir, "video_range.mp4.part")
        // Create 50 MB existing part file
        partFile.writeBytes(ByteArray(50 * 1024 * 1024))

        // Required remaining is 50 MB + 25 MB = 75 MB
        // If available is 60 MB (< 75 MB): should throw InsufficientStorageException
        val failChecker = FakeStorageSpaceChecker(usableSpaceBytes = 60L * 1024L * 1024L)
        val okHttpEngine = OkHttpDownloadEngine(
            client = OkHttpClient.Builder().build(),
            ioDispatcher = Dispatchers.IO,
            storageChecker = failChecker
        )

        val result = okHttpEngine.download(
            taskId = "range_storage_task",
            url = mockWebServer!!.url("/video_range.mp4").toString(),
            destinationFile = destFile,
            onProgress = {}
        )

        assertTrue("Result must be Failure", result is DownloadResult.Failure)
        val failure = result as DownloadResult.Failure
        assertTrue("Error must be InsufficientStorageException", failure.error is InsufficientStorageException)
    }

    @Test
    fun runtimeStorageExhaustion_enospcThrown_failsPermanentlyWithoutRetrying() = runTest(testDispatcher) {
        // Engine throws ENOSPC during streaming
        fakeEngine.simulateStorageExhaustion = true

        val coordinator = DownloadCoordinator(
            engine = fakeEngine,
            downloadTaskDao = fakeDao,
            settingsRepository = settingsRepo,
            destinationDirProvider = { tempDir },
            coordinatorScope = testScope,
            ioDispatcher = testDispatcher,
            storageChecker = fakeStorageChecker
        )

        fakeDao.insert(createTask("task_enospc", totalSizeText = "100 MB"))
        coordinator.dispatchQueue()
        advanceUntilIdle()

        val task = fakeDao.findById("task_enospc")
        assertNotNull(task)
        assertEquals(TaskStatus.FAILED.name, task?.status)
        assertTrue("Status text should indicate storage full", task?.statusText?.contains("Storage full") == true || task?.statusText?.contains("storage") == true)
        assertEquals("Retry count must NOT be incremented for storage exhaustion", 0, task?.retryCount)
        assertNull("Next retry timestamp must be null", task?.nextRetryAt)
    }

    @Test
    fun storageSafetyBuffer_preservesDatabaseWritingHeadroom() {
        assertEquals("Safety buffer must be 25 MiB", 25L * 1024L * 1024L, StoragePolicy.SAFETY_BUFFER_BYTES)
        assertEquals("Unknown headroom must be 50 MiB", 50L * 1024L * 1024L, StoragePolicy.MINIMUM_UNKNOWN_HEADROOM_BYTES)

        // When remaining is 10 MB and available is exactly 10 MB (0 safety headroom)
        val checker = FakeStorageSpaceChecker(usableSpaceBytes = 10L * 1024L * 1024L)
        val hasSpace = StoragePolicy.hasSufficientSpace(
            storageChecker = checker,
            destinationDir = tempDir,
            expectedTotalBytes = 10L * 1024L * 1024L,
            existingBytes = 0L
        )
        assertFalse("Storage check must fail without 25 MiB safety buffer to protect SQLite/Room headroom", hasSpace)
    }

    @Test
    fun storagePolicy_parseBytesFromText_parsesVariousFormatsCorrectly() {
        assertEquals(100L * 1024L * 1024L, StoragePolicy.parseBytesFromText("100 MB"))
        assertEquals(100L * 1024L * 1024L, StoragePolicy.parseBytesFromText("100MB"))
        assertEquals(100L * 1024L * 1024L, StoragePolicy.parseBytesFromText("100 mb"))
        assertEquals((1.25 * 1024 * 1024 * 1024).toLong(), StoragePolicy.parseBytesFromText("1.25 GB"))
        assertEquals(500L * 1024L, StoragePolicy.parseBytesFromText("500 KB"))
        assertEquals(1024L, StoragePolicy.parseBytesFromText("1024 B"))
        assertEquals(1024L * 1024L * 1024L * 1024L, StoragePolicy.parseBytesFromText("1 TB"))

        assertNull(StoragePolicy.parseBytesFromText("0 MB"))
        assertNull(StoragePolicy.parseBytesFromText("--"))
        assertNull(StoragePolicy.parseBytesFromText(""))
        assertNull(StoragePolicy.parseBytesFromText(null))
        assertNull(StoragePolicy.parseBytesFromText("invalid text"))
    }

    // Fake engine that can simulate successful download or runtime storage exhaustion (ENOSPC)
    private class FakeStorageEngine : DownloadEngine {
        val downloadCallCount = AtomicInteger(0)
        var simulateStorageExhaustion = false

        override suspend fun download(
            taskId: String,
            url: String,
            destinationFile: File,
            existingEtag: String?,
            existingLastModified: String?,
            onProgress: suspend (DownloadProgressUpdate) -> Unit
        ): DownloadResult {
            downloadCallCount.incrementAndGet()
            if (simulateStorageExhaustion) {
                return DownloadResult.Failure(
                    IOException("write failed: ENOSPC (No space left on device)"),
                    canResume = true
                )
            }
            destinationFile.parentFile?.mkdirs()
            destinationFile.writeText("downloaded content")
            return DownloadResult.Success(
                file = destinationFile,
                totalBytes = 100L * 1024L * 1024L,
                etag = "etag-1",
                lastModified = null
            )
        }

        override fun pause(taskId: String) {}
        override fun cancel(taskId: String) {}
        override fun isRunning(taskId: String): Boolean = false
    }

    private class FakeDownloadTaskDao : DownloadTaskDao {
        val tasks = ConcurrentHashMap<String, DownloadTaskEntity>()
        val tasksFlow = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())

        override fun observeAll(): Flow<List<DownloadTaskEntity>> = tasksFlow.asStateFlow()
        override fun getById(id: String): Flow<DownloadTaskEntity?> = MutableStateFlow(tasks[id])
        override suspend fun findById(id: String): DownloadTaskEntity? = tasks[id]
        override suspend fun insert(task: DownloadTaskEntity) {
            tasks[task.id] = task
            tasksFlow.value = tasks.values.toList()
        }
        override suspend fun insertAll(tasksList: List<DownloadTaskEntity>) {
            tasksList.forEach { tasks[it.id] = it }
            tasksFlow.value = tasks.values.toList()
        }
        override suspend fun update(task: DownloadTaskEntity) {
            tasks[task.id] = task
            tasksFlow.value = tasks.values.toList()
        }
        override suspend fun updateStatus(id: String, status: String, statusText: String, speedText: String, etaText: String) {
            tasks[id]?.let {
                tasks[id] = it.copy(status = status, statusText = statusText, speedText = speedText, etaText = etaText)
                tasksFlow.value = tasks.values.toList()
            }
        }
        override suspend fun updateAllStatus(fromStatuses: List<String>, toStatus: String, statusText: String, speedText: String, etaText: String) {
            tasks.forEach { (k, v) ->
                if (v.status in fromStatuses) {
                    tasks[k] = v.copy(status = toStatus, statusText = statusText, speedText = speedText, etaText = etaText)
                }
            }
            tasksFlow.value = tasks.values.toList()
        }
        override suspend fun updateAllStatusAndResetRetries(fromStatuses: List<String>, toStatus: String, statusText: String, speedText: String, etaText: String) {
            tasks.forEach { (k, v) ->
                if (v.status in fromStatuses) {
                    tasks[k] = v.copy(status = toStatus, statusText = statusText, speedText = speedText, etaText = etaText, retryCount = 0, nextRetryAt = null)
                }
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
