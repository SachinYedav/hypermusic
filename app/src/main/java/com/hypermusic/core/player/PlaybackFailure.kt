@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.hypermusic.core.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache

enum class PlaybackFailure(val userMessage: String) {
    EXPIRED_URL("The stream link expired. Retry this track."),
    NETWORK("Connection unavailable. Reconnect and retry this track."),
    TIMEOUT("The connection timed out. Retry this track."),
    CONTENT_UNAVAILABLE("This track is unavailable."),
    LOCAL_UNAVAILABLE("The downloaded file is unavailable. Download it again or retry online."),
    DEVICE_UNAVAILABLE("This device file is unavailable."),
    DEVICE_PERMISSION("Audio permission is required to play this device file."),
    CACHE("Cached media could not be read. Retry this track."),
    UNSUPPORTED("This media format is not supported."),
    UNKNOWN("Playback failed. Retry this track.");

    companion object {
        fun classify(error: PlaybackException, isDeviceTrack: Boolean = false): PlaybackFailure {
            val causes = generateSequence<Throwable>(error) { it.cause }.take(16).toList()
            if (isDeviceTrack) {
                return when {
                    causes.any { it is SecurityException } -> DEVICE_PERMISSION
                    causes.any {
                        it is java.io.FileNotFoundException ||
                            it is androidx.media3.datasource.ContentDataSource.ContentDataSourceException ||
                            it is DataSourceException
                    } -> DEVICE_UNAVAILABLE
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> UNSUPPORTED
                    else -> UNKNOWN
                }
            }
            val http = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
            return when {
                http?.responseCode in setOf(403, 410) -> EXPIRED_URL
                http?.responseCode in setOf(404, 451) -> CONTENT_UNAVAILABLE
                causes.any { it is org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException } -> CONTENT_UNAVAILABLE
                causes.any { it is org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException } -> UNSUPPORTED
                causes.any { it is Cache.CacheException || (it is DataSourceException && it.message?.contains("cache", ignoreCase = true) == true) || (it is java.io.FileNotFoundException && it.message?.contains("cache", ignoreCase = true) == true) } -> CACHE
                causes.any { it is java.io.FileNotFoundException } -> LOCAL_UNAVAILABLE
                causes.any { it is java.net.SocketTimeoutException || it is java.util.concurrent.TimeoutException } || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> TIMEOUT
                causes.any { it is java.net.UnknownHostException || it is java.net.ConnectException } -> NETWORK
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> NETWORK
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> UNSUPPORTED
                else -> UNKNOWN
            }
        }
    }
}
