package com.hypermusic.core.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.hypermusic.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

class DeviceMediaRepository(private val context: Context) {

    /** Queries MediaStore audio marked as music with a duration of at least 15 seconds. */
    suspend fun getDeviceAudioFiles(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        var selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 15000"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection += " AND ${MediaStore.Audio.Media.IS_PENDING} = 0"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            selection += " AND ${MediaStore.Audio.Media.IS_TRASHED} = 0"
        }

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                val artworkUri = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    ensureActive()
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val durationMs = cursor.getLong(durationColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn) else 0L

                    val contentUri = ContentUris.withAppendedId(uri, id).toString()
                    val albumArtUri = ContentUris.withAppendedId(artworkUri, albumId).toString()
                    val timestampMs = if (dateAdded > 0) dateAdded * 1000L else 0L

                    tracks.add(
                        Track(
                            id = contentUri,
                            title = title,
                            artist = artist,
                            artwork = albumArtUri,
                            duration = durationMs / 1000,
                            trackType = "song",
                            isDeviceLocal = true,
                            lastPlayedAt = timestampMs
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tracks
    }

    suspend fun isDeviceUriReadable(uriString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val parsed = Uri.parse(uriString)
            context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
