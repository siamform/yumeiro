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
import androidx.compose.foundation.layout.padding
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
import kotlin.math.roundToInt

/**
 * H-1: Off / Light / Night / Strong for the dynamic range compressor (Custom shows once the sliders
 * made the values the person's own). Used by the player's Audio dialog and by Settings → Video player; takes effect on
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

/**
 * F15 (owner, after CloudStream's compressor dialog): threshold, ratio, make-up gain, attack and release, in the
 * player's Audio dialog and in Settings. The sliders show the values in use (a preset's too); moving one makes them
 * the person's own, live on the next audio buffer, saved when the drag ends. Reset returns to Night.
 */
@Composable
fun DrcCustomSliders(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val preset by DynamicRangeCompressor.preset.collectAsState()
    val custom by DynamicRangeCompressor.custom.collectAsState()
    if (preset == DynamicRangeCompressor.Preset.OFF) return
    val c = (if (preset == DynamicRangeCompressor.Preset.CUSTOM) custom else preset.settings) ?: custom
    val edit = { s: DynamicRangeCompressor.Settings -> DynamicRangeCompressor.setCustom(ctx, s, persist = false) }
    val save = { DynamicRangeCompressor.current()?.let { DynamicRangeCompressor.setCustom(ctx, it) }; Unit }
    Column(modifier.fillMaxWidth().testTag("drc_custom")) {
        DrcSlider("Threshold", "${c.thresholdDb.roundToInt()} dB", "Sounds louder than this are turned down", c.thresholdDb, -60f..0f, 59, "drc_threshold", save) { edit(c.copy(thresholdDb = it)) }
        DrcSlider("Ratio", "${"%.1f".format(c.ratio)}:1", "How strongly they are turned down", c.ratio, 1f..20f, 37, "drc_ratio", save) { edit(c.copy(ratio = it)) }
        DrcSlider("Make-up gain", "+${c.makeupDb.roundToInt()} dB", "Turns the whole sound back up", c.makeupDb, 0f..24f, 23, "drc_makeup", save) { edit(c.copy(makeupDb = it)) }
        DrcSlider("Attack", "${"%.1f".format(c.attackMs)} ms", "How fast a loud sound is caught", c.attackMs, 0.5f..200f, 0, "drc_attack", save) { edit(c.copy(attackMs = it)) }
        DrcSlider("Release", "${c.releaseMs.roundToInt()} ms", "How fast the sound comes back", c.releaseMs, 10f..2000f, 0, "drc_release", save) { edit(c.copy(releaseMs = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            androidx.compose.material3.TextButton(onClick = { DynamicRangeCompressor.reset(ctx) }, modifier = Modifier.testTag("drc_reset")) {
                Text("Reset", color = SpotifyGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DrcSlider(
    label: String, value: String, hint: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int, tag: String,
    onDone: () -> Unit, onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 13.sp)
            Text(hint, color = TextSecondary, fontSize = 11.sp)
        }
        Text(value, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("${tag}_value"))
    }
    androidx.compose.material3.Slider(value = v.coerceIn(range), onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range, steps = steps,
        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen),
        modifier = Modifier.fillMaxWidth().testTag(tag))
}
