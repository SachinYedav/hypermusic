package com.hypermusic.core.designsystem.theme

import androidx.compose.ui.graphics.Color

enum class BackgroundTheme(
    val id: String,
    val label: String,
    val color: Color
) {
    AUTO("auto", "Auto", Color.Transparent),
    CRIMSON("crimson", "Crimson", BrandRed),
    PURPLE("purple", "Purple", Color(0xFF8A2BE2)),
    MIDNIGHT("midnight", "Midnight", Color(0xFF191970)),
    EMERALD("emerald", "Emerald", Color(0xFF2E8B57)),
    SUNSET("sunset", "Sunset", Color(0xFFFF4500)),
    OFF("off", "Off", Color.Transparent);

    companion object {
        val curatedPalette: List<BackgroundTheme> = listOf(
            CRIMSON,
            PURPLE,
            MIDNIGHT,
            EMERALD,
            SUNSET
        )

        /** Chooses one auto theme per app process so tab changes keep the same background. */
        val sessionAutoTheme: BackgroundTheme by lazy {
            curatedPalette.random()
        }

        fun fromString(theme: String?): BackgroundTheme {
            if (theme.isNullOrBlank()) return AUTO
            return entries.find {
                it.id.equals(theme, ignoreCase = true) || it.name.equals(theme, ignoreCase = true)
            } ?: AUTO
        }

        fun resolveActiveTheme(theme: String?): BackgroundTheme {
            val selected = fromString(theme)
            return if (selected == AUTO) sessionAutoTheme else selected
        }
    }
}
