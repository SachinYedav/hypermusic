package com.hypermusic.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.components.AppFilterChipsRow
import com.hypermusic.core.designsystem.components.CollapsibleHeaderLayout
import com.hypermusic.core.designsystem.components.ErrorStateView
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.components.WaveLoader
import com.hypermusic.core.designsystem.components.rememberCollapsibleHeaderState
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.feature.shared.components.FeedCarousel
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import com.hypermusic.feature.shared.utils.MediaActionHandler
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    onNavigateToCollection: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToCatalog: (String, String?, String?) -> Unit = { _, _, _ -> },
    onNavigateToCategory: (String, String) -> Unit = { _, _ -> },
    onNavigateToLikedSongs: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
    playerRepository: PlayerRepository = koinInject(),
    actionSheetViewModel: ActionSheetViewModel = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeChip = (uiState as? HomeUiState.Success)?.activeChip

    BackHandler(enabled = activeChip != null) {
        viewModel.loadHomeFeed(null)
    }

    ScreenWrapper(
        disableSafeAreaTop = true,
        enableAmbientGlow = true
    ) {

        when (uiState) {
            is HomeUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WaveLoader()
                }
            }
            is HomeUiState.Error -> {
                val errorState = uiState as HomeUiState.Error
                val friendly = errorState.friendlyError ?: com.hypermusic.core.utils.ErrorClassifier.classify(errorState.message)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorStateView(
                        error = friendly,
                        onRetry = { viewModel.loadHomeFeed(null, forceRefresh = true) }
                    )
                }
            }
            is HomeUiState.Success -> {
                val successState = uiState as HomeUiState.Success
                val shelves = successState.shelves
                val chips = successState.chips
                val activeChip = successState.activeChip
                val listState = rememberLazyListState()
                val headerState = rememberCollapsibleHeaderState()

                var previousChipParams by rememberSaveable { mutableStateOf(activeChip?.params) }
                LaunchedEffect(activeChip) {
                    if (previousChipParams != activeChip?.params) {
                        headerState.snapToExpanded()
                        listState.scrollToItem(0)
                        previousChipParams = activeChip?.params
                    }
                }

                val shouldLoadMore = remember {
                    derivedStateOf {
                        val totalItems = listState.layoutInfo.totalItemsCount
                        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        totalItems > 0 && lastVisibleIndex >= totalItems - 3
                    }
                }

                val canTriggerLoadMore = shouldLoadMore.value &&
                    !successState.isLoadingMore &&
                    !successState.isExhausted &&
                    !successState.continuationError &&
                    successState.continuationToken != null

                LaunchedEffect(canTriggerLoadMore, successState.continuationToken) {
                    if (canTriggerLoadMore) {
                        viewModel.loadMore()
                    }
                }

                val nonEmptyShelves = remember(shelves) {
                    shelves.filter { it.items.isNotEmpty() }
                }

                val density = LocalDensity.current
                val scrollThresholdPx = with(density) { 56.dp.toPx() }
                val scrollAlpha = remember(listState, scrollThresholdPx) {
                    {
                        if (listState.firstVisibleItemIndex > 0) {
                            1f
                        } else {
                            (listState.firstVisibleItemScrollOffset / scrollThresholdPx).coerceIn(0f, 1f)
                        }
                    }
                }

                CollapsibleHeaderLayout(
                    state = headerState,
                    scrollAlpha = scrollAlpha,
                    canScrollProvider = {
                        listState.canScrollForward || listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                    },
                    header = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                            ) {
                                Text(
                                    text = "HyperMusic",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                            }

                            AppFilterChipsRow(
                                chips = chips,
                                activeChip = activeChip,
                                onChipSelect = { chip -> viewModel.loadHomeFeed(chip) },
                                titleProvider = { it.title },
                                isEqual = { a, b -> a.params == b.params }
                            )
                        }
                    }
                ) { contentPadding ->
                    if (successState.isLoadingShelves) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = contentPadding.calculateTopPadding() + 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            WaveLoader()
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = contentPadding.calculateTopPadding() + 8.dp,
                                bottom = LocalBottomContentPadding.current
                            )
                        ) {
                            items(
                                items = nonEmptyShelves,
                                key = { it.id },
                                contentType = { it.type }
                            ) { shelf ->
                                FeedCarousel(
                                    shelf = shelf,
                                    onTrackClick = { track ->
                                        if (track.id == "local_liked_songs") {
                                            onNavigateToLikedSongs()
                                        } else {
                                            MediaActionHandler.handleTrackClick(
                                                track = track,
                                                onNavigateToCollection = onNavigateToCollection,
                                                playerRepository = playerRepository,
                                                shelfItems = shelf.items,
                                                onNavigateToCatalog = onNavigateToCatalog
                                            )
                                        }
                                    },
                                    onMoreClick = { track ->
                                        MediaActionHandler.handleTrackMoreClick(
                                            track = track,
                                            actionSheetViewModel = actionSheetViewModel
                                        )
                                    },
                                    onShelfMoreClick = { targetShelf ->
                                        val endpoint = targetShelf.moreEndpoint
                                        if (!endpoint.isNullOrEmpty()) {
                                            val cleanId = MediaActionHandler.extractBrowseId(endpoint)
                                            when (cleanId) {
                                                "FEmusic_charts", "FEmusic_new_releases", "FEmusic_podcasts", "FEmusic_moods_and_genres" -> {
                                                    onNavigateToCategory(cleanId, targetShelf.title)
                                                }
                                                else -> {
                                                    onNavigateToCatalog(endpoint, targetShelf.title, null)
                                                }
                                            }
                                        }
                                    },
                                    onPlayAll = { tracks ->
                                        val playableTracks = MediaActionHandler.filterPlayableTracks(tracks)
                                        if (playableTracks.isNotEmpty()) {
                                            playerRepository.playTracks(playableTracks)
                                        }
                                    }
                                )
                            }

                            if (successState.isLoadingMore) {
                                item(key = "footer_loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        WaveLoader()
                                    }
                                }
                            } else if (successState.continuationError) {
                                item(key = "footer_retry") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp, horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Couldn't load more",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        TextButton(onClick = { viewModel.loadMore() }) {
                                            Text("Retry")
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
}
