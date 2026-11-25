package com.hypermusic.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.components.EmptyStateView
import com.hypermusic.core.designsystem.components.ErrorStateView
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.components.WaveLoader
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import com.hypermusic.core.designsystem.theme.ShapeFull
import com.hypermusic.core.designsystem.theme.ShapeMd
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.core.utils.ErrorClassifier
import com.hypermusic.core.utils.rememberDominantColor
import com.hypermusic.feature.shared.components.CategoryCards
import com.hypermusic.feature.shared.components.FeedCarousel
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import com.hypermusic.feature.shared.utils.MediaActionHandler
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun ExploreCategoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCollection: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToSearch: () -> Unit = {},
    onNavigateToCatalog: (String, String?, String?) -> Unit = { _, _, _ -> },
    onNavigateToCategory: ((String, String) -> Unit)? = null,
    viewModel: ExploreViewModel = koinViewModel(),
    playerRepository: PlayerRepository = koinInject(),
    actionSheetViewModel: ActionSheetViewModel = koinInject()
) {
    val screenState by viewModel.screenState.collectAsState()
    val uiState = screenState.uiState
    val listState = rememberLazyListState()
    val categoryGridState = rememberLazyGridState()

    val density = LocalDensity.current
    val topSafeArea = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val minHeaderHeight = topSafeArea + 56.dp
    val collapseThresholdPx = with(density) { 90.dp.toPx() }

    val titleAlpha by remember(collapseThresholdPx, screenState.isMoodsAndGenres) {
        derivedStateOf {
            val offset = if (screenState.isMoodsAndGenres) {
                if (categoryGridState.firstVisibleItemIndex > 0) collapseThresholdPx
                else categoryGridState.firstVisibleItemScrollOffset.toFloat().coerceAtMost(collapseThresholdPx)
            } else {
                if (listState.firstVisibleItemIndex > 0) collapseThresholdPx
                else listState.firstVisibleItemScrollOffset.toFloat().coerceAtMost(collapseThresholdPx)
            }
            val fraction = (offset / collapseThresholdPx).coerceIn(0f, 1f)
            if (fraction <= 0.40f) 0f
            else ((fraction - 0.40f) / 0.60f).coerceIn(0f, 1f)
        }
    }

    ScreenWrapper(disableSafeAreaTop = true) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            when (uiState) {
                is ExploreUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        WaveLoader()
                    }
                }
                is ExploreUiState.Error -> {
                    val friendly = uiState.friendlyError ?: ErrorClassifier.classify(uiState.message)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorStateView(
                            error = friendly,
                            onRetry = { viewModel.loadExplorePage() }
                        )
                    }
                }
                is ExploreUiState.Success -> {
                    val shelves = uiState.shelves
                    
                    if (shelves.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyStateView(
                                icon = AppIcons.Music,
                                title = "No content available",
                                subtitle = "There are no tracks or playlists in this category right now."
                            )
                        }
                    } else {
                        val coverImage = if (screenState.isMoodsAndGenres) null else shelves.firstOrNull()?.items?.firstOrNull()?.artwork
                        val context = LocalContext.current
                        val dominantColor = rememberDominantColor(context, coverImage, MaterialTheme.colorScheme.surfaceVariant)

                        if (screenState.isMoodsAndGenres) {
                            CategoryCards(
                                shelves = shelves,
                                isLoading = false,
                                gridState = categoryGridState,
                                showTopShortcuts = false,
                                headerTitle = null,
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    top = minHeaderHeight + 8.dp,
                                    end = 16.dp,
                                    bottom = LocalBottomContentPadding.current
                                ),
                                onCategoryPress = { id, title ->
                                    onNavigateToCategory?.invoke(id, title) ?: onNavigateToCatalog(id, title, null)
                                }
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current)
                            ) {
                                item(key = "explore_header") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        dominantColor.copy(alpha = 0.45f),
                                                        dominantColor.copy(alpha = 0.15f),
                                                        MaterialTheme.colorScheme.background
                                                    )
                                                )
                                            )
                                            .padding(top = minHeaderHeight + 12.dp)
                                    ) {
                                        Text(
                                            text = screenState.title,
                                            fontSize = 32.sp,
                                            lineHeight = 38.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        if (screenState.isCharts) {
                                            ChartCountryFilterRow(
                                                currentCountry = screenState.currentCountry,
                                                quickCountries = screenState.quickCountries,
                                                allCountries = viewModel.chartCountries,
                                                onSelectCountry = { viewModel.selectCountry(it) }
                                            )
                                        }
                                    }
                                }

                                itemsIndexed(
                                    items = shelves,
                                    key = { index, shelf -> "${shelf.title.ifBlank { shelf.id }}_$index" }
                                ) { _, shelf ->
                                    FeedCarousel(
                                        shelf = shelf,
                                        onTrackClick = { track ->
                                            MediaActionHandler.handleTrackClick(
                                                track = track,
                                                onNavigateToCollection = onNavigateToCollection,
                                                playerRepository = playerRepository,
                                                shelfItems = shelf.items,
                                                onNavigateToCatalog = onNavigateToCatalog
                                            )
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
                                                onNavigateToCatalog(endpoint, targetShelf.title, null)
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
                            }
                        }
                    }
                }
            }
            
            val topBarContentColor = MaterialTheme.colorScheme.onBackground

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(minHeaderHeight)
                    .graphicsLayer { alpha = titleAlpha }
                    .background(MaterialTheme.colorScheme.background)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(minHeaderHeight)
                    .padding(top = topSafeArea, start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = AppIcons.ArrowLeft,
                        contentDescription = "Back",
                        tint = topBarContentColor
                    )
                }
                
                Text(
                    text = screenState.title,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = topBarContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .weight(1f)
                        .graphicsLayer { alpha = titleAlpha }
                )

                IconButton(onClick = onNavigateToSearch) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = "Search",
                        tint = topBarContentColor
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = minHeaderHeight)
                    .graphicsLayer { alpha = titleAlpha },
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ChartCountryFilterRow(
    currentCountry: ChartCountry,
    quickCountries: List<ChartCountry>,
    allCountries: List<ChartCountry>,
    onSelectCountry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDropdownOpen by remember { mutableStateOf(false) }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Box {
                FilterChip(
                    selected = true,
                    onClick = { isDropdownOpen = true },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${currentCountry.flag} ${currentCountry.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Icon(
                                imageVector = AppIcons.ChevronDown,
                                contentDescription = "Select country",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = null,
                    shape = ShapeFull
                )

                DropdownMenu(
                    expanded = isDropdownOpen,
                    onDismissRequest = { isDropdownOpen = false },
                    shape = ShapeMd,
                    modifier = Modifier
                        .widthIn(min = 200.dp)
                        .heightIn(max = 380.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    allCountries.forEach { country ->
                        val isSelected = country.code.equals(currentCountry.code, ignoreCase = true)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${country.flag}  ${country.name}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            },
                            onClick = {
                                isDropdownOpen = false
                                onSelectCountry(country.code)
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = AppIcons.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        items(quickCountries, key = { it.code }) { country ->
            FilterChip(
                selected = false,
                onClick = { onSelectCountry(country.code) },
                label = {
                    Text(
                        text = "${country.flag} ${country.name}",
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null,
                shape = ShapeFull
            )
        }
    }
}
