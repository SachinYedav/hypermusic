package com.hypermusic.core.data.repository

import androidx.core.util.AtomicFile
import com.hypermusic.core.data.local.dao.DownloadDao
import com.hypermusic.core.data.local.dao.TrackDao
import com.hypermusic.core.data.local.entity.DownloadEntity
import com.hypermusic.core.data.local.entity.DownloadQueueEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** A durable receipt bridges the filesystem rename and Room transaction. Callers must use an IO dispatcher. */
class DownloadFinalizer(
    private val directory: File,
    private val tracks: TrackDao,
    private val downloads: DownloadDao
) {
    companion object {
        private val files = Mutex()
        private val ownedName = Regex("[A-Za-z0-9_-]+\\.[a-f0-9]{16}\\.[a-z0-9]+")
        suspend fun <T> withExclusiveFiles(action: suspend () -> T): T = files.withLock { action() }
    }

    suspend fun finish(temp: File, target: File, record: DownloadEntity, queue: DownloadQueueEntity, artwork: String?): Boolean =
        withExclusiveFiles {
            val current = downloads.getQueueItem(record.trackId)
            if (current?.addedAt != queue.addedAt || current.status != "DOWNLOADING") return@withExclusiveFiles false
            stage(temp, target, record, queue, artwork)
            val committed = downloads.completeDownload(record, target.canonicalPath, artwork, queue.addedAt)
            if (committed) receipt(target).delete()
            // A failed/paused commit keeps the receipt for recovery. It never advertises an orphan.
            committed
        }

    /** Stages the file and recovery receipt; the caller must hold the file lock. */
    internal fun stage(temp: File, target: File, record: DownloadEntity, queue: DownloadQueueEntity, artwork: String?) {
        checkOwned(target)
        require(temp.canonicalFile.parentFile == directory.canonicalFile)
        if (!temp.isFile || record.sizeBytes <= 0 || temp.length() != record.sizeBytes) throw IOException("Incomplete download")
        val json = JSONObject().put("version", 1).put("trackId", record.trackId)
            .put("file", target.name).put("size", record.sizeBytes).put("queuedAt", queue.addedAt)
            .put("downloadedAt", record.downloadedAt).put("mimeType", record.mimeType)
            .put("formatId", record.formatId).put("hasVideo", record.hasVideo).put("artwork", artwork)
        val pending = receipt(target)
        val output = pending.startWrite()
        try { output.write(json.toString().toByteArray(Charsets.UTF_8)); pending.finishWrite(output) }
        catch (e: Exception) { pending.failWrite(output); throw e }
        if (!temp.renameTo(target) || target.length() != record.sizeBytes) throw IOException("Download finalization failed")
    }

    suspend fun recover() = withExclusiveFiles {
        if (!directory.isDirectory) return@withExclusiveFiles
        val receipts = directory.listFiles().orEmpty().filter { it.name.endsWith(".pending") || it.name.endsWith(".pending.bak") }
            .map { File(directory, it.name.removeSuffix(".bak")) }.distinctBy { it.name }
        val retained = mutableSetOf<String>()
        for (file in receipts) {
            val atomic = AtomicFile(file)
            val json = try { JSONObject(atomic.openRead().bufferedReader().use { it.readText() }) }
            catch (_: Exception) { atomic.delete(); continue }
            val name = json.optString("file")
            val id = json.optString("trackId")
            val target = File(directory, name)
            if (json.optInt("version") != 1 || !ownedName.matches(name) || name.substringBefore('.') != id ||
                target.canonicalFile.parentFile != directory.canonicalFile) { atomic.delete(); continue }
            val existing = tracks.getTrackById(id)
            val recorded = existing?.localFilePath?.let { File(it).canonicalPath } == target.canonicalPath
            val expected = json.optLong("size")
            val valid = DownloadAvailability.inspect(target, expected, true).state == DownloadState.VALID
            if (recorded && downloads.getDownload(id)?.sizeBytes == expected && valid) { atomic.delete(); continue }
            val queue = downloads.getQueueItem(id)
            if (queue != null && queue.addedAt == json.optLong("queuedAt") && valid) {
                val record = DownloadEntity(id, json.getLong("downloadedAt"), expected, "COMPLETED",
                    json.nullableString("mimeType"), json.nullableString("formatId"),
                    if (json.has("hasVideo") && !json.isNull("hasVideo")) json.getBoolean("hasVideo") else null)
                val artwork = json.nullableString("artwork")?.takeIf { File(it).isFile }
                if (downloads.completeDownload(record, target.canonicalPath, artwork, queue.addedAt, allowPending = true)) atomic.delete()
                else retained += target.canonicalPath // A user-paused queue remains paused.
            } else {
                if (!recorded && target.exists() && !target.delete()) throw IOException("Orphan download cleanup failed")
                atomic.delete()
            }
        }
        // Remove unreferenced finalized files that have no recovery receipt.
        val referenced = downloads.getAllDownloadedTrackIds().mapNotNull { tracks.getTrackById(it)?.localFilePath }
            .map { File(it).canonicalPath }.toSet()
        directory.listFiles().orEmpty().filter { it.isFile && ownedName.matches(it.name) }.forEach {
            if (it.canonicalPath !in referenced && it.canonicalPath !in retained && !it.delete()) throw IOException("Orphan download cleanup failed")
        }
    }

    private fun checkOwned(file: File) {
        require(file.canonicalFile.parentFile == directory.canonicalFile && ownedName.matches(file.name))
    }
    private fun receipt(file: File) = AtomicFile(File(directory, file.name + ".pending"))
    private fun JSONObject.nullableString(key: String) = if (!has(key) || isNull(key)) null else getString(key)
}
