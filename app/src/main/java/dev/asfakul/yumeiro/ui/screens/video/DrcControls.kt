/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
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
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Bookmark
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
 * H-1: the compressor's presets. Group Y (B8): Off, Light, Night, Voices, Movie, Music, Strong, Noisy place, Manual
 * (always there: every value yours) and the person's own saved presets. Used by the player's Audio dialog and by
 * Settings → Video player; takes effect on the next audio buffer, so the video keeps playing.
 */
@Composable
fun DrcPresetRow(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { DynamicRangeCompressor.load(ctx) }
    val preset by DynamicRangeCompressor.preset.collectAsState()
    val custom by DynamicRangeCompressor.custom.collectAsState()
    val saved by DynamicRangeCompressor.saved.collectAsState()
    val mine = DynamicRangeCompressor.activeSaved(preset, custom, saved)
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = SpotifyGreen.copy(alpha = 0.22f), selectedLabelColor = SpotifyGreen)
    Column(modifier.fillMaxWidth().testTag("drc_row")) {
        Text(stringResource(R.string.dynamic_range_compressor), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(mine?.let { stringResource(R.string.drc_saved_summary, it.name) } ?: preset.summary, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.testTag("drc_summary"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DynamicRangeCompressor.Preset.entries.forEach { p ->
                FilterChip(
                    selected = p == preset && mine == null,
                    onClick = { if (p == DynamicRangeCompressor.Preset.CUSTOM) DynamicRangeCompressor.selectManual(ctx) else DynamicRangeCompressor.setPreset(ctx, p) },
                    label = { Text(p.label) },
                    colors = chipColors,
                    modifier = Modifier.testTag("drc_preset_${p.name.lowercase()}"),
                )
            }
            saved.forEach { u ->
                FilterChip(
                    selected = u == mine,
                    onClick = { DynamicRangeCompressor.applySaved(ctx, u) },
                    label = { Text(u.name) },
                    leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Bookmark, null, modifier = Modifier.padding(0.dp)) },
                    colors = chipColors,
                    modifier = Modifier.testTag("drc_saved_${u.name}"),
                )
            }
        }
    }
}

