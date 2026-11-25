package com.hypermusic.core.player

/** Stop on the third failed entry. READY alone is not proof of sustained successful playback. */
internal class ConsecutiveFailurePolicy {
    private val failedEntries = linkedSetOf<String>()
    val count: Int get() = failedEntries.size
    fun reset() = failedEntries.clear()
    fun onPlayingTime(playingMs: Long) { if (playingMs >= 10_000) reset() }
    fun shouldSkip(entryId: String, playIntent: Boolean, hasNext: Boolean, repeatOne: Boolean): Boolean {
        if (!playIntent) return false
        if (!failedEntries.add(entryId)) return false
        return count < 3 && hasNext && !repeatOne
    }
}
