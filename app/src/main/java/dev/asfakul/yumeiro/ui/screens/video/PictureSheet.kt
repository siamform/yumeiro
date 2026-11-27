package dev.asfakul.yumeiro.ui.screens.video

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.player.HeatGuard
import dev.asfakul.yumeiro.player.PictureLook
import dev.asfakul.yumeiro.player.PictureLook.Kind
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A4K-3: the picture part of the in-player "Picture and Anime4K" sheet.
 *
 * Live preview: while a slider moves only [PictureLook.live] changes (the shader reads it every frame, so the picture
 * follows the finger without rebuilding the effect graph). The value is saved when the finger lifts or a D-pad press
 * ends. Only changes that alter the effect list (Anime4K iterations, scope switch, Reset all) rebuild it.
 */
@Stable
internal class PictureDraft(val context: Context, val titleKey: String?) {
    var perTitle by mutableStateOf(PictureLook.hasOverride(titleKey))
    var profile by mutableStateOf(PictureLook.profileFor(titleKey))

    /** Show now (no save). */
    fun preview(p: PictureLook.Profile) {
        profile = p
        PictureLook.live.grade = PictureLook.resolve(p, HeatGuard.level.value)
        PictureLook.live.tuning = p.tuning.clamped()
    }

    /** Show and save to the chosen scope. */
    fun commit(p: PictureLook.Profile = profile) {
        preview(p)
        PictureLook.save(context, p, perTitle, titleKey)
    }
}

@Composable
internal fun rememberPictureDraft(context: Context, titleKey: String?): PictureDraft = remember(titleKey) { PictureDraft(context, titleKey) }

