package com.hypermusic.feature.shared.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.components.EmptyStateView
import com.hypermusic.core.designsystem.components.ErrorStateView
import com.hypermusic.core.designsystem.components.MediaGridCard
import com.hypermusic.core.designsystem.components.MediaListItem
import com.hypermusic.core.designsystem.components.ScreenWrapper
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.hypermusic.core.data.repository.DownloadRepository
import com.hypermusic.core.data.repository.DownloadEnqueueResult
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import com.hypermusic.feature.shared.state.ToastType
import com.hypermusic.core.designsystem.components.WaveLoader
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.ArrowLeft
import com.hypermusic.core.designsystem.icon.Music
import com.hypermusic.core.model.Track
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.feature.shared.components.PodcastEpisodeCard
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import com.hypermusic.feature.shared.utils.MediaActionHandler
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CatalogScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCollection: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    viewModel: CatalogViewModel = koinViewModel(),
    playerRepository: PlayerRepository = koinInject(),
    actionSheetViewModel: ActionSheetViewModel = koinInject(),
    downloadRepository: DownloadRepository = koinInject(),
    localMediaRepository: LocalMediaRepository = koinInject(),
    toastViewModel: GlobalToastViewModel = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack by playerRepository.currentTrack.collectAsState()
    val isPlaying by playerRepository.isPlaying.collectAsState()
    val downloadQueue by downloadRepository.downloadQueue.collectAsState()

    val scope = rememberCoroutineScope()
    val downloadedFlow = remember(downloadRepository) { downloadRepository.getAllDownloadedTracks() }
    val downloadedTracks by downloadedFlow.collectAsState(initial = emptyList())
    val downloadedTrackIds = remember(downloadedTracks) { downloadedTracks.map { it.id }.toSet() }

    val savedEpisodesFlow = remember(localMediaRepository) { localMediaRepository.getAllSavedEpisodes() }
    val savedEpisodes by savedEpisodesFlow.collectAsState(initial = emptyList())
    val savedEpisodeIds = remember(savedEpisodes) { savedEpisodes.map { it.episodeId }.toSet() }

    ScreenWrapper {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = AppIcons.ArrowLeft,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!uiState.subtitle.isNullOrBlank()) {
                        Text(
                            text = uiState.subtitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        WaveLoader()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val friendly = uiState.friendlyError
                            ?: com.hypermusic.core.utils.ErrorClassifier.classify(uiState.error)
                        ErrorStateView(
                            error = friendly,
                            onRetry = { viewModel.loadCatalog() }
                        )
                    }
                }
                uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            icon = AppIcons.Music,
                            title = "No items available",
                            subtitle = "There are no tracks or releases listed in this catalog."
                        )
                    }
                }
                uiState.layoutType == CatalogLayoutType.PODCAST_EPISODES -> {
                    val playableTracks = remember(uiState.items) {
                        MediaActionHandler.filterPlayableTracks(uiState.items)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = LocalBottomContentPadding.current
                        )
                    ) {
                        itemsIndexed(
                            items = uiState.items,
                            key = { index, track -> "${track.id}_$index" },
                            contentType = { _, _ -> "podcast_episode" }
                        ) { _, track ->
                            val isEpisodeSaved = track.id in savedEpisodeIds
                            val isDownloaded = track.id in downloadedTrackIds
                            val queueItem = downloadQueue[track.id]
                            val isDownloading = queueItem != null && (queueItem.status == "DOWNLOADING" || queueItem.status == "PENDING")
                            val downloadProgress = if (isDownloading) ((queueItem?.progress ?: 0) / 100f).coerceIn(0f, 1f) else null

                            PodcastEpisodeCard(
                                track = track,
                                isSaved = isEpisodeSaved,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                downloadProgress = downloadProgress,
                                onPlayClick = {
                                    val trackIndex = playableTracks.indexOfFirst { it.id == track.id }
                                    if (trackIndex != -1) {
                                        playerRepository.playTracks(playableTracks, trackIndex)
                                    } else {
                                        playerRepository.playTrack(track)
                                    }
                                },
                                onSaveClick = {
                                    actionSheetViewModel.toggleSaveEpisode(
                                        track = track,
                                        isSaved = isEpisodeSaved,
                                        showId = track.albumId,
                                        showTitle = track.album ?: uiState.title
                                    )
                                },
                                onMoreClick = {
                                    MediaActionHandler.handleTrackMoreClick(
                                        track = track,
                                        actionSheetViewModel = actionSheetViewModel
                                    )
                                },
                                onDownloadClick = {
                                    scope.launch {
                                        val result = downloadRepository.enqueueDownload(track)
                                        val message = when (result) {
                                            DownloadEnqueueResult.WAITING_FOR_WIFI -> "Queued • Waiting for Wi-Fi"
                                            DownloadEnqueueResult.STARTED -> "Download Started"
                                            DownloadEnqueueResult.ALREADY_EXISTS -> "Already Downloaded"
                                            DownloadEnqueueResult.UNSUPPORTED -> "Unsupported for local files"
                                        }
                                        toastViewModel.showToast(message, ToastType.INFO)
                                    }
                                },
                                onRemoveDownloadClick = {
                                    scope.launch {
                                        downloadRepository.removeDownload(track.id)
                                        toastViewModel.showToast("Download removed", ToastType.INFO)
                                    }
                                }
                            )
                        }
                    }
                }
                uiState.layoutType == CatalogLayoutType.TRACK_LIST -> {
                    val playableTracks = remember(uiState.items) {
                        MediaActionHandler.filterPlayableTracks(uiState.items)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = LocalBottomContentPadding.current
                        )
                    ) {
                        itemsIndexed(
                            items = uiState.items,
                            key = { index, track -> "${track.id}_$index" },
                            contentType = { _, _ -> "track" }
                        ) { index, track ->
                            MediaListItem(
                                downloadProgress = downloadQueue[track.id]?.progress?.div(100f),
                                downloadStatus = downloadQueue[track.id]?.status,
                                title = track.title,
                                subtitle = track.artist,
                                imageUrl = track.artwork,
                                onClick = {
                                    val trackIndex = playableTracks.indexOfFirst { it.id == track.id }
                                    if (trackIndex != -1) {
                                        playerRepository.playTracks(playableTracks, trackIndex)
                                    } else {
                                        playerRepository.playTrack(track)
                                    }
                                },
                                onLongClick = {
                                    MediaActionHandler.handleTrackMoreClick(
                                        track = track,
                                        actionSheetViewModel = actionSheetViewModel
                                    )
                                },
                                onMoreClick = {
                                    MediaActionHandler.handleTrackMoreClick(
                                        track = track,
                                        actionSheetViewModel = actionSheetViewModel
                                    )
                                },
                                isCurrentTrack = track.id == currentTrack?.id,
                                isPlaying = isPlaying,
                                leadingContent = {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(28.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = LocalBottomContentPadding.current
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = uiState.items,
                            key = { it.id },
                            contentType = { "catalog_card" }
                        ) { track ->
                            val isArtist = remember(track.trackType) { MediaActionHandler.isArtist(track) }
                            MediaGridCard(
                                downloadProgress = downloadQueue[track.id]?.progress?.div(100f),
                                downloadStatus = downloadQueue[track.id]?.status,
                                title = track.title,
                                subtitle = if (!track.artist.isNullOrBlank()) track.artist else if (isArtist) "Artist" else null,
                                imageUrl = track.artwork,
                                trackType = track.trackType,
                                isCircular = isArtist,
                                isDeviceLocal = track.isDeviceLocal,
                                isCurrentTrack = track.id == currentTrack?.id,
                                isPlaying = isPlaying,
                                onClick = {
                                    MediaActionHandler.handleTrackClick(
                                        track = track,
                                        onNavigateToCollection = onNavigateToCollection,
                                        playerRepository = playerRepository,
                                        shelfItems = uiState.items
                                    )
                                },
                                onLongClick = {
                                    MediaActionHandler.handleTrackMoreClick(
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
