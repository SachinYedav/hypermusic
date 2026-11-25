package com.hypermusic.feature.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.ArrowLeft
import com.hypermusic.core.designsystem.icon.Check
import com.hypermusic.core.designsystem.icon.ChevronRight
import com.hypermusic.core.designsystem.icon.Languages
import com.hypermusic.core.designsystem.icon.Lyrics
import com.hypermusic.core.designsystem.icon.Share2
import com.hypermusic.core.designsystem.icon.X
import com.hypermusic.core.model.Track
import com.hypermusic.core.player.LyricsUiState
import com.hypermusic.core.utils.DynamicColors
import com.hypermusic.core.designsystem.theme.DarkBackground
import com.hypermusic.core.designsystem.theme.DarkBorder
import com.hypermusic.core.designsystem.theme.DarkSurface
import com.hypermusic.core.designsystem.theme.DarkText
import com.hypermusic.core.designsystem.theme.DarkTextMuted
import com.hypermusic.core.designsystem.theme.RadiusLg
import com.hypermusic.core.designsystem.theme.ShapeFull
import com.hypermusic.core.designsystem.theme.ShapeHandle
import com.hypermusic.core.designsystem.theme.ShapeMd
import com.hypermusic.core.designsystem.theme.ShapeSm

enum class LyricsViewMode {
    NORMAL,
    SELECTING,
    SHARE_PREVIEW
}

private val SUPPORTED_LANGUAGES = listOf(
    "en" to "English",
    "hi" to "Hindi",
    "es" to "Spanish",
    "ja" to "Japanese",
    "ko" to "Korean",
    "fr" to "French",
    "de" to "German",
    "pt" to "Portuguese",
    "ru" to "Russian",
    "ar" to "Arabic"
)