@Composable
internal fun PictureScope(d: PictureDraft, onRebuild: () -> Unit, onPreview: () -> Unit) {
    val p = LocalMtPalette.current
    var confirmReset by remember { mutableStateOf(false) }
    Spacer(Modifier.padding(top = 8.dp))
    Text("Now: " + PictureLook.label(d.profile), color = p.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("pic_active"))
    if (d.titleKey != null) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.this_title_only), color = p.textPrimary)
                Text(if (d.perTitle) tr(R.string.saved_for_this_show_other_videos, "Saved for this show; other videos keep the main settings") else tr(R.string.changes_apply_to_all_videos, "Changes apply to all videos"),
                    color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = d.perTitle, modifier = Modifier.testTag("pic_scope"), onCheckedChange = { only ->
                d.perTitle = only
                if (only) PictureLook.save(d.context, d.profile, true, d.titleKey)
                else { PictureLook.clearOverride(d.context, d.titleKey); d.preview(PictureLook.profileFor(d.titleKey)) }
                onRebuild(); onPreview()
            })
        }
    }
    if (HeatGuard.level.collectAsState().value == HeatGuard.Level.HOT) {
        Text(stringResource(R.string.your_device_is_hot_so_sharpening),
            color = p.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp).testTag("pic_hot"))
    }
    Text(stringResource(R.string.hdr_videos_are_always_shown_as),
        color = p.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.padding(top = 6.dp)) {
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.testTag("pic_reset_all")) { Text(stringResource(R.string.reset_all), color = p.accent) }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.reset_all_picture_settings)) },
        text = { Text(stringResource(R.string.every_preset_picture_control_filter_setting)) },
        confirmButton = { TextButton(modifier = Modifier.testTag("pic_reset_all_ok"), onClick = {
            confirmReset = false
            PictureLook.resetAll(d.context); d.perTitle = false; d.preview(PictureLook.Profile())
            onRebuild(); onPreview()
        }) { Text(stringResource(R.string.reset)) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PicturePresets(d: PictureDraft, onPreview: () -> Unit) {
    val p = LocalMtPalette.current
    Section("Preset")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PictureLook.Look.entries.forEach { look ->
            Pill(look.label, "pic_look_${look.name}", look == d.profile.look) {
                // A named preset starts clean; Custom keeps the current controls.
                val next = if (look == PictureLook.Look.CUSTOM) d.profile.copy(look = look)
                else d.profile.copy(look = look, controls = PictureLook.Controls())
                d.commit(next); onPreview()
            }
        }
    }
    Text(d.profile.look.detail, color = p.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PictureControls(d: PictureDraft, onPreview: () -> Unit) {
    val p = LocalMtPalette.current
    Section(tr(R.string.picture_controls, "Picture controls"))
    val c = d.profile.controls
    fun set(n: PictureLook.Controls, save: Boolean) {
        // Moving a control away from a named preset turns it into "Custom (based on …)" (label only).
        val next = d.profile.copy(controls = n)
        if (save) { d.commit(next); onPreview() } else d.preview(next)
    }
    ValueSlider("Brightness", "pic_brightness", c.brightness, -0.5f..0.5f, 0f, Kind.SIGNED, 0.02f, help = null,
        onChange = { v, s -> set(c.copy(brightness = v), s) })
    ValueSlider("Saturation", "pic_saturation", c.saturation, 0f..2f, 1f, Kind.FACTOR, 0.05f, help = null,
        onChange = { v, s -> set(c.copy(saturation = v), s) })
    ValueSlider("Contrast", "pic_contrast", c.contrast, 0.5f..1.5f, 1f, Kind.FACTOR, 0.02f, help = null,
        onChange = { v, s -> set(c.copy(contrast = v), s) })
    ValueSlider("Gamma", "pic_gamma", c.gamma, 0.5f..2f, 1f, Kind.FACTOR, 0.05f, help = null,
        onChange = { v, s -> set(c.copy(gamma = v), s) })
    ValueSlider("Hue", "pic_hue", c.hue, -180f..180f, 0f, Kind.DEGREES, 5f, help = null,
        onChange = { v, s -> set(c.copy(hue = v), s) })
    ValueSlider("Sharpness", "pic_sharpness", c.sharpness, 0f..1f, 0f, Kind.PERCENT, 0.05f, help = null,
        onChange = { v, s -> set(c.copy(sharpness = v), s) })
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.video_filter), color = p.textPrimary, modifier = Modifier.weight(1f))
        Text(c.filter.label, color = p.textSecondary, style = MaterialTheme.typography.labelMedium)
        ResetDot(enabled = c.filter != PictureLook.VideoFilter.NONE, tag = "pic_filter_reset") { set(c.copy(filter = PictureLook.VideoFilter.NONE), true) }
    }
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PictureLook.VideoFilter.entries.forEach { f -> Pill(f.label, "pic_filter_${f.name}", f == c.filter) { set(c.copy(filter = f), true) } }
    }
}

@Composable
internal fun Anime4kFilters(d: PictureDraft, tier: VideoPrefs.GpuTier, onRebuild: () -> Unit, onPreview: () -> Unit) {
    val p = LocalMtPalette.current
    val heat by HeatGuard.level.collectAsState()
    Section("Filter")
    val t = d.profile.tuning
    val allowed = PictureLook.allowedIterations(t.iterations, tier, heat)
    ValueSlider("Iterations", "a4k_iterations", t.iterations.toFloat(), 1f..PictureLook.MAX_ITERATIONS.toFloat(), 1f, null, 1f,
        steps = PictureLook.MAX_ITERATIONS - 2,
        help = tr(R.string.how_many_times_the_anime4k_pass, "How many times the Anime4K pass runs on each frame. More passes give cleaner, sharper lines but use more GPU power ") +
            "and battery. Default 1. Weaker or hot devices run fewer passes automatically.",
        onChange = { v, s ->
            val next = d.profile.copy(tuning = t.copy(iterations = v.roundToInt()))
            if (s) { d.commit(next); onRebuild(); onPreview() } else d.preview(next)
        })
    if (allowed < t.iterations) {
        Text(if (allowed == 0) tr(R.string.paused_while_the_device_is_hot, "Paused while the device is hot.") else tr(R.string.this_device_runs_now_to_keep, "This device runs %1\$s now to keep playback smooth.", allowed),
            color = p.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("a4k_iterations_capped"))
    }
    ValueSlider("Threshold", "a4k_threshold", t.threshold, 0f..1f, 0.2f, Kind.PERCENT, 0.05f,
        help = tr(R.string.how_strong_an_edge_must_be, "How strong an edge must be before Anime4K touches it. Lower sharpens fainter lines (and more noise); higher only ") +
            "sharpens clear outlines. Default 20%.",
        onChange = { v, s -> tune(d, t.copy(threshold = v), s, onPreview) })
    ValueSlider("Range", "a4k_range", t.range, 0f..1f, 0.3f, Kind.PERCENT, 0.05f,
        help = tr(R.string.how_far_a_sharpened_pixel_may, "How far a sharpened pixel may move beyond its neighbours. Low keeps it clean with no halos; high gives crisper lines ") +
            "but can add a thin bright edge. Default 30%.",
        onChange = { v, s -> tune(d, t.copy(range = v), s, onPreview) })
    ValueSlider("Grain", "a4k_grain", t.grain, 0f..1f, 0f, Kind.PERCENT, 0.05f,
        help = tr(R.string.adds_fine_film_grain_after_the, "Adds fine film grain after the filter. A little grain hides banding in dark scenes and makes heavy sharpening look ") +
            "more natural. Default 0% (off).",
        onChange = { v, s -> tune(d, t.copy(grain = v), s, onPreview) })
}

