package com.hypermusic.core.player

import java.util.Random

/** Queue entries, unlike media IDs, are unique even when the same song is queued twice. */
object QueueOrder {
    fun acceptsResult(generation: Long, currentGeneration: Long, requestedEntries: List<String>,
        currentEntries: List<String>, enabled: Boolean, repeatOff: Boolean): Boolean =
        enabled && repeatOff && generation == currentGeneration && requestedEntries == currentEntries

    private fun shuffleListInPlace(list: MutableList<String>, rng: Random) {
        for (i in list.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }
    }

    /**
     * Shuffles pending explicit entries ahead of pending implicit/radio entries.
     * Preserves the passed prefix and current entry when the current entry is present.
     * Otherwise, shuffles all explicit entries. Implicit entries retain their relative order.
     */
    fun buildShuffledTraversal(
        currentTraversal: List<String>,
        currentEntryId: String?,
        implicitIds: Set<String>,
        seed: Long = System.nanoTime()
    ): List<String> {
        if (currentTraversal.isEmpty()) return emptyList()
        val currentIndex = if (currentEntryId != null) currentTraversal.indexOf(currentEntryId) else -1
        val rng = Random(seed)

        if (currentIndex < 0) {
            val explicit = currentTraversal.filter { it !in implicitIds }.toMutableList()
            val implicit = currentTraversal.filter { it in implicitIds }
            shuffleListInPlace(explicit, rng)
            return explicit + implicit
        }

        val passedAndCurrent = currentTraversal.subList(0, currentIndex + 1)
        val pending = currentTraversal.subList(currentIndex + 1, currentTraversal.size)
        val pendingExplicit = pending.filter { it !in implicitIds }.toMutableList()
        val pendingImplicit = pending.filter { it in implicitIds }

        shuffleListInPlace(pendingExplicit, rng)
        return passedAndCurrent + pendingExplicit + pendingImplicit
    }

    /**
     * Moves an entry within the queue while strictly preserving the explicit-before-implicit partition.
     * Moving the currently playing track, past tracks, and upcoming tracks is fully supported.
     * Returns the new traversal list if valid, or null if the move crosses partition boundaries.
     */
    fun moveInTraversal(
        currentTraversal: List<String>,
        currentEntryId: String?,
        entryId: String,
        beforeEntryId: String?,
        implicitIds: Set<String>
    ): List<String>? {
        val from = currentTraversal.indexOf(entryId)
        if (from < 0 || entryId == beforeEntryId) return null

        val isFromImplicit = entryId in implicitIds
        val remaining = currentTraversal.filterIndexed { index, _ -> index != from }

        val firstImplicit = remaining.firstOrNull { it in implicitIds }

        val to = if (beforeEntryId == null) {
            if (isFromImplicit) {
                remaining.size
            } else {
                if (firstImplicit != null) remaining.indexOf(firstImplicit) else remaining.size
            }
        } else {
            val isBeforeImplicit = beforeEntryId in implicitIds
            if (!isFromImplicit && isBeforeImplicit) {
                // An explicit track using the first implicit track as anchor is valid (dropping at boundary)
                if (beforeEntryId == firstImplicit) {
                    remaining.indexOf(beforeEntryId)
                } else {
                    return null // Dropping deep inside radio is invalid
                }
            } else if (isFromImplicit && !isBeforeImplicit) {
                return null // Radio track cannot jump into explicit tracks
            } else {
                remaining.indexOf(beforeEntryId)
            }
        }

        if (to < 0) return null
        val result = remaining.toMutableList()
        result.add(to, entryId)
        return result
    }
}
