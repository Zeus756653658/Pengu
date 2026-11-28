package com.devrayan.pengu.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
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
            route = Route.MUSIC,
            title = "Music",
            selectedIcon = Icons.Filled.MusicNote,
            unselectedIcon = Icons.Outlined.MusicNote,
        ),
        BottomNavItem(
            route = Route.SETTINGS_PAGE,
            title = "Settings",
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
        ),
    )

/**
 * Pengu Floating Pill Bottom Tab Bar with spring sliding active indicator and expanding labels.
 */
@Composable
fun PenguBottomBar(
    modifier: Modifier = Modifier,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val view = LocalView.current
    val density = LocalDensity.current

    val pillContainerColor =
        if (isDark) Color(0xFF1A1A22).copy(alpha = 0.94f) else Color(0xFFFFFFFF).copy(alpha = 0.95f)
    val rimColor =
        if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    val activePillBg =
        if (isDark) {
            Brush.linearGradient(listOf(Color(0xFF6366F1).copy(alpha = 0.28f), Color(0xFF7C3AED).copy(alpha = 0.26f)))
        } else {
            Brush.linearGradient(listOf(Color(0xFF6366F1).copy(alpha = 0.14f), Color(0xFF7C3AED).copy(alpha = 0.16f)))
        }
    val activeColor = if (isDark) Color(0xFFC4B5FD) else Color(0xFF7C3AED)
    val inactiveColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    val selectedIndex =
        BottomNavItems.indexOfFirst { item ->
                when (item.route) {
                    Route.HOME -> currentRoute == Route.HOME
                    Route.DOWNLOAD_QUEUE ->
                        currentRoute == Route.DOWNLOAD_QUEUE ||
                            currentRoute?.startsWith(Route.TASK_LIST) == true ||
                            currentRoute?.startsWith(Route.TASK_LOG) == true
                    Route.DOWNLOADS -> currentRoute == Route.DOWNLOADS
                    Route.MUSIC -> currentRoute == Route.MUSIC
                    Route.SETTINGS_PAGE ->
                        currentRoute == Route.SETTINGS_PAGE || currentRoute?.startsWith("settings") == true
                    else -> currentRoute == item.route
                }
            }
            .coerceAtLeast(0)

    val tabWidths = remember { mutableStateMapOf<Int, Dp>() }
    val tabOffsets = remember { mutableStateMapOf<Int, Dp>() }

    val targetWidth = tabWidths[selectedIndex] ?: 0.dp
    val targetOffset = tabOffsets[selectedIndex] ?: 0.dp

    val animatedWidth by
        animateDpAsState(
            targetValue = targetWidth,
            animationSpec = spring(stiffness = 500f, dampingRatio = 0.85f),
            label = "pillTabWidth",
        )
    val animatedOffset by
        animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(stiffness = 500f, dampingRatio = 0.85f),
            label = "pillTabOffset",
        )

    Box(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier =
                Modifier.wrapContentWidth()
                    .height(56.dp)
                    .shadow(14.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.28f))
                    .clip(CircleShape)
                    .background(pillContainerColor)
                    .border(0.8.dp, rimColor, CircleShape)
                    .padding(5.dp),
        ) {
            if (targetWidth > 0.dp) {
                Box(
                    modifier =
                        Modifier.offset(x = animatedOffset)
                            .width(animatedWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(activePillBg)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxHeight(),
            ) {
                BottomNavItems.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier =
                            Modifier.fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    tabWidths[index] = with(density) { coords.size.width.toDp() }
                                    tabOffsets[index] = with(density) { coords.positionInParent().x.toDp() }
                                }
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    view.slightHapticFeedback()
                                    onNavigate(item.route)
                                }
                                .padding(horizontal = if (isSelected) 13.dp else 10.dp),
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title,
                            tint = if (isSelected) activeColor else inactiveColor,
                            modifier = Modifier.size(21.dp),
                        )
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = item.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
