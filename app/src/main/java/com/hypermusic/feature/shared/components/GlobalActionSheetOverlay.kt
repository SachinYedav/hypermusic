package com.hypermusic.feature.shared.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hypermusic.navigation.navigateToArtist
import com.hypermusic.navigation.navigateToCollection
import com.hypermusic.core.designsystem.components.AppFloatingBottomSheet
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.core.utils.ShareHelper
import com.hypermusic.core.utils.ShareType
import com.hypermusic.feature.shared.state.ActionContext
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import com.hypermusic.feature.shared.utils.MediaActionHandler
import com.hypermusic.core.designsystem.theme.AccentPurple
import com.hypermusic.core.designsystem.theme.ShapeXs
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalActionSheetOverlay(
    actionSheetViewModel: ActionSheetViewModel = koinInject(),
    navController: NavController
) {
    val currentContext by actionSheetViewModel.currentContext.collectAsState()
    val context = LocalContext.current

    if (currentContext != null) {
        AppFloatingBottomSheet(
            onDismissRequest = { actionSheetViewModel.closeSheet() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                ActionSheetHeader(context = currentContext!!)

                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )

                LazyColumn {
                    when (val ctx = currentContext) {
                        is ActionContext.TrackContext,
                        is ActionContext.HistoryTrackContext,
                        is ActionContext.DownloadTrackContext,
                        is ActionContext.PlaylistTrackContext -> {
                            val track = when (ctx) {
                                is ActionContext.TrackContext -> ctx.track
                                is ActionContext.HistoryTrackContext -> ctx.track
                                is ActionContext.DownloadTrackContext -> ctx.track
                                is ActionContext.PlaylistTrackContext -> ctx.track
                                else -> return@LazyColumn
                            }

                            // Scope removal actions to the supplied ActionContext.
                            when (ctx) {
                                is ActionContext.HistoryTrackContext -> {
                                    item {
                                        ActionRow(
                                            icon = AppIcons.Trash2,
                                            label = "Remove from History",
                                            iconColor = MaterialTheme.colorScheme.error,
                                            textColor = MaterialTheme.colorScheme.error,
                                            onClick = { actionSheetViewModel.removeFromHistory(track.id) }
                                        )
                                    }
                                }
                                is ActionContext.DownloadTrackContext -> {
                                    item {
                                        ActionRow(
                                            icon = AppIcons.Trash2,
                                            label = "Remove Download",
                                            iconColor = MaterialTheme.colorScheme.error,
                                            textColor = MaterialTheme.colorScheme.error,
                                            onClick = { actionSheetViewModel.removeDownload(track.id) }
                                        )
                                    }
                                }
                                is ActionContext.PlaylistTrackContext -> {
                                    if (ctx.isLocalPlaylist) {
                                        item {
                                            ActionRow(
                                                icon = AppIcons.Trash2,
                                                label = "Remove from Playlist",
                                                iconColor = MaterialTheme.colorScheme.error,
                                                textColor = MaterialTheme.colorScheme.error,
                                                onClick = { actionSheetViewModel.removeFromPlaylist(ctx.playlistId, track.id) }
                                            )
                                        }
                                    }
                                }
                                else -> {}
                            }

                            val isDownloadContext = ctx is ActionContext.DownloadTrackContext
                            when {
                                track.isDeviceOrigin() -> {
                                    renderDeviceLocalTrackActions(
                                        track = track,
                                        viewModel = actionSheetViewModel
                                    )
                                }
                                MediaActionHandler.isPodcastEpisode(track) -> {
                                    renderPodcastEpisodeActions(
                                        track = track,
                                        showId = track.albumId,
                                        showTitle = track.album,
                                        showDownload = !isDownloadContext,
                                        viewModel = actionSheetViewModel,
                                        navController = navController,
                                        context = context
                                    )
                                }
                                else -> {
                                    renderTrackActions(
                                        track = track,
                                        showDownload = !isDownloadContext,
                                        viewModel = actionSheetViewModel,
                                        navController = navController,
                                        context = context
                                    )
                                }
                            }
                        }
                        is ActionContext.DeviceLocalTrackContext -> {
                            renderDeviceLocalTrackActions(
                                track = ctx.track,
                                viewModel = actionSheetViewModel
                            )
                        }
                        is ActionContext.PodcastEpisodeContext -> {
                            renderPodcastEpisodeActions(
                                track = ctx.track,
                                showId = ctx.showId ?: ctx.track.albumId,
                                showTitle = ctx.showTitle ?: ctx.track.album,
                                showDownload = true,
                                viewModel = actionSheetViewModel,
                                navController = navController,
                                context = context
                            )
                        }
                        is ActionContext.AlbumContext -> {
                            renderContainerActions(
                                id = ctx.id,
                                title = ctx.title,
                                isAlbum = true,
                                viewModel = actionSheetViewModel,
                                context = context
                            )
                        }
                        is ActionContext.PlaylistContext -> {
                            renderContainerActions(
                                id = ctx.id,
                                title = ctx.title,
                                isAlbum = false,
                                viewModel = actionSheetViewModel,
                                context = context
                            )
                        }
                        is ActionContext.PodcastContext -> {
                            renderPodcastShowActions(
                                id = ctx.id,
                                title = ctx.title,
                                author = ctx.author,
                                artworkUrl = ctx.artworkUrl,
                                viewModel = actionSheetViewModel,
                                context = context
                            )
                        }
                        is ActionContext.ArtistContext -> {
                            renderArtistActions(
                                id = ctx.id,
                                name = ctx.name,
                                avatarUrl = ctx.avatarUrl,
                                viewModel = actionSheetViewModel,
                                navController = navController,
                                context = context
                            )
                        }
                        null -> {}
                    }
                }
            }
        }
    }

    val playlistSelectorTracks by actionSheetViewModel.playlistSelectorTracks.collectAsState()
    if (playlistSelectorTracks != null) {
        PlaylistSelectorSheet(
            tracks = playlistSelectorTracks!!,
            onDismiss = { actionSheetViewModel.closePlaylistSelector() }
        )
    }
}


