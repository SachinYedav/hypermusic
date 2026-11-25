package com.hypermusic.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hypermusic.feature.home.HomeScreen
import com.hypermusic.feature.search.SearchScreen
import com.hypermusic.feature.library.LibraryScreen
import com.hypermusic.feature.downloads.DownloadScreen
import com.hypermusic.feature.settings.screens.AppUpdatesScreen
import com.hypermusic.feature.settings.screens.LegalScreen
import com.hypermusic.feature.settings.screens.OpenSourceLicensesScreen
import com.hypermusic.feature.settings.screens.SettingsScreen
import com.hypermusic.feature.settings.screens.StorageManagementScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject

@Composable
fun AppNavGraph(
    playerRepository: com.hypermusic.core.player.PlayerRepository = koinInject(),
    appUpdateRepository: com.hypermusic.core.data.repository.AppUpdateRepository = koinInject(),
    toastViewModel: com.hypermusic.feature.shared.state.GlobalToastViewModel = koinInject(),
    navigationEventBus: NavigationEventBus = koinInject()
) {
    val navController = rememberNavController()
    val currentTrack by playerRepository.currentTrack.collectAsState()
    val navTarget by navigationEventBus.navTargetFlow.collectAsState()

    androidx.compose.runtime.LaunchedEffect(navTarget) {
        navTarget?.let { target ->
            try {
                navigateSafely(navController, target)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            navigationEventBus.consumeNavTarget()
        }
    }

    // Quiet startup check for app updates
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            val result = appUpdateRepository.checkForUpdates()
            result.getOrNull()?.let { checkResult ->
                if (checkResult.isUpdateAvailable && checkResult.updateData != null) {
                    toastViewModel.showAlertCard(
                        title = "Update Available",
                        message = "HyperMusic v${checkResult.updateData.latestVersion} is available with new features and improvements.",
                        type = com.hypermusic.feature.shared.state.ToastType.INFO,
                        primaryAction = com.hypermusic.feature.shared.state.ToastAction("Update") {
                            navigateSafely(navController, "app_updates")
                        },
                        secondaryAction = com.hypermusic.feature.shared.state.ToastAction("Later") {
                            toastViewModel.hideToast()
                        },
                        durationMs = 10000L
                    )
                }
            }
        } catch (e: Exception) {
            // Silently ignore network failures on launch
        }
    }

    val playerError by playerRepository.playerError.collectAsState()
    androidx.compose.runtime.LaunchedEffect(playerError) {
        playerError?.let {
            toastViewModel.showAlertCard(
                title = "Playback Stopped",
                message = it.ifBlank { "Unable to stream audio. Check your internet connection and try again." },
                type = com.hypermusic.feature.shared.state.ToastType.ERROR,
                primaryAction = com.hypermusic.feature.shared.state.ToastAction("Retry") {
                    playerRepository.retryPlayback()
                },
                secondaryAction = com.hypermusic.feature.shared.state.ToastAction("Dismiss") {
                    playerRepository.clearPlayerError()
                    toastViewModel.hideToast()
                },
                durationMs = 8000L
            )
        }
    }

    val navigateToCollection: (String, String, String?, String?, String?) -> Unit = { type, id, title, subtitle, artworkUrl ->
        navController.navigateToCollection(type, id, title, subtitle, artworkUrl)
    }

    val navigateToCatalog: (String, String?, String?) -> Unit = { endpoint, title, subtitle ->
        navController.navigateToCatalog(endpoint, title, subtitle)
    }
    
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val navBarBottom = WindowInsets.navigationBars.getBottom(density)
        val navBarBottomDp = with(density) { navBarBottom.toDp() }
        val bottomBarHeightPx = with(density) { BottomNavContentHeight.toPx() } + navBarBottom.toFloat()

        val totalBottomPadding = if (currentTrack != null) {
            BottomNavContentHeight + navBarBottomDp + 64.dp + 16.dp
        } else {
            BottomNavContentHeight + navBarBottomDp + 16.dp
        }

        androidx.compose.runtime.CompositionLocalProvider(
            com.hypermusic.core.designsystem.theme.LocalBottomContentPadding provides totalBottomPadding
        ) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("home") {
                    HomeScreen(
                        onNavigateToCollection = navigateToCollection,
                        onNavigateToCatalog = navigateToCatalog,
                        onNavigateToCategory = { id, title ->
                            navController.navigateToCategory(id, title)
                        },
                        onNavigateToLikedSongs = { navigateSafely(navController, "liked_songs") }
                    )
                }
                composable("search") { SearchScreen(
                    onNavigateToCategory = { id, title ->
                        navController.navigateToCategory(id, title)
                    },
                    onNavigateToCollection = navigateToCollection
                ) }
                composable(
                    route = "explore/{categoryId}/{title}",
                    arguments = listOf(
                        androidx.navigation.navArgument("categoryId") { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType }
                    )
                ) {
                    com.hypermusic.feature.explore.ExploreCategoryScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToCollection = navigateToCollection,
                        onNavigateToCatalog = navigateToCatalog,
                        onNavigateToCategory = { id, title ->
                            navController.navigateToCategory(id, title)
                        },
                        onNavigateToSearch = {
                            navigationEventBus.triggerAutoFocusSearch()
                            val popped = navController.popBackStack("search", inclusive = false)
                            if (!popped) {
                                navController.navigate("search") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
                composable(
                    route = "collection/{type}/{id}?title={title}&subtitle={subtitle}&artworkUrl={artworkUrl}",
                    arguments = listOf(
                        androidx.navigation.navArgument("type") { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("id") { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType; nullable = true },
                        androidx.navigation.navArgument("subtitle") { type = androidx.navigation.NavType.StringType; nullable = true },
                        androidx.navigation.navArgument("artworkUrl") { type = androidx.navigation.NavType.StringType; nullable = true }
                    )
                ) { backStackEntry ->
                    val type = backStackEntry.arguments?.getString("type") ?: "album"
                    com.hypermusic.feature.shared.screens.CollectionScreen(
                        type = type,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToCollection = navigateToCollection
                    )
                }
                composable(
                    route = "artist/{id}",
                    arguments = listOf(
                        androidx.navigation.navArgument("id") { type = androidx.navigation.NavType.StringType }
                    )
                ) {
                    com.hypermusic.feature.shared.screens.ArtistScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToCollection = navigateToCollection,
                        onNavigateToCatalog = navigateToCatalog
                    )
                }
                composable(
                    route = "catalog/{endpoint}?title={title}&subtitle={subtitle}",
                    arguments = listOf(
                        androidx.navigation.navArgument("endpoint") { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType; nullable = true },
                        androidx.navigation.navArgument("subtitle") { type = androidx.navigation.NavType.StringType; nullable = true }
                    )
                ) {
                    com.hypermusic.feature.shared.screens.CatalogScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToCollection = navigateToCollection
                    )
                }
                composable("downloads") {
                    DownloadScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("library") {
                    LibraryScreen(
                        onNavigateToLikedSongs = { navigateSafely(navController, "liked_songs") },
                        onNavigateToSavedEpisodes = { navigateSafely(navController, "saved_episodes") },
                        onNavigateToCollection = navigateToCollection,
                        onNavigateToDownloads = { navigateSafely(navController, "downloads") }
                    ) 
                }
                composable("liked_songs") {
                    com.hypermusic.feature.library.LikedSongsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("saved_episodes") {
                    com.hypermusic.feature.library.SavedEpisodesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("settings") { 
                    SettingsScreen(
                        onNavigateToStorage = { navigateSafely(navController, "storage_management") },
                        onNavigateToLegal = { navigateSafely(navController, "legal") },
                        onNavigateToOpenSource = { navigateSafely(navController, "open_source_licenses") },
                        onNavigateToEqualizer = { navigateSafely(navController, "equalizer") },
                        onNavigateToWidgets = { navigateSafely(navController, "widgets_preview") },
                        onNavigateToAppUpdates = { navigateSafely(navController, "app_updates") },
                        onNavigateToAbout = { navigateSafely(navController, "about") }
                    ) 
                }
                composable("about") {
                    com.hypermusic.feature.settings.screens.AboutScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToLegal = { navigateSafely(navController, "legal") },
                        onNavigateToOpenSource = { navigateSafely(navController, "open_source_licenses") }
                    )
                }
                composable("app_updates") {
                    AppUpdatesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("widgets_preview") {
                    com.hypermusic.feature.widget.preview.WidgetPreviewScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("equalizer") {
                    com.hypermusic.feature.equalizer.EqualizerScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("storage_management") {
                    StorageManagementScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("legal") {
                    LegalScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("open_source_licenses") {
                    OpenSourceLicensesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
        
        com.hypermusic.feature.player.PlayerSheet(
            navController = navController,
            bottomBarHeight = bottomBarHeightPx,
            onNavigateToEqualizer = { navController.navigate("equalizer") { launchSingleTop = true } },
            bottomBar = { modifier ->
                BottomNavigationBar(navController = navController, modifier = modifier)
            }
        )
        
        com.hypermusic.feature.shared.components.GlobalActionSheetOverlay(navController = navController)
        com.hypermusic.feature.shared.components.ArtistSelectionOverlay(navController = navController)
        com.hypermusic.feature.shared.components.GlobalToastOverlay()
    }
}

private fun getParentTabForRoute(route: String): String? {
    return when {
        route == "downloads" || route == "liked_songs" || route == "saved_episodes" -> "library"
        route == "about" || route == "app_updates" || route == "storage_management" ||
        route == "equalizer" || route == "widgets_preview" ||
        route == "legal" || route == "open_source_licenses" -> "settings"
        route == "explore" || route.startsWith("explore/") -> "search"
        else -> null
    }
}

private fun navigateSafely(navController: androidx.navigation.NavController, target: String) {
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    if (currentRoute == target) {
        return
    }

    val parentTab = getParentTabForRoute(target)
    if (parentTab != null) {
        val currentParentTab = currentRoute?.let { getParentTabForRoute(it) } ?: currentRoute
        // If we are NOT already within the parent tab's hierarchy (e.g. coming from external intent or toast),
        // switch to the parent tab first so the hierarchical backstack is preserved.
        if (currentParentTab != parentTab && currentRoute != parentTab && currentRoute?.startsWith("$parentTab?") != true) {
            navController.navigate(parentTab) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
        navController.navigate(target) {
            launchSingleTop = true
        }
    } else {
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}
