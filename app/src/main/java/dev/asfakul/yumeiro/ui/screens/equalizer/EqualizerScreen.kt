/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.equalizer

import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.player.equalizer.EqualizerBand
import dev.asfakul.yumeiro.player.equalizer.EqualizerState
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.DarkOutlineVariant
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceVariant
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.SpotifyGreenBright
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    state: EqualizerState,
    onBackClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onBandLevelChange: (Short, Short) -> Unit,
    onPresetSelect: (String) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    onResetToDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("equalizer_screen_root"),
        topBar = {
            dev.asfakul.yumeiro.ui.components.SubPageHeader(
                title = "Equalizer",
                onBack = onBackClick,
                subtitle = "Shape how every song sounds",
                handleBack = false,
                modifier = Modifier.testTag("equalizer_back_button"),
            ) {
                IconButton(
                    onClick = onResetToDefault,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("equalizer_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset to flat",
                        tint = TextSecondary
                    )
                }
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { innerPadding ->
        LazyColumn(state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("equalizer"), 
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master Switch Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("equalizer_master_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassFill),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (state.isEnabled) SpotifyGreen.copy(alpha = 0.6f) else DarkOutline
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state.isEnabled) SpotifyGreen else DarkSurfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = if (state.isEnabled) OnAccent else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Equalizer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (state.isEnabled) "On · applied to everything you play" else "Off · you hear the original sound",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.isEnabled) SpotifyGreen else TextTertiary
                                )
                            }
                        }

                        Switch(
                            checked = state.isEnabled,
                            onCheckedChange = onToggleEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnAccent,
                                checkedTrackColor = SpotifyGreen,
                                uncheckedThumbColor = TextTertiary,
                                uncheckedTrackColor = DarkSurfaceVariant
                            ),
                            modifier = Modifier.testTag("equalizer_master_switch")
                        )
                    }
                }
            }

            // Frequency Response Visualizer Canvas
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassFill),
                    border = null
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "音響 · RESPONSE",
                                style = MaterialTheme.typography.labelMedium,
                                color = SpotifyGreen,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = state.currentPreset,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        EqualizerFrequencyCurve(
                            bands = state.bands,
                            isEnabled = state.isEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(DarkBackground)
                        )
                    }
                }
            }

            // Presets Selector Chips
            item {
                Column {
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(state.presets) { preset ->
                            val isSelected = state.currentPreset == preset
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onPresetSelect(preset) }
                                    .testTag("preset_chip_$preset"),
                                color = if (isSelected) SpotifyGreen else DarkSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp,
                                    if (isSelected) SpotifyGreen else DarkOutline
                                )
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) OnAccent else TextPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Frequency Band Sliders
            item {
                Text(
                    text = "Bands",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (state.bands.isEmpty()) item("bands_empty") {
                Text(
                    text = if (state.isSupportedOnDevice) "Bands appear once a song starts playing." else "This phone's audio driver doesn't offer equalizer bands. Presets and enhancements still work where available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp).testTag("equalizer_bands_empty")
                )
            }

            items(state.bands) { band ->
                BandSliderCard(
                    band = band,
                    isEnabled = state.isEnabled,
                    onLevelChange = { newLevel ->
                        onBandLevelChange(band.bandIndex, newLevel)
                    }
                )
            }

            // Sound Enhancements: Bass Boost & Virtualizer
            item {
                Text(
                    text = "Enhancements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Bass Boost Card
            item {
                EffectSliderCard(
                    title = "Bass boost",
                    subtitle = if (state.isBassBoostSupported) "More low-end punch" else "Not available on this phone",
                    value = state.bassBoostStrength,
                    maxValue = 1000,
                    icon = Icons.Default.VolumeUp,
                    isEnabled = state.isEnabled && state.isBassBoostSupported,
                    onValueChange = onBassBoostChange,
                    testTag = "bass_boost_slider"
                )
            }

            // Virtualizer / 3D Audio Card
            item {
                EffectSliderCard(
                    title = "Surround",
                    subtitle = if (state.isVirtualizerSupported) "Wider, roomier sound" else "Not available on this phone",
                    value = state.virtualizerStrength,
                    maxValue = 1000,
                    icon = Icons.Default.SurroundSound,
                    isEnabled = state.isEnabled && state.isVirtualizerSupported,
                    onValueChange = onVirtualizerChange,
                    testTag = "virtualizer_slider"
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun EqualizerFrequencyCurve(
    bands: List<EqualizerBand>,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = SpotifyGreen
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Draw 0dB reference center line
        drawLine(
            color = Color(0x33FFFFFF),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        if (bands.isEmpty()) return@Canvas

        val stepX = width / (bands.size + 1)
        val points = bands.mapIndexed { index, band ->
            val x = stepX * (index + 1)
            // Range usually -1500 to +1500. Map to height (0 at top is +1500, bottom is -1500)
            val min = band.minLevelMb.toFloat()
            val max = band.maxLevelMb.toFloat()
            val ratio = if (max > min) (band.levelMb - min) / (max - min) else 0.5f
            val y = height - (ratio * (height - 30.dp.toPx()) + 15.dp.toPx())
            Offset(x, y)
        }

        // Draw smooth path
        val path = Path().apply {
            moveTo(0f, centerY)
            points.forEachIndexed { i, pt ->
                if (i == 0) {
                    quadraticTo((0f + pt.x) / 2f, (centerY + pt.y) / 2f, pt.x, pt.y)
                } else {
                    val prev = points[i - 1]
                    val cx = (prev.x + pt.x) / 2f
                    val cy = (prev.y + pt.y) / 2f
                    quadraticTo(prev.x, prev.y, cx, cy)
                }
            }
            val lastPt = points.lastOrNull()
            if (lastPt != null) {
                quadraticTo((lastPt.x + width) / 2f, (lastPt.y + centerY) / 2f, width, centerY)
            } else {
                lineTo(width, centerY)
            }
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        // Gradient Fill
        val curveColor = if (isEnabled) accentColor else Color(0xFF6B7A72)
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    curveColor.copy(alpha = if (isEnabled) 0.3f else 0.1f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = height
            )
        )

        // Curve Stroke
        drawPath(
            path = path,
            color = curveColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Band Points
        points.forEach { pt ->
            drawCircle(
                color = curveColor,
                radius = 5.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = if (isEnabled) Color.White else Color.Gray,
                radius = 2.5.dp.toPx(),
                center = pt
            )
        }
    }
}

@Composable
fun BandSliderCard(
    band: EqualizerBand,
    isEnabled: Boolean,
    onLevelChange: (Short) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassFill),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Frequency label badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isEnabled) SpotifyGreen.copy(alpha = 0.15f) else DarkSurfaceVariant,
                modifier = Modifier.width(62.dp)
            ) {
                Text(
                    text = band.displayFrequency,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) SpotifyGreen else TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Slider
            val sliderValue = band.levelMb.toFloat()
            Slider(
                value = sliderValue,
                onValueChange = { onLevelChange(it.toInt().toShort()) },
                valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                enabled = isEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = if (isEnabled) SpotifyGreen else TextTertiary,
                    activeTrackColor = if (isEnabled) SpotifyGreen else DarkSurfaceVariant,
                    inactiveTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("band_slider_${band.bandIndex}")
            )

            Spacer(modifier = Modifier.width(10.dp))

            // dB Readout
            Text(
                text = band.displayLevel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled && band.levelMb != 0.toShort()) SpotifyGreenBright else TextTertiary,
                modifier = Modifier.width(52.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

@Composable
fun EffectSliderCard(
    title: String,
    subtitle: String,
    value: Int,
    maxValue: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    onValueChange: (Int) -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = GlassFill),
        border = null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isEnabled && value > 0) SpotifyGreen.copy(alpha = 0.2f) else DarkSurfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isEnabled && value > 0) SpotifyGreen else TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                val percentage = (value * 100 / maxValue).coerceIn(0, 100)
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled && value > 0) SpotifyGreenBright else TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..maxValue.toFloat(),
                enabled = isEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = if (isEnabled) SpotifyGreen else TextTertiary,
                    activeTrackColor = if (isEnabled) SpotifyGreen else DarkSurfaceVariant,
                    inactiveTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )
        }
    }
}
