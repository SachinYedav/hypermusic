package com.hypermusic.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.designsystem.theme.ShapeFull
import com.hypermusic.core.designsystem.theme.ShapeMd

@Composable
fun LibrarySortBar(
    currentSort: String,
    onSortSelected: (String) -> Unit,
    isGridView: Boolean,
    onToggleViewMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val sortOptions = listOf("Recently played", "Recently added", "Alphabetical")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(ShapeFull)
                    .clickable { expanded = true }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = AppIcons.ArrowUpDown,
                    contentDescription = "Sort",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentSort,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = ShapeMd,
                modifier = Modifier
                    .widthIn(min = 180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                sortOptions.forEach { option ->
                    val isSelected = currentSort == option || (currentSort == "Recents" && option == "Recently played")
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            expanded = false
                            onSortSelected(option)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }

        IconButton(onClick = onToggleViewMode) {
            Icon(
                imageVector = if (isGridView) AppIcons.List else AppIcons.LayoutGrid,
                contentDescription = "Toggle View Mode",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
