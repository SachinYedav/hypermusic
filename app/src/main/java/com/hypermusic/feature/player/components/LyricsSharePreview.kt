package com.hypermusic.feature.player.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.Check
import com.hypermusic.core.designsystem.icon.Share2
import com.hypermusic.core.model.Track
import com.hypermusic.core.utils.DynamicColors
import com.hypermusic.core.designsystem.theme.DarkBackground
import com.hypermusic.core.designsystem.theme.DarkText
import com.hypermusic.core.designsystem.theme.DarkTextMuted
import com.hypermusic.core.designsystem.theme.ShapeFull
import com.hypermusic.core.designsystem.theme.ShapeLg
import com.hypermusic.core.designsystem.theme.ShapeSm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Immutable
data class DisplayLyricLine(
    val originalText: String,
    val translatedText: String?,
    val timeMs: Long?
)

@Immutable
data class ShareCardTheme(
    val name: String,
    val backgroundBrush: Brush,
    val borderColor: Color,
    val textColor: Color,
    val previewColor: Color,
    val isLight: Boolean = false
)

fun getShareCardThemes(colors: DynamicColors?): List<ShareCardTheme> {
    val dynamicGradient = if (colors != null) {
        Brush.linearGradient(listOf(colors.gradientTop, colors.gradientDark))
    } else {
        Brush.linearGradient(listOf(Color(0xFF232328), Color(0xFF141417)))
    }
    val dynamicBorder = colors?.dominant?.copy(alpha = 0.5f) ?: Color(0xFF383842)
    val dynamicText = colors?.titleText ?: DarkText
    val dynamicPreview = colors?.dominant ?: Color(0xFF232328)

    return listOf(
        ShareCardTheme(
            name = "Dynamic Artwork",
            backgroundBrush = dynamicGradient,
            borderColor = dynamicBorder,
            textColor = dynamicText,
            previewColor = dynamicPreview
        ),
        ShareCardTheme(
            name = "Obsidian Matte",
            backgroundBrush = Brush.verticalGradient(listOf(Color(0xFF0F0F12), Color(0xFF18181D))),
            borderColor = Color(0xFF2A2A30),
            textColor = DarkText,
            previewColor = Color(0xFF0F0F12)
        ),
        ShareCardTheme(
            name = "Midnight Slate",
            backgroundBrush = Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B))),
            borderColor = Color(0xFF334155),
            textColor = Color(0xFFF1F5F9),
            previewColor = Color(0xFF0F172A)
        ),
        ShareCardTheme(
            name = "Crimson Velvet",
            backgroundBrush = Brush.linearGradient(listOf(Color(0xFF3B0A14), Color(0xFF1F050A))),
            borderColor = Color(0xFF631022),
            textColor = Color(0xFFFFF1F2),
            previewColor = Color(0xFF3B0A14)
        ),
        ShareCardTheme(
            name = "Deep Espresso",
            backgroundBrush = Brush.linearGradient(listOf(Color(0xFF261610), Color(0xFF140B08))),
            borderColor = Color(0xFF482A1E),
            textColor = Color(0xFFFAF5EE),
            previewColor = Color(0xFF261610)
        ),
        ShareCardTheme(
            name = "Forest Moss",
            backgroundBrush = Brush.linearGradient(listOf(Color(0xFF0D261C), Color(0xFF07140F))),
            borderColor = Color(0xFF1B4D39),
            textColor = Color(0xFFF0FDF4),
            previewColor = Color(0xFF0D261C)
        ),
        ShareCardTheme(
            name = "Royal Indigo",
            backgroundBrush = Brush.linearGradient(listOf(Color(0xFF17153B), Color(0xFF0C0B20))),
            borderColor = Color(0xFF2F2A75),
            textColor = Color(0xFFF3EEFA),
            previewColor = Color(0xFF17153B)
        ),
        ShareCardTheme(
            name = "Paper Minimal",
            backgroundBrush = Brush.linearGradient(listOf(Color(0xFFF5F5F0), Color(0xFFE8E6DF))),
            borderColor = Color(0xFFD5D3CA),
            textColor = Color(0xFF121212),
            previewColor = Color(0xFFF5F5F0),
            isLight = true
        )
    )
}

