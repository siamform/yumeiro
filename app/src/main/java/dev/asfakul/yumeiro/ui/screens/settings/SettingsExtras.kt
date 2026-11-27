/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.asfakul.yumeiro.ui.theme.GlassFill
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.player.PlayerSettings
import dev.asfakul.yumeiro.ui.theme.BrandCyan
import dev.asfakul.yumeiro.ui.theme.BrandViolet
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.DayPart
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.ThemeMode
import dev.asfakul.yumeiro.ui.theme.ThemeSettings
import dev.asfakul.yumeiro.ui.theme.currentDayPart
import dev.asfakul.yumeiro.ui.theme.palette
import dev.asfakul.yumeiro.ui.theme.yuRound

object DeveloperInfo {
    val NAME: String get() = tr(R.string.asfakul_siam, "Asfakul Siam")
    const val HANDLE = "siamform"
    const val WEBSITE = dev.asfakul.yumeiro.notify.Reminders.WEBSITE_URL
    const val GITHUB = "https://github.com/siamform"
    const val SOURCE = "https://github.com/siamform/yumeiro"
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    // Same glass + moon hairline as the Settings home groups, so every page reads as one system.
    val shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeLg
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = GlassFill),
        border = androidx.compose.foundation.BorderStroke(1.dp, dev.asfakul.yumeiro.ui.components.rememberMoonHairline()),
    ) { Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() } }
}

