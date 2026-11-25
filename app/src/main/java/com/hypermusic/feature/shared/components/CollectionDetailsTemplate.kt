package com.hypermusic.feature.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.hypermusic.core.designsystem.theme.ShapeXs
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.core.designsystem.components.EmptyStateView
import com.hypermusic.core.designsystem.components.ErrorStateView
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.components.WaveLoader
import com.hypermusic.core.model.CollectionDetails
import com.hypermusic.core.model.Track
import com.hypermusic.core.utils.rememberDominantColor
import com.hypermusic.core.designsystem.theme.ShapeFull
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding

@Immutable
data class CollectionDownloadUiState(
    val isComplete: Boolean = false,
    val status: String? = null,
    val progress: Float? = null,
    val label: String? = null
)

@Composable
fun CollectionDetailsTemplate(
    collectionDetails: CollectionDetails?,
    isLoading: Boolean,
    error: String?,
    friendlyError: com.hypermusic.core.utils.FriendlyError? = null,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onMoreOptions: () -> Unit,
    onDownloadAll: () -> Unit,
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {},
    type: String = "playlist",
    downloadState: CollectionDownloadUiState = CollectionDownloadUiState(),
    isDownloadEnqueuing: Boolean = false,
    onNavigateToCollection: ((type: String, id: String, title: String?, subtitle: String?, artworkUrl: String?) -> Unit)? = null,
    onArtistClick: (() -> Unit)? = null,
    onShelfTrackClick: ((Track) -> Unit)? = null,
    onShelfMoreClick: ((Track) -> Unit)? = null,
    onShelfPlayAll: ((List<Track>) -> Unit)? = null,
    itemContent: @Composable (index: Int, track: Track) -> Unit
) {
    val listState = rememberLazyListState()

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val topSafeArea = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val minHeaderHeight = topSafeArea + 56.dp
    val isCompactHeight = configuration.screenHeightDp < 500

    val screenWidth = configuration.screenWidthDp.dp
    val isPodcast = type == "podcast"
    val podcastHeroHeight = topSafeArea + 280.dp
    val artworkSize = if (isCompactHeight) 130.dp else minOf(screenWidth * 0.56f, 230.dp)

    val context = LocalContext.current
    val highResUrl = collectionDetails?.artworkUrl
    val dominantColor = rememberDominantColor(context, highResUrl, MaterialTheme.colorScheme.surfaceVariant)
    val backgroundColor = MaterialTheme.colorScheme.background

    val collapseThresholdPx = with(density) {
        if (isPodcast) {
            (topSafeArea + 190.dp).toPx().coerceAtLeast(1f)
        } else {
            (minHeaderHeight + artworkSize + 32.dp).toPx().coerceAtLeast(1f)
        }
    }

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

    val displayFriendlyError = friendlyError ?: error?.let { com.hypermusic.core.utils.ErrorClassifier.classify(it) }

    ScreenWrapper(disableSafeAreaTop = true) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            if (isLoading && collectionDetails == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WaveLoader()
                }
            } else if (displayFriendlyError != null && collectionDetails == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorStateView(
                        error = displayFriendlyError,
                        onRetry = onRetry
                    )
                }
            } else if (collectionDetails != null) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current)
                ) {
                    item(key = "collection_header") {
                        if (isPodcast) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(podcastHeroHeight)
                                ) {
                                    MediaArtwork(
                                        imageUrl = highResUrl,
                                        contentDescription = collectionDetails.title,
                                        trackType = "podcast",
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

                                    // Blend the artwork into the theme background.
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
                                }

                                CollectionInfoSection(
                                    details = collectionDetails,
                                    onPlayAll = onPlayAll,
                                    onShufflePlay = onShufflePlay,
                                    onDownloadAll = onDownloadAll,
                                    isSaved = isSaved,
                                    onToggleSave = onToggleSave,
                                    type = type,
                                    downloadState = downloadState,
                                    isDownloadEnqueuing = isDownloadEnqueuing,
                                    onNavigateToCollection = onNavigateToCollection,
                                    onArtistClick = onArtistClick
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                dominantColor.copy(alpha = 0.50f),
                                                dominantColor.copy(alpha = 0.18f),
                                                backgroundColor
                                            )
                                        )
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(minHeaderHeight + 8.dp))

                                MediaArtwork(
                                    imageUrl = highResUrl,
                                    contentDescription = collectionDetails.title,
                                    trackType = type,
                                    contentScale = ContentScale.Crop,
                                    quality = ArtworkQuality.HERO,
                                    modifier = Modifier
                                        .size(artworkSize)
                                        .shadow(12.dp, MaterialTheme.shapes.medium)
                                        .clip(MaterialTheme.shapes.medium)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                CollectionInfoSection(
                                    details = collectionDetails,
                                    onPlayAll = onPlayAll,
                                    onShufflePlay = onShufflePlay,
                                    onDownloadAll = onDownloadAll,
                                    isSaved = isSaved,
                                    onToggleSave = onToggleSave,
                                    type = type,
                                    downloadState = downloadState,
                                    isDownloadEnqueuing = isDownloadEnqueuing,
                                    onNavigateToCollection = onNavigateToCollection,
                                    onArtistClick = onArtistClick
                                )
                            }
                        }
                    }

                    if (isLoading && collectionDetails.tracks.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                WaveLoader()
                            }
                        }
                    } else if (displayFriendlyError != null && collectionDetails.tracks.isEmpty()) {
                        item {
                            ErrorStateView(
                                error = displayFriendlyError,
                                onRetry = onRetry,
                                modifier = Modifier.padding(top = 24.dp)
                            )
                        }
                    } else if (collectionDetails.tracks.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = AppIcons.Music,
                                title = "No tracks found",
                                subtitle = "This collection doesn't contain any tracks.",
                                modifier = Modifier.padding(top = 24.dp)
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = collectionDetails.tracks,
                            key = { index, track -> "${track.id}_$index" },
                            contentType = { _, _ -> "track" }
                        ) { index, track ->
                            itemContent(index, track)
                        }
                    }

                    if (collectionDetails.shelves.isNotEmpty()) {
                        item(key = "shelves_spacer", contentType = "spacer") {
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                        itemsIndexed(
                            items = collectionDetails.shelves,
                            key = { index, shelf -> "${shelf.title.ifBlank { shelf.id }}_$index" },
                            contentType = { _, _ -> "shelf" }
                        ) { _, shelf ->
                            FeedCarousel(
                                shelf = shelf,
                                onTrackClick = { track ->
                                    when {
                                        com.hypermusic.feature.shared.utils.MediaActionHandler.isPodcastShow(track) ->
                                            onNavigateToCollection?.invoke("podcast", track.id, track.title, track.artist, track.artwork)
                                        com.hypermusic.feature.shared.utils.MediaActionHandler.isArtist(track) ->
                                            onNavigateToCollection?.invoke("artist", track.id, track.title, null, track.artwork)
                                        com.hypermusic.feature.shared.utils.MediaActionHandler.isPlaylist(track) ->
                                            onNavigateToCollection?.invoke("playlist", track.id, track.title, track.artist, track.artwork)
                                        com.hypermusic.feature.shared.utils.MediaActionHandler.isAlbum(track) ->
                                            onNavigateToCollection?.invoke("album", track.id, track.title, track.artist, track.artwork)
                                        else ->
                                            onShelfTrackClick?.invoke(track)
                                    }
                                },
                                onMoreClick = onShelfMoreClick,
                                onPlayAll = onShelfPlayAll ?: { tracks ->
                                    val playableTracks = com.hypermusic.feature.shared.utils.MediaActionHandler.filterPlayableTracks(tracks)
                                    if (playableTracks.isNotEmpty()) {
                                        playableTracks.firstOrNull()?.let { onShelfTrackClick?.invoke(it) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            val hasArtwork = !collectionDetails?.artworkUrl.isNullOrBlank() && !isLoading && error == null
            val targetOnBackground = MaterialTheme.colorScheme.onBackground
            val topBarTint = if (isPodcast && hasArtwork) {
                androidx.compose.ui.graphics.lerp(
                    start = Color.White,
                    stop = targetOnBackground,
                    fraction = (titleAlpha * 2f).coerceIn(0f, 1f)
                )
            } else {
                targetOnBackground
            }

            if (isPodcast && hasArtwork) {
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
                
                Text(
                    text = collectionDetails?.title ?: "",
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

                IconButton(onClick = onMoreOptions) {
                    Icon(imageVector = AppIcons.MoreVertical, contentDescription = "More", tint = topBarTint)
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
fun CollectionInfoSection(
    details: CollectionDetails, 
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onDownloadAll: () -> Unit,
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {},
    type: String = "playlist",
    downloadState: CollectionDownloadUiState = CollectionDownloadUiState(),
    isDownloadEnqueuing: Boolean = false,
    onNavigateToCollection: ((type: String, id: String, title: String?, subtitle: String?, artworkUrl: String?) -> Unit)? = null,
    onArtistClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 0.dp, bottom = 16.dp)
    ) {
        Text(
            text = details.title,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        val hasArtistInfo = !details.artists.isNullOrEmpty() || !details.artistId.isNullOrBlank()
        Text(
            text = "${details.subtitle} ${if (details.year != null) "• ${details.year}" else ""}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(ShapeXs)
                .then(
                    if (onArtistClick != null && hasArtistInfo) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onArtistClick.invoke()
                        }
                    } else Modifier
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = ShapeFull,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground)
            ) {
                Icon(imageVector = AppIcons.Play, contentDescription = "Play", tint = MaterialTheme.colorScheme.background)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play", color = MaterialTheme.colorScheme.background, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            IconButton(
                onClick = onShufflePlay,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(imageVector = AppIcons.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onBackground)
            }

            IconButton(
                onClick = onToggleSave,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = if (isSaved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                    contentDescription = "Save",
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(
                onClick = onDownloadAll,
                enabled = details.tracks.isNotEmpty() && !isDownloadEnqueuing,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .semantics {
                        contentDescription = "Collection downloads"
                        stateDescription = if (isDownloadEnqueuing) "Preparing downloads" else downloadState.label ?: "Not downloaded"
                    }
            ) {
                if (isDownloadEnqueuing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else if (downloadState.status == "DOWNLOADING") {
                    val progress = downloadState.progress ?: 0f
                    if (progress > 0f) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Icon(
                        imageVector = when {
                            downloadState.isComplete -> AppIcons.Check
                            downloadState.status == "PAUSED" -> AppIcons.Pause
                            downloadState.status == "FAILED" -> AppIcons.AlertCircle
                            else -> AppIcons.ArrowDownToLine
                        },
                        contentDescription = when {
                            downloadState.isComplete -> "Downloaded"
                            downloadState.status == "PAUSED" -> "Resume downloads"
                            downloadState.status == "FAILED" -> "Retry downloads"
                            downloadState.status == "PENDING" -> "Downloads queued"
                            else -> "Download"
                        },
                        tint = when {
                            downloadState.isComplete -> MaterialTheme.colorScheme.primary
                            downloadState.status == "FAILED" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onBackground
                        }
                    )
                }
            }
        }
        val downloadLabel = if (isDownloadEnqueuing) "Preparing downloads" else downloadState.label
        downloadLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = if (downloadState.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

