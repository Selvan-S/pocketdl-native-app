package com.pocketdl.app.download

import com.pocketdl.app.download.notification.DownloadNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNotificationManagerUnitTest {

    @Test
    fun constants_verifyChannelConfigurationValues() {
        assertEquals("pocketdl_downloads", DownloadNotificationManager.CHANNEL_ID)
        assertEquals("Active Downloads", DownloadNotificationManager.CHANNEL_NAME)
        assertEquals("Shows progress and status of PocketDL active media downloads", DownloadNotificationManager.CHANNEL_DESCRIPTION)
        assertEquals(1001, DownloadNotificationManager.ONGOING_NOTIFICATION_ID)
    }
}