suspend fun captureAndShareLyricsBitmap(
    context: Context,
    graphicsLayer: GraphicsLayer
) {
    try {
        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
        val cacheDir = File(context.cacheDir, "shared_lyrics").apply { mkdirs() }
        val file = File(cacheDir, "lyrics_${System.currentTimeMillis()}.png")
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Lyrics Card"))
    } catch (_: Exception) {
    }
}

fun shareLyricsText(
    context: Context,
    track: Track?,
    lines: List<DisplayLyricLine>,
    isTranslationEnabled: Boolean
) {
    val linesText = lines.joinToString("\n") { line ->
        if (isTranslationEnabled && !line.translatedText.isNullOrBlank()) {
            "\"${line.originalText}\"\n(${line.translatedText})"
        } else {
            "\"${line.originalText}\""
        }
    }
    val trackTitle = track?.title ?: "Unknown Track"
    val trackArtist = track?.artist ?: "Unknown Artist"
    val shareText = "$linesText\n\n— $trackTitle by $trackArtist\nShared via HyperMusic"
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Lyrics"))
}

@Composable
fun LyricsShareCard(
    modifier: Modifier = Modifier,
    track: Track?,
    selectedLines: List<DisplayLyricLine>,
    theme: ShareCardTheme,
    isTranslationEnabled: Boolean
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(theme.backgroundBrush)
            .border(BorderStroke(1.dp, theme.borderColor.copy(alpha = 0.4f)), ShapeLg)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(track?.artwork)
                        .allowHardware(false)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(ShapeSm)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "Unknown Track",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = theme.textColor.copy(alpha = 0.12f)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                selectedLines.forEach { line ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = line.originalText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.textColor
                        )
                        if (isTranslationEnabled && !line.translatedText.isNullOrBlank()) {
                            Text(
                                text = line.translatedText,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textColor.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = theme.textColor.copy(alpha = 0.12f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HYPERMUSIC",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = theme.textColor.copy(alpha = 0.5f)
                )
                Text(
                    text = "Stream • Ad-Free",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textColor.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun LyricsSharePreviewScreen(
    modifier: Modifier = Modifier,
    track: Track?,
    selectedLines: List<DisplayLyricLine>,
    colors: DynamicColors?,
    isTranslationEnabled: Boolean,
    bottomPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    val themes = remember(colors) { getShareCardThemes(colors) }
    var selectedThemeIndex by remember { mutableIntStateOf(0) }
    val currentTheme = themes.getOrElse(selectedThemeIndex) { themes.first() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .drawWithContent {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                }
        ) {
            LyricsShareCard(
                track = track,
                selectedLines = selectedLines,
                theme = currentTheme,
                isTranslationEnabled = isTranslationEnabled
            )
        }

        Text(
            text = "Card Theme",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = DarkTextMuted
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(themes, key = { _, theme -> theme.name }) { index, theme ->
                val isSelected = index == selectedThemeIndex
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, DarkText, CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(theme.previewColor)
                            .then(
                                if (!isSelected) {
                                    Modifier.border(1.dp, DarkText.copy(alpha = 0.25f), CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { selectedThemeIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        val checkScale by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "checkScale"
                        )
                        val checkAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            label = "checkAlpha"
                        )
                        if (checkScale > 0.05f) {
                            Icon(
                                imageVector = AppIcons.Check,
                                contentDescription = null,
                                tint = if (theme.isLight) DarkBackground else DarkText,
                                modifier = Modifier
                                    .size(14.dp)
                                    .graphicsLayer {
                                        scaleX = checkScale
                                        scaleY = checkScale
                                        alpha = checkAlpha
                                    }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        captureAndShareLyricsBitmap(context, graphicsLayer)
                    }
                },
                shape = ShapeFull,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkText,
                    contentColor = DarkBackground
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Share2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Share Image",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = {
                    shareLyricsText(context, track, selectedLines, isTranslationEnabled)
                },
                shape = ShapeFull,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkText.copy(alpha = 0.12f),
                    contentColor = DarkText
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = "Share Text",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(bottomPadding + 24.dp))
    }
}
