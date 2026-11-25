@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.hypermusic.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.core.model.isDeviceUri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** The single metadata boundary shared by the session, client and persistence. */
object TrackMediaItemMapper {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val TRACK = "hypermusic.track.v1"

    fun toMediaItem(track: Track, uri: String): MediaItem {
        val deviceOrigin = track.isDeviceOrigin()
        val entry = track.copy(
            isDeviceLocal = deviceOrigin,
            queueEntryId = track.queueEntryId ?: UUID.randomUUID().toString()
        )
        val extras = Bundle().apply {
            putString(TRACK, json.encodeToString(entry))
            putString("queueEntryId", entry.queueEntryId)
            putString("trackType", entry.trackType)
            putBoolean("isImplicit", entry.isImplicit)
            putBoolean("isDeviceLocal", deviceOrigin)
        }
        val playbackUri = Uri.parse(uri).let { source ->
            if (source.scheme == "hyper" && source.getQueryParameter("entry") == null)
                source.buildUpon().appendQueryParameter("entry", entry.queueEntryId).build()
            else source
        }
        return MediaItem.Builder().setMediaId(entry.id).setUri(playbackUri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title).setArtist(entry.artist)
                .setAlbumTitle(entry.album).setDescription(entry.description)
                .setArtworkUri(entry.artwork?.takeIf { it.isNotBlank() }?.let(Uri::parse))
                .setExtras(extras).build()).build()
    }

    fun fromMediaItem(item: MediaItem): Track {
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        extras?.getString(TRACK)?.let { encoded ->
            runCatching { json.decodeFromString<Track>(encoded) }.getOrNull()?.let { decoded ->
                return if (decoded.isDeviceLocal != decoded.isDeviceOrigin()) {
                    decoded.copy(isDeviceLocal = true)
                } else decoded
            }
        }
        // Compatibility with externally supplied items and old session metadata.
        return Track(id = item.mediaId, title = metadata.title?.toString() ?: "Unknown",
            artist = metadata.artist?.toString() ?: "Unknown", artwork = metadata.artworkUri?.toString(),
            album = metadata.albumTitle?.toString(), description = metadata.description?.toString(),
            trackType = extras?.getString("trackType") ?: "song",
            isImplicit = extras?.getBoolean("isImplicit") ?: false,
            isDeviceLocal = (extras?.getBoolean("isDeviceLocal") ?: false) || isDeviceUri(item.mediaId),
            queueEntryId = extras?.getString("queueEntryId"))
    }

    /** Metadata promotion must retain the exact source configuration of the existing item. */
    fun promote(item: MediaItem): MediaItem = item.buildUpon().setMediaMetadata(
        toMediaItem(fromMediaItem(item).copy(isImplicit = false),
            item.localConfiguration?.uri?.toString().orEmpty()).mediaMetadata
    ).build()
}
