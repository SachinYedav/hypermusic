package com.hypermusic.feature.settings.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.feature.settings.SettingsViewModel
import com.hypermusic.feature.settings.components.SelectionOption
import com.hypermusic.feature.settings.components.SettingsActionRow
import com.hypermusic.feature.settings.components.SettingsSection
import com.hypermusic.feature.settings.components.SettingsSelectionSheet
import com.hypermusic.feature.settings.components.SettingsSwitchRow
import com.hypermusic.core.designsystem.theme.BackgroundTheme
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    onNavigateToStorage: () -> Unit = {},
    onNavigateToLegal: () -> Unit = {},
    onNavigateToOpenSource: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToWidgets: () -> Unit = {},
    onNavigateToAppUpdates: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val userSettings by viewModel.userSettings.collectAsState()
    val loudnessEnabled by viewModel.loudnessEnabled.collectAsState()

    var sheetVisible by remember { mutableStateOf(false) }
    var sheetType by remember { mutableStateOf("") }

    val themeModeOptions = listOf(
        SelectionOption("system", "System Default"),
        SelectionOption("light", "Light"),
        SelectionOption("dark", "Dark")
    )

    val qualityOptions = listOf(
        SelectionOption("data_saver", "Data Saver (Low)"),
        SelectionOption("normal", "Standard (~128kbps)"),
        SelectionOption("high", "High (Best Available)")
    )

    val backgroundThemeOptions = remember {
        BackgroundTheme.entries.map {
            SelectionOption(it.id, it.label)
        }
    }

    val languageOptions = listOf(
        SelectionOption("auto", "System Default"),
        SelectionOption("en", "English"),
        SelectionOption("hi", "Hindi"),
        SelectionOption("es", "Spanish"),
        SelectionOption("ja", "Japanese"),
        SelectionOption("fr", "French"),
        SelectionOption("de", "German"),
        SelectionOption("ko", "Korean")
    )

    val regionOptions = listOf(
        SelectionOption("auto", "System Default"),
        SelectionOption("US", "United States"),
        SelectionOption("IN", "India"),
        SelectionOption("GB", "United Kingdom"),
        SelectionOption("JP", "Japan"),
        SelectionOption("BR", "Brazil"),
        SelectionOption("CA", "Canada"),
        SelectionOption("FR", "France")
    )

    fun getShortLabel(id: String): String = when (id) {
        "data_saver" -> "Low"
        "normal" -> "Standard"
        "high" -> "High"
        else -> "Normal"
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasMicPermission by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasLocalMediaPermission by remember { mutableStateOf(false) }

    fun updatePermissions() {
        hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            hasLocalMediaPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            hasNotificationPermission = true
            hasLocalMediaPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updatePermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    ScreenWrapper {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = LocalBottomContentPadding.current)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                SettingsSection(title = "Personalization") {
                    SettingsActionRow(
                        icon = if (userSettings.themeMode == "dark") AppIcons.Moon else AppIcons.Sun,
                        label = "Theme Mode",
                        valueLabel = when (userSettings.themeMode) {
                            "light" -> "Light"
                            "dark" -> "Dark"
                            else -> "System Default"
                        },
                        isSelector = true,
                        onClick = {
                            sheetType = "theme_mode"
                            sheetVisible = true
                        }
                    )
                    SettingsActionRow(
                        icon = AppIcons.Palette,
                        label = "Ambient Accent Theme",
                        valueLabel = BackgroundTheme.fromString(userSettings.backgroundTheme).label,
                        isSelector = true,
                        onClick = {
                            sheetType = "background_theme"
                            sheetVisible = true
                        }
                    )
                    SettingsActionRow(
                        icon = AppIcons.LayoutTemplate,
                        label = "Widgets",
                        valueLabel = "Pin",
                        isLast = true,
                        onClick = onNavigateToWidgets
                    )
                }

                SettingsSection(title = "Audio & Sound") {
                    SettingsActionRow(
                        icon = AppIcons.Sliders,
                        label = "Equalizer",
                        onClick = onNavigateToEqualizer
                    )
                    SettingsSwitchRow(
                        icon = AppIcons.AudioWaveform,
                        label = "Loudness Boost",
                        description = "Boost playback volume and dynamic headroom using hardware DSP",
                        checked = loudnessEnabled,
                        onCheckedChange = { viewModel.toggleLoudnessBoost(it) }
                    )
                    SettingsSwitchRow(
                        icon = AppIcons.SkipSilence,
                        label = "Skip Silence",
                        description = "Automatically trim silent gaps at the start and end of tracks",
                        checked = userSettings.isSkipSilenceEnabled,
                        onCheckedChange = { viewModel.toggleSkipSilence(it) },
                        isLast = true
                    )
                }

                SettingsSection(title = "Playback & Streaming") {
                    SettingsActionRow(
                        icon = AppIcons.AudioLines,
                        label = "Streaming Quality",
                        valueLabel = if (userSettings.dataSaverEnabled) "Low (Data Saver)" else getShortLabel(userSettings.streamQuality),
                        isSelector = true,
                        onClick = {
                            sheetType = "streaming"
                            sheetVisible = true
                        }
                    )
                    SettingsSwitchRow(
                        icon = AppIcons.Leaf,
                        label = "Data Saver Mode",
                        checked = userSettings.dataSaverEnabled,
                        onCheckedChange = { viewModel.toggleDataSaver(it) }
                    )
                    SettingsSwitchRow(
                        icon = AppIcons.Infinity,
                        label = "Autoplay",
                        description = "Keep playing similar music when queue ends",
                        checked = userSettings.isAutoplayEnabled,
                        onCheckedChange = { viewModel.toggleAutoplay(it) }
                    )
                    SettingsSwitchRow(
                        icon = AppIcons.PersistentQueue,
                        label = "Persistent Playback Queue",
                        description = "Remember playing queue, active track, and position after closing the app",
                        checked = userSettings.isPersistentQueueEnabled,
                        onCheckedChange = { viewModel.togglePersistentQueue(it) }
                    )
                    SettingsSwitchRow(
                        icon = AppIcons.Video,
                        label = "Keep Screen Awake for Videos",
                        description = "Prevent display from sleeping while watching videos or video podcasts in full player",
                        checked = userSettings.keepScreenAwakeInVideo,
                        onCheckedChange = { viewModel.toggleKeepScreenAwakeInVideo(it) },
                        isLast = true
                    )
                }

                SettingsSection(title = "Downloads & Storage") {
                    SettingsActionRow(
                        icon = AppIcons.ArrowDownToLine,
                        label = "Download Quality",
                        valueLabel = getShortLabel(userSettings.downloadQuality),
                        isSelector = true,
                        onClick = {
                            sheetType = "download"
                            sheetVisible = true
                        }
                    )
                    SettingsSwitchRow(
                        icon = AppIcons.Wifi,
                        label = "Download over Wi-Fi only",
                        checked = userSettings.downloadOverWifiOnly,
                        onCheckedChange = { viewModel.toggleWifiOnly(it) }
                    )
                    SettingsSwitchRow(
                        icon = AppIcons.Video,
                        label = "Download Podcasts as Video",
                        checked = userSettings.downloadPodcastsAsVideo,
                        onCheckedChange = { viewModel.toggleDownloadPodcastsAsVideo(it) }
                    )
                    SettingsActionRow(
                        icon = AppIcons.HardDrive,
                        label = "Storage Management",
                        isLast = true,
                        onClick = onNavigateToStorage
                    )
                }

                SettingsSection(title = "App Permissions") {
                    SettingsActionRow(
                        icon = AppIcons.Bell,
                        label = "Notifications",
                        statusBadge = if (hasNotificationPermission) "Granted" else "Required",
                        statusBadgeBackground = if (hasNotificationPermission) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        statusBadgeTextColor = if (hasNotificationPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        isExternal = true,
                        onClick = { openAppSettings() }
                    )
                    SettingsActionRow(
                        icon = AppIcons.Mic,
                        label = "Microphone Access",
                        statusBadge = if (hasMicPermission) "Granted" else "Optional",
                        statusBadgeBackground = if (hasMicPermission) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        statusBadgeTextColor = if (hasMicPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        isExternal = true,
                        onClick = { openAppSettings() }
                    )
                    SettingsActionRow(
                        icon = AppIcons.Folder,
                        label = "Local Media Access",
                        statusBadge = if (hasLocalMediaPermission) "Granted" else "Optional",
                        statusBadgeBackground = if (hasLocalMediaPermission) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        statusBadgeTextColor = if (hasLocalMediaPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        isExternal = true,
                        isLast = true,
                        onClick = { openAppSettings() }
                    )
                }

                SettingsSection(title = "Content Preferences") {
                    SettingsActionRow(
                        icon = AppIcons.Languages,
                        label = "Content Language",
                        valueLabel = languageOptions.find { it.id == userSettings.contentLanguage }?.label ?: "Auto",
                        isSelector = true,
                        onClick = { 
                            sheetType = "content_language"
                            sheetVisible = true 
                        }
                    )
                    SettingsActionRow(
                        icon = AppIcons.MapPin,
                        label = "Content Region",
                        valueLabel = regionOptions.find { it.id == userSettings.contentRegion }?.label ?: "Auto",
                        isSelector = true,
                        onClick = { 
                            sheetType = "content_region"
                            sheetVisible = true 
                        }
                    )
                    SettingsSwitchRow(
                        icon = AppIcons.ExplicitBadge,
                        label = "Allow Explicit Content",
                        description = "Show and play tracks marked with an [E] badge",
                        checked = userSettings.allowExplicitContent,
                        onCheckedChange = { viewModel.toggleExplicitContent(it) },
                        isLast = true
                    )
                }

                SettingsSection(title = "About") {
                    SettingsActionRow(
                        icon = AppIcons.ArrowUpCircle,
                        label = "App Updates",
                        onClick = onNavigateToAppUpdates
                    )
                    SettingsActionRow(
                        icon = AppIcons.Info,
                        label = "About HyperMusic",
                        valueLabel = "v${com.hypermusic.BuildConfig.VERSION_NAME}",
                        onClick = onNavigateToAbout,
                        isLast = true
                    )
                }
            }
        }
        
        SettingsSelectionSheet(
            visible = sheetVisible,
            onClose = { sheetVisible = false },
            title = when (sheetType) {
                "theme_mode" -> "Theme Mode"
                "streaming" -> "Streaming Quality"
                "download" -> "Download Quality"
                "background_theme" -> "Ambient Accent Theme"
                "content_language" -> "Content Language"
                "content_region" -> "Content Region"
                else -> ""
            },
            options = when (sheetType) {
                "theme_mode" -> themeModeOptions
                "streaming", "download" -> qualityOptions
                "background_theme" -> backgroundThemeOptions
                "content_language" -> languageOptions
                "content_region" -> regionOptions
                else -> emptyList()
            },
            selectedValue = when (sheetType) {
                "theme_mode" -> userSettings.themeMode
                "streaming" -> userSettings.streamQuality
                "download" -> userSettings.downloadQuality
                "background_theme" -> userSettings.backgroundTheme
                "content_language" -> userSettings.contentLanguage
                "content_region" -> userSettings.contentRegion
                else -> ""
            },
            onSelect = { selectedValue ->
                when (sheetType) {
                    "theme_mode" -> viewModel.setThemeMode(selectedValue)
                    "streaming" -> viewModel.setStreamQuality(selectedValue)
                    "download" -> viewModel.setDownloadQuality(selectedValue)
                    "background_theme" -> viewModel.setBackgroundTheme(selectedValue)
                    "content_language" -> viewModel.setContentLanguage(selectedValue)
                    "content_region" -> viewModel.setContentRegion(selectedValue)
                }
            }
        )
    }
}
