package com.hypermusic.core.player

import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.lyrics.*
import com.hypermusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Track- and language-keyed presentation work. Never owns or mutates an engine. */
class PlayerContentRepository(
    private val music: MusicService,
    private val disk: LyricsDiskCache,
    private val translator: LyricsTranslator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val lyrics = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val related = MutableStateFlow<RelatedUiState>(RelatedUiState.Idle)
    val translationEnabled = MutableStateFlow(false)
    val translating = MutableStateFlow(false)
    val language = MutableStateFlow(java.util.Locale.getDefault().language.ifBlank { "en" })
    private data class Cached<T>(val value: T?, val at: Long = android.os.SystemClock.elapsedRealtime())
    private fun <T> cache(limit: Int) = object : LinkedHashMap<String, Cached<T>>(limit, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached<T>>?) = size > limit
    }
    private val lyricsCache = cache<Lyrics>(50)
    private val relatedCache = cache<List<BrowseShelf>>(30)
    private var lyricJob: Job? = null
    private var relatedJob: Job? = null
    private var translationJob: Job? = null
    private var lyricRevision = 0L
    private var relatedRevision = 0L
    private var sourceId: String? = null
    private var cacheEpoch = LyricsDiskCache.generation

    private fun invalidateClearedCache() {
        if (cacheEpoch != LyricsDiskCache.generation) {
            lyricsCache.clear(); relatedCache.clear()
            cacheEpoch = LyricsDiskCache.generation
            cancel()
        }
    }
    private fun <T> Cached<T>.usable() = value != null || android.os.SystemClock.elapsedRealtime() - at < 300_000

    fun cancel() {
        lyricRevision++; relatedRevision++
        lyricJob?.cancel(); relatedJob?.cancel(); translationJob?.cancel()
        translating.value = false
    }
    fun onTrackChanged() { cancel(); sourceId = null; lyrics.value = LyricsUiState.Idle; related.value = RelatedUiState.Idle }

    fun fetchLyrics(id: String, track: Track?, force: Boolean = false) {
        invalidateClearedCache()
        lyricJob?.cancel(); translationJob?.cancel(); translating.value = false
        val request = ++lyricRevision
        val epoch = cacheEpoch
        sourceId = id
        if (!force) lyricsCache[id]?.takeIf { it.usable() }?.let {
            lyrics.value = it.value?.let(LyricsUiState::Success) ?: LyricsUiState.Empty
            if (translationEnabled.value) translate()
            return
        }
        lyrics.value = LyricsUiState.Loading
        lyricJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val saved = if (force) null else disk.load(id)
                    saved ?: run {
                        val lookup = runCatching {
                            if (track?.id == id) LrcLyricsService.fetchLyrics(track.title, track.artist, track.duration) else null
                        }
                        lookup.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                        val lyrics = lookup.getOrNull()
                            ?: if (track?.isDeviceOrigin() == true) null else music.getLyrics(id).getOrThrow()
                        if (lyrics == null) lookup.exceptionOrNull()?.let { throw it }
                        lyrics
                    }
                }?.takeIf { it.text.isNotBlank() }
                ensureActive()
                if (request != lyricRevision || epoch != LyricsDiskCache.generation) return@launch
                lyricsCache[id] = Cached(result)
                lyrics.value = result?.let(LyricsUiState::Success) ?: LyricsUiState.Empty
                if (result != null) withContext(Dispatchers.IO) { disk.save(id, result) }
                if (translationEnabled.value) translate()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (request == lyricRevision) lyrics.value = LyricsUiState.Error("Unable to load lyrics") }
        }
    }

    fun fetchRelated(id: String, isDeviceLocal: Boolean = false, force: Boolean = false) {
        invalidateClearedCache()
        relatedJob?.cancel()
        val request = ++relatedRevision
        if (isDeviceLocal || isDeviceUri(id)) {
            related.value = RelatedUiState.Empty
            return
        }
        if (!force) relatedCache[id]?.takeIf { it.usable() }?.let {
            related.value = it.value?.let(RelatedUiState::Success) ?: RelatedUiState.Empty
            return
        }
        related.value = RelatedUiState.Loading
        relatedJob = scope.launch {
            try {
                val result = music.getRelated(id).getOrThrow()?.takeIf { it.isNotEmpty() }
                ensureActive()
                if (request != relatedRevision) return@launch
                relatedCache[id] = Cached(result)
                related.value = result?.let(RelatedUiState::Success) ?: RelatedUiState.Empty
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (request == relatedRevision) related.value = RelatedUiState.Error("Unable to load related tracks") }
        }
    }

    fun toggleTranslation() {
        translationEnabled.value = !translationEnabled.value
        translationJob?.cancel(); translating.value = false
        if (translationEnabled.value) translate()
    }
    fun setLanguage(lang: String) {
        if (lang == language.value) return
        language.value = lang
        translationJob?.cancel(); translating.value = false
        if (translationEnabled.value) translate(force = true)
    }
    fun translate(force: Boolean = false) {
        translationJob?.cancel(); translating.value = false
        val source = (lyrics.value as? LyricsUiState.Success)?.lyrics ?: return
        val id = sourceId ?: return
        val lang = language.value
        val request = lyricRevision
        val epoch = LyricsDiskCache.generation
        if (!force && source.translatedLang == lang) return
        translationJob = scope.launch {
            translating.value = true
            try {
                val result = withContext(Dispatchers.IO) { translator.translateLyrics(source, lang) } ?: return@launch
                ensureActive()
                if (id != sourceId || request != lyricRevision || lang != language.value ||
                    !translationEnabled.value || epoch != LyricsDiskCache.generation) return@launch
                lyricsCache[id] = Cached(result)
                lyrics.value = LyricsUiState.Success(result)
                withContext(Dispatchers.IO) { disk.save(id, result) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Original lyrics remain available. */ }
            finally { if (id == sourceId && request == lyricRevision && lang == language.value) translating.value = false }
        }
    }
}
