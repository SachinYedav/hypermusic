package com.hypermusic.feature.settings.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.BuildConfig
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.feature.settings.AppUpdateViewModel
import com.hypermusic.feature.settings.UpdateUiState
import com.hypermusic.feature.settings.components.SettingsActionRow
import com.hypermusic.feature.settings.components.SettingsSection
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import com.hypermusic.feature.shared.state.ToastType
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import com.hypermusic.core.designsystem.theme.ShapeLg
import com.hypermusic.core.designsystem.theme.ShapeMd
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdatesScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppUpdateViewModel = koinViewModel(),
    toastViewModel: GlobalToastViewModel = koinInject()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is UpdateUiState.Error) {
            toastViewModel.showToast(
                message = "Unable to check for updates. Check internet connection.",
                type = ToastType.ERROR
            )
        }
    }

    val scrollState = rememberScrollState()
    val isScrolled by remember { derivedStateOf { scrollState.value > 0 } }
    val dividerAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "appUpdatesDividerAlpha"
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
                            text = "App Updates",
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SettingsSection(title = "Version Info") {
                        SettingsActionRow(
                            icon = AppIcons.Info,
                            label = "Installed Version",
                            valueLabel = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            hideChevron = true,
                            isLast = true
                        )
                    }

                    when (uiState) {
                        is UpdateUiState.Checking -> {
                            SettingsSection {
                                SettingsActionRow(
                                    icon = AppIcons.RotateCcw,
                                    label = "Checking for updates...",
                                    hideChevron = true,
                                    isLast = true
                                )
                            }
                        }
                        is UpdateUiState.UpToDate -> {
                            SettingsSection {
                                SettingsActionRow(
                                    icon = AppIcons.CheckCircle2,
                                    label = "App is Up to Date",
                                    valueLabel = "Latest",
                                    hideChevron = true,
                                    isLast = false
                                )
                                SettingsActionRow(
                                    icon = AppIcons.RotateCcw,
                                    label = "Check Again",
                                    hideChevron = true,
                                    isLast = true,
                                    onClick = { viewModel.checkForUpdates() }
                                )
                            }
                        }
                        is UpdateUiState.NoPublishedRelease -> {
                            SettingsSection {
                                SettingsActionRow(
                                    icon = AppIcons.Info,
                                    label = "Development / Preview Build",
                                    valueLabel = "No public releases",
                                    hideChevron = true,
                                    isLast = false
                                )
                                SettingsActionRow(
                                    icon = AppIcons.RotateCcw,
                                    label = "Check Again",
                                    hideChevron = true,
                                    isLast = true,
                                    onClick = { viewModel.checkForUpdates() }
                                )
                            }
                        }
                        is UpdateUiState.Error -> {
                            SettingsSection {
                                SettingsActionRow(
                                    icon = AppIcons.AlertCircle,
                                    label = "Check Failed",
                                    valueLabel = "Tap to retry",
                                    hideChevron = true,
                                    isLast = true,
                                    onClick = { viewModel.checkForUpdates() }
                                )
                            }
                        }
                        is UpdateUiState.UpdateAvailable -> {
                            // Rendered below in dedicated update banner & changelog
                        }
                        is UpdateUiState.Idle -> {}
                    }

                    if (uiState is UpdateUiState.UpdateAvailable) {
                        val updateData = (uiState as UpdateUiState.UpdateAvailable).updateData

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            shape = ShapeLg,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.ArrowUpCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Update Available!",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Version ${updateData.latestVersion} is ready to download.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        if (updateData.downloadUrl.isNotBlank()) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateData.downloadUrl)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ShapeMd,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(
                                        text = "Download Update",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = AppIcons.ExternalLink,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        updateData.advisory?.let { advisory ->
                            SettingsSection(title = advisory.badge ?: "Release Advisory") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.tertiary)
                                        )
                                        Text(
                                            text = advisory.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (advisory.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = advisory.description,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (updateData.changelog.isNotEmpty()) {
                            SettingsSection(title = "What's New in v${updateData.latestVersion}") {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    updateData.changelog.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = if (index == updateData.changelog.lastIndex) 0.dp else 12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 7.dp)
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = item.text,
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(LocalBottomContentPadding.current))
                }
            }
        }
    }
}
