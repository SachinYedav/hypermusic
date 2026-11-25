package com.hypermusic.core.model

data class ReleaseAdvisory(
    val title: String,
    val description: String,
    val badge: String? = null
)

data class AppUpdateData(
    val latestVersion: String,
    val versionCode: Int,
    val forceUpdate: Boolean = false,
    val releaseDate: String = "",
    val downloadUrl: String = "",
    val changelog: List<ChangelogItem> = emptyList(),
    val advisory: ReleaseAdvisory? = null
)

data class ChangelogItem(
    val type: String, // "improvement", "fix", "patch"
    val text: String
)

data class AppUpdateCheckResult(
    val isUpdateAvailable: Boolean,
    val updateData: AppUpdateData? = null,
    val hasPublishedReleases: Boolean = true,
    val errorMessage: String? = null
)
