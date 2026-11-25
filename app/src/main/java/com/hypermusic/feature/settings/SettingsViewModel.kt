package com.hypermusic.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.core.data.repository.DownloadRepository
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.model.UserSettings
import com.hypermusic.core.player.PlayerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val playerRepository: PlayerRepository,
    private val equalizerManager: com.hypermusic.core.audio.EqualizerManager,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val userSettings: StateFlow<UserSettings> = preferencesManager.userSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    val loudnessEnabled = equalizerManager.loudnessEnabled

    fun toggleLoudnessBoost(enabled: Boolean) {
        equalizerManager.setLoudnessEnabled(enabled)
    }

    fun toggleSkipSilence(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.toggleSkipSilence(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setStreamQuality(quality: String) {
        viewModelScope.launch { preferencesManager.setStreamQuality(quality) }
    }

    fun setDownloadQuality(quality: String) {
        viewModelScope.launch { preferencesManager.setDownloadQuality(quality) }
    }

    fun toggleDataSaver(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.toggleDataSaver(enabled) }
    }
    fun toggleAutoplay(autoplayEnabled: Boolean) {
        viewModelScope.launch { preferencesManager.toggleAutoplay(autoplayEnabled) }
    }

    fun togglePersistentQueue(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.togglePersistentQueue(enabled)
            if (!enabled) {
                playerRepository.clearSavedPlaybackState()
            }
        }
    }

    fun toggleWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            preferencesManager.toggleWifiOnly(wifiOnly)
            downloadRepository.startQueueProcessor(forceReplace = true)
        }
    }

    fun toggleDownloadPodcastsAsVideo(downloadAsVideo: Boolean) {
        viewModelScope.launch { preferencesManager.toggleDownloadPodcastsAsVideo(downloadAsVideo) }
    }

    fun setBackgroundTheme(theme: String) {
        viewModelScope.launch { preferencesManager.setBackgroundTheme(theme) }
    }

    fun toggleExplicitContent(allow: Boolean) {
        viewModelScope.launch { preferencesManager.toggleExplicitContent(allow) }
    }

    fun setContentLanguage(language: String) {
        viewModelScope.launch { preferencesManager.setContentLanguage(language) }
    }

    fun setContentRegion(region: String) {
        viewModelScope.launch { preferencesManager.setContentRegion(region) }
    }

    fun toggleKeepScreenAwakeInVideo(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.toggleKeepScreenAwakeInVideo(enabled) }
    }
}
