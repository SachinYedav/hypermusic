package com.hypermusic.feature.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SuggestionRow(
    suggestion: String,
    currentQuery: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        
        Text(
            text = buildAnnotatedString {
                val queryLower = currentQuery.trim().lowercase()
                val suggestionLower = suggestion.lowercase()
                
                if (queryLower.isNotEmpty() && suggestionLower.startsWith(queryLower)) {
                    append(
                        androidx.compose.ui.text.AnnotatedString(
                            text = suggestion.substring(0, queryLower.length),
                            spanStyle = SpanStyle(fontWeight = FontWeight.Bold)
                        )
                    )
                    append(suggestion.substring(queryLower.length))
                } else {
                    append(suggestion)
                }
            },
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp
        )
    }
}
