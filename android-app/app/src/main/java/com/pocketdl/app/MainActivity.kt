package com.pocketdl.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pocketdl.app.core.logging.AppLogger
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.ui.components.PocketDLBottomBar
import com.pocketdl.app.ui.navigation.AppNavHost
import com.pocketdl.app.ui.navigation.Screen
import com.pocketdl.app.ui.theme.PocketDLTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            AppLogger.i(TAG, "POST_NOTIFICATIONS permission granted")
        } else {
            AppLogger.w(TAG, "POST_NOTIFICATIONS permission denied by user; ongoing/alert notifications disabled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            PocketDLTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val topLevelRoutes = listOf(
                    Screen.Home.route,
                    Screen.Captured.route,
                    Screen.Downloads.route,
                    Screen.Settings.route
                )

                val showBottomBar = currentRoute in topLevelRoutes

                val capturedItems by InMemoryRepositoryProvider.capturedMediaRepository
                    .observeCapturedMedia()
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            PocketDLBottomBar(
                                currentRoute = currentRoute,
                                capturedBadgeCount = capturedItems.size,
                                onNavigate = { route ->
                                    if (currentRoute != route) {
                                        navController.navigate(route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val alreadyRequested = prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
                if (!alreadyRequested) {
                    prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "pocketdl_prefs"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}