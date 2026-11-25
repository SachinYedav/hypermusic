package com.hypermusic.feature.player.components

import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.designsystem.icon.AddToPlaylist
import androidx.compose.material3.*
import com.hypermusic.core.designsystem.components.AnimatedEQ
import com.hypermusic.core.model.isDeviceOrigin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.core.designsystem.theme.RadiusLg
import com.hypermusic.core.designsystem.theme.ShapeHandle
import com.hypermusic.core.designsystem.theme.ShapeSm
import com.hypermusic.core.designsystem.theme.ShapeXs
import com.hypermusic.core.player.AutoplayStatus
import com.hypermusic.core.model.Track
import com.hypermusic.core.utils.DynamicColors
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

sealed class QueueItem {
    abstract val key: String

    data class TrackItem(val track: Track, override val key: String) : QueueItem()
    data class HeaderItem(
        val isAutoplayEnabled: Boolean,
        val isFetchingRadio: Boolean = false,
        val autoplayStatus: AutoplayStatus = AutoplayStatus.Idle
    ) : QueueItem() {
        override val key: String = "autoplay_header"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Track>,
    colors: DynamicColors,
    onTrackClick: (Track) -> Unit,
    onCloseClick: () -> Unit,
    parentProgress: Float,
    queueDragProgress: Float = 0f,
    dragDistanceToHalf: Float = 1f,
    onSaveQueue: () -> Unit = {},
    isAutoplayEnabled: Boolean = false,
    onAutoplayToggle: (Boolean) -> Unit = {},
    onMoveTrack: (entryId: String, beforeEntryId: String?) -> Unit = { _, _ -> },
    onRemoveTrack: (Track, Int) -> Unit = { _, _ -> },
    currentTrack: Track? = null,
    currentEntryId: String? = null,
    currentMediaItemIndex: Int = 0,
    isFetchingRadio: Boolean = false,
    autoplayStatus: AutoplayStatus = AutoplayStatus.Idle,
    onRetryAutoplay: () -> Unit = {},
    isPlaying: Boolean = false,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    actionSheetViewModel: com.hypermusic.feature.shared.state.ActionSheetViewModel = org.koin.compose.koinInject(),
    toastViewModel: com.hypermusic.feature.shared.state.GlobalToastViewModel = org.koin.compose.koinInject()
) {
    val currentQueueState = androidx.compose.runtime.rememberUpdatedState(queue)

    val headerAlpha = (queueDragProgress / (dragDistanceToHalf * 0.35f).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val listAlpha = (queueDragProgress / (dragDistanceToHalf * 0.25f).coerceAtLeast(1f)).coerceIn(0f, 1f)

    val uiQueue = remember { mutableStateOf<List<QueueItem>>(emptyList()) }
    val draggedEntry = remember { mutableStateOf<String?>(null) }
    val effectiveCurrentEntryId = currentEntryId ?: currentTrack?.queueEntryId

    remember(queue, isAutoplayEnabled, isFetchingRadio, autoplayStatus) {
        val list = mutableListOf<QueueItem>()
        var headerAdded = false

        queue.forEachIndexed { idx, track ->
            if (!headerAdded && track.isImplicit) {
                list.add(QueueItem.HeaderItem(isAutoplayEnabled, isFetchingRadio, autoplayStatus))
                headerAdded = true
            }
            val key = track.queueEntryId ?: "${if (track.isImplicit) "implicit" else "explicit"}_${track.id}_$idx"
            list.add(QueueItem.TrackItem(track, key))
        }

        if (!headerAdded) {
            list.add(QueueItem.HeaderItem(isAutoplayEnabled, isFetchingRadio, autoplayStatus))
        }

        uiQueue.value = list
        true
    }

    val state = rememberReorderableLazyListState(
        onMove = { from, to ->
            val list = uiQueue.value.toMutableList()
            if (from.index in list.indices && to.index in list.indices) {
                val item = list.removeAt(from.index)
                if (item is QueueItem.TrackItem) draggedEntry.value = item.track.queueEntryId
                list.add(to.index, item)
                uiQueue.value = list
            }
        },
        canDragOver = { draggedOver, dragging ->
            val draggedOverItem = uiQueue.value.getOrNull(draggedOver.index)
            val draggingItem = uiQueue.value.getOrNull(dragging.index)

            if (draggedOverItem is QueueItem.HeaderItem) {
                false
            } else if (draggingItem is QueueItem.TrackItem && draggedOverItem is QueueItem.TrackItem) {
                draggingItem.track.isImplicit == draggedOverItem.track.isImplicit
            } else {
                false
            }
        },
        onDragEnd = { _, _ ->
            val entry = draggedEntry.value
            val ordered = uiQueue.value.filterIsInstance<QueueItem.TrackItem>().mapNotNull { it.track.queueEntryId }
            val before = entry?.let { ordered.getOrNull(ordered.indexOf(it) + 1) }
            if (entry != null) onMoveTrack(entry, before)
            draggedEntry.value = null
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg))
            .background(colors.queueBackgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onCloseClick() }
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(ShapeHandle)
                            .background(colors.titleText.copy(alpha = 0.2f))
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 14.dp)
                        .graphicsLayer { alpha = 1f - headerAlpha }
                        .clickable(
                            enabled = headerAlpha < 0.5f,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onCloseClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your Queue",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.titleText
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp, vertical = 2.dp)
                        .graphicsLayer { alpha = headerAlpha },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Playing from",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = colors.bodyText
                            )
                        )
                        Text(
                            text = "Your Queue",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.titleText
                            )
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(ShapeSm)
                            .background(colors.titleText.copy(alpha = 0.12f))
                            .clickable(
                                enabled = headerAlpha > 0.5f,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val explicitTracks = queue.filter { !it.isImplicit }
                                if (explicitTracks.isNotEmpty() && explicitTracks.all { it.isDeviceOrigin() }) {
                                    toastViewModel.showToast("Unsupported for local files", com.hypermusic.feature.shared.state.ToastType.INFO)
                                } else {
                                    onSaveQueue()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.AddToPlaylist,
                            contentDescription = "Save Queue",
                            modifier = Modifier.size(16.dp),
                            tint = colors.titleText
                        )
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.titleText
                            )
                        )
                    }
                }
            }

            LazyColumn(
                state = state.listState,
                modifier = Modifier
                    .fillMaxSize()
                    .reorderable(state)
                    .graphicsLayer { alpha = listAlpha },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPadding + 32.dp)
            ) {
                itemsIndexed(
                    items = uiQueue.value,
                    key = { _, it -> it.key },
                    contentType = { _, it ->
                        when (it) {
                            is QueueItem.HeaderItem -> "header"
                            is QueueItem.TrackItem -> "track"
                        }
                    }
                ) { uiIndex, queueItem ->
                    org.burnoutcrew.reorderable.ReorderableItem(
                        state = state,
                        key = queueItem.key,
                        defaultDraggingModifier = Modifier.animateItem()
                    ) { isDragging ->
                        when (queueItem) {
                            is QueueItem.TrackItem -> {
                                val mediaIndex = currentQueueState.value.indexOfFirst { it.queueEntryId == queueItem.track.queueEntryId }
                                val isCurrent = if (effectiveCurrentEntryId != null && queueItem.track.queueEntryId != null) {
                                    queueItem.track.queueEntryId == effectiveCurrentEntryId
                                } else {
                                    currentTrack != null && queueItem.track.id == currentTrack.id && mediaIndex == currentMediaItemIndex
                                }

                                key(queueItem.key) {
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        positionalThreshold = { totalDistance -> totalDistance * 0.55f },
                                        confirmValueChange = { dismissValue ->
                                            if (dismissValue == SwipeToDismissBoxValue.StartToEnd || 
                                                dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                                onRemoveTrack(queueItem.track, mediaIndex)
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    )

                                    val isDismissing = dismissState.targetValue != SwipeToDismissBoxValue.Settled ||
                                            dismissState.dismissDirection != SwipeToDismissBoxValue.Settled

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = true,
                                        enableDismissFromEndToStart = true,
                                        backgroundContent = {
                                            if (isDismissing) {
                                                val direction = dismissState.dismissDirection
                                                val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                                    Alignment.CenterStart
                                                } else {
                                                    Alignment.CenterEnd
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                                                        .padding(horizontal = 24.dp),
                                                    contentAlignment = alignment
                                                ) {
                                                    Icon(
                                                        imageVector = AppIcons.Trash2,
                                                        contentDescription = "Remove from Queue",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        },
                                        content = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(if (isDismissing) colors.gradientDark else Color.Transparent)
                                            ) {
                                                TrackItemUI(
                                                    track = queueItem.track,
                                                    colors = colors,
                                                    isDragging = isDragging,
                                                    onClick = { onTrackClick(queueItem.track) },
                                                    onLongClick = { 
                                                        actionSheetViewModel.openSheet(
                                                            com.hypermusic.feature.shared.utils.MediaActionHandler.resolveActionContext(queueItem.track)
                                                        )
                                                    },
                                                    dragHandleModifier = Modifier.detectReorder(state),
                                                    isCurrent = isCurrent,
                                                    isPlaying = isPlaying
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            is QueueItem.HeaderItem -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                        color = colors.titleText.copy(alpha = 0.1f)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Autoplay",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.titleText
                                                )
                                            )
                                            Text(
                                                text = "Similar music will play",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = colors.bodyText
                                                )
                                            )
                                        }
                                        val switchThumbChecked = if (colors.titleText.luminance() > 0.5f) Color(0xFF111111) else Color.White
                                        Switch(
                                            checked = queueItem.isAutoplayEnabled,
                                            onCheckedChange = { onAutoplayToggle(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = switchThumbChecked,
                                                checkedTrackColor = colors.titleText,
                                                uncheckedThumbColor = colors.titleText.copy(alpha = 0.85f),
                                                uncheckedTrackColor = colors.titleText.copy(alpha = 0.2f),
                                                uncheckedBorderColor = colors.titleText.copy(alpha = 0.35f)
                                            )
                                        )
                                    }
                                    if (queueItem.isAutoplayEnabled) {
                                        when (val status = queueItem.autoplayStatus) {
                                            is AutoplayStatus.Loading -> {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        color = colors.titleText,
                                                        strokeWidth = 2.5.dp,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            is AutoplayStatus.LocalOnly -> {
                                                if (queue.none { it.isImplicit }) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                                            .clip(ShapeSm)
                                                            .background(colors.titleText.copy(alpha = 0.06f))
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = AppIcons.Info,
                                                            contentDescription = null,
                                                            tint = colors.bodyText.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Autoplay is unavailable for local device files",
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                color = colors.bodyText.copy(alpha = 0.8f)
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                            is AutoplayStatus.Error -> {
                                                if (queue.none { it.isImplicit }) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                                            .clip(ShapeSm)
                                                            .background(colors.titleText.copy(alpha = 0.08f))
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = AppIcons.AlertCircle,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Couldn't load similar songs",
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontWeight = FontWeight.Medium,
                                                                color = colors.titleText
                                                            ),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(ShapeSm)
                                                                .background(colors.titleText)
                                                                .clickable { onRetryAutoplay() }
                                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "Retry",
                                                                style = MaterialTheme.typography.labelMedium.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (colors.titleText.luminance() > 0.5f) Color(0xFF111111) else Color.White
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            is AutoplayStatus.Idle -> {
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

val ReorderHandleIcon: ImageVector
    get() = AppIcons.ReorderHandle

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackItemUI(
    track: Track,
    colors: DynamicColors,
    isDragging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false
) {
    val bgColor = when {
        isDragging -> colors.titleText.copy(alpha = 0.15f)
        isCurrent -> colors.titleText.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isCurrent) {
                    Modifier.semantics {
                        stateDescription = if (isPlaying) "Playing" else "Current track, paused"
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(ShapeXs)) {
            MediaArtwork(
                imageUrl = track.artwork,
                contentDescription = "Artwork",
                trackType = track.trackType,
                isDeviceLocal = track.isDeviceLocal,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            AnimatedEQ(
                isActive = isCurrent,
                isPlaying = isPlaying,
                color = Color.White,
                modifier = Modifier.matchParentSize()
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                        color = colors.titleText
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.bodyText
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = ReorderHandleIcon,
            contentDescription = "Reorder",
            tint = colors.titleText.copy(alpha = 0.75f),
            modifier = dragHandleModifier.size(24.dp)
        )
    }
}
