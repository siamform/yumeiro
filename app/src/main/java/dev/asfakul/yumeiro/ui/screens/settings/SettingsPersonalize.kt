/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.player.AudioQuality
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import dev.asfakul.yumeiro.ui.navigation.bottomNavItems
import dev.asfakul.yumeiro.ui.navigation.Screen
import dev.asfakul.yumeiro.ui.theme.BrandGold
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

// region Navigation bar

/**
 * Settings → Navigation bar. A live preview of the bar on top, the main tabs as switches, and up to
 * two shortcuts as chips. The 3–6 item rule is enforced by [NavPrefs]; a refused change explains why.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationBarSettingsCard() {
    val context = LocalContext.current
    remember(context) { NavPrefs.init(context); 0 }
    val cfg by NavPrefs.config.collectAsState()
    val items = NavPrefs.barItems(cfg)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Preview
        SettingsCard {
            Text("Preview", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            NavPreview(items.map { it.label to it.selectedIcon })
            Spacer(Modifier.height(8.dp))
            Text("${items.size} of ${NavPrefs.MAX_ITEMS} · icon-only, so up to seven fit a thumb",
                color = TextTertiary, fontSize = 12.sp, modifier = Modifier.testTag("nav_settings_count"))
        }

        GroupLabel("Tabs")
        SettingsCard {
            bottomNavItems.forEachIndexed { i, item ->
                if (i > 0) SettingsDivider()
                val home = item.route == Screen.Home.route
                SettingsToggleRow(
                    title = item.label,
                    subtitle = when {
                        home -> "Always shown: everything else is reachable from Home"
                        else -> "Hidden tabs stay one tap away on Home"
                    },
                    checked = item.route in cfg.tabs,
                    onCheckedChange = { on ->
                        if (home) return@SettingsToggleRow
                        if (!NavPrefs.setTab(context, item.route, on)) AppMessages.info(context, ruleMessage(cfg.size, on))
                    },
                    testTag = "nav_settings_tab_${item.label.lowercase()}",
                    enabled = !home,
                )
            }
        }

        GroupLabel("Shortcuts")
        SettingsCard {
            Text("Pin up to ${NavPrefs.MAX_SHORTCUTS} pages you open every day.", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NavPrefs.Shortcut.entries.forEach { s ->
                    val on = s in cfg.shortcuts
                    SelectChip(
                        label = s.label, selected = on, tag = "nav_settings_shortcut_${s.id}",
                        leading = { Icon(s.item.selectedIcon, null, modifier = Modifier.size(16.dp), tint = if (on) LocalMtPalette.current.onAccent else TextSecondary) },
                    ) {
                        if (!NavPrefs.setShortcut(context, s, !on)) {
                            AppMessages.info(context, if (cfg.shortcuts.size >= NavPrefs.MAX_SHORTCUTS && !on) "Up to ${NavPrefs.MAX_SHORTCUTS} shortcuts. Remove one first." else ruleMessage(cfg.size, !on))
                        }
                    }
                }
            }
        }
        SettingsCard {
            SettingsActionRow("Reset to default", "Home, Music, Videos and Library", "nav_settings_reset") {
                NavPrefs.reset(context); AppMessages.success(context, "Navigation bar reset")
            }
        }
    }
}

private fun ruleMessage(size: Int, adding: Boolean) =
    if (adding && size >= NavPrefs.MAX_ITEMS) "The bar holds up to ${NavPrefs.MAX_ITEMS} items. Remove one first."
    else "Keep at least ${NavPrefs.MIN_ITEMS} items on the bar."

@Composable
private fun NavPreview(items: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>) {
    val p = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.navBar)
            .border(1.dp, dev.asfakul.yumeiro.ui.components.rememberMoonHairline(), RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp).testTag("nav_settings_preview"),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val active = i == 0
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (active) p.accent.copy(alpha = if (p.isDark) 0.18f else 0.14f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) { Icon(icon, null, tint = if (active) (if (p.isDark) p.accentBright else p.accent) else TextSecondary, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.height(3.dp))
                Text(label, color = if (active) TextPrimary else TextSecondary, fontSize = if (items.size >= 6) 10.sp else 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun SelectChip(label: String, selected: Boolean, tag: String, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val bg by animateColorAsState(if (selected) p.accent else p.surfaceContainerHigh.copy(alpha = 0.55f), label = "chip_bg")
    Row(
        Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(50)).background(bg)
            .border(1.dp, if (selected) Color.Transparent else TextTertiary.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(6.dp)) }
        Text(label, color = if (selected) p.onAccent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (selected) { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Check, null, tint = p.onAccent, modifier = Modifier.size(14.dp)) }
    }
}

// endregion

// region Audio quality

/**
 * Settings → Audio → Quality. Two limits (Wi-Fi and mobile data) chosen from the same ladder, with a
 * plain note on what each source can really deliver.
 */
