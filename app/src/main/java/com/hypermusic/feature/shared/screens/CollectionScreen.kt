package com.hypermusic.feature.shared.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.feature.search.components.TrackResultCard
import com.hypermusic.feature.shared.components.CollectionDetailsTemplate
import com.hypermusic.feature.shared.components.CollectionDownloadUiState
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.feature.shared.state.ActionContext
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import com.hypermusic.feature.shared.state.ToastType
import com.hypermusic.navigation.NavigationEventBus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun CollectionScreen(
    type: String, // "album", "playlist", "podcast"
    onNavigateBack: () -> Unit,
    onNavigateToCollection: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    viewModel: CollectionViewModel = koinViewModel(),
    playerRepository: PlayerRepository = koinInject(),
    actionSheetViewModel: ActionSheetViewModel = koinInject(),
    downloadRepository: com.hypermusic.core.data.repository.DownloadRepository = koinInject(),
    toastViewModel: GlobalToastViewModel = koinInject(),
    navigationEventBus: NavigationEventBus = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack by playerRepository.currentTrack.collectAsState()
    val isPlaying by playerRepository.isPlaying.collectAsState()
    
    val collectionDetails = uiState.details
    
    val isLoading = uiState is CollectionUiState.Loading
    val errorState = uiState as? CollectionUiState.Error
    val error = errorState?.message
    val friendlyError = errorState?.friendlyError

    val isSaved by viewModel.isSaved.collectAsState()
    val savedEpisodeIds by viewModel.savedEpisodeIds.collectAsState()
    var isDownloadEnqueuing by remember(collectionDetails?.id) { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    val downloadedFlow = remember(downloadRepository) { downloadRepository.getAllDownloadedTracks() }
    val downloadedTracks by downloadedFlow.collectAsState(initial = emptyList())
    val downloadedTrackIds = remember(downloadedTracks) { downloadedTracks.map { it.id }.toSet() }
    val queueFlow = remember(downloadRepository) { downloadRepository.getQueueItemsWithTracks() }
    val queueMap by downloadRepository.downloadQueue.collectAsState()
    val downloadState = remember(collectionDetails?.tracks, downloadedTrackIds, queueMap) {
        val tracks = collectionDetails?.tracks.orEmpty()
        val targets = tracks.filterNot { it.isDeviceOrigin() }.distinctBy { it.id }
        val completed = targets.count { it.id in downloadedTrackIds }
        val queued = targets.filterNot { it.id in downloadedTrackIds }.mapNotNull { queueMap[it.id] }
        val complete = tracks.isNotEmpty() && completed == targets.size
        val status = when {
            queued.any { it.status == "DOWNLOADING" } -> "DOWNLOADING"
            queued.any { it.status == "PENDING" } -> "PENDING"
            queued.any { it.status == "FAILED" } -> "FAILED"
            queued.any { it.status == "PAUSED" } -> "PAUSED"
            else -> null
        }
        // Track-weighted progress; finalization, not a transfer reaching 100%, marks completion.
        val progress = if (queued.isNotEmpty() && targets.isNotEmpty()) {
            ((completed + queued.sumOf { it.progress.coerceIn(0, 100).toDouble() / 100 }) / targets.size)
                .toFloat().coerceIn(0f, 0.99f)
        } else null
        val label = when {
            complete -> "Available offline"
            status == "DOWNLOADING" -> "Downloading • $completed/${targets.size} offline • ${((progress ?: 0f) * 100).toInt()}%"
            status == "PENDING" -> "Queued • $completed/${targets.size} offline"
            status == "FAILED" -> "Download failed • Tap to retry • $completed/${targets.size} offline"
            status == "PAUSED" -> "Downloads paused • Tap to resume • $completed/${targets.size} offline"
            completed > 0 -> "$completed/${targets.size} available offline"
            else -> null
        }
        CollectionDownloadUiState(complete, status, progress, label)
    }
    var episodeToDeleteDownload by remember { mutableStateOf<Track?>(null) }

    episodeToDeleteDownload?.let { targetTrack ->
        com.hypermusic.core.designsystem.components.AppConfirmationModal(
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

    CollectionDetailsTemplate(
        collectionDetails = collectionDetails,
        isLoading = isLoading,
        error = error,
        friendlyError = friendlyError,
        isSaved = isSaved,
        onToggleSave = { viewModel.toggleSave() },
        type = type,
        onRetry = { viewModel.loadCollection() },
        onNavigateBack = onNavigateBack,
        onNavigateToCollection = onNavigateToCollection,
        onArtistClick = {
            collectionDetails?.let { details ->
                val artists = details.artists ?: if (!details.artistId.isNullOrBlank()) listOf(com.hypermusic.core.model.ArtistInfo(details.artistId, details.subtitle)) else emptyList()
                when {
                    artists.size == 1 -> {
                        val artist = artists.first()
                        onNavigateToCollection("artist", artist.id, artist.name, null, null)
                    }
                    artists.size > 1 -> {
                        actionSheetViewModel.openArtistSelector(artists)
                    }
                    else -> {
                        toastViewModel.showToast("Artist information not available", ToastType.INFO)
                    }
                }
            }
        },
        onShelfTrackClick = { track ->
            playerRepository.playTrack(track)
        },
        onShelfMoreClick = { track ->
            com.hypermusic.feature.shared.utils.MediaActionHandler.handleTrackMoreClick(
                track = track,
                actionSheetViewModel = actionSheetViewModel
            )
        },
        onShelfPlayAll = { tracks ->
            val playableTracks = com.hypermusic.feature.shared.utils.MediaActionHandler.filterPlayableTracks(tracks)
            if (playableTracks.isNotEmpty()) {
                playerRepository.playTracks(playableTracks)
            }
        },
        onPlayAll = {
            collectionDetails?.tracks?.let { tracks ->
                if (tracks.isNotEmpty()) {
                    playerRepository.playTracks(tracks, 0)
                }
            }
        },
        onShufflePlay = {
            collectionDetails?.tracks?.let { tracks ->
                if (tracks.isNotEmpty()) {
                    playerRepository.playTracksShuffled(tracks)
                }
            }
        },
        onMoreOptions = {
            collectionDetails?.let { details ->
                val context = when (type) {
                    "album" -> ActionContext.AlbumContext(details.id, details.title, details.subtitle, details.artworkUrl)
                    "playlist" -> ActionContext.PlaylistContext(details.id, details.title, details.artworkUrl)
                    "podcast" -> ActionContext.PodcastContext(details.id, details.title, details.subtitle, details.artworkUrl)
                    else -> null
                }
                context?.let { actionSheetViewModel.openSheet(it) }
            }
        },
        onDownloadAll = {
            val tracks = collectionDetails?.tracks
            if (tracks.isNullOrEmpty() || isDownloadEnqueuing) return@CollectionDetailsTemplate
            isDownloadEnqueuing = true
            scope.launch {
                try {
                    val downloadedIds = downloadRepository.getAllDownloadedTrackIds().toSet()
                    val notDownloaded = tracks.filter { it.id !in downloadedIds && !it.isDeviceOrigin() }.distinctBy { it.id }
                    if (notDownloaded.isEmpty()) {
                        toastViewModel.showToast("All tracks already downloaded", ToastType.INFO)
                        return@launch
                    }
                    val queueItems = queueFlow.first().associate { it.queueEntity.trackId to it.queueEntity }
                    val toEnqueue = notDownloaded.filter { it.id !in queueItems }
                    val toResume = notDownloaded.mapNotNull { track ->
                        queueItems[track.id]?.takeIf { it.status == "PAUSED" || it.status == "FAILED" }?.trackId
                    }
                    if (toEnqueue.isEmpty() && toResume.isEmpty()) {
                        toastViewModel.showToast(
                            message = "Tracks already in download queue",
                            type = ToastType.INFO,
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
                        "Queued $count tracks • Waiting for Wi-Fi"
                    } else {
                        "Queued $count tracks for download"
                    }
                    toastViewModel.showToast(
                        message = message,
                        type = ToastType.INFO,
                        action = com.hypermusic.feature.shared.state.ToastAction("View") {
                            navigationEventBus.navigateTo("downloads")
                        }
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    toastViewModel.showToast("Couldn't queue downloads. Please try again.", ToastType.ERROR)
                } finally {
                    isDownloadEnqueuing = false
                }
            }
        },
        downloadState = downloadState,
        isDownloadEnqueuing = isDownloadEnqueuing,
        itemContent = { index, track ->
            if (type == "podcast") {
                val isEpisodeSaved = track.id in savedEpisodeIds
                val isDownloaded = track.id in downloadedTrackIds
                val queueItem = queueMap[track.id]
                val isDownloading = queueItem != null && (queueItem.status == "DOWNLOADING" || queueItem.status == "PENDING")
                val downloadProgress = if (isDownloading) ((queueItem?.progress ?: 0) / 100f).coerceIn(0f, 1f) else null

                com.hypermusic.feature.shared.components.PodcastEpisodeCard(
                    track = track,
                    isSaved = isEpisodeSaved,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    onPlayClick = {
                        collectionDetails?.tracks?.let { tracks ->
                            playerRepository.playTracks(tracks, index)
                        } ?: playerRepository.playTrack(track)
                    },
                    onSaveClick = {
                        viewModel.toggleSaveEpisode(track, collectionDetails?.title)
                    },
                    onMoreClick = {
                        actionSheetViewModel.openSheet(
                            ActionContext.PodcastEpisodeContext(
                                track = track,
                                showId = collectionDetails?.id,
                                showTitle = collectionDetails?.title
                            )
                        )
                    },
                    onDownloadClick = {
                        scope.launch {
                            val result = downloadRepository.enqueueDownload(track)
                            when (result) {
                                com.hypermusic.core.data.repository.DownloadEnqueueResult.UNSUPPORTED -> {
                                    toastViewModel.showToast("Unsupported for local files", ToastType.INFO)
                                }
                                com.hypermusic.core.data.repository.DownloadEnqueueResult.ALREADY_EXISTS -> {
                                    toastViewModel.showToast("Already Downloaded", ToastType.INFO)
                                }
                                com.hypermusic.core.data.repository.DownloadEnqueueResult.WAITING_FOR_WIFI,
                                com.hypermusic.core.data.repository.DownloadEnqueueResult.STARTED -> {
                                    val message = if (result == com.hypermusic.core.data.repository.DownloadEnqueueResult.WAITING_FOR_WIFI) {
                                        "Queued • Waiting for Wi-Fi"
                                    } else {
                                        "Download Started"
                                    }
                                    toastViewModel.showToast(
                                        message = message,
                                        type = ToastType.INFO,
                                        action = com.hypermusic.feature.shared.state.ToastAction("View") {
                                            navigationEventBus.navigateTo("downloads")
                                        }
                                    )
                                }
                            }
                        }
                    },
                    onRemoveDownloadClick = {
                        episodeToDeleteDownload = track
                    }
                )
            } else {
                TrackResultCard(
                    track = track,
                    downloadProgress = queueMap[track.id]?.progress?.div(100f),
                    downloadStatus = queueMap[track.id]?.status,
                    isCurrentTrack = track.id == currentTrack?.id,
                    isPlaying = isPlaying,
                    onClick = { 
                        collectionDetails?.tracks?.let { tracks ->
                            playerRepository.playTracks(tracks, index)
                        } ?: playerRepository.playTrack(track)
                    },
                    onMoreClick = {
                        if (type == "playlist" && isSaved) {
                            actionSheetViewModel.openSheet(ActionContext.PlaylistTrackContext(collectionDetails?.id ?: "", track, true))
                        } else {
                            actionSheetViewModel.openSheet(ActionContext.TrackContext(track))
                        }
                    }
                )
            }
        }
    )
}
