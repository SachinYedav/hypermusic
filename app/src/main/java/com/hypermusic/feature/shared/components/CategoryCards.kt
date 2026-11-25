package com.hypermusic.feature.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.components.WaveLoader
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.BarChart2
import com.hypermusic.core.designsystem.icon.Flame
import com.hypermusic.core.designsystem.icon.Podcast
import com.hypermusic.core.model.BrowseShelf
import com.hypermusic.core.designsystem.theme.CategoryCardPalette
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import com.hypermusic.core.designsystem.theme.ShapeSm

@Composable
fun CategoryTile(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp, max = 110.dp)
            .clip(ShapeSm)
            .background(color)
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CategoryCards(
    shelves: List<BrowseShelf>,
    isLoading: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState(),
    showTopShortcuts: Boolean = true,
    headerTitle: String? = "Browse all",
    contentPadding: PaddingValues = PaddingValues(
        start = 16.dp,
        top = 16.dp,
        end = 16.dp,
        bottom = LocalBottomContentPadding.current
    ),
    modifier: Modifier = Modifier,
    onCategoryPress: (String, String) -> Unit
) {
    val allItems = remember(shelves) {
        shelves.flatMap { it.items }.distinctBy { it.id }
    }

    val hasMultipleNamedShelves = remember(shelves) {
        shelves.count { it.title.isNotBlank() && it.title != "Moods & Genres" && it.title != "Moods & genres" } > 1
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        if (showTopShortcuts) {
            headerTitle?.let { title ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StaticCategoryCard(
                        title = "New",
                        icon = AppIcons.Flame,
                        color = Color(0xFFE91E63),
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryPress("new", "New Releases") }
                    )
                    StaticCategoryCard(
                        title = "Charts",
                        icon = AppIcons.BarChart2,
                        color = Color(0xFF8A2BE2),
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryPress("charts", "Charts") }
                    )
                    StaticCategoryCard(
                        title = "Podcasts",
                        icon = AppIcons.Podcast,
                        color = Color(0xFF2E8B57),
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryPress("podcasts", "Podcasts") }
                    )
                }
            }
        }

        if (isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WaveLoader()
                }
            }
        } else if (hasMultipleNamedShelves) {
            var colorOffset = 0
            shelves.forEachIndexed { shelfIndex, shelf ->
                if (shelf.items.isNotEmpty()) {
                    if (shelf.title.isNotBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "shelf_hdr_${shelf.id}_$shelfIndex") {
                            Text(
                                text = shelf.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = if (shelfIndex > 0 || showTopShortcuts) 12.dp else 0.dp, bottom = 4.dp)
                            )
                        }
                    }
                    itemsIndexed(
                        items = shelf.items,
                        key = { index, item -> "${item.id}_${shelfIndex}_$index" },
                        contentType = { _, _ -> "category" }
                    ) { index, item ->
                        val tileColor = CategoryCardPalette[(colorOffset + index) % CategoryCardPalette.size]
                        CategoryTile(
                            title = item.title,
                            color = tileColor,
                            onClick = { onCategoryPress(item.id, item.title) }
                        )
                    }
                    colorOffset += shelf.items.size
                }
            }
        } else {
            val singleTitle = shelves.firstOrNull()?.title?.takeIf { it.isNotBlank() }
            if (singleTitle != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "shelf_single_hdr") {
                    Text(
                        text = singleTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = if (showTopShortcuts) 12.dp else 0.dp, bottom = 4.dp)
                    )
                }
            }
            itemsIndexed(
                items = allItems,
                key = { index, item -> "${item.id}_$index" },
                contentType = { _, _ -> "category" }
            ) { index, item ->
                val tileColor = CategoryCardPalette[index % CategoryCardPalette.size]
                CategoryTile(
                    title = item.title,
                    color = tileColor,
                    onClick = { onCategoryPress(item.id, item.title) }
                )
            }
        }
    }
}

@Composable
fun StaticCategoryCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(ShapeSm)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(color, color.copy(alpha = 0.5f))
                )
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
