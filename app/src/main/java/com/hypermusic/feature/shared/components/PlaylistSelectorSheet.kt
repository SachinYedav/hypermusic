package com.hypermusic.feature.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.hypermusic.core.designsystem.theme.AccentPurple
import com.hypermusic.core.designsystem.theme.ShapeXs
import com.hypermusic.core.designsystem.theme.ShapeSm
import com.hypermusic.core.designsystem.theme.ShapeMd
import com.hypermusic.core.designsystem.theme.ShapeLg
import com.hypermusic.core.designsystem.theme.ShapeXl
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hypermusic.navigation.NavigationEventBus
import com.hypermusic.MainActivity
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import com.hypermusic.feature.shared.state.ToastAction
import com.hypermusic.feature.shared.state.ToastType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectorSheet(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    localMediaRepository: LocalMediaRepository = koinInject(),
    toastViewModel: GlobalToastViewModel = koinInject(),
    navigationEventBus: NavigationEventBus = koinInject()
) {
    val coroutineScope = rememberCoroutineScope()
    
    val allPlaylists by localMediaRepository.getAllPlaylists().collectAsState(initial = emptyList())
    // Only user-created playlists accept added tracks; saved remote playlists are read-only.
    val userPlaylists = allPlaylists.filter { it.isLocal }
    
    var showCreateDialog by remember { mutableStateOf(false) }

    com.hypermusic.core.designsystem.components.AppFloatingBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = if (tracks.size > 1) "Add ${tracks.size} tracks to Playlist" else "Add to Playlist",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                item(key = "new_playlist", contentType = "action") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCreateDialog = true }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(ShapeXs)
                                .background(AccentPurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Plus,
                                contentDescription = "New Playlist",
                                tint = AccentPurple
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = "New Playlist",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentPurple
                            )
                        )
                    }
                }

                items(
                    items = userPlaylists,
                    key = { it.playlistId },
                    contentType = { "playlist" }
                ) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    val eligibleTracks = tracks.filter { !it.isDeviceOrigin() }
                                    if (eligibleTracks.isEmpty()) {
                                        toastViewModel.showToast("Unsupported for local files", ToastType.INFO)
                                        onDismiss()
                                        return@launch
                                    }
                                    localMediaRepository.addTracksToPlaylist(playlist.playlistId, eligibleTracks)
                                    toastViewModel.showToast(
                                        message = if (eligibleTracks.size > 1) "Added ${eligibleTracks.size} tracks to ${playlist.name}" else "Added to ${playlist.name}",
                                        type = ToastType.LIKED,
                                        action = ToastAction("View") {
                                            navigationEventBus.setLibraryFilter("Playlists")
                                            navigationEventBus.navigateTo("library")
                                        }
                                    )
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MediaArtwork(
                            imageUrl = playlist.artworkUrl,
                            contentDescription = null,
                            trackType = "playlist",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(ShapeXs),
                            contentScale = ContentScale.Crop
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
                
                if (userPlaylists.isEmpty()) {
                    item(key = "empty_playlists", contentType = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "You don't have any custom playlists yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                coroutineScope.launch {
                    val eligibleTracks = tracks.filter { !it.isDeviceOrigin() }
                    if (eligibleTracks.isEmpty()) {
                        toastViewModel.showToast("Unsupported for local files", ToastType.INFO)
                        onDismiss()
                        return@launch
                    }
                    val newId = localMediaRepository.createPlaylist(name, null)
                    localMediaRepository.addTracksToPlaylist(newId, eligibleTracks)
                    toastViewModel.showToast(
                        message = if (eligibleTracks.size > 1) "Created $name with ${eligibleTracks.size} tracks" else "Created $name",
                        type = ToastType.LIKED,
                        action = ToastAction("View") {
                            navigationEventBus.setLibraryFilter("Playlists")
                            navigationEventBus.navigateTo("library")
                        }
                    )
                    onDismiss()
                }
            }
        )
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Playlist",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeMd,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPurple,
                    focusedLabelColor = AccentPurple
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim())
                    }
                },
                shape = ShapeSm
            ) {
                Text(
                    "Create",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = AccentPurple
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ShapeSm
            ) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        shape = ShapeLg,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
