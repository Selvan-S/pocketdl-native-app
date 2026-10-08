package com.pocketdl.app.ui.navigation

/**
 * Screen destinations for PocketDL navigation.
 */
sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Dashboard")
    object Captured : Screen("captured", "Captured Media")
    object Downloads : Screen("downloads", "Downloads Library")
    object Queue : Screen("queue", "Download Queue")
    object MediaDetails : Screen("media_details/{mediaId}", "Media Details") {
        fun createRoute(mediaId: String) = "media_details/$mediaId"
    }
    object Analysis : Screen("analysis/{mediaId}", "Sniffer Analysis") {
        fun createRoute(mediaId: String) = "analysis/$mediaId"
    }
    object ExtensionConnection : Screen("extension_connection", "Extension Setup")
    object Settings : Screen("settings", "Settings")
    object StorageCleanup : Screen("storage_cleanup", "Storage Cleanup")
    object DownloadDetails : Screen("download_details/{downloadId}", "Download Details") {
        fun createRoute(downloadId: String) = "download_details/$downloadId"
    }
}
