package com.hypermusic.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hypermusic.core.designsystem.theme.ShapeFull
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.hypermusic.core.designsystem.theme.LikedSongsGradient
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import androidx.compose.ui.layout.ContentScale
import com.hypermusic.core.model.isDeviceOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.core.utils.rememberDominantColor
import com.hypermusic.feature.library.components.LibraryEntityCard
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import org.koin.compose.koinInject

@Composable
fun LikedSongsScreen(
    onNavigateBack: () -> Unit,
    localMediaRepository: com.hypermusic.core.data.repository.LocalMediaRepository = koinInject(),
    playerRepository: PlayerRepository = koinInject(),
    actionSheetViewModel: ActionSheetViewModel = koinInject(),
    downloadRepository: com.hypermusic.core.data.repository.DownloadRepository = koinInject(),
    toastViewModel: com.hypermusic.feature.shared.state.GlobalToastViewModel = koinInject(),
    navigationEventBus: com.hypermusic.navigation.NavigationEventBus = koinInject()
) {
    val likedFlow = remember(localMediaRepository) { localMediaRepository.getLikedTracks() }
    val likedTracks by likedFlow.collectAsState(initial = emptyList())
    val currentTrack by playerRepository.currentTrack.collectAsState()
    val isPlaying by playerRepository.isPlaying.collectAsState()
    val queueMap by downloadRepository.downloadQueue.collectAsState()
    val downloadedFlow = remember(downloadRepository) { downloadRepository.getAllDownloadedTracks() }
    val downloadedTracks by downloadedFlow.collectAsState(initial = emptyList())
    val downloadedTrackIds = remember(downloadedTracks) { downloadedTracks.map { it.id }.toSet() }
    val downloadableTracks = remember(likedTracks) { likedTracks.filterNot { it.isDeviceOrigin() } }
    val downloadedCount = remember(downloadableTracks, downloadedTrackIds) { downloadableTracks.count { it.id in downloadedTrackIds } }
    val isAllDownloaded = downloadableTracks.isNotEmpty() && downloadedCount == downloadableTracks.size
    val hasActiveDownload = remember(downloadableTracks, queueMap) { downloadableTracks.any { queueMap[it.id]?.status == "DOWNLOADING" } }
    val hasPendingDownload = remember(downloadableTracks, queueMap) { downloadableTracks.any { queueMap[it.id]?.status == "PENDING" } }
    val isDownloadingOrQueued = hasActiveDownload || hasPendingDownload
    val firstTrackArtwork = likedTracks.firstOrNull()?.artwork
    val listState = rememberLazyListState()

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val topSafeArea = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val minHeaderHeight = topSafeArea + 56.dp

    val totalDurationSec = remember(likedTracks) { likedTracks.sumOf { it.duration ?: 0L } }
    val durationText = remember(totalDurationSec) {
        if (totalDurationSec <= 0L) ""
        else {
            val hrs = totalDurationSec / 3600
            val mins = (totalDurationSec % 3600) / 60
            if (hrs > 0) "$hrs hr $mins min" else "$mins min"
        }
    }
    val subtitleText = remember(likedTracks.size, durationText) {
        val count = "${likedTracks.size} ${if (likedTracks.size == 1) "song" else "songs"}"
        if (durationText.isNotBlank()) "$count • $durationText" else count
    }

    val context = LocalContext.current
    val dominantColor = rememberDominantColor(context, firstTrackArtwork, Color(0xFF6B11FF))

    val collapseThresholdPx = with(density) { 110.dp.toPx() }

    val titleAlpha by remember(collapseThresholdPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val fraction = (listState.firstVisibleItemScrollOffset / collapseThresholdPx).coerceIn(0f, 1f)
                if (fraction <= 0.40f) 0f
                else ((fraction - 0.40f) / 0.60f).coerceIn(0f, 1f)
            }
        }
    }

    ScreenWrapper(disableSafeAreaTop = true) {
        Box(modifier = Modifier.fillMaxSize()) {

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current)
            ) {
                item(key = "liked_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        dominantColor.copy(alpha = 0.55f),
                                        dominantColor.copy(alpha = 0.20f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                            .padding(top = minHeaderHeight + 8.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .shadow(8.dp, MaterialTheme.shapes.medium)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(brush = Brush.linearGradient(colors = LikedSongsGradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.HeartFilled,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Liked music",
                                    fontSize = 24.sp,
                                    lineHeight = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = subtitleText,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "All the music that you like is automatically saved and organized here.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (downloadedCount > 0 && downloadableTracks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isAllDownloaded) "All downloaded" else "$downloadedCount of ${downloadableTracks.size} downloaded",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isAllDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (downloadableTracks.isNotEmpty()) {
                                        scope.launch {
                                            val notDownloaded = downloadableTracks.filter { it.id !in downloadedTrackIds }
                                            val queueItems = queueMap
                                            val toEnqueue = notDownloaded.filter { it.id !in queueItems }
                                            val toResume = notDownloaded.mapNotNull { track ->
                                                queueItems[track.id]?.takeIf { it.status == "PAUSED" || it.status == "FAILED" }?.trackId
                                            }
                                            if (notDownloaded.isEmpty()) {
                                                toastViewModel.showToast(
                                                    message = "All songs are downloaded and available offline",
                                                    type = com.hypermusic.feature.shared.state.ToastType.INFO
                                                )
                                                return@launch
                                            }
                                            if (toEnqueue.isEmpty() && toResume.isEmpty()) {
                                                toastViewModel.showToast(
                                                    message = "Songs already in download queue",
                                                    type = com.hypermusic.feature.shared.state.ToastType.INFO,
                                                    action = com.hypermusic.feature.shared.state.ToastAction("View") {
                                                        navigationEventBus.navigateTo("downloads")
                                                    }
                                                )
                                                return@launch
                                            }
                                            downloadRepository.resumeDownloads(toResume)
                                            val waitingForWifi = if (toEnqueue.isNotEmpty()) {
                                                downloadRepository.enqueueBulkDownload(toEnqueue) == com.hypermusic.core.data.repository.DownloadEnqueueResult.WAITING_FOR_WIFI
                                            } else downloadRepository.isWaitingForWifi()
                                            val count = toEnqueue.size + toResume.size
                                            val message = if (waitingForWifi) {
                                                "Queued $count songs • Waiting for Wi-Fi"
                                            } else {
                                                "Queued $count songs for download"
                                            }
                                            toastViewModel.showToast(
                                                message = message,
                                                type = com.hypermusic.feature.shared.state.ToastType.INFO,
                                                action = com.hypermusic.feature.shared.state.ToastAction("View") {
                                                    navigationEventBus.navigateTo("downloads")
                                                }
                                            )
                                        }
                                    }
                                },
                                enabled = downloadableTracks.isNotEmpty(),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (likedTracks.isNotEmpty()) 0.6f else 0.3f))
                            ) {
                                Icon(
                                    imageVector = when {
                                        isAllDownloaded -> AppIcons.Check
                                        isDownloadingOrQueued -> AppIcons.ArrowDownCircle
                                        else -> AppIcons.ArrowDownToLine
                                    },
                                    contentDescription = when {
                                        isAllDownloaded -> "Downloaded"
                                        hasActiveDownload -> "Downloading"
                                        hasPendingDownload -> "Queued"
                                        else -> "Download all"
                                    },
                                    tint = when {
                                        isAllDownloaded || isDownloadingOrQueued -> MaterialTheme.colorScheme.primary
                                        likedTracks.isNotEmpty() -> MaterialTheme.colorScheme.onBackground
                                        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = {
                                    if (likedTracks.isNotEmpty()) {
                                        playerRepository.toggleShuffle()
                                        playerRepository.playTracks(likedTracks, 0)
                                    }
                                },
                                enabled = likedTracks.isNotEmpty(),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (likedTracks.isNotEmpty()) 1f else 0.38f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .shadow(if (likedTracks.isNotEmpty()) 6.dp else 0.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(if (likedTracks.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.38f))
                                    .clickable(enabled = likedTracks.isNotEmpty()) {
                                        playerRepository.playTracks(likedTracks, 0)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Play,
                                    contentDescription = "Play",
                                    tint = Color.Black.copy(alpha = if (likedTracks.isNotEmpty()) 1f else 0.38f),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .padding(start = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (likedTracks.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No liked songs yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = likedTracks,
                        key = { _, track -> track.id },
                        contentType = { _, _ -> "track" }
                    ) { index, track ->
                        LibraryEntityCard(
                            title = track.title,
                            subtitle = track.artist,
                            imageUrl = track.artwork,
                            isCircular = false,
                            trackId = track.id,
                            trackType = track.trackType,
                            isDeviceLocal = track.isDeviceLocal,
                            isCurrentTrack = track.id == currentTrack?.id,
                            isPlaying = isPlaying,
                            downloadProgress = queueMap[track.id]?.progress?.div(100f),
                            downloadStatus = queueMap[track.id]?.status,
                            onClick = { playerRepository.playTracks(likedTracks, index) },
                            onMoreClick = { actionSheetViewModel.openSheet(com.hypermusic.feature.shared.utils.MediaActionHandler.resolveActionContext(track)) }
                        )
                    }
                }
            }

            val topBarTint = MaterialTheme.colorScheme.onBackground

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
                        tint = topBarTint
                    )
                }

                Text(
                    text = "Liked Songs",
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