@Composable
fun AppearanceSettingsCard() {
    val context = LocalContext.current
    remember(context) { ThemeSettings.init(context); ThemeSettings }
    val mode by ThemeSettings.mode.collectAsState()
    val amoled by ThemeSettings.amoled.collectAsState()
    val ambient by ThemeSettings.ambient.collectAsState()
    val kana by ThemeSettings.kana.collectAsState()
    SettingsCard {
        ThemeMode.entries.forEach { m ->
            Row(
                Modifier.fillMaxWidth().clip(yuRound(10))
                    .clickable { ThemeSettings.setMode(context, m) }
                    .padding(vertical = 4.dp)
                    .testTag("theme_${m.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == m, onClick = { ThemeSettings.setMode(context, m) },
                    colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen))
                Column(Modifier.weight(1f)) {
                    Text(m.label, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(m.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                // Each theme's own 夢色 scene, so the vibe is visible before choosing it.
                val preview = dev.asfakul.yumeiro.ui.theme.previewPaletteFor(m, androidx.compose.foundation.isSystemInDarkTheme(), dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current)
                Box(
                    Modifier.padding(start = 8.dp).size(width = 36.dp, height = 48.dp).clip(yuRound(10))
                        .dreamScenePreview(preview)
                        .border(if (mode == m) 2.dp else 1.dp, if (mode == m) SpotifyGreen else TextPrimary.copy(alpha = 0.14f), yuRound(10)),
                )
            }
        }
        if (mode == ThemeMode.TIME) {
            Spacer(Modifier.height(8.dp))
            val now = currentDayPart()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                DayPart.entries.forEach { part ->
                    val p = part.palette()
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(p.background, p.accent)))
                                .then(if (part == now) Modifier.border(2.dp, SpotifyGreen, CircleShape) else Modifier),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(part.label, color = if (part == now) SpotifyGreen else TextTertiary, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
        SettingsDivider()
        AccentToneSection(mode)
        SettingsDivider()
        BackdropSettingsSection()
        SettingsDivider()
        SettingsToggleRow(
            title = tr(R.string.pure_black_amoled, "Pure black (AMOLED)"),
            subtitle = "Use true black backgrounds in dark themes",
            checked = amoled,
            onCheckedChange = { ThemeSettings.setAmoled(context, it) },
            testTag = "toggle_amoled",
        )
        SettingsDivider()
        SettingsToggleRow(
            title = tr(R.string.ambient_colour_from_artwork, "Ambient colour from artwork"),
            subtitle = tr(R.string.tint_the_background_glow_with_the, "Tint the background glow with the colour of what's playing"),
            checked = ambient,
            onCheckedChange = { ThemeSettings.setAmbient(context, it) },
            testTag = "toggle_ambient",
        )
        SettingsDivider()
        LanguageRow()
        SettingsDivider()
        SettingsToggleRow(
            title = tr(R.string.compact_text_and_corners, "Compact text and corners"),
            subtitle = tr(R.string.slightly_smaller_text_and_softer_tighter, "Slightly smaller text and softer, tighter corners across Yumeiro. Off: the larger standard size. If you chose large text in Android settings, your size is kept"),
            checked = dev.asfakul.yumeiro.ui.theme.UiScale.compact,
            onCheckedChange = { on ->
                dev.asfakul.yumeiro.ui.theme.UiScale.set(context, on)
                // Text size lives in the window's configuration: redraw the screen once so every page, dialog and sheet follows.
                var c: android.content.Context? = context
                while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
                (c as? android.app.Activity)?.takeIf { it is dev.asfakul.yumeiro.MainActivity }?.recreate()
            },
            testTag = "toggle_compact_ui",
        )
        SettingsDivider()
        SettingsToggleRow(
            title = tr(R.string.japanese_accents, "Japanese accents"),
            subtitle = tr(R.string.small_kana_labels_such_as_and, "Small kana labels such as 設定 and 音楽 above page titles. Off: English only"),
            checked = kana,
            onCheckedChange = { ThemeSettings.setKana(context, it) },
            testTag = "toggle_kana",
        )
    }
}

/** TH-1: accent colour over the theme. Round moon swatches (48 dp targets) with their kana. */
@Composable
private fun AccentToneSection(mode: ThemeMode) {
    val context = LocalContext.current
    val accent by ThemeSettings.accent.collectAsState()
    Text(stringResource(R.string.accent_colour), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
    Text(
        if (mode == ThemeMode.DYNAMIC) tr(R.string.material_you_uses_your_wallpaper_colours, "Material You uses your wallpaper colours; the accent applies to the other themes")
        else tr(R.string.buttons_highlights_and_the_selected_tab, "Buttons, highlights and the selected tab"),
        color = TextSecondary, style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        dev.asfakul.yumeiro.ui.theme.AccentTone.entries.forEach { t ->
            val selected = accent == t
            Column(
                Modifier.weight(1f).clip(yuRound(12))
                    .clickable(onClickLabel = t.label) { ThemeSettings.setAccent(context, t) }
                    .padding(vertical = 4.dp).testTag("accent_${t.name.lowercase()}")
                    .semantics { this.selected = selected },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(t.swatch, t.swatch.copy(alpha = 0.55f))))
                        .border(if (selected) 3.dp else 1.dp, if (selected) TextPrimary else TextPrimary.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(t.kana, color = Color(0xFF101014), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(4.dp))
                Text(t.label.substringBefore(' '), color = if (selected) TextPrimary else TextTertiary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/** Settings → Subtitles & intros: captions on by default and the subtitle look (FINAL-47: was on the Player page). */
@Composable
fun SubtitleDisplayCard() {
    val context = LocalContext.current
    remember(context) { PlayerSettings.init(context); PlayerSettings }
    val cc by PlayerSettings.captionsDefault.collectAsState()
    SettingsCard {
        SettingsToggleRow(tr(R.string.captions_on_by_default, "Captions on by default"), tr(R.string.show_embedded_or_loaded_subtitles_automatically, "Show embedded or loaded subtitles automatically (CC)"), cc,
            { PlayerSettings.setCaptionsDefault(context, it) }, "toggle_cc_default")
        SettingsDivider()
        var subStyleOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        Column(
            Modifier.fillMaxWidth().clip(yuRound(10))
                .clickable { subStyleOpen = true }
                .padding(vertical = 8.dp)
                .testTag("settings_subtitle_style"),
        ) {
            Text(stringResource(R.string.subtitle_style), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.size_colour_outline_or_box_and), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (subStyleOpen) dev.asfakul.yumeiro.ui.screens.video.SubtitleStyleDialog(onDismiss = { subStyleOpen = false })
    }
}

@Composable
fun VideoPlayerSettingsCard() {
    val context = LocalContext.current
    remember(context) { PlayerSettings.init(context); PlayerSettings }
    val bg by PlayerSettings.backgroundPlay.collectAsState()
    val pip by PlayerSettings.autoPip.collectAsState()
    SettingsCard {
        SettingsToggleRow(tr(R.string.background_play, "Background play"), tr(R.string.keep_videos_playing_with_the_screen, "Keep videos playing with the screen off or in other apps, with controls in the notification. Off: the video pauses when you leave the app"), bg,
            { PlayerSettings.setBackgroundPlay(context, it) }, "toggle_bg_play")
        SettingsDivider()
        SettingsToggleRow("Picture-in-picture", tr(R.string.show_the_pip_button_and_shrink, "Show the PiP button and shrink the video into a floating window when you leave the app. Off: PiP is disabled"), pip,
            { PlayerSettings.setAutoPip(context, it) }, "toggle_auto_pip")
        SettingsDivider()
        if (!dev.asfakul.yumeiro.Edition.isTv) {
            val boostOn by PlayerSettings.volumeBoost.collectAsState()
            SettingsToggleRow(tr(R.string.volume_boost_up_to_200, "Volume boost up to 200 %"), tr(R.string.swipe_up_past_full_volume_to, "Swipe up past full volume to make quiet videos louder. It resets for every video. Loud sound can damage your hearing and small speakers"), boostOn,
                { PlayerSettings.setVolumeBoost(context, it) }, "toggle_volume_boost")
            SettingsDivider()
        }
        // H-1: dynamic range compressor, every video player path (also the TV, for decoded sound).
        dev.asfakul.yumeiro.ui.screens.video.DrcPresetRow(Modifier.padding(top = 6.dp))
        dev.asfakul.yumeiro.ui.screens.video.DrcCustomSliders()
        SettingsDivider()
        val back by PlayerSettings.backAction.collectAsState()
        Text(stringResource(R.string.back_button_while_a_video_plays), color = TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        Text(stringResource(R.string.you_can_also_swipe_down_on), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        PlayerSettings.BackAction.entries.forEach { a ->
            Row(
                Modifier.fillMaxWidth().clip(yuRound(10))
                    .clickable { PlayerSettings.setBackAction(context, a) }
                    .padding(vertical = 2.dp)
                    .testTag("back_action_${a.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = back == a, onClick = { PlayerSettings.setBackAction(context, a) },
                    colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen))
                Column(Modifier.weight(1f)) {
                    Text(a.label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    Text(a.detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DeveloperCard() {
    val context = LocalContext.current
    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(yuRound(14)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.version_build, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                val integrity = remember { dev.asfakul.yumeiro.security.IntegrityGuard.status(context) }
                if (integrity == dev.asfakul.yumeiro.security.IntegrityGuard.Status.UNOFFICIAL) {
                    Text(
                        stringResource(R.string.unofficial_build_not_signed_by_the),
                        color = dev.asfakul.yumeiro.ui.theme.DangerTone, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("unofficial_build_notice"),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().clip(yuRound(14))
                .background(Brush.linearGradient(listOf(BrandViolet.copy(alpha = 0.16f), BrandCyan.copy(alpha = 0.06f))))
                .padding(12.dp),
        ) {
            Column {
                Text(stringResource(R.string.developer), color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(DeveloperInfo.NAME, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(stringResource(R.string.designer_full_stack_developer), color = TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        // Developer links only. Support and the Telegram community each have one card below,
        // so nothing on the About page is shown twice.
        val more = remember(context) { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.SOCIAL_LINKS) }
        // Wraps on narrow phones (5 links with OL-13).
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DevLink(Icons.Default.Language, null, "Website", "dev_website") { open(DeveloperInfo.WEBSITE) }
            DevLink(null, R.drawable.ic_brand_github, "GitHub", "dev_github") { open(DeveloperInfo.GITHUB) }
            // OL-13: the developer's own Facebook, Instagram and Telegram.
            if (more) dev.asfakul.yumeiro.notify.SocialLinks.developer.forEach { l -> DevLink(null, l.icon, l.label + " " + l.handle, "dev_" + l.id) { open(l.url) } }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            tr(R.string.engine_androidx_media3_exoplayer_jetpack_compose, "Engine: AndroidX Media3 (ExoPlayer) · Jetpack Compose\nPrivacy: no ads, no tracking, no analytics"),
            color = TextTertiary, fontSize = 11.sp, lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))
        MadeInBangladesh()
    }
}


/** "Made with love in Bangladesh" tagline with a small drawn flag (green field, red disc). */
@Composable
fun MadeInBangladesh(modifier: Modifier = Modifier) {
    Row(modifier.testTag("made_in_bangladesh"), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 20.dp, height = 12.dp).clip(yuRound(2)).background(Color(0xFF006A4E)),
        ) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 5.dp).size(7.dp).clip(CircleShape).background(Color(0xFFF42A41)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.made_with_love_in_bangladesh), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Round icon link in the developer card (portfolio, GitHub, social profiles). */
@Composable
private fun DevLink(vector: androidx.compose.ui.graphics.vector.ImageVector?, drawable: Int?, label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.minimumInteractiveComponentSize().size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(GlassFill)
            .clickable(onClickLabel = label, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (vector != null) Icon(vector, label, tint = TextPrimary, modifier = Modifier.size(20.dp))
        else if (drawable != null) Icon(painterResource(drawable), label, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}

/** F2: Settings › Appearance › Language: follow the device or pick a shipped language (kept; Android 13+ syncs it). */
@Composable
internal fun LanguageRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    val saved = remember { dev.asfakul.yumeiro.l10n.AppLanguage.saved(context) }
    val device = androidx.compose.ui.res.stringResource(dev.asfakul.yumeiro.R.string.l10n_device_language)
    val now = dev.asfakul.yumeiro.l10n.AppLanguage.SHIPPED.firstOrNull { it.tag == saved }?.native ?: device
    SettingsActionRow(
        title = androidx.compose.ui.res.stringResource(dev.asfakul.yumeiro.R.string.l10n_language),
        subtitle = now, testTag = "settings_language", onClick = { open = true },
    )
    if (open) dev.asfakul.yumeiro.ui.components.TsukiDialog(
        onDismissRequest = { open = false },
        modifier = Modifier.testTag("language_dialog"),
        title = { Text(androidx.compose.ui.res.stringResource(dev.asfakul.yumeiro.R.string.l10n_language)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                LanguageOption(device, null, saved.isEmpty(), "language_device") { open = false; dev.asfakul.yumeiro.l10n.AppLanguage.set(context, "") }
                for (l in dev.asfakul.yumeiro.l10n.AppLanguage.SHIPPED) LanguageOption(
                    l.native, if (l.complete) null else androidx.compose.ui.res.stringResource(dev.asfakul.yumeiro.R.string.l10n_preview, l.english),
                    saved == l.tag, "language_${l.tag}",
                ) { open = false; dev.asfakul.yumeiro.l10n.AppLanguage.set(context, l.tag) }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text(androidx.compose.ui.res.stringResource(dev.asfakul.yumeiro.R.string.close)) } },
    )
}

@Composable
private fun LanguageOption(name: String, note: String?, selected: Boolean, tag: String, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(yuRound(10))
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onPick)
            .padding(horizontal = 4.dp, vertical = 6.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontSize = 15.sp)
            if (note != null) Text(note, color = TextTertiary, fontSize = 12.sp)
        }
    }
}

