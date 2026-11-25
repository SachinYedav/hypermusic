package com.hypermusic.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.hypermusic.core.designsystem.theme.ShapeXs
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.hypermusic.core.designsystem.components.AppConfirmationModal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.core.data.local.entity.DownloadQueueWithTrack
import com.hypermusic.core.data.repository.DownloadRepository
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DownloadScreen(
    onNavigateBack: () -> Unit,
    downloadRepository: DownloadRepository = koinInject(),
    preferencesManager: com.hypermusic.core.datastore.PreferencesManager = koinInject()
) {
    val queueItems by downloadRepository.getQueueItemsWithTracks().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val userSettings by preferencesManager.userSettingsFlow.collectAsState(initial = com.hypermusic.core.model.UserSettings())
    val isCellularBypassActive by downloadRepository.isCellularBypassActive.collectAsState()
    val isUnmetered by androidx.compose.runtime.produceState(
        initialValue = com.hypermusic.core.utils.NetworkUtils.isUnmeteredConnection(context)
    ) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (cm == null) {
            value = false
            return@produceState
        }
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                value = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
            }
            override fun onLost(network: android.net.Network) {
                value = com.hypermusic.core.utils.NetworkUtils.isUnmeteredConnection(context)
            }
            override fun onAvailable(network: android.net.Network) {
                value = com.hypermusic.core.utils.NetworkUtils.isUnmeteredConnection(context)
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitDispose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }
    val hasPending = queueItems.any { it.queueEntity.status == "PENDING" }
    val isWaitingForWifi = userSettings.downloadOverWifiOnly && !isUnmetered && hasPending && !isCellularBypassActive
    var confirmCancelAll by remember { mutableStateOf(false) }

    if (confirmCancelAll) {
        AppConfirmationModal(
            title = "Cancel all queued downloads?",
            message = "Queued and partial downloads will be removed. Completed offline audio is kept.",
            confirmText = "Cancel downloads",
            onDismiss = { confirmCancelAll = false },
            onConfirm = {
                confirmCancelAll = false
                coroutineScope.launch { downloadRepository.clearAllDownloads() }
            }
        )
    }

    ScreenWrapper {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 800.dp).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = AppIcons.ArrowLeft,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Active Downloads",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                
                if (queueItems.isNotEmpty()) {
                    val hasPausedItems = queueItems.any { it.queueEntity.status == "PAUSED" }
                    if (hasPausedItems) {
                        IconButton(onClick = { 
                            coroutineScope.launch { downloadRepository.resumeAllDownloads() } 
                        }) {
                            Icon(
                                imageVector = AppIcons.Play, 
                                contentDescription = "Resume All",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        IconButton(onClick = { 
                            coroutineScope.launch { downloadRepository.pauseAllDownloads() } 
                        }) {
                            Icon(
                                imageVector = AppIcons.Pause, 
                                contentDescription = "Pause All",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = { 
                        confirmCancelAll = true
                    }) {
                        Icon(
                            imageVector = AppIcons.Trash2, 
                            contentDescription = "Cancel all queued downloads",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isWaitingForWifi,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = com.hypermusic.core.designsystem.theme.ShapeSm,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppIcons.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Waiting for Wi-Fi",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Downloads paused because 'Download over Wi-Fi only' is active",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    downloadRepository.resumeOverCellularNow()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "Use mobile data now",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            if (queueItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = AppIcons.ArrowDownCircle,
                            contentDescription = "No Downloads",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No active downloads",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = LocalBottomContentPadding.current)
                ) {
                    items(
                        items = queueItems,
                        key = { it.queueEntity.id }
                    ) { item ->
                        DownloadQueueItemCard(
                            item = item,
                            isWaitingForWifi = isWaitingForWifi,
                            onPause = {
                                coroutineScope.launch {
                                    downloadRepository.pauseDownload(item.queueEntity.trackId)
                                }
                            },
                            onResume = {
                                coroutineScope.launch {
                                    downloadRepository.resumeDownload(item.queueEntity.trackId)
                                }
                            },
                            onCancel = {
                                coroutineScope.launch {
                                    downloadRepository.cancelDownload(item.queueEntity.trackId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun DownloadQueueItemCard(
    item: DownloadQueueWithTrack,
    isWaitingForWifi: Boolean = false,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val track = item.track
    val entity = item.queueEntity
    
    val isDownloading = entity.status == "DOWNLOADING" || entity.status == "PENDING"
    val progressPercent = (entity.progress / 100f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaArtwork(
            imageUrl = track?.localArtworkPath ?: track?.artworkUrl ?: "",
            contentDescription = null,
            trackType = track?.trackType,
            isDeviceLocal = false,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(ShapeXs)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track?.title ?: "Unknown Track",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track?.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (entity.status == "DOWNLOADING") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progressPercent },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${entity.progress}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }
            } else {
                val statusText = if (entity.status == "PENDING") {
                    if (isWaitingForWifi) "Waiting for Wi-Fi" else "Queued"
                } else {
                    entity.status.lowercase().replaceFirstChar { it.uppercase() }
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (entity.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entity.status == "FAILED") {
                IconButton(
                    onClick = onResume,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.RotateCcw,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = if (isDownloading) onPause else onResume,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isDownloading) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (isDownloading) "Pause" else "Resume",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = AppIcons.X,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