@Composable
fun AudioQualityCard() {
    val context = LocalContext.current
    remember(context) { AudioQuality.init(context); 0 }
    val wifi by AudioQuality.wifi.collectAsState()
    val mobile by AudioQuality.mobile.collectAsState()
    var onMobile by rememberSaveable { mutableStateOf(false) }
    val chosen = if (onMobile) mobile else wifi

    SettingsCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetTab("Wi-Fi", wifi.label, Icons.Default.Wifi, !onMobile, "audio_quality_tab_wifi", Modifier.weight(1f)) { onMobile = false }
            NetTab("Mobile data", mobile.label, Icons.Default.NetworkCell, onMobile, "audio_quality_tab_mobile", Modifier.weight(1f)) { onMobile = true }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AudioQuality.Tier.entries.forEach { t ->
                TierRow(t, t == chosen) {
                    if (onMobile) AudioQuality.setMobile(context, t) else AudioQuality.setWifi(context, t)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Your own files and home servers play exactly as stored: lossless stays lossless and Atmos goes to the phone's decoder when it supports it. " +
                "YouTube Music streams reach about 256 kbps AAC or 160 kbps Opus, so higher tiers there mean the best it offers.",
            color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.testTag("audio_quality_note"),
        )
    }
}

@Composable
private fun NetTab(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val bg by animateColorAsState(if (selected) p.accent.copy(alpha = if (p.isDark) 0.18f else 0.14f) else p.surfaceContainerHigh.copy(alpha = 0.45f), label = "net_tab")
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg)
            .border(1.dp, if (selected) p.accent.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) (if (p.isDark) p.accentBright else p.accent) else TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TierRow(t: AudioQuality.Tier, selected: Boolean, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val bg by animateColorAsState(if (selected) p.accent.copy(alpha = if (p.isDark) 0.12f else 0.10f) else Color.Transparent, label = "tier_bg")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp).testTag("audio_quality_${t.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kanji seal for the tier (極 · 高 · 盤 ...) — the Japanese voice of the app, not decoration.
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                .background(if (t.lossless || t == AudioQuality.Tier.ATMOS) BrandGold.copy(alpha = 0.16f) else p.surfaceContainerHigh.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) { Text(t.kana, color = if (t.lossless || t == AudioQuality.Tier.ATMOS) BrandGold else TextSecondary, fontSize = if (t.kana.length > 1) 13.sp else 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (t.lossless) { Spacer(Modifier.width(6.dp)); StatusChip("Lossless", true) }
            }
            Text(t.spec, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.detail, color = TextTertiary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (selected) p.accent else Color.Transparent)
                .border(1.5.dp, if (selected) p.accent else TextTertiary.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Default.Check, "Selected", tint = p.onAccent, modifier = Modifier.size(14.dp)) }
    }
}

// endregion


/**
 * TorrServer address for magnet / .torrent links (Live TV › Play a link). Yumeiro has no torrent
 * engine; TorrServer (github.com/YouROK/TorrServer) runs on the phone or the local network.
 */
@Composable
internal fun TorrServerCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.remember(context) { dev.asfakul.yumeiro.stream.TorrentSettings.init(context); 0 }
    val saved by dev.asfakul.yumeiro.stream.TorrentSettings.server.collectAsState()
    var text by androidx.compose.runtime.saveable.rememberSaveable(saved) { mutableStateOf(saved) }
    var error by remember { mutableStateOf<String?>(null) }
    SettingsCard {
        Text("TorrServer address", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            "Magnet and .torrent links stream through a TorrServer you run on this phone or your network (default port 8090). " +
                "Leave empty to open torrent links in another app.",
            color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.OutlinedTextField(
            value = text, onValueChange = { text = it; error = null }, singleLine = true,
            placeholder = { Text("http://127.0.0.1:8090") }, isError = error != null,
            supportingText = error?.let { e -> { Text(e) } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag("torrserver_field"),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (saved.isNotEmpty()) androidx.compose.material3.TextButton(onClick = { dev.asfakul.yumeiro.stream.TorrentSettings.setServer(context, ""); text = "" }) { Text("Clear") }
            androidx.compose.material3.TextButton(
                onClick = {
                    if (dev.asfakul.yumeiro.stream.TorrentSettings.setServer(context, text)) {
                        text = dev.asfakul.yumeiro.stream.TorrentSettings.server.value
                        dev.asfakul.yumeiro.ui.components.AppMessages.info(context, if (text.isEmpty()) "Torrent links open in another app" else "TorrServer saved")
                    } else error = "Enter an address like 192.168.1.5:8090"
                },
                modifier = Modifier.testTag("torrserver_save"),
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        }
    }
}
