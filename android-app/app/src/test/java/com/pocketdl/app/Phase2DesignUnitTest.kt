package com.pocketdl.app

import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2DesignUnitTest {

    @Test
    fun mockDataProvider_providesCapturedMediaList() {
        val capturedList = MockDataProvider.sampleCapturedList
        assertTrue(capturedList.isNotEmpty())
        assertEquals(3, capturedList.size)
        assertNotNull(capturedList.first().title)
    }

    @Test
    fun mockDataProvider_providesDownloadsList() {
        val downloads = MockDataProvider.sampleDownloadsList
        assertTrue(downloads.isNotEmpty())
        assertEquals(4, downloads.size)
    }

    @Test
    fun mockDataProvider_providesExtensionStatus() {
        val status = MockDataProvider.sampleExtensionStatus
        assertTrue(status.isConnected)
        assertEquals(8080, status.listenerPort)
    }

    @Test
    fun navigationRoutes_generateCorrectUrls() {
        assertEquals("home", Screen.Home.route)
        assertEquals("captured", Screen.Captured.route)
        assertEquals("downloads", Screen.Downloads.route)
        assertEquals("queue", Screen.Queue.route)
        assertEquals("media_details/cap_1", Screen.MediaDetails.createRoute("cap_1"))
        assertEquals("analysis/cap_1", Screen.Analysis.createRoute("cap_1"))
        assertEquals("extension_connection", Screen.ExtensionConnection.route)
        assertEquals("settings", Screen.Settings.route)
        assertEquals("storage_cleanup", Screen.StorageCleanup.route)
    }
}
