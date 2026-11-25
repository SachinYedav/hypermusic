package com.hypermusic.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.X
import com.hypermusic.core.designsystem.theme.ShapeSm
import kotlinx.coroutines.launch

/** Shows filter chips with the active chip first and a clear action. */
@Composable
fun <T> AppFilterChipsRow(
    chips: List<T>,
    activeChip: T?,
    onChipSelect: (T?) -> Unit,
    titleProvider: (T) -> String,
    modifier: Modifier = Modifier,
    isEqual: (T, T) -> Boolean = { a, b -> a == b }
) {
    if (chips.isEmpty()) return

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val orderedChips = remember(chips, activeChip) {
        if (activeChip != null) {
            val otherChips = chips.filter { !isEqual(it, activeChip) }
            listOf(activeChip) + otherChips
        } else {
            chips
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val inactiveBg = if (isDark) Color(0xFF1E2939) else MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (activeChip != null) {
            Box(
                modifier = Modifier
                    .clip(ShapeSm)
                    .background(inactiveBg)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), ShapeSm)
                    .clickable {
                        onChipSelect(null)
                        coroutineScope.launch { scrollState.animateScrollTo(0) }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.X,
                    contentDescription = "Clear filter",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        orderedChips.forEach { chip ->
            val isSelected = activeChip != null && isEqual(activeChip, chip)

            val backgroundColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSurface else inactiveBg,
                label = "ChipBgColor"
            )
            val textColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                label = "ChipTextColor"
            )
            val borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

            Box(
                modifier = Modifier
                    .clip(ShapeSm)
                    .background(backgroundColor)
                    .then(
                        if (isSelected) Modifier else Modifier.border(1.dp, borderColor, ShapeSm)
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = {
                            onChipSelect(if (isSelected) null else chip)
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = titleProvider(chip),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}
