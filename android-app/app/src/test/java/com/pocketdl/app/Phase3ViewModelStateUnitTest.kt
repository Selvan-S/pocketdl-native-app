package com.pocketdl.app

import com.pocketdl.app.data.repository.DownloadEngineType
import com.pocketdl.app.data.repository.InMemoryCapturedMediaRepository
import com.pocketdl.app.data.repository.InMemoryDownloadRepository
import com.pocketdl.app.data.repository.InMemoryExtensionRepository
import com.pocketdl.app.data.repository.InMemorySettingsRepository
import com.pocketdl.app.data.repository.InMemoryStorageRepository
import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.mock.TaskStatus
import com.pocketdl.app.ui.screens.captured.CapturedFilter
import com.pocketdl.app.ui.screens.captured.CapturedViewModel
import com.pocketdl.app.ui.screens.downloads.DownloadFilter
import com.pocketdl.app.ui.screens.downloads.DownloadsViewModel
import com.pocketdl.app.ui.screens.extension.ExtensionViewModel
import com.pocketdl.app.ui.screens.home.HomeViewModel
import com.pocketdl.app.ui.screens.media_details.MediaDetailsViewModel
import com.pocketdl.app.ui.screens.queue.QueueViewModel
import com.pocketdl.app.ui.screens.settings.SettingsViewModel
import com.pocketdl.app.ui.screens.storage.StorageCleanupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase3ViewModelStateUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun capturedViewModel_initialStateAndFiltering() = runTest {
        val capturedRepo = InMemoryCapturedMediaRepository()
        val downloadRepo = InMemoryDownloadRepository()
        val viewModel = CapturedViewModel(capturedRepo, downloadRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.items.size)
        assertEquals(CapturedFilter.ALL, state.filter)

        // Filter Video
        viewModel.onFilterSelected(CapturedFilter.VIDEO)
        advanceUntilIdle()
        val videoState = viewModel.uiState.value
        assertEquals(CapturedFilter.VIDEO, videoState.filter)
        assertEquals(1, videoState.items.size)

        // Filter HLS
        viewModel.onFilterSelected(CapturedFilter.HLS)
        advanceUntilIdle()
        val hlsState = viewModel.uiState.value
        assertEquals(1, hlsState.items.size)
        assertTrue(hlsState.items.first().isHls)

        // Filter Audio
        viewModel.onFilterSelected(CapturedFilter.AUDIO)
        advanceUntilIdle()
        val audioState = viewModel.uiState.value
        assertEquals(1, audioState.items.size)
        assertEquals("MP3", audioState.items.first().formatBadge)
    }

    @Test
    fun capturedViewModel_qualitySheetAndDownloadEnqueue() = runTest {
        val capturedRepo = InMemoryCapturedMediaRepository()
        val downloadRepo = InMemoryDownloadRepository()
        val viewModel = CapturedViewModel(capturedRepo, downloadRepo)
        advanceUntilIdle()

        val item = viewModel.uiState.value.items.first()
        viewModel.onOpenQualitySheet(item)
        assertTrue(viewModel.uiState.value.showQualitySheet)
        assertEquals(item, viewModel.uiState.value.selectedMediaForQuality)

        val qualityOption = MockDataProvider.sampleQualityOptions.first()
        viewModel.onConfirmQuality(qualityOption)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showQualitySheet)
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun capturedViewModel_deleteItemUpdatesList() = runTest {
        val capturedRepo = InMemoryCapturedMediaRepository()
        val downloadRepo = InMemoryDownloadRepository()
        val viewModel = CapturedViewModel(capturedRepo, downloadRepo)
        advanceUntilIdle()

        val initialCount = viewModel.uiState.value.items.size
        val idToDelete = viewModel.uiState.value.items.first().id
        viewModel.onDeleteCaptured(idToDelete)
        advanceUntilIdle()

        assertEquals(initialCount - 1, viewModel.uiState.value.items.size)
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun downloadsViewModel_filteringAndPauseResume() = runTest {
        val downloadRepo = InMemoryDownloadRepository()
        val storageRepo = InMemoryStorageRepository()
        val viewModel = DownloadsViewModel(downloadRepo, storageRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(4, state.items.size)

        // Filter Active
        viewModel.onFilterSelected(DownloadFilter.ACTIVE)
        val activeItems = viewModel.uiState.value.items
        assertTrue(activeItems.all { it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.PAUSED })

        // Pause active download
        val downloadingItem = state.items.first { it.status == TaskStatus.DOWNLOADING }
        viewModel.onPauseResume(downloadingItem.id)
        advanceUntilIdle()

        // Resume paused download
        viewModel.onPauseResume(downloadingItem.id)
        advanceUntilIdle()
    }

    @Test
    fun queueViewModel_controlsAndPriority() = runTest {
        val downloadRepo = InMemoryDownloadRepository()
        val settingsRepo = InMemorySettingsRepository()
        val viewModel = QueueViewModel(downloadRepo, settingsRepo)
        advanceUntilIdle()

        val queued = viewModel.uiState.value.queuedTasks
        assertTrue(queued.isNotEmpty())

        viewModel.startAll()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.userMessage)

        viewModel.pauseAll()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.userMessage)

        val taskId = queued.first().id
        viewModel.startNow(taskId)
        advanceUntilIdle()

        viewModel.removeTask(taskId)
        advanceUntilIdle()
    }

    @Test
    fun storageCleanupViewModel_selectionAndPurge() = runTest {
        val downloadRepo = InMemoryDownloadRepository()
        val storageRepo = InMemoryStorageRepository()
        val viewModel = StorageCleanupViewModel(downloadRepo, storageRepo)
        advanceUntilIdle()

        val initialCount = viewModel.uiState.value.items.size
        assertTrue(initialCount > 0)

        // Select item
        val firstId = viewModel.uiState.value.items.first().id
        viewModel.toggleSelectItem(firstId)
        assertTrue(viewModel.uiState.value.selectedIds.contains(firstId))

        // Select All
        viewModel.selectAll()
        assertEquals(initialCount, viewModel.uiState.value.selectedIds.size)

        // Purge selected
        viewModel.purgeSelected()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.items.size)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun settingsViewModel_engineAndLimits() = runTest {
        val settingsRepo = InMemorySettingsRepository()
        val viewModel = SettingsViewModel(settingsRepo)
        advanceUntilIdle()

        assertEquals(DownloadEngineType.NATIVE_OKHTTP, viewModel.uiState.value.selectedEngine)

        viewModel.onEngineSelected(DownloadEngineType.ARIA2)
        advanceUntilIdle()
        assertEquals(DownloadEngineType.ARIA2, viewModel.uiState.value.selectedEngine)

        viewModel.onAllowCellularToggled(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.allowCellular)

        val initialParallel = viewModel.uiState.value.maxParallelDownloads
        viewModel.cycleMaxParallel()
        advanceUntilIdle()
        assertEquals(initialParallel + 1, viewModel.uiState.value.maxParallelDownloads)
    }

    @Test
    fun extensionViewModel_connectionAndToken() = runTest {
        val extensionRepo = InMemoryExtensionRepository()
        val viewModel = ExtensionViewModel(extensionRepo)
        advanceUntilIdle()

        val initialToken = viewModel.uiState.value.pairingToken
        viewModel.regenerateToken()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.pairingToken)
        assertFalse(initialToken == viewModel.uiState.value.pairingToken)

        val initialConnection = viewModel.uiState.value.status?.isConnected == true
        viewModel.toggleConnection()
        advanceUntilIdle()
        assertEquals(!initialConnection, viewModel.uiState.value.status?.isConnected)
    }

    @Test
    fun homeViewModel_detectUrlAddsItem() = runTest {
        val capturedRepo = InMemoryCapturedMediaRepository()
        val downloadRepo = InMemoryDownloadRepository()
        val extensionRepo = InMemoryExtensionRepository()
        val viewModel = HomeViewModel(capturedRepo, downloadRepo, extensionRepo)
        advanceUntilIdle()

        var navigatedId = ""
        viewModel.onDetectUrl("https://vimeo.com/video12345") { targetId ->
            navigatedId = targetId
        }
        advanceUntilIdle()

        assertTrue(navigatedId.isNotBlank())
        assertTrue(viewModel.uiState.value.recentCaptured.any { it.id == navigatedId })
    }

    @Test
    fun mediaDetailsViewModel_loadsMediaAndEnqueues() = runTest {
        val capturedRepo = InMemoryCapturedMediaRepository()
        val downloadRepo = InMemoryDownloadRepository()
        val viewModel = MediaDetailsViewModel(capturedRepo, downloadRepo)

        viewModel.loadMedia("cap_1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.media)
        assertEquals("cap_1", state.media?.id)
        assertTrue(state.options.isNotEmpty())

        val option = state.options.first()
        viewModel.onConfirmDownload(option)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.downloadEnqueued)
        assertNotNull(viewModel.uiState.value.feedbackMessage)
    }
}
