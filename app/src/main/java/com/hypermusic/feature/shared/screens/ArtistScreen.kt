package com.hypermusic.feature.shared.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.feature.shared.components.ArtistBioSection
import com.hypermusic.core.designsystem.components.EmptyStateView
import com.hypermusic.core.designsystem.components.ErrorStateView
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.components.WaveLoader
import com.hypermusic.core.model.ArtistDetails
import com.hypermusic.core.utils.rememberDominantColor
import com.hypermusic.core.designsystem.theme.ShapeFull
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import com.hypermusic.feature.shared.components.FeedCarousel
import com.hypermusic.core.player.PlayerRepository
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun ArtistScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCollection: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToCatalog: (String, String?, String?) -> Unit = { _, _, _ -> },
    viewModel: ArtistViewModel = koinViewModel(),
    playerRepository: PlayerRepository = koinInject(),
    actionSheetViewModel: com.hypermusic.feature.shared.state.ActionSheetViewModel = koinInject(),
    toastViewModel: com.hypermusic.feature.shared.state.GlobalToastViewModel = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val topSafeArea = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val minHeaderHeight = topSafeArea + 56.dp
    val isCompactHeight = configuration.screenHeightDp < 500
    val heroHeight = if (isCompactHeight) minHeaderHeight + 160.dp else topSafeArea + 300.dp
    val collapseThresholdPx = with(density) { (if (isCompactHeight) 160.dp else 240.dp).toPx().coerceAtLeast(1f) }

    val titleAlpha by remember(collapseThresholdPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val fraction = (listState.firstVisibleItemScrollOffset / collapseThresholdPx).coerceIn(0f, 1f)
                if (fraction <= 0.60f) 0f
                else ((fraction - 0.60f) / 0.40f).coerceIn(0f, 1f)
            }
        }
    }

    ScreenWrapper(disableSafeAreaTop = true) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            when (val state = uiState) {
                is ArtistUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        WaveLoader()
                    }
                }
                is ArtistUiState.Error -> {
                    val friendly = state.friendlyError ?: com.hypermusic.core.utils.ErrorClassifier.classify(state.message)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorStateView(
                            error = friendly,
                            onRetry = { viewModel.loadArtist() }
                        )
                    }
                }
                is ArtistUiState.Success -> {
                    val details = state.details
                    val isSaved by viewModel.isSaved.collectAsState()

                    val highResUrl = details.artworkUrl
                    val context = LocalContext.current
                    val dominantColor = rememberDominantColor(context, highResUrl, MaterialTheme.colorScheme.surfaceVariant)
                    val backgroundColor = MaterialTheme.colorScheme.background

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current)
                    ) {
                        item(key = "artist_header") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroHeight)
                            ) {
                                MediaArtwork(
                                    imageUrl = highResUrl,
                                    contentDescription = details.title,
                                    trackType = "artist",
                                    contentScale = ContentScale.Crop,
                                    quality = ArtworkQuality.HERO,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Top status bar protection gradient
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(minHeaderHeight + 24.dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.55f),
                                                    Color.Black.copy(alpha = 0.20f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                    )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                0.0f to Color.Transparent,
                                                0.30f to Color.Transparent,
                                                0.55f to dominantColor.copy(alpha = 0.35f),
                                                0.80f to backgroundColor.copy(alpha = 0.85f),
                                                1.0f to backgroundColor
                                            )
                                        )
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                ) {
                                    ArtistInfoSection(
                                        details = details,
                                        isSaved = isSaved,
                                        onToggleSave = { viewModel.toggleSave() }
                                    )
                                }
                            }
                        }

                        if (details.shelves.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = AppIcons.Music,
                                    title = "No releases available",
                                    subtitle = "There are no tracks or albums listed for this artist.",
                                    modifier = Modifier.padding(top = 24.dp)
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = details.shelves,
                                key = { index, shelf -> "${shelf.title.ifBlank { shelf.id }}_$index" }
                            ) { _, shelf ->
                                FeedCarousel(
                                    shelf = shelf,
                                    onTrackClick = { track ->
                                        com.hypermusic.feature.shared.utils.MediaActionHandler.handleTrackClick(
                                            track = track,
                                            onNavigateToCollection = onNavigateToCollection,
                                            playerRepository = playerRepository,
                                            shelfItems = shelf.items
                                        )
                                    },
                                    onMoreClick = { track ->
                                        com.hypermusic.feature.shared.utils.MediaActionHandler.handleTrackMoreClick(
                                            track = track,
                                            actionSheetViewModel = actionSheetViewModel
                                        )
                                    },
                                    onShelfMoreClick = { targetShelf ->
                                        val endpoint = targetShelf.moreEndpoint
                                        if (!endpoint.isNullOrEmpty()) {
                                            onNavigateToCatalog(endpoint, targetShelf.title, details.title)
                                        }
                                    },
                                    onPlayAll = { tracks ->
                                        val playableTracks = com.hypermusic.feature.shared.utils.MediaActionHandler.filterPlayableTracks(tracks)
                                        if (playableTracks.isNotEmpty()) {
                                            playerRepository.playTracks(playableTracks)
                                        }
                                    }
                                )
                            }
                        }

                        if (!details.description.isNullOrBlank() || !details.descriptionRuns.isNullOrEmpty()) {
                            item {
                                ArtistBioSection(
                                    title = "About ${details.title}",
                                    description = details.description,
                                    runs = details.descriptionRuns
                                )
                            }
                        }
                    }
                }
            }
            
            val hasArtwork = (uiState as? ArtistUiState.Success)?.details?.artworkUrl?.isNotBlank() == true
            val targetOnBackground = MaterialTheme.colorScheme.onBackground
            val topBarTint = if (hasArtwork) {
                androidx.compose.ui.graphics.lerp(
                    start = Color.White,
                    stop = targetOnBackground,
                    fraction = (titleAlpha * 2f).coerceIn(0f, 1f)
                )
            } else {
                targetOnBackground
            }

            if (hasArtwork) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(minHeaderHeight + 20.dp)
                        .graphicsLayer { alpha = 1f - titleAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

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
                    Icon(imageVector = AppIcons.ArrowLeft, contentDescription = "Back", tint = topBarTint)
                }
                
                val title = if (uiState is ArtistUiState.Success) (uiState as ArtistUiState.Success).details.title else ""
                Text(
                    text = title,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = topBarTint,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .weight(1f)
                        .graphicsLayer { alpha = titleAlpha },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val context = LocalContext.current
                IconButton(onClick = { 
                    if (uiState is ArtistUiState.Success) {
                        val details = (uiState as ArtistUiState.Success).details
                        com.hypermusic.core.utils.ShareHelper.shareContent(
                            context = context,
                            type = com.hypermusic.core.utils.ShareType.ARTIST,
                            id = viewModel.artistId,
                            name = details.title
                        )
                    }
                }) {
                    Icon(imageVector = AppIcons.Share2, contentDescription = "Share", tint = topBarTint)
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
fun ArtistInfoSection(
    details: ArtistDetails,
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = details.title,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (details.subtitle.isNotBlank()) {
                Text(
                    text = details.subtitle,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        OutlinedButton(
            onClick = onToggleSave,
            modifier = Modifier.height(40.dp),
            shape = ShapeFull,
            border = BorderStroke(
                width = 1.dp,
                color = if (isSaved) Color.Transparent else Color.White.copy(alpha = 0.5f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isSaved) Color.White else Color.Black.copy(alpha = 0.35f),
                contentColor = if (isSaved) Color.Black else Color.White
            )
        ) {
            Text(
                text = if (isSaved) "Following" else "Follow",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
