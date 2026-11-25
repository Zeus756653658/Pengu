package com.devrayan.pengu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.ui.common.Route

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val BottomNavItems =
    listOf(
        BottomNavItem(
            route = Route.HOME,
            title = "Home",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
        ),
        BottomNavItem(
            route = Route.DOWNLOAD_QUEUE,
            title = "Progress",
            selectedIcon = Icons.Filled.HourglassTop,
            unselectedIcon = Icons.Outlined.HourglassTop,
        ),
        BottomNavItem(
            route = Route.DOWNLOADS,
            title = "Downloads",
            selectedIcon = Icons.Filled.Subscriptions,
            unselectedIcon = Icons.Outlined.Subscriptions,
        ),
        BottomNavItem(
            route = Route.SETTINGS_PAGE,
            title = "Settings",
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
        ),
    )

/**
 * Modern VidSnap-style bottom navigation bar with vibrant purple/indigo active accents
 */
@Composable
fun PenguBottomBar(
    modifier: Modifier = Modifier,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val navContainerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val activeColor = Color(0xFF7C3AED) // Vibrant modern violet
    val inactiveColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF9CA3AF)
    val indicatorColor = if (isDark) Color(0xFF3B1E63) else Color(0xFFF3E8FF)

    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB),
            thickness = 0.5.dp,
        )
        NavigationBar(
            containerColor = navContainerColor,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        ) {
            BottomNavItems.forEach { item ->
                val isSelected =
                    when (item.route) {
                        Route.HOME -> currentRoute == Route.HOME
                        Route.DOWNLOAD_QUEUE ->
                            currentRoute == Route.DOWNLOAD_QUEUE ||
                                currentRoute?.startsWith(Route.TASK_LIST) == true ||
                                currentRoute?.startsWith(Route.TASK_LOG) == true
                        Route.DOWNLOADS -> currentRoute == Route.DOWNLOADS
                        Route.SETTINGS_PAGE -> currentRoute == Route.SETTINGS_PAGE || currentRoute?.startsWith("settings") == true
                        else -> currentRoute == item.route
                    }

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title,
                        )
                    },
                    label = {
                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = activeColor,
                            selectedTextColor = activeColor,
                            unselectedIconColor = inactiveColor,
                            unselectedTextColor = inactiveColor,
                            indicatorColor = indicatorColor,
                        ),
                )
            }
        }
    }
}
