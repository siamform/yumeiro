/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** Round 13: Settings → Audio → "Quality and audio" (opens its own page). */
@Composable
fun QualityAndAudioEntry() {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsCard {
        SettingsActionRow(
            "Quality and audio",
            "Default quality, Sub or Dub, speed, decoder, renderer, Anime4K, default player, player buttons and resume",
            "settings_quality_audio",
        ) { open = true }
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val p = LocalMtPalette.current
        Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().testTag("settings_page_quality_audio")) {
            dev.asfakul.yumeiro.ui.components.SubPageHeader(title = "Quality and audio", onBack = { open = false }, subtitle = "Audio quality & sound", handleBack = false)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item("q") { QualityAndAudioCard() }
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(title: String, tag: String, options: List<T>, selected: T, label: (T) -> String, detail: (T) -> String? = { null }, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingsActionRow(title, label(selected) + (detail(selected)?.let { " · $it" } ?: ""), tag) { open = true }
    if (open) TsukiDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(o); open = false }.padding(vertical = 4.dp).testTag("${tag}_${label(o).lowercase().replace(' ', '_')}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o == selected, onClick = { onPick(o); open = false }, colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen))
                        Column(Modifier.weight(1f)) {
                            Text(label(o), color = TextPrimary, fontSize = 15.sp)
                            detail(o)?.let { Text(it, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
    )
}

@Composable
fun QualityAndAudioCard() {
    val context = LocalContext.current
    remember(context) { VideoPrefs.init(context); VideoPrefs }
    val quality by VideoPrefs.quality.collectAsState()
    val audio by VideoPrefs.audio.collectAsState()
    val speed by VideoPrefs.speed.collectAsState()
    val decoder by VideoPrefs.decoder.collectAsState()
    val renderer by VideoPrefs.renderer.collectAsState()
    val anime4k by VideoPrefs.anime4k.collectAsState()
    val tier by VideoPrefs.gpuTier.collectAsState()
    val player by VideoPrefs.defaultPlayer.collectAsState()
    val resume by VideoPrefs.resume.collectAsState()
    GroupLabel("Playback")
    SettingsCard {
        ChoiceRow("Default quality", "qa_quality", VideoPrefs.Quality.entries, quality, { it.label },
            { if (it == VideoPrefs.Quality.AUTO) "Best for your connection" else "Never starts above this" }) { VideoPrefs.setQuality(context, it) }
        SettingsDivider()
        ChoiceRow("Default audio", "qa_audio", VideoPrefs.AudioDefault.entries, audio, { it.label }, { it.detail }) { VideoPrefs.setAudio(context, it) }
        SettingsDivider()
        ChoiceRow("Default speed", "qa_speed", VideoPrefs.SPEEDS, speed, { if (it == 1f) "Normal (1x)" else "${it}x" }) { VideoPrefs.setSpeed(context, it) }
        SettingsDivider()
        SettingsToggleRow("Resume playback", "Continue where you left off. Off: every video starts from the beginning", resume, { VideoPrefs.setResume(context, it) }, "qa_resume")
    }
    GroupLabel("Picture")
    SettingsCard {
        ChoiceRow("Video decoder", "qa_decoder", VideoPrefs.Decoder.entries, decoder, { it.label }, { it.detail }) { VideoPrefs.setDecoder(context, it) }
        SettingsDivider()
        ChoiceRow("Video renderer", "qa_renderer", VideoPrefs.Renderer.entries, renderer, { it.label }, { it.detail }) { VideoPrefs.setRenderer(context, it) }
        SettingsDivider()
        SettingsToggleRow("Anime4K enhancement", "Sharpens and darkens line art on the GPU. Best on clean 1080p anime sources. Applies to the next video", anime4k, { VideoPrefs.setAnime4k(context, it) }, "qa_anime4k")
        if (anime4k) {
            SettingsDivider()
            ChoiceRow("Anime4K GPU tier", "qa_gpu_tier", VideoPrefs.GpuTier.entries, tier, { it.label }, { it.detail }) { VideoPrefs.setGpuTier(context, it) }
        }
        Text("Decoder, renderer and Anime4K changes apply to the next video you open.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
    GroupLabel("Player")
    SettingsCard {
        ChoiceRow("Default player", "qa_player", VideoPrefs.DefaultPlayer.entries, player, { it.label }, { it.detail }) { VideoPrefs.setDefaultPlayer(context, it) }
        SettingsDivider()
        PlayerControlsEditor()
    }
}

/** Reorder or hide the buttons in the player's top bar. */
@Composable
private fun PlayerControlsEditor() {
    val context = LocalContext.current
    val order by VideoPrefs.controls.collectAsState()
    val hidden by VideoPrefs.hidden.collectAsState()
    Text("Player controls", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    Text("Reorder the buttons in the player's top bar, or hide the ones you never use", color = TextTertiary, fontSize = 12.5.sp)
    Spacer(Modifier.size(6.dp))
    order.forEachIndexed { i, c ->
        val off = c in hidden
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("qa_control_${c.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
            Text(c.label, color = if (off) TextTertiary else TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { VideoPrefs.setControls(context, VideoPrefs.move(order, c, -1), hidden) }, enabled = i > 0) {
                Icon(Icons.Default.KeyboardArrowUp, "Move ${c.label} up", tint = if (i > 0) TextSecondary else TextTertiary.copy(alpha = 0.4f))
            }
            IconButton(onClick = { VideoPrefs.setControls(context, VideoPrefs.move(order, c, 1), hidden) }, enabled = i < order.lastIndex) {
                Icon(Icons.Default.KeyboardArrowDown, "Move ${c.label} down", tint = if (i < order.lastIndex) TextSecondary else TextTertiary.copy(alpha = 0.4f))
            }
            IconButton(onClick = { VideoPrefs.setControls(context, order, if (off) hidden - c else hidden + c) }) {
                Icon(if (off) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (off) "Show ${c.label}" else "Hide ${c.label}", tint = if (off) TextTertiary else SpotifyGreen)
            }
        }
    }
    TextButton(onClick = { VideoPrefs.setControls(context, VideoPrefs.Control.entries.toList(), emptySet()) }, modifier = Modifier.testTag("qa_controls_reset")) { Text("Reset to default", color = SpotifyGreen) }
}

/** Round 13: Settings → Player → Player info overlay. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerInfoOverlayCard() {
    val context = LocalContext.current
    remember(context) { VideoPrefs.init(context); VideoPrefs }
    val on by VideoPrefs.infoOverlay.collectAsState()
    val fields by VideoPrefs.infoFields.collectAsState()
    SettingsCard {
        SettingsToggleRow("Player info overlay", "A \"Stats for nerds\" panel shown with the player controls", on, { VideoPrefs.setInfoOverlay(context, it) }, "toggle_player_info_overlay")
        if (on) {
            Text("Fields to show", color = TextSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                VideoPrefs.InfoField.entries.forEach { f ->
                    val sel = f in fields
                    FilterChip(
                        selected = sel,
                        onClick = { VideoPrefs.setInfoFields(context, if (sel) fields - f else fields + f) },
                        label = { Text(f.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SpotifyGreen.copy(alpha = 0.22f)),
                        modifier = Modifier.testTag("info_field_${f.name.lowercase()}"),
                    )
                }
            }
            Row {
                TextButton(onClick = { VideoPrefs.setInfoFields(context, VideoPrefs.InfoField.entries.toSet()) }) { Text("All", color = SpotifyGreen) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { VideoPrefs.setInfoFields(context, VideoPrefs.DEFAULT_INFO_FIELDS) }) { Text("Default", color = SpotifyGreen) }
            }
        }
    }
}
