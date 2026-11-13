/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

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

object DeveloperInfo {
    const val NAME = "Asfakul Siam"
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
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
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
                    Modifier.padding(start = 8.dp).size(width = 36.dp, height = 48.dp).clip(RoundedCornerShape(10.dp))
                        .dreamScenePreview(preview)
                        .border(if (mode == m) 2.dp else 1.dp, if (mode == m) SpotifyGreen else TextPrimary.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
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
        BackdropSettingsSection()
        SettingsDivider()
        SettingsToggleRow(
            title = "Pure black (AMOLED)",
            subtitle = "Use true black backgrounds in dark themes",
            checked = amoled,
            onCheckedChange = { ThemeSettings.setAmoled(context, it) },
            testTag = "toggle_amoled",
        )
        SettingsDivider()
        SettingsToggleRow(
            title = "Ambient colour from artwork",
            subtitle = "Tint the background glow with the colour of what's playing",
            checked = ambient,
            onCheckedChange = { ThemeSettings.setAmbient(context, it) },
            testTag = "toggle_ambient",
        )
        SettingsDivider()
        SettingsToggleRow(
            title = "Japanese accents",
            subtitle = "Small kana labels such as 設定 and 音楽 above page titles. Off: English only",
            checked = kana,
            onCheckedChange = { ThemeSettings.setKana(context, it) },
            testTag = "toggle_kana",
        )
    }
}

@Composable
fun VideoPlayerSettingsCard() {
    val context = LocalContext.current
    remember(context) { PlayerSettings.init(context); PlayerSettings }
    val bg by PlayerSettings.backgroundPlay.collectAsState()
    val pip by PlayerSettings.autoPip.collectAsState()
    val cc by PlayerSettings.captionsDefault.collectAsState()
    SettingsCard {
        SettingsToggleRow("Background play", "Keep videos playing with the screen off or in other apps, with controls in the notification. Off: the video pauses when you leave the app", bg,
            { PlayerSettings.setBackgroundPlay(context, it) }, "toggle_bg_play")
        SettingsDivider()
        SettingsToggleRow("Picture-in-picture", "Show the PiP button and shrink the video into a floating window when you leave the app. Off: PiP is disabled", pip,
            { PlayerSettings.setAutoPip(context, it) }, "toggle_auto_pip")
        SettingsDivider()
        SettingsToggleRow("Captions on by default", "Show embedded or loaded subtitles automatically (CC)", cc,
            { PlayerSettings.setCaptionsDefault(context, it) }, "toggle_cc_default")
        SettingsDivider()
        var subStyleOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .clickable { subStyleOpen = true }
                .padding(vertical = 8.dp)
                .testTag("settings_subtitle_style"),
        ) {
            Text("Subtitle style", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text("Size, colour, outline or box, and height above the bottom. Also in the player's subtitle menu", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (subStyleOpen) dev.asfakul.yumeiro.ui.screens.video.SubtitleStyleDialog(onDismiss = { subStyleOpen = false })
        SettingsDivider()
        val back by PlayerSettings.backAction.collectAsState()
        Text("Back button while a video plays", color = TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        Text("You can also swipe down on the video to shrink it", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        PlayerSettings.BackAction.entries.forEach { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
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
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Yumeiro", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                val integrity = remember { dev.asfakul.yumeiro.security.IntegrityGuard.status(context) }
                if (integrity == dev.asfakul.yumeiro.security.IntegrityGuard.Status.UNOFFICIAL) {
                    Text(
                        "Unofficial build — not signed by the Yumeiro developer. Download the real app from github.com/siamform/yumeiro",
                        color = dev.asfakul.yumeiro.ui.theme.DangerTone, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("unofficial_build_notice"),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(BrandViolet.copy(alpha = 0.16f), BrandCyan.copy(alpha = 0.06f))))
                .padding(12.dp),
        ) {
            Column {
                Text("Developer", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(DeveloperInfo.NAME, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Designer & full-stack developer", color = TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        // Developer links only. Support and the Telegram community each have one card below,
        // so nothing on the About page is shown twice.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            DevLink(Icons.Default.Language, null, "Website", "dev_website") { open(DeveloperInfo.WEBSITE) }
            DevLink(null, R.drawable.ic_brand_github, "GitHub", "dev_github") { open(DeveloperInfo.GITHUB) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Engine: AndroidX Media3 (ExoPlayer) · Jetpack Compose\nPrivacy: no ads, no tracking, no analytics",
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
            Modifier.size(width = 20.dp, height = 12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF006A4E)),
        ) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 5.dp).size(7.dp).clip(CircleShape).background(Color(0xFFF42A41)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("Made with love in Bangladesh", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Round icon link in the developer card (portfolio, GitHub, social profiles). */
@Composable
private fun DevLink(vector: androidx.compose.ui.graphics.vector.ImageVector?, drawable: Int?, label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(GlassFill)
            .clickable(onClickLabel = label, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (vector != null) Icon(vector, label, tint = TextPrimary, modifier = Modifier.size(20.dp))
        else if (drawable != null) Icon(painterResource(drawable), label, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}
