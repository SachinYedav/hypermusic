package com.hypermusic.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserSettings(
    val themeMode: String = "system",
    val streamQuality: String = "normal",
    val downloadQuality: String = "normal",
    val dataSaverEnabled: Boolean = false,
    val downloadOverWifiOnly: Boolean = false,
    val isAutoplayEnabled: Boolean = true,
    val repeatMode: String = "off",
    val isShuffleEnabled: Boolean = false,
    val downloadPodcastsAsVideo: Boolean = true,
    val backgroundTheme: String = "auto",
    val allowExplicitContent: Boolean = true,
    val contentLanguage: String = "auto",
    val contentRegion: String = "auto",
    val isPersistentQueueEnabled: Boolean = true,
    val isSkipSilenceEnabled: Boolean = false,
    val keepScreenAwakeInVideo: Boolean = true
)