private fun LazyListScope.renderTrackActions(
    track: Track,
    showDownload: Boolean,
    viewModel: ActionSheetViewModel,
    navController: NavController,
    context: Context
) {
    item {
        ActionRow(icon = AppIcons.PlayNext, label = "Play Next", onClick = { viewModel.playNext(track) })
    }
    item {
        ActionRow(icon = AppIcons.AddToQueue, label = "Add to Queue", onClick = { viewModel.appendToQueue(track) })
    }
    item {
        ActionRow(icon = AppIcons.AddToPlaylist, label = "Add to Playlist", onClick = {
            viewModel.openPlaylistSelector(listOf(track))
        })
    }
    item {
        val isLiked by viewModel.isTrackLikedFlow(track.id).collectAsState(initial = false)
        ActionRow(
            icon = if (isLiked) AppIcons.HeartFilled else AppIcons.Heart,
            label = if (isLiked) "Liked" else "Like",
            iconColor = if (isLiked) AccentPurple else MaterialTheme.colorScheme.onSurface,
            onClick = { viewModel.toggleLike(track, isLiked) }
        )
    }
    if (showDownload) {
        item {
            ActionRow(icon = AppIcons.ArrowDownToLine, label = "Download", onClick = { viewModel.downloadTrack(track) })
        }
    }
    item {
        ActionRow(
            icon = AppIcons.Disc3,
            label = "Go to Album",
            onClick = {
                viewModel.closeSheet()
                if (!track.albumId.isNullOrEmpty()) {
                    navController.navigateToCollection("album", track.albumId, track.album, track.artist, track.artwork)
                }
            }
        )
    }
    item {
        ActionRow(
            icon = AppIcons.User,
            label = "Go to Artist",
            onClick = {
                viewModel.closeSheet()
                if (!track.artists.isNullOrEmpty()) {
                    if (track.artists.size == 1) {
                        navController.navigateToArtist(track.artists[0].id)
                    } else {
                        viewModel.openArtistSelector(track.artists)
                    }
                }
            }
        )
    }
    item {
        ActionRow(icon = AppIcons.Share2, label = "Share", onClick = {
            ShareHelper.shareContent(context, ShareType.TRACK, track.id, track.title)
            viewModel.closeSheet()
        })
    }
}

private fun LazyListScope.renderDeviceLocalTrackActions(
    track: Track,
    viewModel: ActionSheetViewModel
) {
    item {
        ActionRow(icon = AppIcons.PlayNext, label = "Play Next", onClick = { viewModel.playNext(track) })
    }
    item {
        ActionRow(icon = AppIcons.AddToQueue, label = "Add to Queue", onClick = { viewModel.appendToQueue(track) })
    }
    item {
        val isLiked by viewModel.isTrackLikedFlow(track.id).collectAsState(initial = false)
        if (isLiked) {
            ActionRow(
                icon = AppIcons.HeartFilled,
                label = "Remove from Liked Songs",
                iconColor = AccentPurple,
                onClick = { viewModel.toggleLike(track, isLiked) }
            )
        }
    }
}

