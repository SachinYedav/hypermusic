package com.hypermusic.feature.settings.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hypermusic.BuildConfig
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.ArrowLeft
import com.hypermusic.core.designsystem.icon.BookOpen
import com.hypermusic.core.designsystem.icon.Bug
import com.hypermusic.core.designsystem.icon.GitBranch
import com.hypermusic.core.designsystem.icon.Info
import com.hypermusic.core.designsystem.icon.ShieldCheck
import com.hypermusic.feature.settings.components.SettingsActionRow
import com.hypermusic.feature.settings.components.SettingsSection
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLegal: () -> Unit,
    onNavigateToOpenSource: () -> Unit
) {
    val context = LocalContext.current

    val scrollState = rememberScrollState()
    val isScrolled by remember { derivedStateOf { scrollState.value > 0 } }
    val dividerAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "aboutDividerAlpha"
    )

    ScreenWrapper(disableSafeAreaTop = true) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxSize()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "About HyperMusic",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = AppIcons.ArrowLeft,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isScrolled) MaterialTheme.colorScheme.background else Color.Transparent
                    )
                )

                if (dividerAlpha > 0f) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = dividerAlpha },
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = LocalBottomContentPadding.current)
                ) {
                    SettingsSection {
                        SettingsActionRow(
                            icon = AppIcons.Info,
                            label = "App Version",
                            valueLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            hideChevron = true
                        )
                        SettingsActionRow(
                            icon = AppIcons.ShieldCheck,
                            label = "Legal & Privacy",
                            onClick = onNavigateToLegal
                        )
                        SettingsActionRow(
                            icon = AppIcons.BookOpen,
                            label = "Open Source Licenses",
                            onClick = onNavigateToOpenSource
                        )
                        SettingsActionRow(
                            icon = AppIcons.GitBranch,
                            label = "Source Code (GitHub)",
                            isExternal = true,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PROJECT_REPOSITORY_URL))
                                context.startActivity(intent)
                            }
                        )
                        SettingsActionRow(
                            icon = AppIcons.Bug,
                            label = "Report a Bug",
                            isExternal = true,
                            isLast = true,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.PROJECT_REPOSITORY_URL}/issues"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}
