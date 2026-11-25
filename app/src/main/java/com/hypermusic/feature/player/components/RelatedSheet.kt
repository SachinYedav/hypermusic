package com.hypermusic.feature.player.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.AudioWaveform
import com.hypermusic.core.designsystem.icon.X
import com.hypermusic.core.model.Track
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.core.player.RelatedUiState
import com.hypermusic.core.utils.DynamicColors
import com.hypermusic.feature.shared.components.FeedCarousel
import com.hypermusic.feature.shared.components.ArtistBioSection
import com.hypermusic.feature.shared.utils.MediaActionHandler
import com.hypermusic.core.designsystem.theme.RadiusLg
import com.hypermusic.core.designsystem.theme.ShapeHandle
import org.koin.compose.koinInject

/**
 * Dedicated drawer sheet displaying related tracks and recommendations.
 */
@Composable
fun RelatedSheet(
    track: Track?,
    relatedState: RelatedUiState,
    colors: DynamicColors? = null,
    bottomPadding: Dp = 0.dp,
    onTrackClick: (Track) -> Unit,
    onNavigateToCollection: ((type: String, id: String, title: String?, subtitle: String?, artworkUrl: String?) -> Unit)? = null,
    onCloseClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    playerRepository: PlayerRepository = koinInject(),
    actionSheetViewModel: com.hypermusic.feature.shared.state.ActionSheetViewModel = koinInject()
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary

    val backgroundModifier = if (colors != null) {
        Modifier.background(colors.gradientMid)
    } else {
        Modifier.background(surfaceColor)
    }

    val sheetColorScheme = if (colors != null) {
        darkColorScheme(
            background = Color.Transparent,
            onBackground = Color.White,
            surface = Color.Transparent,
            onSurface = Color.White,
            surfaceVariant = Color.White.copy(alpha = 0.12f),
            onSurfaceVariant = Color.White.copy(alpha = 0.70f),
            primary = primaryColor
        )
    } else {
        MaterialTheme.colorScheme
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg))
            .then(backgroundModifier)
    ) {
        MaterialTheme(colorScheme = sheetColorScheme) {
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(ShapeHandle)
                            .background(onSurfaceColor.copy(alpha = 0.2f))
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Related",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = onSurfaceColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCloseClick) {
                        Icon(
                            imageVector = AppIcons.X,
                            contentDescription = "Close Related",
                            modifier = Modifier.size(24.dp),
                            tint = onSurfaceColor
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    color = onSurfaceColor.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (relatedState) {
                    is RelatedUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomPadding + 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = primaryColor,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Finding recommendations...",
                                    color = onSurfaceVariantColor,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    is RelatedUiState.Success -> {
                        val carouselShelves = androidx.compose.runtime.remember(relatedState.shelves) {
                            relatedState.shelves.filter { it.type != "artist_bio" && it.items.isNotEmpty() }
                        }
                        val bioShelf = androidx.compose.runtime.remember(relatedState.shelves) {
                            relatedState.shelves.firstOrNull { it.type == "artist_bio" && !it.description.isNullOrBlank() }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 16.dp,
                                bottom = bottomPadding + 40.dp
                            )
                        ) {
                            items(carouselShelves, key = { it.id }) { shelf ->
                                FeedCarousel(
                                    shelf = shelf,
                                    onTrackClick = { item ->
                                        MediaActionHandler.handleTrackClick(
                                            track = item,
                                            onNavigateToCollection = { type, id, title, subtitle, artwork ->
                                                onCloseClick()
                                                onNavigateToCollection?.invoke(type, id, title, subtitle, artwork)
                                            },
                                            playerRepository = playerRepository,
                                            shelfItems = shelf.items,
                                            onCloseOverlay = onCloseClick
                                        )
                                    },
                                    onMoreClick = { item ->
                                        MediaActionHandler.handleTrackMoreClick(
                                            track = item,
                                            actionSheetViewModel = actionSheetViewModel
                                        )
                                    },
                                    onPlayAll = { tracks ->
                                        val playableTracks = MediaActionHandler.filterPlayableTracks(tracks)
                                        if (playableTracks.isNotEmpty()) {
                                            playerRepository.playTracks(playableTracks)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            if (bioShelf != null && (!bioShelf.description.isNullOrBlank() || !bioShelf.descriptionRuns.isNullOrEmpty())) {
                                item {
                                    ArtistBioSection(
                                        title = bioShelf.title,
                                        description = bioShelf.description,
                                        runs = bioShelf.descriptionRuns
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }
                    }
                    is RelatedUiState.Empty -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomPadding + 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.AudioWaveform,
                                    contentDescription = null,
                                    tint = onSurfaceVariantColor.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No recommendations available for this track",
                                    color = onSurfaceVariantColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    is RelatedUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomPadding + 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Could not load recommendations",
                                    color = onSurfaceVariantColor,
                                    fontSize = 15.sp
                                )
                                Button(
                                    onClick = onRetry,
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                                ) {
                                    Text("Retry", color = Color.White)
                                }
                            }
                        }
                    }
                    is RelatedUiState.Idle -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
