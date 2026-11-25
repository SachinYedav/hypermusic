package com.hypermusic.feature.equalizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import com.hypermusic.core.designsystem.theme.ShapeHandle
import com.hypermusic.core.designsystem.theme.ShapeSm
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import org.koin.androidx.compose.koinViewModel
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onNavigateBack: () -> Unit,
    viewModel: EqualizerViewModel = koinViewModel()
) {
    val isSupported by viewModel.isSupported.collectAsState()
    val isEnabled by viewModel.isEnabled.collectAsState()
    val bands by viewModel.bands.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val currentPreset by viewModel.currentPreset.collectAsState()

    val bassBoostSupported by viewModel.bassBoostSupported.collectAsState()
    val bassBoostStrength by viewModel.bassBoostStrength.collectAsState()
    val virtualizerSupported by viewModel.virtualizerSupported.collectAsState()
    val virtualizerStrength by viewModel.virtualizerStrength.collectAsState()

    val scrollState = rememberScrollState()
    val isScrolled by remember { derivedStateOf { scrollState.value > 0 } }
    val dividerAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "equalizerDividerAlpha"
    )

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TopAppBar(
                    modifier = Modifier.widthIn(max = 800.dp),
                    title = {
                        Text(
                            text = "Equalizer",
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
                    actions = {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { viewModel.setEnabled(it) },
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .semantics { contentDescription = "Enable equalizer" },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isScrolled) MaterialTheme.colorScheme.background else Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )

                if (dividerAlpha > 0f) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = dividerAlpha },
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (!isSupported) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Equalizer is not supported on this device or player session.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
            if (presets.isNotEmpty()) {
                Text(
                    text = "Recommended presets",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                val chunkedPresets = presets.withIndex().chunked(3)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .alpha(if (isEnabled) 1f else 0.5f)
                ) {
                    chunkedPresets.forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { (index, presetName) ->
                                val isSelected = currentPreset.toInt() == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(ShapeSm)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable(enabled = isEnabled) { viewModel.usePreset(index.toShort()) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = presetName,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Manual Adjustment",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .alpha(if (isEnabled) 1f else 0.5f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                bands.forEach { band ->
                    val freqStr = if (band.centerFrequency >= 1000000) {
                        String.format(java.util.Locale.ROOT, "%.1fkHz", band.centerFrequency / 1000000f)
                    } else {
                        "${band.centerFrequency / 1000}Hz"
                    }
                    val dbLevel = band.currentLevel / 100f

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            VerticalSlider(
                                value = band.currentLevel.toFloat(),
                                valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                                onValueChange = { newValue ->
                                    if (isEnabled) viewModel.setBandLevel(band.index, newValue.toInt().toShort())
                                },
                                contentDescription = "$freqStr band",
                                enabled = isEnabled
                            )
                        }

                        Text(
                            text = freqStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            text = if (dbLevel > 0) String.format(java.util.Locale.ROOT, "+%.0f dB", dbLevel) else String.format(java.util.Locale.ROOT, "%.0f dB", dbLevel),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (dbLevel != 0f && isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (bassBoostSupported || virtualizerSupported) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .alpha(if (isEnabled) 1f else 0.5f)
                ) {
                    Text(
                        text = "Enhance Sound",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "Put in earphones before adjusting sound effects",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        if (bassBoostSupported) {
                            SimpleRotaryKnob(
                                title = "Bass",
                                value = bassBoostStrength / 1000f,
                                onValueChange = { if (isEnabled) viewModel.setBassBoostStrength((it * 1000).toInt().toShort()) },
                                enabled = isEnabled
                            )
                        }
                        if (virtualizerSupported) {
                            SimpleRotaryKnob(
                                title = "Immersive Audio",
                                value = virtualizerStrength / 1000f,
                                onValueChange = { if (isEnabled) viewModel.setVirtualizerStrength((it * 1000).toInt().toShort()) },
                                enabled = isEnabled
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(LocalBottomContentPadding.current + 16.dp))
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true
) {
    androidx.compose.ui.layout.Layout(
        content = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                modifier = if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier,
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                if (enabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            .border(
                                width = 2.dp,
                                color = if (enabled) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                },
                track = { sliderState ->
                    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
                    val fraction = ((sliderState.value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .clip(ShapeHandle)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(ShapeHandle)
                                .background(
                                    if (enabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(
            androidx.compose.ui.unit.Constraints.fixed(constraints.maxHeight, constraints.maxWidth)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelativeWithLayer(
                x = -(placeable.width - constraints.maxWidth) / 2,
                y = -(placeable.height - constraints.maxHeight) / 2
            ) {
                rotationZ = -90f
            }
        }
    }
}

@Composable
fun SimpleRotaryKnob(
    title: String,
    value: Float, // 0f to 1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var angle by remember(value) { mutableFloatStateOf(value * 270f) }
    var isFocused by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val inactiveDotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val knobBaseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val knobInnerColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .semantics {
                    contentDescription = title
                    stateDescription = "${(value * 100).toInt()}%"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                    if (!enabled) {
                        disabled()
                    } else {
                        setProgress { targetValue ->
                            val clamped = targetValue.coerceIn(0f, 1f)
                            angle = clamped * 270f
                            onValueChange(clamped)
                            true
                        }
                    }
                }
                .onFocusChanged { isFocused = it.isFocused }
                .onKeyEvent { keyEvent ->
                    if (!enabled || keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (value >= 1f) return@onKeyEvent false
                            val newVal = (value + 0.05f).coerceIn(0f, 1f)
                            angle = newVal * 270f
                            onValueChange(newVal)
                            true
                        }
                        Key.DirectionDown -> {
                            if (value <= 0f) return@onKeyEvent false
                            val newVal = (value - 0.05f).coerceIn(0f, 1f)
                            angle = newVal * 270f
                            onValueChange(newVal)
                            true
                        }
                        else -> false
                    }
                }
                .focusable(enabled = enabled)
                .then(
                    if (isFocused && enabled) Modifier.border(2.dp, primaryColor, CircleShape)
                    else Modifier
                )
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, dragAmount ->
                        if (!enabled) return@detectDragGestures
                        change.consume()
                        val delta = dragAmount.x - dragAmount.y
                        val newAngle = (angle + delta).coerceIn(0f, 270f)
                        angle = newAngle
                        onValueChange(newAngle / 270f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2

                val dotCount = 27
                val startAngle = 135f
                val sweepAngle = 270f
                val angleStep = sweepAngle / (dotCount - 1)

                val dotsRadius = radius - 4.dp.toPx()

                for (i in 0 until dotCount) {
                    val dotAngle = startAngle + (i * angleStep)
                    val dotAngleRad = Math.toRadians(dotAngle.toDouble())

                    val dotX = center.x + dotsRadius * cos(dotAngleRad).toFloat()
                    val dotY = center.y + dotsRadius * sin(dotAngleRad).toFloat()

                    val isActive = (i * angleStep) <= angle

                    drawCircle(
                        color = if (isActive) primaryColor else inactiveDotColor,
                        radius = if (isActive) 3.dp.toPx() else 1.5.dp.toPx(),
                        center = Offset(dotX, dotY)
                    )
                }

                val knobRadius = dotsRadius - 10.dp.toPx()
                drawCircle(
                    color = knobBaseColor,
                    radius = knobRadius,
                    center = center
                )

                val innerCircleRadius = knobRadius - 10.dp.toPx()
                drawCircle(
                    color = knobInnerColor,
                    radius = innerCircleRadius,
                    center = center
                )

                val currentAngleRad = Math.toRadians((startAngle + angle).toDouble())
                val innerOffset = innerCircleRadius - 20.dp.toPx()
                val outerOffset = innerCircleRadius - 6.dp.toPx()

                val startX = center.x + innerOffset * cos(currentAngleRad).toFloat()
                val startY = center.y + innerOffset * sin(currentAngleRad).toFloat()
                val stopX = center.x + outerOffset * cos(currentAngleRad).toFloat()
                val stopY = center.y + outerOffset * sin(currentAngleRad).toFloat()

                drawLine(
                    color = primaryColor.copy(alpha = 0.8f),
                    start = Offset(startX, startY),
                    end = Offset(stopX, stopY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            Text(
                text = "${(value * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
