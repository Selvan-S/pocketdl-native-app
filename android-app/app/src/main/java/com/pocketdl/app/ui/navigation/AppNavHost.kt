package com.pocketdl.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocketdl.app.ui.screens.analysis.MediaSnifferScreen
import com.pocketdl.app.ui.screens.captured.CapturedScreen
import com.pocketdl.app.ui.screens.download_details.DownloadDetailsScreen
import com.pocketdl.app.ui.screens.downloads.DownloadsScreen
import com.pocketdl.app.ui.screens.extension.ExtensionConnectionScreen
import com.pocketdl.app.ui.screens.home.HomeScreen
import com.pocketdl.app.ui.screens.media_details.MediaDetailsScreen
import com.pocketdl.app.ui.screens.queue.QueueScreen
import com.pocketdl.app.ui.screens.settings.SettingsScreen
import com.pocketdl.app.ui.screens.storage.StorageCleanupScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCaptured = { navController.navigate(Screen.Captured.route) },
                onNavigateToDownloads = { navController.navigate(Screen.Downloads.route) },
                onNavigateToQueue = { navController.navigate(Screen.Queue.route) },
                onNavigateToExtension = { navController.navigate(Screen.ExtensionConnection.route) },
                onNavigateToStorage = { navController.navigate(Screen.StorageCleanup.route) },
                onNavigateToMediaDetail = { mediaId -> navController.navigate(Screen.MediaDetails.createRoute(mediaId)) },
                onNavigateToAnalysis = { mediaId -> navController.navigate(Screen.Analysis.createRoute(mediaId)) }
            )
        }
        composable(Screen.Captured.route) {
            CapturedScreen(
                onMediaSelected = { mediaId ->
                    navController.navigate(Screen.MediaDetails.createRoute(mediaId))
                },
                onNavigateToAnalysis = { mediaId ->
                    navController.navigate(Screen.Analysis.createRoute(mediaId))
                }
            )
        }
        composable(Screen.Downloads.route) {
            DownloadsScreen(
                onDownloadSelected = { downloadId ->
                    navController.navigate(Screen.DownloadDetails.createRoute(downloadId))
                }
            )
        }
        composable(Screen.Queue.route) {
            QueueScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.ExtensionConnection.route) {
            ExtensionConnectionScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.StorageCleanup.route) {
            StorageCleanupScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.MediaDetails.route,
            arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            MediaDetailsScreen(
                mediaId = mediaId,
                onBackClick = { navController.popBackStack() },
                onNavigateToAnalysis = { id -> navController.navigate(Screen.Analysis.createRoute(id)) }
            )
        }
        composable(
            route = Screen.Analysis.route,
            arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            MediaSnifferScreen(
                mediaId = mediaId,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.DownloadDetails.route,
            arguments = listOf(navArgument("downloadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val downloadId = backStackEntry.arguments?.getString("downloadId") ?: ""
            DownloadDetailsScreen(
                downloadId = downloadId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
