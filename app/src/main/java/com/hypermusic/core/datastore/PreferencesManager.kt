package com.hypermusic.core.datastore

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hypermusic.core.model.UserSettings
import com.hypermusic.core.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        const val SYNC_PREFS_NAME = "sync_theme_pref"
        const val KEY_CACHED_THEME = "cached_theme_mode"

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STREAM_QUALITY = stringPreferencesKey("stream_quality")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val DATA_SAVER_ENABLED = booleanPreferencesKey("data_saver_enabled")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val IS_AUTOPLAY_ENABLED = booleanPreferencesKey("is_autoplay_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        val DOWNLOAD_PODCASTS_AS_VIDEO = booleanPreferencesKey("download_podcasts_as_video")
        val LIBRARY_GRID_VIEW = booleanPreferencesKey("library_grid_view")
        val LIBRARY_SORT_MODE = stringPreferencesKey("library_sort_mode")
        val BACKGROUND_THEME = stringPreferencesKey("background_theme")
        val ALLOW_EXPLICIT_CONTENT = booleanPreferencesKey("allow_explicit_content")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val CONTENT_REGION = stringPreferencesKey("content_region")
        val PERSISTENT_QUEUE_ENABLED = booleanPreferencesKey("persistent_queue_enabled")
        val SKIP_SILENCE_ENABLED = booleanPreferencesKey("skip_silence_enabled")
        val KEEP_SCREEN_AWAKE_IN_VIDEO = booleanPreferencesKey("keep_screen_awake_in_video")

        fun applyUiMode(context: Context, themeMode: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
                    val targetMode = when (themeMode) {
                        "dark" -> UiModeManager.MODE_NIGHT_YES
                        "light" -> UiModeManager.MODE_NIGHT_NO
                        else -> UiModeManager.MODE_NIGHT_AUTO
                    }
                    uiModeManager?.setApplicationNightMode(targetMode)
                } catch (e: Exception) {
                    Logger.w("PreferencesManager", "Failed to setApplicationNightMode", e)
                }
            }
        }
    }

    fun getInitialThemeMode(): String {
        val syncPrefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return syncPrefs.getString(KEY_CACHED_THEME, "system") ?: "system"
    }

    fun syncCacheIfNeeded(themeMode: String) {
        val syncPrefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val currentCached = syncPrefs.getString(KEY_CACHED_THEME, null)
        if (currentCached != themeMode) {
            syncPrefs.edit().putString(KEY_CACHED_THEME, themeMode).apply()
            applyUiMode(context, themeMode)
        }
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themeMode = prefs[THEME_MODE] ?: "system",
            streamQuality = prefs[STREAM_QUALITY] ?: "normal",
            downloadQuality = prefs[DOWNLOAD_QUALITY] ?: "normal",
            dataSaverEnabled = prefs[DATA_SAVER_ENABLED] ?: false,
            downloadOverWifiOnly = prefs[WIFI_ONLY] ?: false,
            isAutoplayEnabled = prefs[IS_AUTOPLAY_ENABLED] ?: true,
            repeatMode = prefs[REPEAT_MODE] ?: "off",
            isShuffleEnabled = prefs[SHUFFLE_MODE] ?: false,
            downloadPodcastsAsVideo = prefs[DOWNLOAD_PODCASTS_AS_VIDEO] ?: true,
            backgroundTheme = prefs[BACKGROUND_THEME] ?: "auto",
            allowExplicitContent = prefs[ALLOW_EXPLICIT_CONTENT] ?: true,
            contentLanguage = prefs[CONTENT_LANGUAGE] ?: "auto",
            contentRegion = prefs[CONTENT_REGION] ?: "auto",
            isPersistentQueueEnabled = prefs[PERSISTENT_QUEUE_ENABLED] ?: true,
            isSkipSilenceEnabled = prefs[SKIP_SILENCE_ENABLED] ?: false,
            keepScreenAwakeInVideo = prefs[KEEP_SCREEN_AWAKE_IN_VIDEO] ?: true
        )
    }

    val libraryIsGridView: Flow<Boolean> = context.dataStore.data.map { it[LIBRARY_GRID_VIEW] ?: false }
    val librarySortMode: Flow<String> = context.dataStore.data.map {
        val stored = it[LIBRARY_SORT_MODE] ?: "Recently played"
        if (stored == "Recents") "Recently played" else stored
    }

    suspend fun setLibraryGridView(isGrid: Boolean) {
        context.dataStore.edit { it[LIBRARY_GRID_VIEW] = isGrid }
    }

    suspend fun setLibrarySortMode(sortMode: String) {
        context.dataStore.edit { it[LIBRARY_SORT_MODE] = sortMode }
    }

    suspend fun setThemeMode(themeMode: String) {
        val syncPrefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        syncPrefs.edit().putString(KEY_CACHED_THEME, themeMode).apply()
        applyUiMode(context, themeMode)
        context.dataStore.edit { prefs -> prefs[THEME_MODE] = themeMode }
    }

    suspend fun setStreamQuality(quality: String) {
        context.dataStore.edit { prefs -> prefs[STREAM_QUALITY] = quality }
    }

    suspend fun setDownloadQuality(quality: String) {
        context.dataStore.edit { prefs -> prefs[DOWNLOAD_QUALITY] = quality }
    }

    suspend fun toggleDataSaver(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[DATA_SAVER_ENABLED] = enabled }
    }

    suspend fun toggleWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { prefs -> prefs[WIFI_ONLY] = wifiOnly }
    }

    suspend fun toggleAutoplay(autoplayEnabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[IS_AUTOPLAY_ENABLED] = autoplayEnabled }
    }

    suspend fun setRepeatMode(repeatMode: String) {
        context.dataStore.edit { prefs -> prefs[REPEAT_MODE] = repeatMode }
    }

    suspend fun setShuffleMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SHUFFLE_MODE] = enabled }
    }

    suspend fun toggleDownloadPodcastsAsVideo(downloadAsVideo: Boolean) {
        context.dataStore.edit { prefs -> prefs[DOWNLOAD_PODCASTS_AS_VIDEO] = downloadAsVideo }
    }

    suspend fun setBackgroundTheme(theme: String) {
        context.dataStore.edit { prefs -> prefs[BACKGROUND_THEME] = theme }
    }

    suspend fun toggleExplicitContent(allow: Boolean) {
        context.dataStore.edit { prefs -> prefs[ALLOW_EXPLICIT_CONTENT] = allow }
    }

    suspend fun setContentLanguage(language: String) {
        context.dataStore.edit { prefs -> prefs[CONTENT_LANGUAGE] = language }
    }

    suspend fun setContentRegion(region: String) {
        context.dataStore.edit { prefs -> prefs[CONTENT_REGION] = region }
    }

    suspend fun togglePersistentQueue(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PERSISTENT_QUEUE_ENABLED] = enabled }
    }

    suspend fun toggleSkipSilence(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SKIP_SILENCE_ENABLED] = enabled }
    }

    suspend fun toggleKeepScreenAwakeInVideo(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEEP_SCREEN_AWAKE_IN_VIDEO] = enabled }
    }
}
