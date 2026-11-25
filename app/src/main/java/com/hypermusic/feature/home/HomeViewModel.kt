package com.hypermusic.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.core.data.local.entity.ArtistEntity
import com.hypermusic.core.data.local.entity.RecentCollectionEntity
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.model.BrowseShelf
import com.hypermusic.core.model.FilterChip
import com.hypermusic.core.model.Track
import com.hypermusic.core.utils.ErrorClassifier
import com.hypermusic.core.utils.FriendlyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@Immutable
sealed interface HomeUiState {
    @Immutable
    object Loading : HomeUiState

    @Immutable
    data class Success(
        val shelves: List<BrowseShelf>,
        val chips: List<FilterChip>,
        val activeChip: FilterChip? = null,
        val isLoadingShelves: Boolean = false,
        val continuationToken: String? = null,
        val visitorData: String? = null,
        val isLoadingMore: Boolean = false,
        val continuationError: Boolean = false,
        val isExhausted: Boolean = false
    ) : HomeUiState

    @Immutable
    data class Error(val message: String, val friendlyError: FriendlyError? = null) : HomeUiState
}

class HomeViewModel(
    private val musicService: MusicService,
    private val localMediaRepository: LocalMediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val feedCache = mutableMapOf<String?, HomeUiState.Success>()

    private var fetchJob: Job? = null
    private var continuationJob: Job? = null
    private var localShelves: List<BrowseShelf> = emptyList()
    private val localShelfRefresh = MutableStateFlow(0L)
    private var feedRevision = 0L
    private var consecutiveEmptyBatches = 0

    init {
        viewModelScope.launch {
            var previousHistory: List<Track>? = null
            var previousCollections: List<RecentCollectionEntity>? = null
            var previousLikes: List<Track>? = null
            var previousArtists: List<ArtistEntity>? = null
            var collectionCards: List<Track> = emptyList()
            var playAgain: BrowseShelf? = null
            var jumpBackIn: BrowseShelf? = null
            var favoriteArtists: BrowseShelf? = null
            var artistScoresValidUntil = 0L
            val previewCache = mutableMapOf<String, List<Track>>()

            combine(
                localMediaRepository.getRecentHistory(limit = 10).distinctUntilChanged(),
                localMediaRepository.getRecentCollections(limit = 20).distinctUntilChanged(),
                localMediaRepository.getLikedTracks().distinctUntilChanged(),
                localMediaRepository.getAllArtists().distinctUntilChanged(),
                localShelfRefresh
            ) { history, collections, liked, artists, _ ->
                val collectionsChanged = previousCollections != collections
                if (previousHistory != history) {
                    playAgain = history.takeIf { it.size >= 3 }?.let(::createPlayAgainShelf)
                }
                if (collectionsChanged) {
                    val activePreviews = collections.mapNotNull { it.previewTracksJson }.toSet()
                    previewCache.keys.retainAll(activePreviews)
                    collectionCards = buildRecentCollectionCards(collections, previewCache)
                }
                if (collectionsChanged || previousLikes != liked) {
                    jumpBackIn = buildLocalCollectionCards(liked, collectionCards)
                        .takeIf { it.isNotEmpty() }?.let(::createJumpBackInShelf)
                }
                val now = System.currentTimeMillis()
                if (collectionsChanged || previousArtists != artists || now >= artistScoresValidUntil) {
                    favoriteArtists = buildFavoriteArtistsShelf(collections, artists, now)
                    artistScoresValidUntil = nextArtistScoreChange(collections, artists, now)
                }
                previousHistory = history
                previousCollections = collections
                previousLikes = liked
                previousArtists = artists
                listOfNotNull(playAgain, jumpBackIn, favoriteArtists)
            }.flowOn(Dispatchers.Default).distinctUntilChanged().collect { shelves ->
                localShelves = shelves
                refreshLocalShelves()
            }
        }
        loadHomeFeed(null)
    }

    private fun refreshLocalShelves() {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success && currentState.activeChip == null) {
            val updatedShelves = mergeShelves(currentState.shelves, null)
            _uiState.value = currentState.copy(shelves = updatedShelves)
        }
    }

    fun loadHomeFeed(chip: FilterChip?, forceRefresh: Boolean = false) {
        val revision = ++feedRevision
        localShelfRefresh.value++
        val cacheKey = chip?.params
        consecutiveEmptyBatches = 0

        fetchJob?.cancel()
        continuationJob?.cancel()

        if (!forceRefresh) {
            val cached = feedCache[cacheKey]
            if (cached != null) {
                val updatedShelves = mergeShelves(cached.shelves, chip)
                val refreshedCachedState = cached.copy(shelves = updatedShelves)
                _uiState.value = refreshedCachedState
                return
            }
        }

        fetchJob = viewModelScope.launch {
            val currentState = _uiState.value
            val currentChips = (currentState as? HomeUiState.Success)?.chips ?: emptyList()
            if (currentState is HomeUiState.Success) {
                _uiState.value = currentState.copy(
                    activeChip = chip,
                    isLoadingShelves = true,
                    continuationError = false
                )
            } else {
                _uiState.value = HomeUiState.Loading
            }

            val result = musicService.getHomeFeed(chip?.params)
            if (revision != feedRevision) return@launch

            if (result.isSuccess) {
                val data = result.getOrThrow()
                val finalChips = data.chips.ifEmpty { currentChips }
                val mergedShelves = mergeShelves(data.shelves, chip)

                val newState = HomeUiState.Success(
                    shelves = mergedShelves,
                    chips = finalChips,
                    activeChip = chip,
                    isLoadingShelves = false,
                    continuationToken = data.continuationToken,
                    visitorData = data.visitorData,
                    isLoadingMore = false,
                    continuationError = false,
                    isExhausted = data.continuationToken == null
                )

                feedCache[cacheKey] = newState
                _uiState.value = newState

                if (data.continuationToken != null) {
                    triggerWarmUp(data.continuationToken, data.visitorData, chip)
                }
            } else {
                val exception = result.exceptionOrNull()
                val friendly = ErrorClassifier.classify(exception)
                _uiState.value = HomeUiState.Error(friendly.title, friendly)
            }
        }
    }

    private fun triggerWarmUp(token: String, visitorData: String?, chip: FilterChip?) {
        val revision = feedRevision
        continuationJob?.cancel()
        val current = _uiState.value
        if (current is HomeUiState.Success && current.activeChip == chip) {
            _uiState.value = current.copy(isLoadingMore = true, continuationError = false)
        }
        continuationJob = viewModelScope.launch {
            val result = musicService.getHomeContinuation(token, visitorData)
            val currentState = _uiState.value
            if (revision == feedRevision && currentState is HomeUiState.Success && currentState.activeChip == chip) {
                if (result.isSuccess) {
                    val data = result.getOrThrow()
                    if (data.shelves.isNotEmpty()) {
                        val updatedShelves = appendShelves(currentState.shelves, data.shelves)
                        val updatedState = currentState.copy(
                            shelves = updatedShelves,
                            continuationToken = data.continuationToken,
                            isLoadingMore = false,
                            continuationError = false,
                            isExhausted = data.continuationToken == null
                        )
                        feedCache[chip?.params] = updatedState
                        _uiState.value = updatedState
                    } else if (data.continuationToken != null) {
                        val updatedState = currentState.copy(
                            continuationToken = data.continuationToken,
                            isLoadingMore = false,
                            continuationError = false
                        )
                        feedCache[chip?.params] = updatedState
                        _uiState.value = updatedState
                    } else {
                        val updatedState = currentState.copy(
                            continuationToken = null,
                            isLoadingMore = false,
                            isExhausted = true
                        )
                        feedCache[chip?.params] = updatedState
                        _uiState.value = updatedState
                    }
                } else {
                    _uiState.value = currentState.copy(
                        isLoadingMore = false,
                        continuationError = true
                    )
                }
            }
        }
    }

    fun loadMore() {
        val revision = feedRevision
        val currentState = _uiState.value as? HomeUiState.Success ?: return
        if (currentState.isLoadingMore || currentState.isExhausted) return
        val token = currentState.continuationToken ?: return
        if (continuationJob?.isActive == true) return

        _uiState.value = currentState.copy(
            isLoadingMore = true,
            continuationError = false
        )

        continuationJob = viewModelScope.launch {
            val result = musicService.getHomeContinuation(token, currentState.visitorData)
            val activeState = _uiState.value as? HomeUiState.Success
            if (revision != feedRevision || activeState == null || activeState.activeChip != currentState.activeChip) {
                return@launch
            }

            if (result.isSuccess) {
                val data = result.getOrThrow()
                val updatedShelves = appendShelves(activeState.shelves, data.shelves)
                val hasNewShelves = updatedShelves.size > activeState.shelves.size
                val nextToken = data.continuationToken

                if (!hasNewShelves) {
                    consecutiveEmptyBatches++
                } else {
                    consecutiveEmptyBatches = 0
                }

                val isExhausted = nextToken == null ||
                    (!hasNewShelves && nextToken == token) ||
                    consecutiveEmptyBatches >= 2

                val updatedState = activeState.copy(
                    shelves = updatedShelves,
                    continuationToken = if (isExhausted) null else nextToken,
                    isLoadingMore = false,
                    continuationError = false,
                    isExhausted = isExhausted
                )
                feedCache[currentState.activeChip?.params] = updatedState
                _uiState.value = updatedState
            } else {
                _uiState.value = activeState.copy(
                    isLoadingMore = false,
                    continuationError = true
                )
            }
        }
    }

    private fun buildLocalCollectionCards(localLikedTracks: List<Track>, recentCards: List<Track>): List<Track> {
        val cards = mutableListOf<Track>()

        // 1. Liked Songs Card (if user has at least 4 liked tracks to fill all preview slots)
        if (localLikedTracks.size >= 4) {
            val firstArtwork = localLikedTracks.firstOrNull()?.artwork
            cards.add(
                Track(
                    id = "local_liked_songs",
                    title = "Liked Songs",
                    artist = "Favorites • ${localLikedTracks.size} songs",
                    artwork = firstArtwork,
                    trackType = "playlist",
                    previewTracks = localLikedTracks.take(4)
                )
            )
        }

        cards.addAll(recentCards)
        return cards.distinctBy { it.id }.take(4)
    }

    private fun buildRecentCollectionCards(
        collections: List<RecentCollectionEntity>,
        previewCache: MutableMap<String, List<Track>>
    ): List<Track> {
        val cards = mutableListOf<Track>()
        for (item in collections) {
            val previewList = item.previewTracksJson?.let { json ->
                previewCache.getOrPut(json) { LocalMediaRepository.deserializePreviewTracks(json) }
            }.orEmpty()
            if (previewList.size >= 4) {
                val subtitle = item.subtitle ?: when (item.type) {
                    "album" -> "Album"
                    "podcast" -> "Podcast"
                    else -> "Playlist"
                }
                cards.add(
                    Track(
                        id = item.id,
                        title = item.title,
                        artist = subtitle,
                        artwork = item.artworkUrl ?: previewList.firstOrNull()?.artwork,
                        trackType = item.type,
                        previewTracks = previewList.take(4)
                    )
                )
            }
        }

        return cards
    }

    private fun mergeShelves(remoteShelves: List<BrowseShelf>, chip: FilterChip?): List<BrowseShelf> {
        val cleanRemote = appendShelves(emptyList(), remoteShelves.filterNot { it.id.startsWith("local_") })
        if (chip != null) {
            return cleanRemote
        }

        val result = cleanRemote.toMutableList()

        for (shelf in localShelves) {
            val anchor = when (shelf.id) {
                "local_play_again" -> 0
                "local_jump_back_in" -> 2
                else -> 4
            }
            result.add(minOf(anchor, result.size), shelf)
        }

        return result
    }

    private fun buildFavoriteArtistsShelf(
        localRecentCollections: List<RecentCollectionEntity>,
        localFollowedArtists: List<ArtistEntity>,
        now: Long
    ): BrowseShelf? {
        val visitedArtists = localRecentCollections.filter { it.type == "artist" }
        val followedArtistIds = localFollowedArtists.map { it.artistId }.toSet()

        val allArtistIds = (followedArtistIds + visitedArtists.map { it.id }).toSet()
        if (allArtistIds.size < 3) return null

        data class ScoredArtist(
            val id: String,
            val name: String,
            val avatarUrl: String?,
            val score: Int
        )

        val scoredList = allArtistIds.mapNotNull { artistId ->
            val followed = localFollowedArtists.find { it.artistId == artistId }
            val visited = visitedArtists.find { it.id == artistId }

            val name = followed?.name ?: visited?.title ?: return@mapNotNull null
            val avatar = followed?.avatarUrl ?: visited?.artworkUrl

            val isFollowed = followed != null
            val visitCount = visited?.visitCount ?: 0
            val lastVisitedAt = visited?.lastVisitedAt ?: followed?.savedAt ?: 0L

            val recencyBonus = when {
                now - lastVisitedAt < 48L * 3600 * 1000 -> 5
                now - lastVisitedAt < 7L * 24 * 3600 * 1000 -> 2
                else -> 0
            }

            val score = (if (isFollowed) 10 else 0) + (visitCount * 2) + recencyBonus
            ScoredArtist(artistId, name, avatar, score)
        }.sortedByDescending { it.score }

        if (scoredList.size < 3) return null

        val topArtists = scoredList.take(10).map { artist ->
            Track(
                id = artist.id,
                title = artist.name,
                artist = "Artist",
                artwork = artist.avatarUrl,
                trackType = "artist"
            )
        }

        return BrowseShelf(
            id = "local_favorite_artists",
            title = "Artists you love",
            type = "carousel",
            items = topArtists
        )
    }

    private fun nextArtistScoreChange(
        collections: List<RecentCollectionEntity>,
        artists: List<ArtistEntity>,
        now: Long
    ): Long {
        val visits = collections.filter { it.type == "artist" }.associateBy { it.id }
        val followed = artists.associateBy { it.artistId }
        return (visits.keys + followed.keys).asSequence().flatMap { id ->
            val lastVisit = visits[id]?.lastVisitedAt ?: followed.getValue(id).savedAt
            sequenceOf(lastVisit + 48L * 3600 * 1000, lastVisit + 7L * 24 * 3600 * 1000)
        }.filter { it > now }.minOrNull() ?: Long.MAX_VALUE
    }

    private fun appendShelves(existing: List<BrowseShelf>, incoming: List<BrowseShelf>): List<BrowseShelf> {
        val result = existing.toMutableList()
        val byId = existing.associateBy { it.id }.toMutableMap()
        for (shelf in incoming) {
            var candidate = shelf
            var occurrence = 2
            while (true) {
                val previous = byId[candidate.id]
                if (previous == null) {
                    byId[candidate.id] = candidate
                    result.add(candidate)
                    break
                }
                if (previous == candidate) break
                candidate = shelf.copy(id = "${shelf.id}#$occurrence")
                occurrence++
            }
        }
        return result
    }

    private fun createJumpBackInShelf(cards: List<Track>): BrowseShelf {
        return BrowseShelf(
            id = "local_jump_back_in",
            title = "Jump back in",
            type = "collection_cards",
            items = cards
        )
    }

    private fun createPlayAgainShelf(tracks: List<Track>): BrowseShelf {
        return BrowseShelf(
            id = "local_play_again",
            title = "Play again",
            type = "grid",
            items = tracks
        )
    }
}