/**
 * F15 (owner, after CloudStream's compressor dialog) and Group Y (B8): every value the compressor has (threshold,
 * ratio, knee, make-up gain, attack and release), in the player's Audio dialog and in Settings. The sliders show the
 * values in use (a preset's too); moving one makes them the person's own (Manual), live on the next audio buffer,
 * saved when the drag ends. "Save as preset" keeps them under a name; Reset returns to Night.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DrcCustomSliders(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val preset by DynamicRangeCompressor.preset.collectAsState()
    val custom by DynamicRangeCompressor.custom.collectAsState()
    val saved by DynamicRangeCompressor.saved.collectAsState()
    var naming by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (preset == DynamicRangeCompressor.Preset.OFF) return
    val c = (if (preset == DynamicRangeCompressor.Preset.CUSTOM) custom else preset.settings) ?: custom
    val mine = DynamicRangeCompressor.activeSaved(preset, custom, saved)
    val edit = { s: DynamicRangeCompressor.Settings -> DynamicRangeCompressor.setCustom(ctx, s, persist = false) }
    val save = { DynamicRangeCompressor.current()?.let { DynamicRangeCompressor.setCustom(ctx, it) }; Unit }
    val needName = stringResource(R.string.drc_name_needed)
    val fullNote = stringResource(R.string.drc_full, DynamicRangeCompressor.MAX_SAVED)
    if (naming) DrcSaveDialog(saved, onDismiss = { naming = false }) { name ->
        when (DynamicRangeCompressor.savePreset(ctx, name)) {
            DynamicRangeCompressor.SaveResult.Saved, DynamicRangeCompressor.SaveResult.Replaced -> {
                naming = false
                // The saved values become the Manual choice, so its chip shows as selected.
                DynamicRangeCompressor.saved.value.firstOrNull { it.name.equals(DynamicRangeCompressor.cleanName(name), true) }?.let { DynamicRangeCompressor.applySaved(ctx, it) }
                null
            }
            DynamicRangeCompressor.SaveResult.NoName -> needName
            DynamicRangeCompressor.SaveResult.Full -> fullNote
            DynamicRangeCompressor.SaveResult.Off -> { naming = false; null }
        }
    }
    Column(modifier.fillMaxWidth().testTag("drc_custom")) {
        DrcSlider("Threshold", "${c.thresholdDb.roundToInt()} dB", "Sounds louder than this are turned down", c.thresholdDb, -60f..0f, 59, "drc_threshold", save) { edit(c.copy(thresholdDb = it)) }
        DrcSlider("Ratio", "${"%.1f".format(c.ratio)}:1", "How strongly they are turned down", c.ratio, 1f..20f, 37, "drc_ratio", save) { edit(c.copy(ratio = it)) }
        DrcSlider(stringResource(R.string.drc_knee), "${c.kneeDb.roundToInt()} dB", stringResource(R.string.drc_knee_hint), c.kneeDb, 0f..24f, 23, "drc_knee", save) { edit(c.copy(kneeDb = it)) }
        DrcSlider(tr(R.string.make_up_gain, "Make-up gain"), "+${c.makeupDb.roundToInt()} dB", tr(R.string.turns_the_whole_sound_back_up, "Turns the whole sound back up"), c.makeupDb, 0f..24f, 23, "drc_makeup", save) { edit(c.copy(makeupDb = it)) }
        DrcSlider("Attack", "${"%.1f".format(c.attackMs)} ms", "How fast a loud sound is caught", c.attackMs, 0.5f..200f, 0, "drc_attack", save) { edit(c.copy(attackMs = it)) }
        DrcSlider("Release", "${c.releaseMs.roundToInt()} ms", "How fast the sound comes back", c.releaseMs, 10f..2000f, 0, "drc_release", save) { edit(c.copy(releaseMs = it)) }
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (mine != null) androidx.compose.material3.TextButton(onClick = { DynamicRangeCompressor.deletePreset(ctx, mine.name) }, modifier = Modifier.testTag("drc_delete_saved")) {
                Text(stringResource(R.string.drc_delete_saved, mine.name), color = TextSecondary)
            }
            androidx.compose.material3.TextButton(onClick = { naming = true }, modifier = Modifier.testTag("drc_save")) {
                Text(stringResource(R.string.drc_save_as), color = SpotifyGreen, fontWeight = FontWeight.SemiBold)
            }
            androidx.compose.material3.TextButton(onClick = { DynamicRangeCompressor.reset(ctx) }, modifier = Modifier.testTag("drc_reset")) {
                Text(stringResource(R.string.reset), color = SpotifyGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Group Y (B8): name the values in use. [onSave] returns an error to show, or null when saved. */
@Composable
private fun DrcSaveDialog(saved: List<DynamicRangeCompressor.UserPreset>, onDismiss: () -> Unit, onSave: (String) -> String?) {
    val suggestion = stringResource(R.string.drc_default_name, saved.size + 1)
    var name by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(suggestion) }
    var error by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val clash = saved.any { it.name.equals(name.trim(), ignoreCase = true) }
    dev.asfakul.yumeiro.ui.components.TsukiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drc_save_as)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it.take(DynamicRangeCompressor.MAX_NAME); error = null }, singleLine = true,
                    label = { Text(stringResource(R.string.drc_name)) }, isError = error != null,
                    modifier = Modifier.fillMaxWidth().testTag("drc_save_name"),
                )
                val note = error ?: if (clash) stringResource(R.string.drc_replaces) else null
                if (note != null) Text(note, color = if (error != null) dev.asfakul.yumeiro.ui.theme.DangerTone else TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).testTag("drc_save_note"))
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { error = onSave(name) }, modifier = Modifier.testTag("drc_save_confirm")) { Text(stringResource(R.string.save)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
