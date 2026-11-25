package com.hypermusic.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.data.repository.DownloadRepository
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.core.designsystem.components.AppConfirmationModal
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import com.hypermusic.core.designsystem.theme.PodcastEpisodesGradient
import com.hypermusic.core.model.Track
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.core.utils.rememberDominantColor
import com.hypermusic.feature.shared.components.PodcastEpisodeCard
import com.hypermusic.feature.shared.state.ActionContext
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import com.hypermusic.feature.shared.state.ToastAction
import com.hypermusic.feature.shared.state.ToastType
import com.hypermusic.navigation.NavigationEventBus
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SavedEpisodesScreen(
    onNavigateBack: () -> Unit,
    localMediaRepository: LocalMediaRepository = koinInject(),
    playerRepository: PlayerRepository = koinInject(),
    actionSheetViewModel: ActionSheetViewModel = koinInject(),
    downloadRepository: DownloadRepository = koinInject(),
    navigationEventBus: NavigationEventBus = koinInject(),
    toastViewModel: GlobalToastViewModel = koinInject()
) {
    val savedEpisodesFlow = remember(localMediaRepository) { localMediaRepository.getAllSavedEpisodes() }
    val savedEpisodes by savedEpisodesFlow.collectAsState(initial = emptyList())
    val downloadedFlow = remember(downloadRepository) { downloadRepository.getAllDownloadedTracks() }
    val downloadedTracks by downloadedFlow.collectAsState(initial = emptyList())
    val downloadedTrackIds = remember(downloadedTracks) { downloadedTracks.map { it.id }.toSet() }
    val queueMap by downloadRepository.downloadQueue.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var episodeToDeleteDownload by remember { mutableStateOf<Track?>(null) }
    episodeToDeleteDownload?.let { targetTrack ->
        AppConfirmationModal(
            title = "Remove Download?",
            message = "Are you sure you want to delete this downloaded episode from your device storage?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                episodeToDeleteDownload = null
                scope.launch {
                    downloadRepository.removeDownload(targetTrack.id)
                    toastViewModel.showToast("Episode download removed", ToastType.INFO)
                }
            },
            onDismiss = { episodeToDeleteDownload = null }
        )
    }

    val density = LocalDensity.current
    val topSafeArea = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val minHeaderHeight = topSafeArea + 56.dp

    val episodeTracks = remember(savedEpisodes) {
        savedEpisodes.map { ep ->
            Track(
                id = ep.episodeId,
                title = ep.title,
                artist = ep.author,
                artwork = ep.artworkUrl,
                duration = ep.duration,
                trackType = "podcast_episode",
                description = ep.description,
                releaseDate = ep.releaseDate,
                album = ep.showTitle,
                albumId = ep.showId
            )
        }
    }

    val downloadedCount = remember(episodeTracks, downloadedTrackIds) { episodeTracks.count { it.id in downloadedTrackIds } }
    val isAllDownloaded = episodeTracks.isNotEmpty() && downloadedCount == episodeTracks.size
    val hasActiveDownload = remember(episodeTracks, queueMap) { episodeTracks.any { queueMap[it.id]?.status == "DOWNLOADING" } }
    val hasPendingDownload = remember(episodeTracks, queueMap) { episodeTracks.any { queueMap[it.id]?.status == "PENDING" } }
    val isDownloadingOrQueued = hasActiveDownload || hasPendingDownload

    val totalDurationSec = remember(savedEpisodes) { savedEpisodes.sumOf { it.duration } }
    val durationText = remember(totalDurationSec) {
        if (totalDurationSec <= 0L) ""
        else {
            val hrs = totalDurationSec / 3600
            val mins = (totalDurationSec % 3600) / 60
            if (hrs > 0) "$hrs hr $mins min" else "$mins min"
        }
    }
    val subtitleText = remember(savedEpisodes.size, durationText) {
        val count = "${savedEpisodes.size} ${if (savedEpisodes.size == 1) "episode" else "episodes"}"
        if (durationText.isNotBlank()) "$count • $durationText" else count
    }

    val context = LocalContext.current
    val firstEpisodeArtwork = savedEpisodes.firstOrNull()?.artworkUrl
    val dominantColor = rememberDominantColor(context, firstEpisodeArtwork, Color(0xFF8B5CF6))

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
                item(key = "saved_episodes_header") {
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
                                    .background(brush = Brush.linearGradient(colors = PodcastEpisodesGradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.BookmarkFilled,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Episodes for later",
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
                            text = "Episodes you save for later are collected here.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (downloadedCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isAllDownloaded) "All downloaded" else "$downloadedCount of ${episodeTracks.size} downloaded",
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
                                    if (episodeTracks.isNotEmpty()) {
                                        scope.launch {
                                            val notDownloaded = episodeTracks.filter { it.id !in downloadedTrackIds }
                                            val queueItems = queueMap
                                            val toEnqueue = notDownloaded.filter { it.id !in queueItems }
                                            val toResume = notDownloaded.mapNotNull { track ->
                                                queueItems[track.id]?.takeIf { it.status == "PAUSED" || it.status == "FAILED" }?.trackId
                                            }
                                            if (notDownloaded.isEmpty()) {
                                                toastViewModel.showToast(
                                                    message = "All episodes are downloaded and available offline",
                                                    type = ToastType.INFO
                                                )
                                                return@launch
                                            }
                                            if (toEnqueue.isEmpty() && toResume.isEmpty()) {
                                                toastViewModel.showToast(
                                                    message = "Episodes already in download queue",
                                                    type = ToastType.INFO,
                                                    action = ToastAction("View") {
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
                                                "Queued $count episodes • Waiting for Wi-Fi"
                                            } else {
                                                "Queued $count episodes for download"
                                            }
                                            toastViewModel.showToast(
                                                message = message,
                                                type = ToastType.INFO,
                                                action = ToastAction("View") {
                                                    navigationEventBus.navigateTo("downloads")
                                                }
                                            )
                                        }
                                    }
                                },
                                enabled = episodeTracks.isNotEmpty(),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (episodeTracks.isNotEmpty()) 0.6f else 0.3f))
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
                                        episodeTracks.isNotEmpty() -> MaterialTheme.colorScheme.onBackground
                                        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = {
                                    if (episodeTracks.isNotEmpty()) {
                                        playerRepository.toggleShuffle()
                                        playerRepository.playTracks(episodeTracks, 0)
                                    }
                                },
                                enabled = episodeTracks.isNotEmpty(),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (episodeTracks.isNotEmpty()) 1f else 0.38f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .shadow(if (episodeTracks.isNotEmpty()) 6.dp else 0.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(if (episodeTracks.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.38f))
                                    .clickable(enabled = episodeTracks.isNotEmpty()) {
                                        playerRepository.playTracks(episodeTracks, 0)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Play,
                                    contentDescription = "Play",
                                    tint = Color.Black.copy(alpha = if (episodeTracks.isNotEmpty()) 1f else 0.38f),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .padding(start = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (savedEpisodes.isEmpty()) {
                    item(key = "empty_content") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No saved episodes yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = episodeTracks,
                        key = { _, track -> track.id },
                        contentType = { _, _ -> "podcast_episode" }
                    ) { index, epTrack ->
                        val isDownloaded = epTrack.id in downloadedTrackIds
                        val queueItem = queueMap[epTrack.id]
                        val isDownloading = queueItem != null && (queueItem.status == "DOWNLOADING" || queueItem.status == "PENDING")
                        val downloadProgress = if (isDownloading) ((queueItem?.progress ?: 0) / 100f).coerceIn(0f, 1f) else null

                        PodcastEpisodeCard(
                            track = epTrack,
                            isSaved = true,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            downloadProgress = downloadProgress,
                            onPlayClick = {
                                playerRepository.playTracks(episodeTracks, index)
                            },
                            onSaveClick = {
                                scope.launch {
                                    localMediaRepository.unsaveEpisodeForLater(epTrack.id)
                                }
                            },
                            onMoreClick = {
                                val ep = savedEpisodes.getOrNull(index)
                                actionSheetViewModel.openSheet(
                                    ActionContext.PodcastEpisodeContext(
                                        track = epTrack,
                                        showId = ep?.showId,
                                        showTitle = ep?.showTitle
                                    )
                                )
                            },
                            onDownloadClick = {
                                scope.launch {
                                    val result = downloadRepository.enqueueDownload(epTrack)
                                    when (result) {
                                        com.hypermusic.core.data.repository.DownloadEnqueueResult.UNSUPPORTED -> {
                                            toastViewModel.showToast("Unsupported for local files", ToastType.INFO)
                                        }
                                        com.hypermusic.core.data.repository.DownloadEnqueueResult.ALREADY_EXISTS -> {
                                            toastViewModel.showToast("Already Downloaded", ToastType.INFO)
                                        }
                                        com.hypermusic.core.data.repository.DownloadEnqueueResult.WAITING_FOR_WIFI,
                                        com.hypermusic.core.data.repository.DownloadEnqueueResult.STARTED -> {
                                            val msg = if (result == com.hypermusic.core.data.repository.DownloadEnqueueResult.WAITING_FOR_WIFI) {
                                                "Queued • Waiting for Wi-Fi"
                                            } else {
                                                "Download Started"
                                            }
                                            toastViewModel.showToast(
                                                message = msg,
                                                type = ToastType.INFO,
                                                action = ToastAction("View") {
                                                    navigationEventBus.navigateTo("downloads")
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            onRemoveDownloadClick = {
                                episodeToDeleteDownload = epTrack
                            }
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
                    text = "Episodes for later",
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
