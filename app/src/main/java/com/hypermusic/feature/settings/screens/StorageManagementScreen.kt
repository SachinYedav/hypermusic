package com.hypermusic.feature.settings.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.components.AppConfirmationModal
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.feature.settings.StorageManagementViewModel
import com.hypermusic.core.designsystem.theme.ShapeMd
import com.hypermusic.core.designsystem.theme.ShapeSm
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: StorageManagementViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.recalculateStorage()
    }

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearDownloadsDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val isScrolled by remember { derivedStateOf { scrollState.value > 0 } }
    val dividerAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "storageDividerAlpha"
    )

    ScreenWrapper(disableSafeAreaTop = true) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxSize()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Storage Management",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = AppIcons.ArrowLeft,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isScrolled) MaterialTheme.colorScheme.background else Color.Transparent
                    )
                )

                if (dividerAlpha > 0f) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = dividerAlpha },
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HyperMusic takes ${uiState.appTotalStorage}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${uiState.freeDeviceStorage} free on device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val appTotal = uiState.appTotalStorageRaw.toFloat()
                        val downloadsWeight = if (appTotal > 0) (uiState.downloadsSizeRaw.toFloat() / appTotal).coerceIn(0f, 1f) else 0f
                        val cacheWeight = if (appTotal > 0) (uiState.cacheSizeRaw.toFloat() / appTotal).coerceIn(0f, 1f) else 0f
                        val databaseWeight = if (appTotal > 0) (uiState.databaseSizeRaw.toFloat() / appTotal).coerceIn(0f, 1f) else 0f

                        val downloadsColor = MaterialTheme.colorScheme.primary
                        val cacheColor = MaterialTheme.colorScheme.tertiary
                        val databaseColor = MaterialTheme.colorScheme.secondary
                        val emptyColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(ShapeSm)
                                .background(emptyColor)
                                .drawBehind {
                                    val width = size.width
                                    val height = size.height

                                    var currentX = 0f

                                    val downloadsWidth = width * downloadsWeight
                                    if (downloadsWidth > 0) {
                                        drawRect(
                                            color = downloadsColor,
                                            topLeft = Offset(currentX, 0f),
                                            size = Size(downloadsWidth, height)
                                        )
                                        currentX += downloadsWidth
                                    }

                                    val cacheWidth = width * cacheWeight
                                    if (cacheWidth > 0) {
                                        drawRect(
                                            color = cacheColor,
                                            topLeft = Offset(currentX, 0f),
                                            size = Size(cacheWidth, height)
                                        )
                                        currentX += cacheWidth
                                    }

                                    val databaseWidth = width * databaseWeight
                                    if (databaseWidth > 0) {
                                        drawRect(
                                            color = databaseColor,
                                            topLeft = Offset(currentX, 0f),
                                            size = Size(databaseWidth, height)
                                        )
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StorageBreakdownRow(
                                color = downloadsColor,
                                label = "Downloads",
                                size = uiState.downloadsSize
                            )
                            StorageBreakdownRow(
                                color = cacheColor,
                                label = "Cache",
                                size = uiState.cacheSize
                            )
                            StorageBreakdownRow(
                                color = databaseColor,
                                label = "Database & Data",
                                size = uiState.databaseSize
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(bottom = LocalBottomContentPadding.current)
                    ) {
                        Text(
                            text = "App Data",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        StorageItemCard(
                            title = "App Cache",
                            subtitle = "Artwork images, stream cache and temporary data",
                            size = uiState.cacheSize,
                            icon = AppIcons.Database,
                            onClearClick = { showClearCacheDialog = true }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        StorageItemCard(
                            title = "Downloads",
                            subtitle = "Offline songs, podcasts and cover artwork",
                            size = uiState.downloadsSize,
                            icon = AppIcons.ArrowDown,
                            onClearClick = { showClearDownloadsDialog = true }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        StorageItemCard(
                            title = "App Database & Data",
                            subtitle = "Saved playlists, albums, favorites and library metadata",
                            size = uiState.databaseSize,
                            icon = AppIcons.Library,
                            onClearClick = null
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        StorageItemCard(
                            title = "Playback History",
                            subtitle = "Records of what you've listened to",
                            size = uiState.historyCount,
                            icon = AppIcons.History,
                            onClearClick = { showClearHistoryDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showClearCacheDialog) {
        AppConfirmationModal(
            title = "Clear Cache",
            message = "This will free up space by removing temporary images and data. They will be re-downloaded when needed.",
            onConfirm = {
                viewModel.clearCache()
                showClearCacheDialog = false
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }

    if (showClearHistoryDialog) {
        AppConfirmationModal(
            title = "Clear History",
            message = "This will permanently remove all your listening history. It cannot be undone.",
            onConfirm = {
                viewModel.clearHistory()
                showClearHistoryDialog = false
            },
            onDismiss = { showClearHistoryDialog = false }
        )
    }

    if (showClearDownloadsDialog) {
        AppConfirmationModal(
            title = "Remove All Downloads",
            message = "This will delete all offline audio and video files. Are you sure you want to proceed?",
            onConfirm = {
                viewModel.clearDownloads()
                showClearDownloadsDialog = false
            },
            onDismiss = { showClearDownloadsDialog = false }
        )
    }
}

@Composable
private fun StorageBreakdownRow(
    color: Color,
    label: String,
    size: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = size,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StorageItemCard(
    title: String,
    subtitle: String,
    size: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClearClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeMd)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = size,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (onClearClick != null) {
            IconButton(onClick = onClearClick) {
                Icon(
                    imageVector = AppIcons.Trash2,
                    contentDescription = "Clear $title",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
