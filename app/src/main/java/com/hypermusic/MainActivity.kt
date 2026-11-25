package com.hypermusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.model.UserSettings
import com.hypermusic.feature.widget.data.WidgetActions
import com.hypermusic.navigation.AppNavGraph
import com.hypermusic.navigation.NavigationEventBus
import com.hypermusic.core.designsystem.theme.HyperMusicTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val navigationEventBus: NavigationEventBus by inject()
    private val preferencesManager: PreferencesManager by inject()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialTheme = preferencesManager.getInitialThemeMode()
        var userSettings by mutableStateOf(UserSettings(themeMode = initialTheme))

        lifecycleScope.launch {
            preferencesManager.userSettingsFlow.collect { settings ->
                preferencesManager.syncCacheIfNeeded(settings.themeMode)
                userSettings = settings
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleNavigationIntent(intent)

        setContent {
            val isDarkTheme = when (userSettings.themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            HyperMusicTheme(darkTheme = isDarkTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent == null) return

        val navigateTo = intent.getStringExtra("navigate_to")
        val libraryFilter = intent.getStringExtra("library_filter")
        if (navigateTo != null) {
            if (libraryFilter != null) {
                navigationEventBus.setLibraryFilter(libraryFilter)
            }
            navigationEventBus.navigateTo(navigateTo)
            intent.removeExtra("navigate_to")
            intent.removeExtra("library_filter")
            return
        }

        val action = intent.action ?: return
        if (action == WidgetActions.ACTION_OPEN_SEARCH) {
            navigationEventBus.triggerAutoFocusSearch()
        }
        val target = when (action) {
            WidgetActions.ACTION_OPEN_SEARCH -> "search"
            WidgetActions.ACTION_OPEN_PLAYLISTS -> {
                navigationEventBus.setLibraryFilter("Playlists")
                "library"
            }
            WidgetActions.ACTION_OPEN_EXPLORE -> "search"
            WidgetActions.ACTION_OPEN_HISTORY -> {
                navigationEventBus.setLibraryFilter("History")
                "library"
            }
            WidgetActions.ACTION_OPEN_LIKED_SONGS -> "liked_songs"
            else -> null
        }
        // Consume the action so activity recreation does not repeat an external navigation request.
        intent.action = null
        if (target != null) {
            navigationEventBus.navigateTo(target)
        }
    }
}
