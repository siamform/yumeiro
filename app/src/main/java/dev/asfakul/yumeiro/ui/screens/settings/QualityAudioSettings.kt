/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.ui.theme.pageBackground
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
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
import dev.asfakul.yumeiro.ui.theme.yuRound

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
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(yuRound(10)).clickable { onPick(o); open = false }.padding(vertical = 4.dp).testTag("${tag}_${label(o).lowercase().replace(' ', '_')}"),
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
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
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
    remember(context) { dev.asfakul.yumeiro.player.Anime4kV2.init(context); dev.asfakul.yumeiro.player.Anime4kV2 }
    val a4kEngine by dev.asfakul.yumeiro.player.Anime4kV2.engine.collectAsState()
    val a4kPreset by dev.asfakul.yumeiro.player.Anime4kV2.preset.collectAsState()
    val a4kStyle by dev.asfakul.yumeiro.player.Anime4kV2.style.collectAsState()
    val player by VideoPrefs.defaultPlayer.collectAsState()
    val resume by VideoPrefs.resume.collectAsState()
    GroupLabel("Playback")
    SettingsCard {
        ChoiceRow(tr(R.string.default_quality, "Default quality"), "qa_quality", VideoPrefs.Quality.entries, quality, { it.label },
            { if (it == VideoPrefs.Quality.AUTO) tr(R.string.best_for_your_connection, "Best for your connection") else tr(R.string.never_starts_above_this, "Never starts above this") }) { VideoPrefs.setQuality(context, it) }
        SettingsDivider()
        ChoiceRow(tr(R.string.default_audio, "Default audio"), "qa_audio", VideoPrefs.AudioDefault.entries, audio, { it.label }, { it.detail }) { VideoPrefs.setAudio(context, it) }
        SettingsDivider()
        ChoiceRow(tr(R.string.default_speed, "Default speed"), "qa_speed", VideoPrefs.SPEEDS, speed, { if (it == 1f) tr(R.string.normal_1x, "Normal (1x)") else "${it}x" }) { VideoPrefs.setSpeed(context, it) }
        SettingsDivider()
        SettingsToggleRow(tr(R.string.resume_playback, "Resume playback"), tr(R.string.continue_where_you_left_off_off, "Continue where you left off. Off: every video starts from the beginning"), resume, { VideoPrefs.setResume(context, it) }, "qa_resume")
        SettingsDivider()
        // B17: search the other installed extensions for the same title and play the first working source.
        remember(context) { dev.asfakul.yumeiro.online.OtherSources.init(context); true }
        val others by dev.asfakul.yumeiro.online.OtherSources.mode.collectAsState()
        ChoiceRow(tr(R.string.other_extensions, "Other extensions"), "qa_other_sources", dev.asfakul.yumeiro.online.OtherSources.Mode.entries, others, { it.label }, { it.detail }) { dev.asfakul.yumeiro.online.OtherSources.setMode(context, it) }
    }
    GroupLabel("Picture")
    SettingsCard {
        ChoiceRow(tr(R.string.video_decoder, "Video decoder"), "qa_decoder", VideoPrefs.Decoder.entries, decoder, { it.label }, { it.detail }) { VideoPrefs.setDecoder(context, it) }
        SettingsDivider()
        ChoiceRow(tr(R.string.video_renderer, "Video renderer"), "qa_renderer", VideoPrefs.Renderer.entries, renderer, { it.label }, { it.detail }) { VideoPrefs.setRenderer(context, it) }
        SettingsDivider()
        SettingsToggleRow(tr(R.string.anime4k_enhancement, "Anime4K enhancement"), tr(R.string.sharpens_and_darkens_line_art_on, "Sharpens and darkens line art on the GPU. Best on clean 1080p anime sources. Applies to the next video"), anime4k, { VideoPrefs.setAnime4k(context, it) }, "qa_anime4k")
        if (anime4k) {
            SettingsDivider()
            ChoiceRow(tr(R.string.anime4k_engine, "Anime4K engine"), "qa_a4k2_engine", dev.asfakul.yumeiro.player.Anime4kV2.Engine.entries, a4kEngine, { it.label }, { it.detail }) { dev.asfakul.yumeiro.player.Anime4kV2.setEngine(context, it) }
            SettingsDivider()
            if (a4kEngine == dev.asfakul.yumeiro.player.Anime4kV2.Engine.CLASSIC) {
                ChoiceRow(tr(R.string.anime4k_gpu_tier, "Anime4K GPU tier"), "qa_gpu_tier", VideoPrefs.GpuTier.entries, tier, { it.label }, { it.detail }) { VideoPrefs.setGpuTier(context, it) }
            } else {
                ChoiceRow(tr(R.string.anime4k_preset, "Anime4K preset"), "qa_a4k2_preset", dev.asfakul.yumeiro.player.Anime4kV2.Preset.entries, a4kPreset, { it.label }, { it.detail }) { dev.asfakul.yumeiro.player.Anime4kV2.setPreset(context, it) }
                SettingsDivider()
                ChoiceRow(tr(R.string.anime4k_style, "Anime4K style"), "qa_a4k2_style", dev.asfakul.yumeiro.player.Anime4kV2.Style.entries, a4kStyle, { it.label }, { it.detail }) { dev.asfakul.yumeiro.player.Anime4kV2.setStyle(context, it) }
            }
        }
        Text(stringResource(R.string.decoder_renderer_and_classic_anime4k_changes), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
    GroupLabel("Player")
    SettingsCard {
        ChoiceRow(tr(R.string.default_player, "Default player"), "qa_player", VideoPrefs.DefaultPlayer.entries, player, { it.label }, { it.detail }) { VideoPrefs.setDefaultPlayer(context, it) }
        SettingsDivider()
        if (player != VideoPrefs.DefaultPlayer.YUMEIRO) {
            // F-07: which other app; only players that are installed are offered.
            val ext by VideoPrefs.externalApp.collectAsState()
            val apps = androidx.compose.runtime.remember { listOf<dev.asfakul.yumeiro.player.ExternalPlayers.App?>(null) + dev.asfakul.yumeiro.player.ExternalPlayers.installed(context) }
            val picked = apps.firstOrNull { it?.pkg == ext } ?: apps.first()
            ChoiceRow(tr(R.string.other_player_app, "Other player app"), "qa_external_app", apps, picked, { it?.label ?: tr(R.string.choose_each_time, "Choose each time") },
                { a -> when { a == null -> tr(R.string.android_asks_which_app_to_use, "Android asks which app to use"); dev.asfakul.yumeiro.player.ExternalPlayers.passesHeaders(a) -> tr(R.string.gets_the_links_headers_and_subtitles, "Gets the link's headers and subtitles"); else -> tr(R.string.gets_subtitles_links_that_need_a, "Gets subtitles; links that need a login or referer may not play") } }) {
                VideoPrefs.setExternalApp(context, it?.pkg.orEmpty())
            }
            SettingsDivider()
        }
    }
}

/**
 * PC-1 (owner list 11): Settings > Video player > Player controls. Every button of the player can be
 * switched off: the top-bar buttons (in the user's order, with how many stay on the bar before the rest
 * go into More), the previous / next and 10 s buttons, and the fullscreen button.
 */
@Composable
fun PlayerControlsCard() {
    val context = LocalContext.current
    remember(context) { VideoPrefs.init(context); VideoPrefs }
    val order by VideoPrefs.controls.collectAsState()
    val hidden by VideoPrefs.hidden.collectAsState()
    val bar by VideoPrefs.barCount.collectAsState()
    val off by VideoPrefs.extrasOff.collectAsState()
    val place = remember(order, hidden, bar) { VideoPrefs.placeOf(order, hidden, bar) }
    Column(Modifier.fillMaxWidth().testTag("player_controls_card")) {
        Text(stringResource(R.string.top_bar), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.reorder_the_buttons_or_hide_the), color = TextTertiary, fontSize = 12.5.sp)
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("qa_bar_count"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.buttons_on_the_bar), color = TextPrimary, fontSize = 14.sp)
                Text(if (bar == 1) "1 button, the rest under More" else tr(R.string.buttons_the_rest_under_more, "%1\$s buttons, the rest under More", bar), color = TextTertiary, fontSize = 12.sp)
            }
            IconButton(onClick = { VideoPrefs.setBarCount(context, bar - 1) }, enabled = bar > VideoPrefs.BAR_MIN, modifier = Modifier.testTag("qa_bar_less")) {
                Icon(Icons.Default.Remove, stringResource(R.string.fewer_buttons_on_the_bar), tint = if (bar > VideoPrefs.BAR_MIN) TextSecondary else TextTertiary.copy(alpha = 0.4f))
            }
            Text("$bar", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { VideoPrefs.setBarCount(context, bar + 1) }, enabled = bar < VideoPrefs.BAR_MAX, modifier = Modifier.testTag("qa_bar_more")) {
                Icon(Icons.Default.Add, stringResource(R.string.more_buttons_on_the_bar), tint = if (bar < VideoPrefs.BAR_MAX) TextSecondary else TextTertiary.copy(alpha = 0.4f))
            }
        }
        order.forEachIndexed { i, c ->
            val where = place[c] ?: VideoPrefs.Place.MORE
            val isOff = where == VideoPrefs.Place.HIDDEN
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("qa_control_${c.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.label, color = if (isOff) TextTertiary else TextPrimary, fontSize = 14.sp)
                    Text(
                        when (where) { VideoPrefs.Place.BAR -> "On the bar"; VideoPrefs.Place.MORE -> "Under More"; VideoPrefs.Place.HIDDEN -> "Hidden" },
                        color = if (where == VideoPrefs.Place.BAR) SpotifyGreen else TextTertiary, fontSize = 11.5.sp,
                        modifier = Modifier.testTag("qa_control_place_${c.name.lowercase()}"),
                    )
                }
                IconButton(onClick = { VideoPrefs.setControls(context, VideoPrefs.move(order, c, -1), hidden) }, enabled = i > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up_2, c.label), tint = if (i > 0) TextSecondary else TextTertiary.copy(alpha = 0.4f))
                }
                IconButton(onClick = { VideoPrefs.setControls(context, VideoPrefs.move(order, c, 1), hidden) }, enabled = i < order.lastIndex) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down_2, c.label), tint = if (i < order.lastIndex) TextSecondary else TextTertiary.copy(alpha = 0.4f))
                }
                IconButton(onClick = { VideoPrefs.setControls(context, order, if (isOff) hidden - c else hidden + c) }, modifier = Modifier.testTag("qa_control_toggle_${c.name.lowercase()}")) {
                    Icon(if (isOff) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (isOff) "Show ${c.label}" else "Hide ${c.label}", tint = if (isOff) TextTertiary else SpotifyGreen)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.middle_and_bottom), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.play_and_pause_always_stay), color = TextTertiary, fontSize = 12.5.sp)
        SettingsToggleRow(tr(R.string.previous_and_next_video, "Previous and next video"), VideoPrefs.Extra.PREV_NEXT.hint, VideoPrefs.Extra.PREV_NEXT !in off, { VideoPrefs.setExtra(context, VideoPrefs.Extra.PREV_NEXT, it) }, "qa_extra_prev_next")
        SettingsToggleRow(tr(R.string.rewind_and_forward_10_s, "Rewind and forward 10 s"), VideoPrefs.Extra.SEEK.hint, VideoPrefs.Extra.SEEK !in off, { VideoPrefs.setExtra(context, VideoPrefs.Extra.SEEK, it) }, "qa_extra_seek")
        SettingsToggleRow(tr(R.string.fullscreen_button, "Fullscreen button"), VideoPrefs.Extra.FULLSCREEN.hint, VideoPrefs.Extra.FULLSCREEN !in off, { VideoPrefs.setExtra(context, VideoPrefs.Extra.FULLSCREEN, it) }, "qa_extra_fullscreen")
        TextButton(onClick = {
            VideoPrefs.setControls(context, VideoPrefs.Control.entries.toList(), emptySet())
            VideoPrefs.setBarCount(context, VideoPrefs.BAR_DEFAULT)
            VideoPrefs.Extra.entries.forEach { VideoPrefs.setExtra(context, it, true) }
        }, modifier = Modifier.testTag("qa_controls_reset")) { Text(stringResource(R.string.reset_to_default), color = SpotifyGreen) }
    }
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
        SettingsToggleRow(tr(R.string.player_info_overlay, "Player info overlay"), tr(R.string.a_stats_for_nerds_panel_shown, "A \"Stats for nerds\" panel shown with the player controls"), on, { VideoPrefs.setInfoOverlay(context, it) }, "toggle_player_info_overlay")
        if (on) {
            Text(stringResource(R.string.fields_to_show), color = TextSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
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
                TextButton(onClick = { VideoPrefs.setInfoFields(context, VideoPrefs.InfoField.entries.toSet()) }) { Text(stringResource(R.string.all), color = SpotifyGreen) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { VideoPrefs.setInfoFields(context, VideoPrefs.DEFAULT_INFO_FIELDS) }) { Text(stringResource(R.string.label_default), color = SpotifyGreen) }
            }
        }
    }
}
