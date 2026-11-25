package com.hypermusic.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.designsystem.theme.BackgroundTheme
import org.koin.compose.koinInject

@Composable
fun ScreenWrapper(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    enableAmbientGlow: Boolean = false,
    disableSafeAreaTop: Boolean = false,
    disableSafeAreaBottom: Boolean = true,
    content: @Composable () -> Unit
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    val topPadding = if (disableSafeAreaTop) 0.dp else insets.calculateTopPadding()
    val bottomPadding = if (disableSafeAreaBottom) 0.dp else insets.calculateBottomPadding()

    val backgroundModifier = if (enableAmbientGlow) {
        Modifier.ambientGlowBackground(backgroundColor)
    } else {
        Modifier.background(backgroundColor)
    }

    val contentColor = if (backgroundColor == MaterialTheme.colorScheme.background) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.contentColorFor(backgroundColor)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {
            content()
        }
    }
}

@Composable
private fun Modifier.ambientGlowBackground(
    backgroundColor: Color,
    preferencesManager: PreferencesManager = koinInject()
): Modifier {
    val userSettings by preferencesManager.userSettingsFlow.collectAsState(initial = null)
    val isDark = when (userSettings?.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val activeTheme = BackgroundTheme.resolveActiveTheme(userSettings?.backgroundTheme)

    val targetGlowColor = if (activeTheme == BackgroundTheme.OFF) {
        backgroundColor
    } else {
        val glowAlpha = if (isDark) 0.40f else 0.18f
        activeTheme.color.copy(alpha = glowAlpha)
    }

    val animatedGlowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(durationMillis = 600),
        label = "ambientGlowColor"
    )

    val glowHeightPx = with(LocalDensity.current) { 450.dp.toPx() }

    return this.background(
        brush = Brush.verticalGradient(
            colors = listOf(animatedGlowColor, backgroundColor),
            startY = 0f,
            endY = glowHeightPx
        )
    )
}
