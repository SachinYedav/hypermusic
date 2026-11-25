package com.hypermusic.feature.search

import android.util.LruCache
import androidx.compose.runtime.Immutable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.model.BrowseShelf
import com.hypermusic.core.model.SearchSuggestions
import com.hypermusic.core.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.hypermusic.core.utils.ErrorClassifier
import com.hypermusic.core.utils.FriendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

@Immutable
sealed interface SearchUiState {
    @Immutable
    object Initial : SearchUiState
    @Immutable
    data class Categories(val shelves: List<BrowseShelf>, val isLoading: Boolean = false) : SearchUiState
    @Immutable
    object Searching : SearchUiState
    @Immutable
    data class Success(val results: List<Track>) : SearchUiState
    @Immutable
    data class Error(val message: String, val friendlyError: FriendlyError? = null) : SearchUiState
}

@Immutable
data class SearchScreenState(
    val uiState: SearchUiState = SearchUiState.Initial,
    val suggestions: SearchSuggestions = SearchSuggestions(),
    val isFocused: Boolean = false
)

class SearchViewModel(
    private val musicService: MusicService
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _suggestions = MutableStateFlow(SearchSuggestions())
    val suggestions: StateFlow<SearchSuggestions> = _suggestions.asStateFlow()

    private val _isFocused = MutableStateFlow(false)
    val isFocused: StateFlow<Boolean> = _isFocused.asStateFlow()

    private val suggestionCache = LruCache<String, SearchSuggestions>(30)

    val screenState: StateFlow<SearchScreenState> = combine(
        _uiState,
        _suggestions,
        _isFocused
    ) { ui, sugg, foc ->
        SearchScreenState(uiState = ui, suggestions = sugg, isFocused = foc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchScreenState())

    private var cachedCategories = emptyList<BrowseShelf>()

    init {
        loadExploreCategories()
        setupSuggestionDebounce()
    }

    fun loadExploreCategories() {
        viewModelScope.launch {
            if (_uiState.value is SearchUiState.Initial || _uiState.value is SearchUiState.Error) {
                _uiState.value = SearchUiState.Categories(cachedCategories, isLoading = true)
            }
            
            val result = musicService.getExplorePage("FEmusic_moods_and_genres")
            if (result.isSuccess) {
                cachedCategories = result.getOrNull() ?: emptyList()
                if (_uiState.value is SearchUiState.Categories || _uiState.value is SearchUiState.Initial) {
                    _uiState.value = SearchUiState.Categories(cachedCategories, isLoading = false)
                }
            } else {
                val exception = result.exceptionOrNull()
                val friendly = ErrorClassifier.classify(exception)
                if (cachedCategories.isNotEmpty()) {
                    _uiState.value = SearchUiState.Categories(cachedCategories, isLoading = false)
                } else if (_searchQuery.value.isEmpty()) {
                    _uiState.value = SearchUiState.Error(friendly.title, friendly)
                }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun setupSuggestionDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(200)
                .distinctUntilChanged()
                .filter { it.length >= 2 && _isFocused.value }
                .collectLatest { query ->
                    fetchSuggestionsInternal(query)
                }
        }
    }

    private suspend fun fetchSuggestionsInternal(query: String) {
        val cleanQuery = query.lowercase().trim()
        if (cleanQuery.length < 2) return

        val cached = suggestionCache.get(cleanQuery)
        if (cached != null) {
            if (_searchQuery.value.trim().lowercase() == cleanQuery) {
                _suggestions.value = cached
            }
            return
        }

        val result = musicService.getSearchSuggestions(cleanQuery)
        if (result.isSuccess) {
            val raw = result.getOrNull() ?: SearchSuggestions()
            val filtered = SearchSuggestions(
                queries = raw.queries
                    .map { it.trim() }
                    .distinctBy { it.lowercase() }
                    .take(8),
                richItems = raw.richItems
                    .distinctBy { it.id }
                    .take(3)
            )

            suggestionCache.put(cleanQuery, filtered)

            if (_searchQuery.value.trim().lowercase() == cleanQuery) {
                _suggestions.value = filtered
            }
        }
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
        val clean = query.trim().lowercase()
        if (clean.length < 2) {
            _suggestions.value = SearchSuggestions()
        } else {
            suggestionCache.get(clean)?.let { cached ->
                _suggestions.value = cached
            }
        }
        if (query.isEmpty()) {
            if (cachedCategories.isNotEmpty()) {
                _uiState.value = SearchUiState.Categories(cachedCategories, isLoading = false)
            } else {
                loadExploreCategories()
            }
        }
    }

    fun setFocused(focused: Boolean) {
        _isFocused.value = focused
        if (!focused) {
            _suggestions.value = SearchSuggestions()
        } else if (_searchQuery.value.length >= 2) {
            val clean = _searchQuery.value.trim().lowercase()
            val cached = suggestionCache.get(clean)
            if (cached != null) {
                _suggestions.value = cached
            } else {
                viewModelScope.launch {
                    fetchSuggestionsInternal(_searchQuery.value)
                }
            }
        }
    }

    fun cancelSearch() {
        _searchQuery.value = ""
        _isFocused.value = false
        _suggestions.value = SearchSuggestions()
        if (cachedCategories.isNotEmpty()) {
            _uiState.value = SearchUiState.Categories(cachedCategories, isLoading = false)
        } else {
            loadExploreCategories()
        }
    }

    fun submitSearch(query: String) {
        val finalQuery = query.trim()
        if (finalQuery.isEmpty()) return

        _searchQuery.value = finalQuery
        setFocused(false)
        _uiState.value = SearchUiState.Searching
        _suggestions.value = SearchSuggestions()

        viewModelScope.launch {
            val result = musicService.search(finalQuery)
            if (result.isSuccess) {
                val tracks = (result.getOrNull() ?: emptyList()).distinctBy { it.id }
                _uiState.value = SearchUiState.Success(tracks)
            } else {
                val exception = result.exceptionOrNull()
                val friendly = ErrorClassifier.classify(exception)
                _uiState.value = SearchUiState.Error(friendly.title, friendly)
            }
        }
    }
}
