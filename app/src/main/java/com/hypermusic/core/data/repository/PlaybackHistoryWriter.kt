package com.hypermusic.core.data.repository

import androidx.room.withTransaction
import com.hypermusic.core.data.local.AppDatabase
import com.hypermusic.core.data.local.entity.PlaybackCheckpoint
import com.hypermusic.core.data.local.entity.HistoryEntity
import com.hypermusic.core.data.mapper.toEntity
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicLong

/** Survives service teardown. Immutable, cumulative checkpoints make retries idempotent. */
class PlaybackHistoryWriter(private val db: AppDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val epoch = AtomicLong()
    private val submissionLock = Any()
    val generation: Long get() = epoch.get()
    @Volatile var clearedAtMs: Long = 0
        private set
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    init { scope.launch { for (command in commands) command() } }

    fun checkpoint(track: Track, session: String, totalMs: Long, generation: Long) {
        if (track.isDeviceOrigin() || totalMs < 10_000) return
        synchronized(submissionLock) { commands.trySend {
            repeat(3) { attempt ->
                if (generation != epoch.get()) return@trySend
                try {
                    db.withTransaction {
                        if (generation != epoch.get()) return@withTransaction
                        db.trackDao().saveMetadata(track.toEntity())
                        val old = db.historyDao().getCheckpoint(session)
                        if (old == null) db.historyDao().pruneCheckpoints(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
                        val delta = totalMs - (old?.creditedMs ?: 0)
                        if (delta > 0) {
                            db.historyDao().credit(track.id, delta, if (old == null) 1 else 0, System.currentTimeMillis())
                            db.historyDao().putCheckpoint(PlaybackCheckpoint(session, track.id, totalMs, System.currentTimeMillis()))
                        }
                    }
                    return@trySend
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (attempt == 2) com.hypermusic.core.utils.Logger.fatal("PlaybackHistory", "Checkpoint commit failed", e)
                    else delay(100L * (attempt + 1))
                }
            }
        } }
    }

    suspend fun clear(trackId: String? = null) {
        val completion = CompletableDeferred<Unit>()
        synchronized(submissionLock) {
        clearedAtMs = android.os.SystemClock.elapsedRealtime()
        epoch.incrementAndGet() // Already queued pre-clear checkpoints are obsolete immediately.
        commands.trySend {
            try {
                db.withTransaction {
                    if (trackId == null) {
                        db.historyDao().clearHistory()
                        db.historyDao().clearCheckpoints()
                    } else {
                        db.historyDao().deleteHistoryByTrackId(trackId)
                        db.historyDao().deleteCheckpointsForTrack(trackId)
                    }
                    db.trackDao().deleteOrphanTracks()
                }
                completion.complete(Unit)
            } catch (e: Exception) { completion.completeExceptionally(e) }
        }
        }
        completion.await()
    }

    internal suspend fun awaitWrites() {
        val completion = CompletableDeferred<Unit>()
        commands.send { completion.complete(Unit) }
        completion.await()
    }
}
