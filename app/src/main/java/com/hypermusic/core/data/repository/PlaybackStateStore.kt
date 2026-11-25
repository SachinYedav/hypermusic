package com.hypermusic.core.data.repository

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.util.AtomicFile
import com.hypermusic.core.model.ArtistInfo
import com.hypermusic.core.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Immutable
data class PlaybackStateSnapshot(
    val queueTracks: List<Track>,
    val currentIndex: Int,
    val playbackPositionMs: Long,
    val totalDurationMs: Long,
    val shuffleModeEnabled: Boolean,
    val repeatMode: Int,
    val isVideoMode: Boolean,
    val traversalOrder: List<Int> = emptyList()
)

class PlaybackStateStore(context: Context) {

    private val lock = Any()
    private val atomicFile = AtomicFile(File(context.filesDir, "playback_state.json"))

    private data class Write(val snapshot: PlaybackStateSnapshot?, val revision: Long)
    private var revision = 0L
    private val committedRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    private val writes = kotlinx.coroutines.channels.Channel<Write>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val latest = java.util.concurrent.atomic.AtomicReference<Write?>()
    private val writerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    init {
        writerScope.launch {
            for (write in writes) {
                try {
                    if (write.snapshot == null) synchronized(lock) { atomicFile.delete() }
                    else writeSnapshot(write.snapshot)
                    committedRevision.value = write.revision
                } catch (e: Exception) {
                    com.hypermusic.core.utils.Logger.fatal("PlaybackStateStore", "Resume state commit failed", e)
                }
            }
        }
    }

    fun saveState(snapshot: PlaybackStateSnapshot) {
        synchronized(latest) {
            val write = Write(snapshot.copy(queueTracks = snapshot.queueTracks.toList(), traversalOrder = snapshot.traversalOrder.toList()), ++revision)
            latest.set(write); writes.trySend(write)
        }
    }

    private fun writeSnapshot(snapshot: PlaybackStateSnapshot) {
        synchronized(lock) {
            try {
                val root = JSONObject()
                root.put("version", 1)
                root.put("traversalOrder", JSONArray(snapshot.traversalOrder))
                root.put("currentIndex", snapshot.currentIndex)
                root.put("playbackPositionMs", snapshot.playbackPositionMs)
                root.put("totalDurationMs", snapshot.totalDurationMs)
                root.put("shuffleModeEnabled", snapshot.shuffleModeEnabled)
                root.put("repeatMode", snapshot.repeatMode)
                root.put("isVideoMode", snapshot.isVideoMode)

                val tracksArray = JSONArray()
                for (track in snapshot.queueTracks) {
                    tracksArray.put(trackToJson(track))
                }
                root.put("queueTracks", tracksArray)

                writeInternal(root.toString())
            } catch (e: Exception) { throw e }
        }
    }

    fun loadState(): PlaybackStateSnapshot? {
        latest.get()?.let { return it.snapshot }
        synchronized(lock) {
            val jsonStr = readInternal() ?: return null
            return try {
                val root = JSONObject(jsonStr)
                if (root.optInt("version", 1) != 1) return null
                val tracksArray = root.optJSONArray("queueTracks") ?: return null
                val tracks = ArrayList<Track>(tracksArray.length())
                for (i in 0 until tracksArray.length()) {
                    val tObj = tracksArray.getJSONObject(i)
                    tracks.add(jsonToTrack(tObj))
                }
                if (tracks.isEmpty()) return null

                PlaybackStateSnapshot(
                    queueTracks = tracks,
                    currentIndex = root.optInt("currentIndex", 0),
                    playbackPositionMs = root.optLong("playbackPositionMs", 0L),
                    totalDurationMs = root.optLong("totalDurationMs", 0L),
                    shuffleModeEnabled = root.optBoolean("shuffleModeEnabled", false),
                    repeatMode = root.optInt("repeatMode", 0),
                    isVideoMode = root.optBoolean("isVideoMode", false),
                    traversalOrder = root.optJSONArray("traversalOrder")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList()
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun clearState() {
        synchronized(latest) {
            val write = Write(null, ++revision)
            latest.set(write); writes.trySend(write)
        }
    }

    internal suspend fun awaitWrites() {
        val expected = synchronized(latest) { revision }
        committedRevision.first { it >= expected }
    }

    private fun writeInternal(content: String) {
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
            throw e
        }
    }

    private fun readInternal(): String? {
        return try {
            atomicFile.openRead().use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (_: Exception) {
            null
        }
    }

    private val trackJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun trackToJson(track: Track): JSONObject = JSONObject(trackJson.encodeToString(track))
    private fun jsonToTrack(obj: JSONObject): Track = trackJson.decodeFromString<Track>(obj.toString())
}