private fun tune(d: PictureDraft, t: PictureLook.Tuning, save: Boolean, onPreview: () -> Unit) {
    val next = d.profile.copy(tuning = t)
    if (save) { d.commit(next); onPreview() } else d.preview(next)
}

/**
 * Slider with a live value, an optional "?" help dialog and a reset button. [onChange] gets (value, save): save is
 * false while dragging and true when the finger lifts, a D-pad step lands, or reset is pressed.
 * TV: left / right on the focused slider moves one [step].
 */
@Composable
internal fun ValueSlider(
    label: String, tag: String, value: Float, range: ClosedFloatingPointRange<Float>, default: Float, kind: Kind?, step: Float,
    help: String?, steps: Int = 0, onChange: (Float, Boolean) -> Unit,
) {
    val p = LocalMtPalette.current
    var showHelp by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val v = dragging ?: value
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.textPrimary)
        if (help != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.minimumInteractiveComponentSize().size(24.dp).clip(CircleShape).background(p.glass).clickable { showHelp = true }
                    .semantics { contentDescription = "About $label" }.testTag("${tag}_help"),
                contentAlignment = Alignment.Center,
            ) { Text("?", color = p.textPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.weight(1f))
        Text(kind?.let { PictureLook.show(v, it) } ?: v.roundToInt().toString(), color = p.textSecondary,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("${tag}_value"))
        ResetDot(enabled = abs(value - default) > 1e-4f, tag = "${tag}_reset") { dragging = null; onChange(default, true) }
    }
    Slider(
        value = v,
        onValueChange = { dragging = it; onChange(it, false) },
        onValueChangeFinished = { dragging?.let { onChange(it, true) }; dragging = null },
        valueRange = range, steps = steps,
        colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent),
        modifier = Modifier.fillMaxWidth().testTag(tag).onPreviewKeyEvent { e ->
            val dir = when (e.key.nativeKeyCode) { KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
            if (dir == 0) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) onChange((value + dir * step).coerceIn(range.start, range.endInclusive), true)
            true
        }.focusable(),
    )
    if (showHelp && help != null) AlertDialog(
        onDismissRequest = { showHelp = false },
        title = { Text(label) }, text = { Text(help) },
        confirmButton = { TextButton(onClick = { showHelp = false }, modifier = Modifier.testTag("${tag}_help_ok")) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun ResetDot(enabled: Boolean, tag: String, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag)) {
        Text(stringResource(R.string.reset), color = if (enabled) p.accent else p.textSecondary.copy(alpha = 0.4f), style = MaterialTheme.typography.labelMedium)
    }
}
