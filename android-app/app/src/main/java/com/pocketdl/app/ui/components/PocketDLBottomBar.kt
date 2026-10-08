package com.pocketdl.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.pocketdl.app.ui.navigation.Screen
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.SurfaceDark

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(Screen.Home.route, "Home", Icons.Default.Home)
    object Captured : BottomNavItem(Screen.Captured.route, "Captured", Icons.Default.Inbox)
    object Downloads : BottomNavItem(Screen.Downloads.route, "Downloads", Icons.Default.Download)
    object Settings : BottomNavItem(Screen.Settings.route, "Settings", Icons.Default.Settings)

    companion object {
        val items = listOf(Home, Captured, Downloads, Settings)
    }
}

@Composable
fun PocketDLBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    capturedBadgeCount: Int = 3
) {
    NavigationBar(
        containerColor = SurfaceDark,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        BottomNavItem.items.forEach { item ->
            val isSelected = currentRoute == item.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    if (item == BottomNavItem.Captured && capturedBadgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = ElectricCyan,
                                    contentColor = SurfaceDark
                                ) {
                                    Text(text = capturedBadgeCount.toString())
                                }
                            }
                        ) {
                            Icon(imageVector = item.icon, contentDescription = item.title)
                        }
                    } else {
                        Icon(imageVector = item.icon, contentDescription = item.title)
                    }
                },
                label = { Text(text = item.title, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ElectricCyan,
                    selectedTextColor = ElectricCyan,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