@Composable
fun LyricsSheet(
    track: Track?,
    lyricsState: LyricsUiState,
    colors: DynamicColors? = null,
    playbackPosition: Long = 0L,
    playbackPositionProvider: () -> Long = { playbackPosition },
    bottomPadding: Dp = 0.dp,
    isTranslationEnabled: Boolean = false,
    isTranslatingLyrics: Boolean = false,
    targetTranslationLang: String = "en",
    onToggleTranslation: () -> Unit = {},
    onSelectTranslationLanguage: (String) -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onCloseClick: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    var viewMode by remember { mutableStateOf(LyricsViewMode.NORMAL) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var isTranslateMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(track?.id) {
        selectedIndices.clear()
        viewMode = LyricsViewMode.NORMAL
    }

    BackHandler(enabled = viewMode != LyricsViewMode.NORMAL) {
        if (viewMode == LyricsViewMode.SHARE_PREVIEW) {
            viewMode = LyricsViewMode.SELECTING
        } else if (viewMode == LyricsViewMode.SELECTING) {
            selectedIndices.clear()
            viewMode = LyricsViewMode.NORMAL
        }
    }

    val backgroundModifier = if (colors != null) {
        Modifier.background(colors.gradientMid)
    } else {
        Modifier.background(DarkBackground)
    }

    val displayLines: List<DisplayLyricLine> = remember(lyricsState) {
        if (lyricsState is LyricsUiState.Success) {
            val lyrics = lyricsState.lyrics
            if (lyrics.isSynced && lyrics.lines.isNotEmpty()) {
                lyrics.lines.map { DisplayLyricLine(it.text, it.translatedText, it.timeMs) }
            } else {
                val rawLines = lyrics.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val rawTrans = lyrics.translatedPlainText?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
                rawLines.mapIndexed { index, text ->
                    DisplayLyricLine(
                        originalText = text,
                        translatedText = rawTrans?.getOrNull(index),
                        timeMs = null
                    )
                }
            }
        } else {
            emptyList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg))
            .then(backgroundModifier)
    ) {
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
                        .background(DarkText.copy(alpha = 0.25f))
                )
            }

            when (viewMode) {
                LyricsViewMode.NORMAL -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lyrics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onCloseClick) {
                            Icon(
                                imageVector = AppIcons.X,
                                contentDescription = "Close Lyrics",
                                modifier = Modifier.size(24.dp),
                                tint = DarkText
                            )
                        }
                    }
                }
                LyricsViewMode.SELECTING -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Select up to 5 lines",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                            Text(
                                text = when {
                                    selectedIndices.isEmpty() -> "Tap any line to start a verse quote"
                                    selectedIndices.size == 5 -> "Maximum verse length reached"
                                    else -> "Tap highlighted lines to expand quote"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkTextMuted
                            )
                        }

                        Surface(
                            shape = ShapeFull,
                            color = if (selectedIndices.isNotEmpty()) DarkText.copy(alpha = 0.16f) else DarkText.copy(alpha = 0.08f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "${selectedIndices.size}/5",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = DarkText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                selectedIndices.clear()
                                viewMode = LyricsViewMode.NORMAL
                            }
                        ) {
                            Icon(
                                imageVector = AppIcons.X,
                                contentDescription = "Cancel Selection",
                                modifier = Modifier.size(24.dp),
                                tint = DarkText
                            )
                        }
                    }
                }
                LyricsViewMode.SHARE_PREVIEW -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewMode = LyricsViewMode.SELECTING }) {
                            Icon(
                                imageVector = AppIcons.ArrowLeft,
                                contentDescription = "Back to selection",
                                modifier = Modifier.size(24.dp),
                                tint = DarkText
                            )
                        }

                        Text(
                            text = "Share Lyrics Card",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                selectedIndices.clear()
                                viewMode = LyricsViewMode.NORMAL
                            }
                        ) {
                            Icon(
                                imageVector = AppIcons.X,
                                contentDescription = "Close",
                                modifier = Modifier.size(24.dp),
                                tint = DarkText
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                color = DarkText.copy(alpha = 0.08f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (viewMode) {
                LyricsViewMode.NORMAL -> {
                    when (lyricsState) {
                        is LyricsUiState.Loading -> {
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
                                        color = DarkText,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "Fetching lyrics...",
                                        color = DarkTextMuted,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        is LyricsUiState.Success -> {
                            val lyrics = lyricsState.lyrics
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (lyrics.isSynced && lyrics.lines.isNotEmpty()) {
                                    val activeIndex by remember(lyrics.lines) {
                                        derivedStateOf {
                                            val currentPos = playbackPositionProvider()
                                            lyrics.lines.indexOfLast { it.timeMs <= currentPos }
                                        }
                                    }
                                    val listState = rememberLazyListState()

                                    LaunchedEffect(activeIndex) {
                                        if (activeIndex >= 0 && !listState.isScrollInProgress) {
                                            val targetScrollIndex = (activeIndex - 2).coerceAtLeast(0)
                                            listState.animateScrollToItem(targetScrollIndex)
                                        }
                                    }

                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 24.dp,
                                            end = 24.dp,
                                            top = 16.dp,
                                            bottom = bottomPadding + 96.dp
                                        )
                                    ) {
                                        itemsIndexed(lyrics.lines, key = { index, line -> "${line.timeMs}_$index" }) { index, line ->
                                            val isCurrent = index == activeIndex
                                            val targetColor = if (isCurrent) {
                                                DarkText
                                            } else {
                                                DarkText.copy(alpha = 0.45f)
                                            }
                                            val animatedColor by animateColorAsState(targetValue = targetColor, label = "lyricColor")

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(ShapeSm)
                                                    .clickable { onSeek(line.timeMs) }
                                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = line.text,
                                                    color = animatedColor,
                                                    style = if (isCurrent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                                )
                                                if (isTranslationEnabled && !line.translatedText.isNullOrBlank()) {
                                                    Text(
                                                        text = line.translatedText,
                                                        color = animatedColor.copy(alpha = if (isCurrent) 0.85f else 0.45f),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        lyrics.source?.let { source ->
                                            item {
                                                Spacer(modifier = Modifier.height(28.dp))
                                                Text(
                                                    text = "Lyrics provided by $source",
                                                    color = DarkTextMuted.copy(alpha = 0.6f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 24.dp,
                                            end = 24.dp,
                                            top = 16.dp,
                                            bottom = bottomPadding + 96.dp
                                        )
                                    ) {
                                        itemsIndexed(displayLines, key = { index, _ -> "plain_$index" }) { _, line ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = line.originalText,
                                                    color = DarkText,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (isTranslationEnabled && !line.translatedText.isNullOrBlank()) {
                                                    Text(
                                                        text = line.translatedText,
                                                        color = DarkTextMuted,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        lyrics.source?.let { source ->
                                            item {
                                                Spacer(modifier = Modifier.height(28.dp))
                                                Text(
                                                    text = "Lyrics provided by $source",
                                                    color = DarkTextMuted.copy(alpha = 0.6f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 24.dp)
                                        .padding(bottom = bottomPadding + 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box {
                                        Surface(
                                            onClick = { isTranslateMenuExpanded = true },
                                            shape = ShapeFull,
                                            color = if (isTranslationEnabled) {
                                                DarkText
                                            } else {
                                                DarkText.copy(alpha = 0.12f)
                                            },
                                            contentColor = if (isTranslationEnabled) {
                                                DarkBackground
                                            } else {
                                                DarkText
                                            },
                                            shadowElevation = if (isTranslationEnabled) 4.dp else 0.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (isTranslatingLyrics) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = if (isTranslationEnabled) {
                                                            DarkBackground
                                                        } else {
                                                            DarkText
                                                        }
                                                    )
                                                    Text(
                                                        text = "Translating...",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = AppIcons.Languages,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = if (isTranslationEnabled) {
                                                            "Translated (${targetTranslationLang.uppercase()})"
                                                        } else {
                                                            "Translate"
                                                        },
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = isTranslateMenuExpanded,
                                            onDismissRequest = { isTranslateMenuExpanded = false },
                                            shape = ShapeMd,
                                            modifier = Modifier
                                                .widthIn(min = 200.dp)
                                                .heightIn(max = 380.dp)
                                                .background(DarkSurface)
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "Off (Original Lyrics)",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (!isTranslationEnabled) FontWeight.Bold else FontWeight.Normal,
                                                        color = DarkText
                                                    )
                                                },
                                                trailingIcon = if (!isTranslationEnabled) {
                                                    {
                                                        Icon(
                                                            imageVector = AppIcons.Check,
                                                            contentDescription = null,
                                                            tint = DarkText,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                } else null,
                                                onClick = {
                                                    if (isTranslationEnabled) {
                                                        onToggleTranslation()
                                                    }
                                                    isTranslateMenuExpanded = false
                                                },
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                            )
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                color = DarkBorder
                                            )
                                            SUPPORTED_LANGUAGES.forEach { (code, name) ->
                                                val isCurrent = isTranslationEnabled && code == targetTranslationLang
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "$name (${code.uppercase()})",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                            color = DarkText
                                                        )
                                                    },
                                                    trailingIcon = if (isCurrent) {
                                                        {
                                                            Icon(
                                                                imageVector = AppIcons.Check,
                                                                contentDescription = null,
                                                                tint = DarkText,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    } else null,
                                                    onClick = {
                                                        onSelectTranslationLanguage(code)
                                                        if (!isTranslationEnabled) {
                                                            onToggleTranslation()
                                                        }
                                                        isTranslateMenuExpanded = false
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            selectedIndices.clear()
                                            viewMode = LyricsViewMode.SELECTING
                                        },
                                        shape = ShapeFull,
                                        color = DarkText.copy(alpha = 0.12f),
                                        contentColor = DarkText
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Share2,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Share",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is LyricsUiState.Empty -> {
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
                                        imageVector = AppIcons.Lyrics,
                                        contentDescription = null,
                                        tint = DarkTextMuted.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "Lyrics are not available for this song",
                                        color = DarkTextMuted,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        is LyricsUiState.Error -> {
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
                                        text = "Could not load lyrics",
                                        color = DarkTextMuted,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Button(
                                        onClick = onRetry,
                                        shape = ShapeSm,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DarkText,
                                            contentColor = DarkBackground
                                        )
                                    ) {
                                        Text("Retry", color = DarkBackground)
                                    }
                                }
                            }
                        }
                        is LyricsUiState.Idle -> {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                LyricsViewMode.SELECTING -> {
                    val hasActiveSelection = selectedIndices.isNotEmpty()
                    val minSelected = selectedIndices.minOrNull()
                    val maxSelected = selectedIndices.maxOrNull()

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 24.dp,
                                end = 24.dp,
                                top = 16.dp,
                                bottom = bottomPadding + 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(displayLines, key = { index, _ -> "select_$index" }) { index, line ->
                                val isSelected = selectedIndices.contains(index)
                                val isSelectable = if (!hasActiveSelection) {
                                    true
                                } else {
                                    val newMin = minOf(minSelected ?: index, index)
                                    val newMax = maxOf(maxSelected ?: index, index)
                                    newMax - newMin + 1 <= 5
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ShapeSm)
                                        .then(
                                            when {
                                                isSelected -> Modifier.background(DarkText.copy(alpha = 0.12f))
                                                hasActiveSelection && isSelectable -> Modifier.background(DarkText.copy(alpha = 0.04f))
                                                else -> Modifier
                                            }
                                        )
                                        .clickable {
                                            if (isSelected) {
                                                if (selectedIndices.size == 1) {
                                                    selectedIndices.clear()
                                                } else {
                                                    val minS = minSelected ?: index
                                                    val maxS = maxSelected ?: index
                                                    if (index == minS) {
                                                        selectedIndices.remove(minS)
                                                    } else if (index == maxS) {
                                                        selectedIndices.remove(maxS)
                                                    } else {
                                                        selectedIndices.clear()
                                                        selectedIndices.add(index)
                                                    }
                                                }
                                            } else {
                                                if (!hasActiveSelection) {
                                                    selectedIndices.add(index)
                                                } else {
                                                    val minS = minSelected ?: index
                                                    val maxS = maxSelected ?: index
                                                    val newMin = minOf(minS, index)
                                                    val newMax = maxOf(maxS, index)
                                                    if (newMax - newMin + 1 <= 5) {
                                                        selectedIndices.clear()
                                                        for (i in newMin..newMax) {
                                                            selectedIndices.add(i)
                                                        }
                                                    } else {
                                                        selectedIndices.clear()
                                                        selectedIndices.add(index)
                                                    }
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = line.originalText,
                                        color = when {
                                            isSelected -> DarkText
                                            hasActiveSelection && isSelectable -> DarkText.copy(alpha = 0.65f)
                                            hasActiveSelection -> DarkText.copy(alpha = 0.20f)
                                            else -> DarkText.copy(alpha = 0.65f)
                                        },
                                        style = if (isSelected) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (isTranslationEnabled && !line.translatedText.isNullOrBlank()) {
                                        Text(
                                            text = line.translatedText,
                                            color = when {
                                                isSelected -> DarkText.copy(alpha = 0.85f)
                                                hasActiveSelection && isSelectable -> DarkText.copy(alpha = 0.45f)
                                                hasActiveSelection -> DarkText.copy(alpha = 0.12f)
                                                else -> DarkText.copy(alpha = 0.45f)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            onClick = {
                                if (selectedIndices.isNotEmpty()) {
                                    viewMode = LyricsViewMode.SHARE_PREVIEW
                                }
                            },
                            enabled = selectedIndices.isNotEmpty(),
                            shape = ShapeFull,
                            color = if (selectedIndices.isNotEmpty()) {
                                DarkText
                            } else {
                                DarkText.copy(alpha = 0.12f)
                            },
                            contentColor = if (selectedIndices.isNotEmpty()) {
                                DarkBackground
                            } else {
                                DarkText.copy(alpha = 0.45f)
                            },
                            shadowElevation = if (selectedIndices.isNotEmpty()) 4.dp else 0.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = bottomPadding + 20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Next (${selectedIndices.size}/5)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = AppIcons.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                LyricsViewMode.SHARE_PREVIEW -> {
                    val sortedSelectedLines = remember(selectedIndices.toList(), displayLines) {
                        selectedIndices.sorted().mapNotNull { index -> displayLines.getOrNull(index) }
                    }

                    LyricsSharePreviewScreen(
                        track = track,
                        selectedLines = sortedSelectedLines,
                        colors = colors,
                        isTranslationEnabled = isTranslationEnabled,
                        bottomPadding = bottomPadding
                    )
                }
            }
        }
    }
}
