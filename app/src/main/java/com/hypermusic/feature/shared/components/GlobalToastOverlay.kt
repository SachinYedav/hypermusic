package com.hypermusic.feature.shared.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.icon.AlertCircle
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.CheckCircle2
import com.hypermusic.core.designsystem.icon.Heart
import com.hypermusic.core.designsystem.icon.Info
import com.hypermusic.core.designsystem.icon.X
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import com.hypermusic.feature.shared.state.ToastLayoutMode
import com.hypermusic.feature.shared.state.ToastType
import com.hypermusic.navigation.BottomNavContentHeight
import com.hypermusic.core.designsystem.theme.AccentPurple
import com.hypermusic.core.designsystem.theme.ShapeSm
import com.hypermusic.core.designsystem.theme.ShapeXs
import org.koin.compose.koinInject

@Composable
fun GlobalToastOverlay(
    toastViewModel: GlobalToastViewModel = koinInject(),
    playerRepository: PlayerRepository = koinInject()
) {
    val toastState by toastViewModel.state.collectAsState()
    val playerSheetState by playerRepository.playerSheetState.collectAsState()
    val density = LocalDensity.current

    val navBarBottom = WindowInsets.navigationBars.getBottom(density)
    val navBarBottomDp = with(density) { navBarBottom.toDp() }

    // Keep the toast above navigation, the mini player and system insets as applicable.
    val isFullscreenOrQueue = playerSheetState >= 2
    val isMiniPlayer = playerSheetState == 1

    val targetBottomOffset = when {
        isFullscreenOrQueue -> navBarBottomDp + 16.dp
        isMiniPlayer -> BottomNavContentHeight + navBarBottomDp + 64.dp + 8.dp
        else -> BottomNavContentHeight + navBarBottomDp + 8.dp
    }

    val bottomOffset by animateDpAsState(
        targetValue = targetBottomOffset,
        animationSpec = tween(300),
        label = "toastBottomOffset"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = toastState.isVisible,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(200)),
            modifier = Modifier.padding(bottom = bottomOffset)
        ) {
            val containerColor = MaterialTheme.colorScheme.inverseSurface
            val contentColor = MaterialTheme.colorScheme.inverseOnSurface
            val actionColor = if (toastState.type == ToastType.LIKED) AccentPurple else contentColor

            val icon = when (toastState.type) {
                ToastType.INFO -> AppIcons.Info
                ToastType.SUCCESS -> AppIcons.CheckCircle2
                ToastType.ERROR -> AppIcons.AlertCircle
                ToastType.LIKED -> AppIcons.Heart
            }

            val iconTint = when (toastState.type) {
                ToastType.ERROR -> MaterialTheme.colorScheme.error
                ToastType.LIKED -> AccentPurple
                else -> contentColor
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp, shape = ShapeSm)
                    .clip(ShapeSm)
                    .background(containerColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
            ) {
                if (toastState.layoutMode == ToastLayoutMode.CARD) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = toastState.title ?: "Notice",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                ),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { toastViewModel.hideToast() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.X,
                                    contentDescription = "Dismiss",
                                    tint = contentColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (toastState.message.isNotBlank()) {
                            Text(
                                text = toastState.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = contentColor.copy(alpha = 0.85f),
                                    lineHeight = 18.sp
                                ),
                                modifier = Modifier.padding(start = 30.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (toastState.secondaryAction != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(ShapeSm)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), ShapeSm)
                                        .clickable {
                                            toastState.secondaryAction!!.onClick()
                                            toastViewModel.hideToast()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = toastState.secondaryAction!!.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = contentColor.copy(alpha = 0.85f)
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            if (toastState.action != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(ShapeSm)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable {
                                            toastState.action!!.onClick()
                                            toastViewModel.hideToast()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = toastState.action!!.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (toastState.type == ToastType.LIKED) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(ShapeXs)
                                    .background(AccentPurple),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = toastState.message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (toastState.action != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = toastState.action!!.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = actionColor
                                ),
                                modifier = Modifier
                                    .clip(ShapeXs)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        toastState.action!!.onClick()
                                        toastViewModel.hideToast()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
