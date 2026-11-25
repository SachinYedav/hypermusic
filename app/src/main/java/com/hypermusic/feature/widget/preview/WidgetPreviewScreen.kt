package com.hypermusic.feature.widget.preview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.hypermusic.R
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import com.hypermusic.feature.shared.state.ToastType
import com.hypermusic.feature.widget.data.WidgetPlaybackState
import com.hypermusic.feature.widget.manager.WidgetUpdateManager
import com.hypermusic.feature.widget.pinning.WidgetPinningManager
import com.hypermusic.feature.widget.receiver.CapsuleWidgetProvider
import com.hypermusic.feature.widget.receiver.LargePlayerWidgetProvider
import com.hypermusic.feature.widget.receiver.RecommendedWidgetProvider
import com.hypermusic.feature.widget.receiver.SearchAndPlayWidgetProvider
import com.hypermusic.core.designsystem.theme.ShapeFull
import com.hypermusic.core.designsystem.theme.ShapeLg
import com.hypermusic.core.designsystem.theme.ShapeMd
import com.hypermusic.core.designsystem.theme.ShapeSm
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPreviewScreen(
    onNavigateBack: () -> Unit,
    widgetUpdateManager: WidgetUpdateManager = koinInject(),
    toastViewModel: GlobalToastViewModel = koinInject()
) {
    val context = LocalContext.current
    val playbackState by widgetUpdateManager.playbackState.collectAsState()

    fun handlePinWidget(receiverClass: Class<*>, widgetName: String) {
        val isSupported = WidgetPinningManager.isPinningSupported(context)
        if (!isSupported) {
            toastViewModel.showToast(
                message = "Your launcher doesn't support automatic pinning. Touch and hold home screen to add manually.",
                type = ToastType.ERROR
            )
            return
        }

        val requestDispatched = WidgetPinningManager.pinWidget(context, receiverClass, widgetName)
        if (!requestDispatched) {
            toastViewModel.showToast(
                message = "Failed to request widget pinning. Touch and hold your home screen to place it manually.",
                type = ToastType.ERROR
            )
        }
    }

    val scrollState = rememberScrollState()
    val isScrolled by remember { derivedStateOf { scrollState.value > 0 } }
    val dividerAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "widgetPreviewDividerAlpha"
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
                            text = "Home screen widgets",
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

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(top = 12.dp, bottom = LocalBottomContentPadding.current)
                ) {
                    val isWideScreen = maxWidth >= 640.dp

                    val searchTitle = stringResource(R.string.widget_search_and_play_name)
                    val searchDesc = stringResource(R.string.widget_search_and_play_desc)
                    val capsuleTitle = stringResource(R.string.widget_capsule_name)
                    val capsuleDesc = stringResource(R.string.widget_capsule_desc)
                    val recommendedTitle = stringResource(R.string.widget_recommended_name)
                    val recommendedDesc = stringResource(R.string.widget_recommended_desc)
                    val largeTitle = stringResource(R.string.widget_large_player_name)
                    val largeDesc = stringResource(R.string.widget_large_player_desc)

                    if (isWideScreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                WidgetCardContainer(
                                    title = searchTitle,
                                    subtitle = searchDesc,
                                    cardShape = ShapeLg,
                                    onPin = { handlePinWidget(SearchAndPlayWidgetProvider::class.java, searchTitle) }
                                ) {
                                    SearchAndPlayPreview(playbackState)
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                WidgetCardContainer(
                                    title = recommendedTitle,
                                    subtitle = recommendedDesc,
                                    cardShape = ShapeLg,
                                    onPin = { handlePinWidget(RecommendedWidgetProvider::class.java, recommendedTitle) }
                                ) {
                                    RecommendedPreview(playbackState)
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                WidgetCardContainer(
                                    title = capsuleTitle,
                                    subtitle = capsuleDesc,
                                    cardShape = ShapeFull,
                                    onPin = { handlePinWidget(CapsuleWidgetProvider::class.java, capsuleTitle) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        CapsulePreview(playbackState)
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                WidgetCardContainer(
                                    title = largeTitle,
                                    subtitle = largeDesc,
                                    cardShape = ShapeLg,
                                    onPin = { handlePinWidget(LargePlayerWidgetProvider::class.java, largeTitle) }
                                ) {
                                    LargePlayerPreview(playbackState)
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            WidgetCardContainer(
                                title = searchTitle,
                                subtitle = searchDesc,
                                cardShape = ShapeLg,
                                onPin = {
                                    handlePinWidget(SearchAndPlayWidgetProvider::class.java, searchTitle)
                                }
                            ) {
                                SearchAndPlayPreview(playbackState)
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            WidgetCardContainer(
                                title = capsuleTitle,
                                subtitle = capsuleDesc,
                                cardShape = ShapeFull,
                                onPin = {
                                    handlePinWidget(CapsuleWidgetProvider::class.java, capsuleTitle)
                                }
                            ) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CapsulePreview(playbackState)
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            WidgetCardContainer(
                                title = recommendedTitle,
                                subtitle = recommendedDesc,
                                cardShape = ShapeLg,
                                onPin = {
                                    handlePinWidget(RecommendedWidgetProvider::class.java, recommendedTitle)
                                }
                            ) {
                                RecommendedPreview(playbackState)
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            WidgetCardContainer(
                                title = largeTitle,
                                subtitle = largeDesc,
                                cardShape = ShapeLg,
                                onPin = {
                                    handlePinWidget(LargePlayerWidgetProvider::class.java, largeTitle)
                                }
                            ) {
                                LargePlayerPreview(playbackState)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCardContainer(
    title: String,
    subtitle: String,
    cardShape: androidx.compose.ui.graphics.Shape,
    onPin: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
        ) {
            content()
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onPin,
            shape = ShapeSm,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 5.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Icon(
                imageVector = AppIcons.Plus,
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Add to Home",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}


@Composable
private fun SearchAndPlayPreview(state: WidgetPlaybackState) {
    val topColor = Color(state.dynamicColors.gradientTopArgb)
    val darkColor = Color(state.dynamicColors.gradientDarkArgb)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(Brush.verticalGradient(listOf(topColor, darkColor)))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.artwork != null) {
                    Image(
                        bitmap = state.artwork.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(ShapeMd)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(ShapeMd)
                            .background(Color(0xFF201F25)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_default_artwork),
                            contentDescription = null,
                            tint = Color(0xFF7E7D8A),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.hasTrack && state.title.isNotBlank()) state.title else "Music",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (state.hasTrack && state.artist.isNotBlank()) state.artist else "Turn on the HyperMusic",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_widget_skip_previous),
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(
                            id = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                        ),
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_widget_skip_next),
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_widget_search),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Search Songs, Playlists and Artists",
                    fontSize = 13.5.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CapsulePreview(state: WidgetPlaybackState) {
    val topColor = Color(state.dynamicColors.gradientTopArgb)
    val darkColor = Color(state.dynamicColors.gradientDarkArgb)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(topColor, darkColor)))
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.artwork != null) {
                Image(
                    bitmap = state.artwork.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF201F25)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_default_artwork),
                        contentDescription = null,
                        tint = Color(0xFF7E7D8A),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = if (state.hasTrack && state.title.isNotBlank()) state.title else "Music",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                painter = painterResource(
                    id = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                ),
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                painter = painterResource(id = R.drawable.ic_widget_skip_next),
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun RecommendedPreview(state: WidgetPlaybackState) {
    val topColor = Color(state.dynamicColors.gradientTopArgb)
    val darkColor = Color(state.dynamicColors.gradientDarkArgb)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(Brush.verticalGradient(listOf(topColor, darkColor)))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.artwork != null) {
                    Image(
                        bitmap = state.artwork.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(ShapeMd)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(ShapeMd)
                            .background(Color(0xFF201F25)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_default_artwork),
                            contentDescription = null,
                            tint = Color(0xFF7E7D8A),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.hasTrack && state.title.isNotBlank()) state.title else "Music",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (state.hasTrack && state.artist.isNotBlank()) state.artist else "Turn on the HyperMusic",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_widget_skip_previous),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(
                        id = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                    ),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_widget_skip_next),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Row {
                ShortcutSquare(R.drawable.ic_widget_playlist)
                Spacer(modifier = Modifier.width(6.dp))
                ShortcutSquare(R.drawable.ic_widget_explore)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                ShortcutSquare(R.drawable.ic_widget_history)
                Spacer(modifier = Modifier.width(6.dp))
                ShortcutSquare(R.drawable.ic_widget_heart_filled)
            }
        }
    }
}

@Composable
private fun ShortcutSquare(iconRes: Int) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(ShapeMd)
            .background(Color.White.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun LargePlayerPreview(state: WidgetPlaybackState) {
    val topColor = Color(state.dynamicColors.gradientTopArgb)
    val darkColor = Color(state.dynamicColors.gradientDarkArgb)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(Brush.verticalGradient(listOf(topColor, darkColor)))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ShapeMd),
            contentAlignment = Alignment.Center
        ) {
            if (state.artwork != null) {
                Image(
                    bitmap = state.artwork.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF201F25)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_default_artwork),
                        contentDescription = null,
                        tint = Color(0xFF7E7D8A),
                        modifier = Modifier.size(60.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (state.hasTrack && state.title.isNotBlank()) state.title else "Music",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = if (state.hasTrack && state.artist.isNotBlank()) state.artist else "Turn on the HyperMusic",
            fontSize = 12.5.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val repeatIcon = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                R.drawable.ic_widget_repeat_one
            } else {
                R.drawable.ic_widget_repeat
            }
            Icon(
                painter = painterResource(id = repeatIcon),
                contentDescription = "Repeat",
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_widget_skip_previous),
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )

            Icon(
                painter = painterResource(
                    id = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                ),
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_widget_skip_next),
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )

            val canLike = state.hasTrack && state.canLike
            Icon(
                painter = painterResource(
                    id = if (canLike && state.isLiked) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart_outline
                ),
                contentDescription = when {
                    canLike -> if (state.isLiked) "Unlike track" else "Like track"
                    state.hasTrack -> "Like unavailable for device files"
                    else -> "No track playing"
                },
                tint = if (canLike) Color.White else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
