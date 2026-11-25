package com.hypermusic.feature.shared.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.hypermusic.core.designsystem.theme.ShapeLg
import com.hypermusic.core.designsystem.theme.ShapeXs
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.model.TextRun

/**
 * Reusable expandable biography card supporting structured text runs with native link annotations.
 * Shared across ArtistScreen and RelatedSheet.
 */
@Composable
fun ArtistBioSection(
    title: String,
    description: String? = null,
    runs: List<TextRun>? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val totalLength = remember(description, runs) {
        runs?.sumOf { it.text.length } ?: (description?.length ?: 0)
    }
    val isLongText = totalLength > 220
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = ShapeLg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            val annotatedText = remember(description, runs, primaryColor) {
                buildStructuredBioText(description, runs, primaryColor)
            }
            Text(
                text = annotatedText,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded || !isLongText) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            if (isLongText) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isExpanded) "Show less" else "Read more",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor,
                    modifier = Modifier
                        .clip(ShapeXs)
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        }
    }
}

fun buildStructuredBioText(
    fallbackDescription: String?,
    runs: List<TextRun>?,
    primaryColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        if (!runs.isNullOrEmpty()) {
            for (run in runs) {
                val url = run.url
                if (!url.isNullOrEmpty()) {
                    val targetUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                    withLink(
                        LinkAnnotation.Url(
                            url = targetUrl,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = primaryColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) {
                        append(run.text)
                    }
                } else {
                    append(run.text)
                }
            }
        } else if (!fallbackDescription.isNullOrEmpty()) {
            append(fallbackDescription)
        }
    }
}
