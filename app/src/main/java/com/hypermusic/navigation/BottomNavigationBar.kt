package com.hypermusic.navigation

import android.annotation.SuppressLint
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.TabHomeFilled
import com.hypermusic.core.designsystem.icon.TabHomeOutline
import com.hypermusic.core.designsystem.theme.ShapeFull
import com.hypermusic.core.designsystem.icon.TabLibraryFilled
import com.hypermusic.core.designsystem.icon.TabLibraryOutline
import com.hypermusic.core.designsystem.icon.TabSearchFilled
import com.hypermusic.core.designsystem.icon.TabSearchOutline
import com.hypermusic.core.designsystem.icon.TabSettingsFilled
import com.hypermusic.core.designsystem.icon.TabSettingsOutline

sealed class BottomNavItem(
    val route: String, 
    val unselectedIcon: ImageVector, 
    val selectedIcon: ImageVector, 
    val label: String
) {
    object Home : BottomNavItem(
        route = "home", 
        unselectedIcon = AppIcons.TabHomeOutline, 
        selectedIcon = AppIcons.TabHomeFilled, 
        label = "Home"
    )
    object Search : BottomNavItem(
        route = "search", 
        unselectedIcon = AppIcons.TabSearchOutline, 
        selectedIcon = AppIcons.TabSearchFilled, 
        label = "Search"
    )
    object Library : BottomNavItem(
        route = "library", 
        unselectedIcon = AppIcons.TabLibraryOutline, 
        selectedIcon = AppIcons.TabLibraryFilled, 
        label = "Library"
    )
    object Settings : BottomNavItem(
        route = "settings", 
        unselectedIcon = AppIcons.TabSettingsOutline, 
        selectedIcon = AppIcons.TabSettingsFilled, 
        label = "Settings"
    )
}

val BottomNavContentHeight = 56.dp

@SuppressLint("RestrictedApi")
@Composable
fun BottomNavigationBar(navController: NavController, modifier: Modifier = Modifier) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search,
        BottomNavItem.Library,
        BottomNavItem.Settings
    )
    val backgroundBrush = Brush.verticalGradient(
        0.00f to Color.Transparent,
        0.20f to MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
        0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.90f),
        0.75f to MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
        1.00f to MaterialTheme.colorScheme.background
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentBackStack by navController.currentBackStack.collectAsState()

    val activeTabRoute = remember(currentBackStack) {
        currentBackStack.mapNotNull { it.destination.route }.lastOrNull { route ->
            route == "home" || route == "search" || route == "library" || route == "settings"
        } ?: "home"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(brush = backgroundBrush)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(BottomNavContentHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isCurrentTab = item.route == activeTabRoute
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (isCurrentTab) {
                                val isAtTabRoot = currentRoute == item.route || currentRoute?.startsWith("${item.route}?") == true
                                if (!isAtTabRoot) {
                                    navController.popBackStack(item.route, inclusive = false)
                                }
                            } else {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(30.dp)
                            .clip(ShapeFull)
                            .indication(
                                interactionSource = interactionSource,
                                indication = ripple()
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedBottomIcon(
                            isSelected = isCurrentTab,
                            unselectedIcon = item.unselectedIcon,
                            selectedIcon = item.selectedIcon,
                            contentDescription = item.label
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isCurrentTab) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = if (isCurrentTab) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedBottomIcon(
    isSelected: Boolean,
    unselectedIcon: ImageVector,
    selectedIcon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = isSelected,
        animationSpec = tween(durationMillis = 180),
        label = "icon_crossfade"
    ) { selected ->
        val icon = if (selected) selectedIcon else unselectedIcon
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(24.dp)
        )
    }
}