private fun LazyListScope.renderPodcastEpisodeActions(
    track: Track,
    showId: String?,
    showTitle: String?,
    showDownload: Boolean,
    viewModel: ActionSheetViewModel,
    navController: NavController,
    context: Context
) {
    item {
        ActionRow(icon = AppIcons.Play, label = "Play Episode", onClick = { viewModel.playTrack(track) })
    }
    item {
        ActionRow(icon = AppIcons.PlayNext, label = "Play Next", onClick = { viewModel.playNext(track, isEpisode = true) })
    }
    item {
        ActionRow(icon = AppIcons.AddToQueue, label = "Add to Queue", onClick = { viewModel.appendToQueue(track, isEpisode = true) })
    }
    item {
        val isSaved by viewModel.isEpisodeSavedFlow(track.id).collectAsState(initial = false)
        ActionRow(
            icon = if (isSaved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
            label = if (isSaved) "Remove from Saved Episodes" else "Save Episode for Later",
            iconColor = if (isSaved) AccentPurple else MaterialTheme.colorScheme.onSurface,
            onClick = { viewModel.toggleSaveEpisode(track, isSaved, showId, showTitle) }
        )
    }
    val targetShowId = showId ?: track.albumId
    if (!targetShowId.isNullOrBlank()) {
        item {
            ActionRow(icon = AppIcons.Podcast, label = "Go to Podcast Show", onClick = {
                viewModel.closeSheet()
                navController.navigateToCollection("podcast", targetShowId, showTitle ?: track.album, null, track.artwork)
            })
        }
    }
    if (showDownload) {
        item {
            ActionRow(icon = AppIcons.ArrowDownToLine, label = "Download Episode", onClick = { viewModel.downloadTrack(track) })
        }
    }
    item {
        ActionRow(icon = AppIcons.Share2, label = "Share Episode", onClick = {
            ShareHelper.shareContent(context, ShareType.PODCAST_EPISODE, track.id, track.title)
            viewModel.closeSheet()
        })
    }
}

private fun LazyListScope.renderContainerActions(
    id: String,
    title: String,
    isAlbum: Boolean,
    viewModel: ActionSheetViewModel,
    context: Context
) {
    val typeName = if (isAlbum) "Album" else "Playlist"
    val shareType = if (isAlbum) ShareType.ALBUM else ShareType.PLAYLIST

    item {
        ActionRow(icon = AppIcons.Play, label = "Play", onClick = {
            viewModel.playContainer(id, isAlbum)
        })
    }
    item {
        ActionRow(icon = AppIcons.Shuffle, label = "Shuffle Play", onClick = {
            viewModel.playContainer(id, isAlbum, shuffle = true)
        })
    }
    item {
        ActionRow(icon = AppIcons.AddToQueue, label = "Add to Queue", onClick = {
            viewModel.playContainer(id, isAlbum, appendToQueue = true)
        })
    }
    item {
        val isSaved by if (isAlbum) {
            viewModel.isAlbumSavedFlow(id).collectAsState(initial = false)
        } else {
            viewModel.isPlaylistSavedFlow(id).collectAsState(initial = false)
        }
        ActionRow(
            icon = if (isSaved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
            label = if (isSaved) "Remove from Library" else "Save $typeName",
            iconColor = if (isSaved) AccentPurple else MaterialTheme.colorScheme.onSurface,
            onClick = {
                if (isAlbum) viewModel.toggleSaveAlbum(id, isSaved)
                else viewModel.toggleSavePlaylist(id, isSaved)
            }
        )
    }
    item {
        ActionRow(icon = AppIcons.ArrowDownToLine, label = "Download", onClick = {
            viewModel.downloadContainer(id, isAlbum)
        })
    }
    item {
        ActionRow(icon = AppIcons.Share2, label = "Share", onClick = {
            ShareHelper.shareContent(context, shareType, id, title)
            viewModel.closeSheet()
        })
    }
}

private fun LazyListScope.renderPodcastShowActions(
    id: String,
    title: String,
    author: String,
    artworkUrl: String?,
    viewModel: ActionSheetViewModel,
    context: Context
) {
    item {
        ActionRow(icon = AppIcons.Play, label = "Play", onClick = {
            viewModel.playPodcastShow(id)
        })
    }
    item {
        ActionRow(icon = AppIcons.PlayNext, label = "Play Next", onClick = {
            viewModel.queuePodcastShowNext(id)
        })
    }
    item {
        val isSaved by viewModel.isPodcastSavedFlow(id).collectAsState(initial = false)
        ActionRow(
            icon = if (isSaved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
            label = if (isSaved) "Remove from Library" else "Save Podcast",
            iconColor = if (isSaved) AccentPurple else MaterialTheme.colorScheme.onSurface,
            onClick = {
                viewModel.toggleSavePodcastShow(id, isSaved, title, author, artworkUrl)
            }
        )
    }
    item {
        ActionRow(icon = AppIcons.Share2, label = "Share", onClick = {
            ShareHelper.shareContent(context, ShareType.PODCAST, id, title)
            viewModel.closeSheet()
        })
    }
}

private fun LazyListScope.renderArtistActions(
    id: String,
    name: String,
    avatarUrl: String?,
    viewModel: ActionSheetViewModel,
    navController: NavController,
    context: Context
) {
    item {
        ActionRow(icon = AppIcons.User, label = "Go to Profile", onClick = {
            viewModel.closeSheet()
            navController.navigateToArtist(id)
        })
    }
    item {
        var isSaved by remember(id) { mutableStateOf(false) }
        LaunchedEffect(id) {
            isSaved = viewModel.isArtistSaved(id)
        }
        ActionRow(
            icon = if (isSaved) AppIcons.UserCheck else AppIcons.UserPlus,
            label = if (isSaved) "Unfollow" else "Follow",
            iconColor = if (isSaved) AccentPurple else MaterialTheme.colorScheme.onSurface,
            onClick = {
                viewModel.toggleFollowArtist(id, name, avatarUrl, isSaved)
            }
        )
    }
    item {
        ActionRow(icon = AppIcons.Share2, label = "Share", onClick = {
            ShareHelper.shareContent(context, ShareType.ARTIST, id, name)
            viewModel.closeSheet()
        })
    }
}


@Composable
fun ActionSheetHeader(context: ActionContext) {
    val title: String
    val subtitle: String
    val artworkUrl: String?
    val isCircular: Boolean
    val trackType: String?
    val isDeviceLocal: Boolean

    when (context) {
        is ActionContext.TrackContext,
        is ActionContext.DeviceLocalTrackContext,
        is ActionContext.HistoryTrackContext,
        is ActionContext.DownloadTrackContext,
        is ActionContext.PlaylistTrackContext -> {
            val track = when (context) {
                is ActionContext.TrackContext -> context.track
                is ActionContext.DeviceLocalTrackContext -> context.track
                is ActionContext.HistoryTrackContext -> context.track
                is ActionContext.DownloadTrackContext -> context.track
                is ActionContext.PlaylistTrackContext -> context.track
                else -> return
            }
            title = track.title
            subtitle = if (MediaActionHandler.isPodcastEpisode(track)) {
                if (!track.album.isNullOrBlank()) "Episode • ${track.album}"
                else if (!track.artist.isNullOrBlank()) "Episode • ${track.artist}"
                else "Podcast Episode"
            } else {
                track.artist
            }
            artworkUrl = track.artwork
            isCircular = false
            trackType = track.trackType
            isDeviceLocal = track.isDeviceOrigin()
        }
        is ActionContext.AlbumContext -> {
            title = context.title
            val cleanArtist = context.artist?.removePrefix("Album • ")?.removePrefix("Album •")?.trim() ?: ""
            subtitle = if (cleanArtist.isNotEmpty()) "Album • $cleanArtist" else "Album"
            artworkUrl = context.artworkUrl
            isCircular = false
            trackType = "album"
            isDeviceLocal = false
        }
        is ActionContext.PlaylistContext -> {
            title = context.title
            subtitle = "Playlist"
            artworkUrl = context.artworkUrl
            isCircular = false
            trackType = "playlist"
            isDeviceLocal = false
        }
        is ActionContext.ArtistContext -> {
            title = context.name
            subtitle = "Artist"
            artworkUrl = context.avatarUrl
            isCircular = true
            trackType = "artist"
            isDeviceLocal = false
        }
        is ActionContext.PodcastContext -> {
            title = context.title
            val cleanAuthor = context.author?.removePrefix("Podcast • ")?.removePrefix("Podcast •")?.trim() ?: ""
            subtitle = if (cleanAuthor.isNotEmpty()) "Podcast • $cleanAuthor" else "Podcast"
            artworkUrl = context.artworkUrl
            isCircular = false
            trackType = "podcast"
            isDeviceLocal = false
        }
        is ActionContext.PodcastEpisodeContext -> {
            title = context.track.title
            val rawShow = context.showTitle?.ifBlank { null } ?: context.track.artist?.ifBlank { null }
            val cleanShow = rawShow?.removePrefix("Episode • ")?.removePrefix("Episode •")?.trim()
            subtitle = if (!cleanShow.isNullOrBlank()) {
                "Episode • $cleanShow"
            } else {
                "Podcast Episode"
            }
            artworkUrl = context.track.artwork
            isCircular = false
            trackType = "podcast_episode"
            isDeviceLocal = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = if (isCircular) CircleShape else ShapeXs
        MediaArtwork(
            imageUrl = artworkUrl,
            contentDescription = null,
            trackType = trackType,
            isDeviceLocal = isDeviceLocal,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onSurface,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        )
    }
}
