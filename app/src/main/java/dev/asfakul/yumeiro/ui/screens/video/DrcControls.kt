/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.player.DynamicRangeCompressor
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary

/**
 * H-1: Off / Light / Night / Strong for the dynamic range compressor (Custom shows when it was set
 * in Settings). Used by the player's Audio dialog and by Settings → Video player; takes effect on
 * the next audio buffer, so the video keeps playing.
 */
@Composable
fun DrcPresetRow(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { DynamicRangeCompressor.load(ctx) }
    val preset by DynamicRangeCompressor.preset.collectAsState()
    Column(modifier.fillMaxWidth().testTag("drc_row")) {
        Text("Dynamic range compressor", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(preset.summary, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.testTag("drc_summary"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DynamicRangeCompressor.Preset.entries
                .filter { it != DynamicRangeCompressor.Preset.CUSTOM || preset == DynamicRangeCompressor.Preset.CUSTOM }
                .forEach { p ->
                    FilterChip(
                        selected = p == preset,
                        onClick = { DynamicRangeCompressor.setPreset(ctx, p) },
                        label = { Text(p.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SpotifyGreen.copy(alpha = 0.22f), selectedLabelColor = SpotifyGreen),
                        modifier = Modifier.testTag("drc_preset_${p.name.lowercase()}"),
                    )
                }
        }
    }
}

/** H-1: Custom threshold, ratio, make-up gain and release (Settings only). Moving one switches to Custom. */
@Composable
fun DrcCustomSliders() {
    val ctx = LocalContext.current
    val c by DynamicRangeCompressor.custom.collectAsState()
    val preset by DynamicRangeCompressor.preset.collectAsState()
    if (preset == DynamicRangeCompressor.Preset.OFF) return
    Column(Modifier.fillMaxWidth().testTag("drc_custom")) {
        DrcSlider("Threshold", "${c.thresholdDb.toInt()} dB", c.thresholdDb, -60f..0f, "drc_threshold") { DynamicRangeCompressor.setCustom(ctx, c.copy(thresholdDb = it)) }
        DrcSlider("Ratio", "${"%.1f".format(c.ratio)}:1", c.ratio, 1f..20f, "drc_ratio") { DynamicRangeCompressor.setCustom(ctx, c.copy(ratio = it)) }
        DrcSlider("Make-up gain", "+${c.makeupDb.toInt()} dB", c.makeupDb, 0f..24f, "drc_makeup") { DynamicRangeCompressor.setCustom(ctx, c.copy(makeupDb = it)) }
        DrcSlider("Release", "${c.releaseMs.toInt()} ms", c.releaseMs, 10f..2000f, "drc_release") { DynamicRangeCompressor.setCustom(ctx, c.copy(releaseMs = it)) }
    }
}

@Composable
private fun DrcSlider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, tag: String, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextSecondary, fontSize = 12.sp)
    }
    androidx.compose.material3.Slider(value = v.coerceIn(range), onValueChange = onChange, valueRange = range,
        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen),
        modifier = Modifier.fillMaxWidth().testTag(tag))
}
