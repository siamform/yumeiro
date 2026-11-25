/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.player.Anime4kV2
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/**
 * A4K-2 (owner item 26): Anime4K inside the player. On/off, Classic or V2, the V2 presets and styles, and the Custom
 * profile; every pick is saved and applied to the playing video at once through [onChanged] (applyEngineLevel), so
 * playback never has to stop. Sliders apply when the finger lifts, so the effect graph is not rebuilt every frame.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Anime4kSheet(
    onChanged: (on: Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** A4K-3: the playing title's key (per-title override). */
    titleKey: String? = null,
    /** A4K-3: a value was committed; redraw the current frame when paused. */
    onPreview: () -> Unit = {},
) {
    val p = LocalMtPalette.current
    val context = LocalContext.current
    val on by VideoPrefs.anime4k.collectAsState()
    val engine by Anime4kV2.engine.collectAsState()
    val preset by Anime4kV2.preset.collectAsState()
    val style by Anime4kV2.style.collectAsState()
    val tier by VideoPrefs.gpuTier.collectAsState()
    val custom by Anime4kV2.custom.collectAsState()
    val params = if (engine == Anime4kV2.Engine.V2) Anime4kV2.params(preset, style, custom) else null
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface, shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet, dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp).testTag("a4k_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoFixHigh, null, tint = p.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Picture and Anime4K", style = MaterialTheme.typography.titleLarge, color = p.textPrimary, modifier = Modifier.weight(1f))
            }
            // A4K-3: where changes are saved, presets and picture controls (all work with Anime4K on or off).
            val picture = rememberPictureDraft(context, titleKey)
            PictureScope(picture, onRebuild = { onChanged(on) }, onPreview = onPreview)
            PicturePresets(picture, onPreview = onPreview)
            PictureControls(picture, onPreview = onPreview)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anime4K", style = MaterialTheme.typography.titleMedium, color = p.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = { VideoPrefs.setAnime4k(context, it); onChanged(it) }, modifier = Modifier.testTag("a4k_switch"))
            }
            Text(
                "Active: " + Anime4kV2.label(engine, on, preset, style, tier) + (if (on && params != null) " · ${Anime4kV2.taps(params)} samples per pixel" else ""),
                color = p.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("a4k_active"),
            )
            if (on) Anime4kFilters(picture, tier, onRebuild = { onChanged(true) }, onPreview = onPreview)
            if (!on) return@Column
            Section("Engine")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Anime4kV2.Engine.entries.forEach { e -> Pill(e.label, "a4k_engine_${e.name}", e == engine) { Anime4kV2.setEngine(context, e); onChanged(true) } }
            }
            if (engine == Anime4kV2.Engine.CLASSIC) {
                Section("GPU tier")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoPrefs.GpuTier.entries.forEach { t -> Pill(t.label, "a4k_tier_${t.name}", t == tier) { VideoPrefs.setGpuTier(context, t); onChanged(true) } }
                }
                return@Column
            }
            Section("Preset")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Anime4kV2.Preset.entries.forEach { x -> Pill(x.label, "a4k_preset_${x.name}", x == preset) { Anime4kV2.setPreset(context, x); onChanged(true) } }
            }
            Text(preset.detail, color = p.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            if (preset != Anime4kV2.Preset.CUSTOM) {
                Section("Style")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Anime4kV2.Style.entries.forEach { x -> Pill(x.label, "a4k_style_${x.name}", x == style) { Anime4kV2.setStyle(context, x); onChanged(true) } }
                }
                Text(style.detail, color = p.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            } else {
                Section("Custom")
                Level("Restore strength", "a4k_custom_sharpen", custom.sharpen) { Anime4kV2.setCustom(context, custom.copy(sharpen = it)); onChanged(true) }
                Level("Line darkening", "a4k_custom_darken", custom.darken) { Anime4kV2.setCustom(context, custom.copy(darken = it)); onChanged(true) }
                Level("Denoise", "a4k_custom_denoise", custom.denoise) { Anime4kV2.setCustom(context, custom.copy(denoise = it)); onChanged(true) }
                Toggle("Wide reach", "Restores softer, blurrier detail (samples 2 pixels away)", "a4k_custom_wide", custom.wide) { Anime4kV2.setCustom(context, custom.copy(wide = it)); onChanged(true) }
                Toggle("Diagonal samples", "More precise lines; 9 samples per pixel instead of 5", "a4k_custom_diag", custom.diagonals) { Anime4kV2.setCustom(context, custom.copy(diagonals = it)); onChanged(true) }
            }
        }
    }
}

@Composable
internal fun Section(title: String) {
    val p = LocalMtPalette.current
    Spacer(Modifier.height(16.dp))
    Text(title, color = p.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun Pill(label: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) p.accent else p.glass)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) p.onAccent else p.textPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Level(label: String, tag: String, value: Float, onDone: (Float) -> Unit) {
    val p = LocalMtPalette.current
    var v by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.textPrimary, modifier = Modifier.weight(1f))
        Text("${(v * 100).toInt()}%", color = p.textSecondary, style = MaterialTheme.typography.labelMedium)
    }
    Slider(
        value = v, onValueChange = { v = it }, onValueChangeFinished = { onDone(v) }, valueRange = 0f..1f,
        colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent),
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun Toggle(label: String, detail: String, tag: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalMtPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.textPrimary)
            Text(detail, color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = value, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
