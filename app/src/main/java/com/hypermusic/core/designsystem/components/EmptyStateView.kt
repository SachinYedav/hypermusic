package com.hypermusic.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.icon.AlertCircle
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.RotateCcw
import com.hypermusic.core.utils.ErrorClassifier
import com.hypermusic.core.utils.FriendlyError
import com.hypermusic.core.designsystem.theme.ShapeMd
import com.hypermusic.core.designsystem.theme.ShapeSm

/** Empty-state placeholder with an optional action. */
@Composable
fun EmptyStateView(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    isError: Boolean = false,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val resolvedIcon = icon ?: if (isError) AppIcons.AlertCircle else null

        if (resolvedIcon != null) {
            val badgeBg = if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
            val iconTint = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(color = badgeBg, shape = ShapeMd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = resolvedIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )
        }

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAction,
                shape = ShapeSm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isError) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
            ) {
                if (actionIcon != null) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                }
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** Error placeholder using [ErrorClassifier] and [FriendlyError]. */
@Composable
fun ErrorStateView(
    error: FriendlyError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    EmptyStateView(
        title = error.title,
        subtitle = error.description,
        icon = AppIcons.AlertCircle,
        isError = true,
        actionLabel = if (error.canRetry && onRetry != null) "Try Again" else null,
        actionIcon = if (error.canRetry && onRetry != null) AppIcons.RotateCcw else null,
        onAction = onRetry,
        modifier = modifier
    )
}

@Composable
fun ErrorStateView(
    errorMessage: String?,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val friendly = ErrorClassifier.classify(errorMessage)
    ErrorStateView(
        error = friendly,
        modifier = modifier,
        onRetry = onRetry
    )
}

@Composable
fun ErrorStateView(
    throwable: Throwable?,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val friendly = ErrorClassifier.classify(throwable)
    ErrorStateView(
        error = friendly,
        modifier = modifier,
        onRetry = onRetry
    )
}
