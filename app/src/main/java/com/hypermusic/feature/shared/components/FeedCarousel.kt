package com.hypermusic.feature.shared.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import com.hypermusic.core.data.local.entity.DownloadQueueEntity
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.player.PlayerRepository
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.components.AnimatedEQ
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.core.designsystem.components.MediaMoreButton
import com.hypermusic.core.model.BrowseShelf
import com.hypermusic.core.model.Track
import com.hypermusic.feature.shared.utils.MediaActionHandler
import com.hypermusic.core.designsystem.theme.ShapeSm
import com.hypermusic.core.designsystem.theme.ShapeXs

@Composable
fun FeedCarousel(
    shelf: BrowseShelf,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: ((Track) -> Unit)? = null,
    onPlayAll: ((List<Track>) -> Unit)? = null,
    onShelfMoreClick: ((BrowseShelf) -> Unit)? = null,
    playerRepository: PlayerRepository = koinInject(),
    downloadRepository: com.hypermusic.core.data.repository.DownloadRepository = koinInject()
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val isArtistCarousel = remember(shelf.items) {
        shelf.items.isNotEmpty() && shelf.items.all { MediaActionHandler.isArtist(it) }
    }

    val isCompactGrid = remember(shelf.id, shelf.type, shelf.items, isArtistCarousel) {
        shelf.id == "local_play_again" || (
            shelf.type != "chart_artists" &&
            !isArtistCarousel &&
            shelf.items.isNotEmpty() &&
            shelf.items.all {
                it.trackType in listOf("song", "video", "podcast_episode", "device_local", null)
            } &&
            shelf.items.none {
                MediaActionHandler.isPodcastShow(it) || it.trackType == "playlist" || it.trackType == "album" || MediaActionHandler.isGenre(it)
            } &&
            (shelf.items.any { it.trackType == "song" || it.trackType == null || it.trackType == "device_local" } || shelf.type == "grid")
        )
    }
    
    val isPlayableShelf = remember(shelf.type, shelf.items) {
        shelf.type != "collection_cards" &&
        shelf.type != "chart_artists" &&
        shelf.items.isNotEmpty() && shelf.items.all {
            it.trackType == "song" || it.trackType == "video" || MediaActionHandler.isPodcastEpisode(it) || it.trackType == "device_local" || it.trackType == null
        } && shelf.items.none { MediaActionHandler.isGenre(it) }
    }

    val displayItems = remember(shelf.items) {
        shelf.items.distinctBy { it.id }
    }

    Column(modifier = modifier.padding(bottom = 16.dp)) {
        FeedCarouselHeader(
            title = shelf.title,
            moreEndpoint = shelf.moreEndpoint,
            isPlayableShelf = isPlayableShelf,
            shelf = shelf,
            onShelfMoreClick = onShelfMoreClick,
            onPlayAll = onPlayAll
        )

        when {
            shelf.type == "collection_cards" -> {
                CollectionCardsCarousel(
                    displayItems = displayItems,
                    onTrackClick = onTrackClick,
                    onPlayAll = onPlayAll,
                    onMoreClick = onMoreClick,
                    playerRepository = playerRepository
                )
            }
            shelf.type == "chart_artists" -> {
                ChartArtistsGrid(
                    displayItems = displayItems,
                    screenWidth = screenWidth,
                    onTrackClick = onTrackClick,
                    onMoreClick = onMoreClick
                )
            }
            isCompactGrid -> {
                CompactTracksGrid(
                    displayItems = displayItems,
                    screenWidth = screenWidth,
                    playerRepository = playerRepository,
                    downloadRepository = downloadRepository,
                    onTrackClick = onTrackClick,
                    onMoreClick = onMoreClick
                )
            }
            else -> {
                StandardTracksCarousel(
                    displayItems = displayItems,
                    playerRepository = playerRepository,
                    downloadRepository = downloadRepository,
                    onTrackClick = onTrackClick,
                    onMoreClick = onMoreClick
                )
            }
        }
    }
}

