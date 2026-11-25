package com.hypermusic.feature.search

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.components.EmptyStateView
import com.hypermusic.core.designsystem.components.ErrorStateView
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.components.WaveLoader
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.Search
import com.hypermusic.feature.shared.components.CategoryCards
import com.hypermusic.feature.search.components.SearchHeader
import com.hypermusic.feature.search.components.SearchSpotlightCard
import com.hypermusic.feature.search.components.SuggestionRow
import com.hypermusic.feature.search.components.TrackResultCard
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.feature.shared.state.ActionContext
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import com.hypermusic.feature.shared.state.ToastAction
import com.hypermusic.feature.shared.state.ToastType
import com.hypermusic.navigation.NavigationEventBus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SearchScreen(
    onNavigateToCategory: (String, String) -> Unit = { _, _ -> },
    onNavigateToCollection: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    viewModel: SearchViewModel = koinViewModel(),
    playerRepository: PlayerRepository = koinInject(),
    actionSheetViewModel: ActionSheetViewModel = koinInject(),
    navigationEventBus: NavigationEventBus = koinInject(),
    toastViewModel: GlobalToastViewModel = koinInject(),
    downloadRepository: com.hypermusic.core.data.repository.DownloadRepository = koinInject()
) {
    val screenState by viewModel.screenState.collectAsState()
    val uiState = screenState.uiState
    val suggestions = screenState.suggestions
    val isFocused = screenState.isFocused
    val query by viewModel.searchQuery.collectAsState()
    val currentTrack by playerRepository.currentTrack.collectAsState()
    val isPlaying by playerRepository.isPlaying.collectAsState()
    val downloadQueue by downloadRepository.downloadQueue.collectAsState()
    val autoFocusTrigger by navigationEventBus.autoFocusSearchTrigger.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(autoFocusTrigger) {
        if (autoFocusTrigger > 0L) {
            viewModel.setFocused(true)
            kotlinx.coroutines.delay(150)
            runCatching {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            navigationEventBus.consumeAutoFocusSearch()
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcribedText = matches?.firstOrNull()
            if (!transcribedText.isNullOrEmpty()) {
                viewModel.onQueryChange(transcribedText)
                viewModel.submitSearch(transcribedText)
            }
        }
    }

    val startSpeechRecognition = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            toastViewModel.showAlertCard(
                title = "Voice Search Unavailable",
                message = "No speech recognition service found on this device. Install Google Speech Services to search with your voice.",
                type = ToastType.ERROR,
                primaryAction = ToastAction("Install") {
                    try {
                        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.googlequicksearchbox"))
                        context.startActivity(marketIntent)
                    } catch (e: Exception) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox"))
                        context.startActivity(webIntent)
                    }
                },
                secondaryAction = ToastAction("Dismiss") {
                    toastViewModel.hideToast()
                },
                durationMs = 8000L
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition()
        } else {
            toastViewModel.showAlertCard(
                title = "Microphone Access Required",
                message = "Voice search needs microphone access to listen to song queries. Please grant permission in App Settings.",
                type = ToastType.INFO,
                primaryAction = ToastAction("Open Settings") {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                secondaryAction = ToastAction("Not Now") {
                    toastViewModel.hideToast()
                }
            )
        }
    }

    fun launchVoiceSearch() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startSpeechRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler(enabled = isFocused || query.isNotEmpty()) {
        viewModel.cancelSearch()
        focusManager.clearFocus()
    }

    val suggestionsListState = rememberLazyListState()
    val resultsListState = rememberLazyListState()
    val categoryGridState = rememberLazyGridState()

    val isScrolled by remember(uiState, isFocused, query) {
        derivedStateOf {
            when {
                isFocused && query.length >= 2 ->
                    suggestionsListState.firstVisibleItemIndex > 0 || suggestionsListState.firstVisibleItemScrollOffset > 0
                uiState is SearchUiState.Success ->
                    resultsListState.firstVisibleItemIndex > 0 || resultsListState.firstVisibleItemScrollOffset > 0
                uiState is SearchUiState.Categories ->
                    categoryGridState.firstVisibleItemIndex > 0 || categoryGridState.firstVisibleItemScrollOffset > 0
                else -> false
            }
        }
    }

    val dividerAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "searchDividerAlpha"
    )

    ScreenWrapper(disableSafeAreaTop = false) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            SearchHeader(
                query = query,
                isFocused = isFocused,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSubmit = { viewModel.submitSearch(query) },
                onClear = { viewModel.onQueryChange("") },
                onMicPress = { launchVoiceSearch() },
                onFocusChange = { viewModel.setFocused(it) },
                onCancel = {
                    viewModel.cancelSearch()
                    focusManager.clearFocus()
                },
                focusRequester = focusRequester
            )

            if (dividerAlpha > 0f) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = dividerAlpha },
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isFocused && query.length >= 2) {
                    LazyColumn(
                        state = suggestionsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current)
                    ) {
                        if (suggestions.richItems.isNotEmpty()) {
                            items(
                                items = suggestions.richItems,
                                key = { "rich_${it.id}" },
                                contentType = { "rich" }
                            ) { richTrack ->
                                TrackResultCard(
                                    track = richTrack,
                                    downloadProgress = downloadQueue[richTrack.id]?.progress?.div(100f),
                                    downloadStatus = downloadQueue[richTrack.id]?.status,
                                    isCurrentTrack = richTrack.id == currentTrack?.id,
                                    isPlaying = isPlaying,
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.submitSearch(richTrack.title)
                                        com.hypermusic.feature.shared.utils.MediaActionHandler.handleTrackClick(
                                            track = richTrack,
                                            onNavigateToCollection = onNavigateToCollection,
                                            playerRepository = playerRepository,
                                            onNavigateToCatalog = { endpoint, title, _ ->
                                                onNavigateToCategory(endpoint, title ?: "")
                                            }
                                        )
                                    },
                                    onMoreClick = {
                                        focusManager.clearFocus()
                                        com.hypermusic.feature.shared.utils.MediaActionHandler.handleTrackMoreClick(
                                            track = richTrack,
                                            actionSheetViewModel = actionSheetViewModel
                                        )
                                    }
                                )
                            }
                        }

                        items(
                            items = suggestions.queries,
                            key = { "query_$it" },
                            contentType = { "query" }
                        ) { suggestion ->
                            SuggestionRow(
                                suggestion = suggestion,
                                currentQuery = query,
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.submitSearch(suggestion)
                                }
                            )
                        }
                    }
                } else {
                    when (uiState) {
                        is SearchUiState.Initial -> {
                        }
                        is SearchUiState.Categories -> {
                            val state = (uiState as SearchUiState.Categories)
                            CategoryCards(
                                shelves = state.shelves,
                                isLoading = state.isLoading,
                                gridState = categoryGridState,
                                onCategoryPress = { id, title ->
                                    onNavigateToCategory(id, title)
                                }
                            )
                        }
                        is SearchUiState.Searching -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                WaveLoader()
                            }
                        }
                        is SearchUiState.Error -> {
                            val errorState = uiState as SearchUiState.Error
                            val friendly = errorState.friendlyError ?: com.hypermusic.core.utils.ErrorClassifier.classify(errorState.message)
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ErrorStateView(
                                    error = friendly,
                                    onRetry = {
                                        if (query.isNotBlank()) {
                                            viewModel.submitSearch(query)
                                        } else {
                                            viewModel.loadExploreCategories()
                                        }
                                    }
                                )
                            }
                        }
                        is SearchUiState.Success -> {
                            val results = (uiState as SearchUiState.Success).results
                            if (results.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    EmptyStateView(
                                        icon = AppIcons.Search,
                                        title = "No results found",
                                        subtitle = if (query.isNotBlank()) "No matches found for \"$query\"" else "Try searching with different keywords"
                                    )
                                }
                            } else {
                                val (spotlightItem, remainingResults) = remember(results) {
                                    val spotlight = results.firstOrNull()?.takeIf {
                                        it.trackType in listOf("artist", "album", "playlist", "podcast_show") ||
                                            com.hypermusic.feature.shared.utils.MediaActionHandler.isPodcastShow(it) ||
                                            it.id.startsWith("UC") || it.id.startsWith("HC") ||
                                            it.id.startsWith("VL") || it.id.startsWith("MPREb")
                                    }
                                    val remaining = if (spotlight != null) results.drop(1) else results
                                    spotlight to remaining
                                }

                                LazyColumn(
                                    state = resultsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current)
                                ) {
                                    if (spotlightItem != null) {
                                        item(key = "spotlight_${spotlightItem.id}", contentType = "spotlight") {
                                            SearchSpotlightCard(
                                                track = spotlightItem,
                                                downloadProgress = downloadQueue[spotlightItem.id]?.progress?.div(100f),
                                                downloadStatus = downloadQueue[spotlightItem.id]?.status,
                                                onClick = {
                                                    com.hypermusic.feature.shared.utils.MediaActionHandler.handleTrackClick(
                                                        track = spotlightItem,
                                                        onNavigateToCollection = onNavigateToCollection,
                                                        playerRepository = playerRepository,
                                                        onNavigateToCatalog = { endpoint, title, _ ->
                                                            onNavigateToCategory(endpoint, title ?: "")
                                                        }
                                                    )
                                                },
                                                onMoreClick = {
                                                    com.hypermusic.feature.shared.utils.MediaActionHandler.handleTrackMoreClick(
                                                        track = spotlightItem,
                                                        actionSheetViewModel = actionSheetViewModel
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    items(
                                        items = remainingResults,
                                        key = { it.id },
                                        contentType = { "track" }
                                    ) { track ->
                                        TrackResultCard(
                                            track = track,
                                            downloadProgress = downloadQueue[track.id]?.progress?.div(100f),
                                            downloadStatus = downloadQueue[track.id]?.status,
                                            isCurrentTrack = track.id == currentTrack?.id,
                                            isPlaying = isPlaying,
                                            onClick = {
                                                com.hypermusic.feature.shared.utils.MediaActionHandler.handleTrackClick(
                                                    track = track,
                                                    onNavigateToCollection = onNavigateToCollection,
                                                    playerRepository = playerRepository,
                                                    onNavigateToCatalog = { endpoint, title, _ ->
                                                        onNavigateToCategory(endpoint, title ?: "")
                                                    }
                                                )
                                            },
                                            onMoreClick = {
                                                com.hypermusic.feature.shared.utils.MediaActionHandler.handleTrackMoreClick(
                                                    track = track,
                                                    actionSheetViewModel = actionSheetViewModel
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
