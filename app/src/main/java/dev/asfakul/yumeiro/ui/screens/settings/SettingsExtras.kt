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
    const val PORTFOLIO = "https://asfakuls-studio.vercel.app/"
    const val GITHUB = "https://github.com/siamform"
    const val SOURCE = "https://github.com/siamform/yumeiro"
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassFill),
        border = null,
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

@Composable
fun AppearanceSettingsCard() {
    val context = LocalContext.current
    remember(context) { ThemeSettings.init(context); ThemeSettings }
    val mode by ThemeSettings.mode.collectAsState()
    val amoled by ThemeSettings.amoled.collectAsState()
    val ambient by ThemeSettings.ambient.collectAsState()
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
                        color = androidx.compose.ui.graphics.Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall,
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
        Button(
            onClick = { dev.asfakul.yumeiro.ui.screens.extras.AppPages.open(dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.SUPPORT) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDD00), contentColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("buy_me_a_coffee"),
        ) {
            Icon(Icons.Default.Coffee, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Buy me a coffee", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        // The developer's links live here (they are about the developer, not app settings).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            DevLink(Icons.Default.Language, null, "Portfolio", "dev_portfolio") { open(DeveloperInfo.PORTFOLIO) }
            DevLink(null, R.drawable.ic_brand_github, "GitHub", "dev_github") { open(DeveloperInfo.GITHUB) }
            dev.asfakul.yumeiro.notify.Reminders.Social.entries.forEach { sm ->
                val icon = when (sm) {
                    dev.asfakul.yumeiro.notify.Reminders.Social.FACEBOOK -> R.drawable.ic_brand_facebook
                    dev.asfakul.yumeiro.notify.Reminders.Social.INSTAGRAM -> R.drawable.ic_brand_instagram
                    dev.asfakul.yumeiro.notify.Reminders.Social.LINKEDIN -> R.drawable.ic_brand_linkedin
                }
                DevLink(null, icon, sm.label, "social_${sm.name.lowercase()}") {
                    if (dev.asfakul.yumeiro.ui.screens.extras.Links.open(context, sm.url)) dev.asfakul.yumeiro.notify.Reminders.recordFollow(context, sm)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.TextButton(
            onClick = { if (dev.asfakul.yumeiro.ui.screens.extras.Links.open(context, dev.asfakul.yumeiro.notify.Reminders.GITHUB_URL)) dev.asfakul.yumeiro.notify.Reminders.recordStar(context) },
            modifier = Modifier.fillMaxWidth().testTag("social_github_star"),
        ) {
            Icon(Icons.Default.Star, null, tint = BrandViolet, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text("Star Yumeiro on GitHub", color = TextPrimary, fontSize = 13.sp)
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