@Composable
private fun FeedCarouselHeader(
    title: String,
    moreEndpoint: String?,
    isPlayableShelf: Boolean,
    shelf: BrowseShelf,
    onShelfMoreClick: ((BrowseShelf) -> Unit)?,
    onPlayAll: ((List<Track>) -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.weight(1f, fill = false)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!moreEndpoint.isNullOrEmpty() && onShelfMoreClick != null) {
                TextButton(
                    onClick = { onShelfMoreClick(shelf) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Show all",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isPlayableShelf && onPlayAll != null) {
                TextButton(
                    onClick = {
                        val playableTracks = shelf.items.filter {
                            MediaActionHandler.isPlayable(it)
                        }
                        if (playableTracks.isNotEmpty()) {
                            onPlayAll(playableTracks)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Play all",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionCardsCarousel(
    displayItems: List<Track>,
    onTrackClick: (Track) -> Unit,
    onPlayAll: ((List<Track>) -> Unit)?,
    onMoreClick: ((Track) -> Unit)?,
    playerRepository: PlayerRepository
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = displayItems,
            key = { "collection_card_${it.id}" }
        ) { collectionTrack ->
            CollectionPreviewCard(
                collection = collectionTrack,
                onCardClick = { onTrackClick(collectionTrack) },
                onPlayAllClick = {
                    val previewList = collectionTrack.previewTracks ?: emptyList()
                    val playable = MediaActionHandler.filterPlayableTracks(previewList)
                    if (playable.isNotEmpty()) {
                        onPlayAll?.invoke(playable) ?: playerRepository.playTracks(playable, 0)
                    }
                },
                onTrackClick = { track, list ->
                    val playable = MediaActionHandler.filterPlayableTracks(list)
                    val index = playable.indexOfFirst { it.id == track.id }
                    if (index != -1) {
                        playerRepository.playTracks(playable, index)
                    } else {
                        playerRepository.playTrack(track)
                    }
                },
                onTrackMoreClick = { track ->
                    onMoreClick?.invoke(track)
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChartArtistsGrid(
    displayItems: List<Track>,
    screenWidth: Dp,
    onTrackClick: (Track) -> Unit,
    onMoreClick: ((Track) -> Unit)?
) {
    val rowCount = minOf(displayItems.size.coerceAtLeast(1), 4)
    val gridHeight = (rowCount * 64 + (rowCount - 1) * 4).dp

    LazyHorizontalGrid(
        rows = GridCells.Fixed(rowCount),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(gridHeight)
    ) {
        itemsIndexed(
            items = displayItems,
            key = { _, it -> it.id }
        ) { index, track ->
            val isSingleColumn = displayItems.size <= rowCount
            val cardWidth = if (screenWidth < 600.dp) {
                if (isSingleColumn) screenWidth - 32.dp else screenWidth * 0.82f
            } else {
                if (isSingleColumn) androidx.compose.ui.unit.min(screenWidth - 32.dp, 400.dp) else 340.dp
            }

            Row(
                modifier = Modifier
                    .width(cardWidth)
                    .clip(ShapeSm)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = { onTrackClick(track) },
                        onLongClick = { onMoreClick?.invoke(track) }
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.width(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val rankText = track.chartRank ?: "${index + 1}"
                    Text(
                        text = rankText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1
                    )
                    when (track.chartTrend) {
                        "UP" -> {
                            Icon(
                                imageVector = AppIcons.ArrowDropUp,
                                contentDescription = "Rank up",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        "DOWN" -> {
                            Icon(
                                imageVector = AppIcons.ArrowDropDown,
                                contentDescription = "Rank down",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        "NEUTRAL" -> {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            )
                        }
                        else -> {
                            // No movement data
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                ) {
                    MediaArtwork(
                        imageUrl = track.artwork,
                        contentDescription = track.title,
                        trackType = "artist",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitleText = if (!track.artist.isNullOrBlank()) {
                        track.artist
                    } else {
                        "Artist"
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onMoreClick != null) {
                    MediaMoreButton(
                        onClick = { onMoreClick.invoke(track) },
                        iconSize = 20.dp,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CompactTracksGrid(
    displayItems: List<Track>,
    screenWidth: Dp,
    playerRepository: PlayerRepository,
    downloadRepository: com.hypermusic.core.data.repository.DownloadRepository,
    onTrackClick: (Track) -> Unit,
    onMoreClick: ((Track) -> Unit)?
) {
    val rowCount = minOf(displayItems.size.coerceAtLeast(1), 5)
    val gridHeight = (rowCount * 64 + (rowCount - 1) * 4).dp

    LazyHorizontalGrid(
        rows = GridCells.Fixed(rowCount),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(gridHeight)
    ) {
        itemsIndexed(
            items = displayItems,
            key = { _, it -> it.id }
        ) { _, track ->
            val isSingleColumn = displayItems.size <= rowCount
            val cardWidth = if (screenWidth < 600.dp) {
                if (isSingleColumn) screenWidth - 32.dp else screenWidth * 0.85f
            } else {
                if (isSingleColumn) androidx.compose.ui.unit.min(screenWidth - 32.dp, 400.dp) else 350.dp
            }
            val isArtist = MediaActionHandler.isArtist(track)

            Row(
                modifier = Modifier
                    .width(cardWidth)
                    .clip(ShapeSm)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = { onTrackClick(track) },
                        onLongClick = { onMoreClick?.invoke(track) }
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shape = if (isArtist) CircleShape else ShapeXs
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(shape)
                ) {
                    MediaArtwork(
                        imageUrl = track.artwork,
                        contentDescription = track.title,
                        trackType = if (isArtist) "artist" else track.trackType,
                        isDeviceLocal = track.isDeviceLocal,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!isArtist) {
                        FeedTrackPlaybackIndicator(
                            trackId = track.id,
                            playerRepository = playerRepository,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val subtitleText = if (!track.artist.isNullOrBlank()) {
                        track.artist
                    } else if (isArtist) {
                        "Artist"
                    } else {
                        ""
                    }
                    if (subtitleText.isNotEmpty()) {
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (onMoreClick != null) {
                    FeedTrackMoreButton(
                        trackId = track.id,
                        downloadRepository = downloadRepository,
                        onClick = { onMoreClick.invoke(track) },
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardTracksCarousel(
    displayItems: List<Track>,
    playerRepository: PlayerRepository,
    downloadRepository: com.hypermusic.core.data.repository.DownloadRepository,
    onTrackClick: (Track) -> Unit,
    onMoreClick: ((Track) -> Unit)?
) {
    val hasVideoItems = remember(displayItems) {
        displayItems.any { it.trackType == "video" || MediaActionHandler.isPodcastEpisode(it) }
    }
    val minRowHeight = if (hasVideoItems) 260.dp else 226.dp
    LazyRow(
        modifier = Modifier.defaultMinSize(minHeight = minRowHeight),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(
            items = displayItems,
            key = { it.id }
        ) { track ->
            FeedTrackCard(
                track = track,
                playerRepository = playerRepository,
                downloadRepository = downloadRepository,
                onClick = onTrackClick,
                onMoreClick = onMoreClick
            )
        }
    }
}

@Composable
private fun rememberIsCurrentTrack(trackId: String, playerRepository: PlayerRepository): State<Boolean> {
    val currentTrack = playerRepository.currentTrack.collectAsState()
    return remember(trackId, playerRepository) {
        derivedStateOf(structuralEqualityPolicy()) { currentTrack.value?.id == trackId }
    }
}

@Composable
private fun rememberDownloadEntry(
    trackId: String,
    downloadRepository: com.hypermusic.core.data.repository.DownloadRepository
): State<DownloadQueueEntity?> {
    val queue = downloadRepository.downloadQueue.collectAsState()
    return remember(trackId, downloadRepository) {
        derivedStateOf(structuralEqualityPolicy()) { queue.value[trackId] }
    }
}

@Composable
private fun FeedTrackPlaybackIndicator(
    trackId: String,
    playerRepository: PlayerRepository,
    modifier: Modifier
) {
    val isCurrentTrack by rememberIsCurrentTrack(trackId, playerRepository)
    if (isCurrentTrack) {
        val isPlaying by playerRepository.isPlaying.collectAsState()
        AnimatedEQ(
            isPlaying = isPlaying,
            color = Color.White,
            modifier = modifier.semantics {
                stateDescription = if (isPlaying) "Playing" else "Current track, paused"
            }
        )
    }
}

@Composable
private fun FeedTrackMoreButton(
    trackId: String,
    downloadRepository: com.hypermusic.core.data.repository.DownloadRepository,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val entry by rememberDownloadEntry(trackId, downloadRepository)
    MediaMoreButton(
        onClick = onClick,
        downloadProgress = entry?.progress?.div(100f),
        downloadStatus = entry?.status,
        iconSize = 20.dp,
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

@Composable
private fun FeedTrackCard(
    track: Track,
    playerRepository: PlayerRepository,
    downloadRepository: com.hypermusic.core.data.repository.DownloadRepository,
    onClick: (Track) -> Unit,
    onMoreClick: ((Track) -> Unit)?
) {
    val isCurrentTrack by rememberIsCurrentTrack(track.id, playerRepository)
    val entry by rememberDownloadEntry(track.id, downloadRepository)
    val isPlaying = if (isCurrentTrack) {
        val playing by playerRepository.isPlaying.collectAsState()
        playing
    } else false
    TrackCard(
        track = track,
        onClick = onClick,
        onMoreClick = onMoreClick,
        isCurrentTrack = isCurrentTrack,
        isPlaying = isPlaying,
        downloadProgress = entry?.progress?.div(100f),
        downloadStatus = entry?.status
    )
}
