package com.hypermusic.core.data.repository

import android.content.Context
import com.hypermusic.BuildConfig
import com.hypermusic.core.model.AppUpdateCheckResult
import com.hypermusic.core.model.AppUpdateData
import com.hypermusic.core.model.ChangelogItem
import com.hypermusic.core.model.ReleaseAdvisory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateRepository(
    private val context: Context
) {
    companion object {
        private const val UPDATE_ENDPOINT = "https://raw.githubusercontent.com/SachinYedav/hypermusic/main/public/releases.json"
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 10000
    }

    suspend fun checkForUpdates(): Result<AppUpdateCheckResult> = withContext(Dispatchers.IO) {
        try {
            val url = URL(UPDATE_ENDPOINT)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "HyperMusic-Android/${BuildConfig.VERSION_NAME}")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                return@withContext Result.failure(
                    Exception("Server returned status $responseCode: $errorBody")
                )
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val rootJson = JSONObject(responseText)

            val releasesArr = rootJson.optJSONArray("releases")
            if (releasesArr != null) {
                if (releasesArr.length() == 0) {
                    return@withContext Result.success(
                        AppUpdateCheckResult(
                            isUpdateAvailable = false,
                            updateData = null,
                            hasPublishedReleases = false
                        )
                    )
                }

                var selectedRelease: JSONObject? = null
                for (i in 0 until releasesArr.length()) {
                    val r = releasesArr.optJSONObject(i) ?: continue
                    if (r.optString("channel") == "stable") {
                        selectedRelease = r
                        break
                    }
                }
                if (selectedRelease == null) {
                    selectedRelease = releasesArr.optJSONObject(0)
                }

                if (selectedRelease != null) {
                    val latestVersion = selectedRelease.optString("version", "")
                    val remoteVersionCode = selectedRelease.optInt("versionCode", 0)
                    val releaseDate = selectedRelease.optString("date", "")
                    val apkObj = selectedRelease.optJSONObject("apk")
                    val downloadUrl = apkObj?.optString("url") ?: selectedRelease.optString("url", "")

                    val changelogList = mutableListOf<ChangelogItem>()
                    val detailsObj = selectedRelease.optJSONObject("details")
                    if (detailsObj != null) {
                        fun addItems(arrName: String, type: String) {
                            val arr = detailsObj.optJSONArray(arrName) ?: return
                            for (j in 0 until arr.length()) {
                                val t = arr.optString(j)
                                if (t.isNotBlank()) changelogList.add(ChangelogItem(type = type, text = t))
                            }
                        }
                        addItems("features", "feature")
                        addItems("improvements", "improvement")
                        addItems("fixes", "fix")
                    }

                    val advisoryObj = selectedRelease.optJSONObject("advisory")
                    val advisory = if (advisoryObj != null) {
                        val title = advisoryObj.optString("title", "")
                        val description = advisoryObj.optString("description", "")
                        if (title.isNotBlank() || description.isNotBlank()) {
                            val badge = advisoryObj.optString("badge").takeIf { !it.isNullOrBlank() }
                            ReleaseAdvisory(title = title, description = description, badge = badge)
                        } else null
                    } else null

                    val updateData = AppUpdateData(
                        latestVersion = latestVersion,
                        versionCode = remoteVersionCode,
                        forceUpdate = false,
                        releaseDate = releaseDate,
                        downloadUrl = downloadUrl,
                        changelog = changelogList,
                        advisory = advisory
                    )

                    val isNewer = remoteVersionCode > BuildConfig.VERSION_CODE
                    return@withContext Result.success(
                        AppUpdateCheckResult(
                            isUpdateAvailable = isNewer,
                            updateData = updateData
                        )
                    )
                }
            }

            val status = rootJson.optString("status")
            if (status != "success") {
                val message = rootJson.optString("message", "No updates found.")
                return@withContext Result.failure(Exception(message))
            }

            val dataObj = rootJson.optJSONObject("data")
                ?: return@withContext Result.failure(Exception("Missing update data in response."))

            val latestVersion = dataObj.optString("latestVersion", "")
            val remoteVersionCode = dataObj.optInt("versionCode", 0)
            val forceUpdate = dataObj.optBoolean("forceUpdate", false)
            val releaseDate = dataObj.optString("releaseDate", "")
            val downloadUrl = dataObj.optString("downloadUrl", "")

            val changelogList = mutableListOf<ChangelogItem>()
            val changelogArr = dataObj.optJSONArray("changelog")
            if (changelogArr != null) {
                for (i in 0 until changelogArr.length()) {
                    val itemObj = changelogArr.optJSONObject(i)
                    if (itemObj != null) {
                        changelogList.add(
                            ChangelogItem(
                                type = itemObj.optString("type", "improvement"),
                                text = itemObj.optString("text", "")
                            )
                        )
                    }
                }
            }

            val advisoryObj = dataObj.optJSONObject("advisory")
            val advisory = if (advisoryObj != null) {
                val title = advisoryObj.optString("title", "")
                val description = advisoryObj.optString("description", "")
                if (title.isNotBlank() || description.isNotBlank()) {
                    val badge = advisoryObj.optString("badge").takeIf { !it.isNullOrBlank() }
                    ReleaseAdvisory(
                        title = title,
                        description = description,
                        badge = badge
                    )
                } else null
            } else null

            val updateData = AppUpdateData(
                latestVersion = latestVersion,
                versionCode = remoteVersionCode,
                forceUpdate = forceUpdate,
                releaseDate = releaseDate,
                downloadUrl = downloadUrl,
                changelog = changelogList,
                advisory = advisory
            )

            val isNewer = remoteVersionCode > BuildConfig.VERSION_CODE
            val result = AppUpdateCheckResult(
                isUpdateAvailable = isNewer,
                updateData = updateData
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
