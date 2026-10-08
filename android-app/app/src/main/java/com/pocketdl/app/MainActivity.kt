package com.pocketdl.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.ui.components.PocketDLBottomBar
import com.pocketdl.app.ui.navigation.AppNavHost
import com.pocketdl.app.ui.navigation.Screen
import com.pocketdl.app.ui.theme.PocketDLTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}