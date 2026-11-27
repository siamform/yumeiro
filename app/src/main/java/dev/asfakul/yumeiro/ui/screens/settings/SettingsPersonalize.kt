/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
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
import androidx.compose.foundation.selection.toggleable
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
import dev.asfakul.yumeiro.ui.theme.BrandGold
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound

// region Navigation bar

/**
 * Settings → Navigation bar. A live preview of the bar on top, then B14's reorderable list
 * ([NavBarEditor]): what is on the bar, in order, and what can be added. The 3–7 item rule is
 * enforced by [NavPrefs]; a refused change explains why. Reset asks first.
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
            Text(stringResource(R.string.preview), color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            NavPreview(items.map { it.label to it.selectedIcon })
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.nav_edit_count, items.size, NavPrefs.MAX_ITEMS, cfg.shortcuts.size, NavPrefs.MAX_SHORTCUTS),
                color = TextTertiary, fontSize = 12.sp, modifier = Modifier.testTag("nav_settings_count"))
        }

        NavBarEditor(cfg)
        if (!dev.asfakul.yumeiro.Edition.isTv) GroupLabel("Layout")
        if (!dev.asfakul.yumeiro.Edition.isTv) SettingsCard {
            remember(context) { dev.asfakul.yumeiro.ui.tv.TvMode.init(context); 0 }
            val layoutPref by dev.asfakul.yumeiro.ui.tv.TvMode.pref.collectAsState()
            val detectedTv = remember(context) { dev.asfakul.yumeiro.ui.tv.TvMode.detect(context) }
            Text(
                tr(R.string.tv_layout_puts_a_rail_on, "TV layout puts a rail on the left and is made for a remote. Automatic picks it on TVs") +
                    (if (detectedTv) " (this device is one)." else "; this device looks like a phone or tablet."),
                color = TextSecondary, fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dev.asfakul.yumeiro.ui.tv.TvMode.Pref.entries.forEach { p ->
                    SelectChip(label = p.label, selected = layoutPref == p, tag = "nav_settings_layout_${p.name.lowercase()}") {
                        dev.asfakul.yumeiro.ui.tv.TvMode.set(context, p)
                    }
                }
            }
        }
        var confirmReset by rememberSaveable { mutableStateOf(false) }
        SettingsCard {
            SettingsActionRow(tr(R.string.reset_to_default, "Reset to default"), tr(R.string.home_music_videos_and_library, "Home, Music, Videos and Library"), "nav_settings_reset") { confirmReset = true }
        }
        if (confirmReset) {
            val done = stringResource(R.string.nav_edit_reset_done)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text(stringResource(R.string.nav_edit_reset_title)) },
                text = { Text(stringResource(R.string.nav_edit_reset_text)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { confirmReset = false; NavPrefs.reset(context); AppMessages.success(context, done) },
                        modifier = Modifier.testTag("nav_settings_reset_confirm"),
                    ) { Text(stringResource(R.string.nav_edit_reset)) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.nav_edit_cancel)) }
                },
            )
        }
    }
}

@Composable
private fun NavPreview(items: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>) {
    val p = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().clip(yuRound(22)).background(p.navBar)
            .border(1.dp, dev.asfakul.yumeiro.ui.components.rememberMoonHairline(), yuRound(22))
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
            NetTab(tr(R.string.mobile_data, "Mobile data"), mobile.label, Icons.Default.NetworkCell, onMobile, "audio_quality_tab_mobile", Modifier.weight(1f)) { onMobile = true }
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
            tr(R.string.your_own_files_and_home_servers, "Your own files and home servers play exactly as stored: lossless stays lossless and Atmos goes to the phone's decoder when it supports it. ") +
                tr(R.string.youtube_music_streams_reach_about_256, "YouTube Music streams reach about 256 kbps AAC or 160 kbps Opus, so higher tiers there mean the best it offers."),
            color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.testTag("audio_quality_note"),
        )
    }
}

@Composable
private fun NetTab(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val bg by animateColorAsState(if (selected) p.accent.copy(alpha = if (p.isDark) 0.18f else 0.14f) else p.surfaceContainerHigh.copy(alpha = 0.45f), label = "net_tab")
    Row(
        modifier.clip(yuRound(16)).background(bg)
            .border(1.dp, if (selected) p.accent.copy(alpha = 0.55f) else Color.Transparent, yuRound(16))
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
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(yuRound(14)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp).testTag("audio_quality_${t.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kanji seal for the tier (極 · 高 · 盤 ...) — the Japanese voice of the app, not decoration.
        Box(
            Modifier.size(36.dp).clip(yuRound(11))
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
        ) { if (selected) Icon(Icons.Default.Check, stringResource(R.string.selected), tint = p.onAccent, modifier = Modifier.size(14.dp)) }
    }
}

// endregion


/**
 * Torrent links: the built-in engine switch (B-31, behind its flag and a privacy notice) and the TorrServer
 * address (github.com/YouROK/TorrServer, on the phone or the local network) used as the fallback.
 */
@Composable
internal fun TorrServerCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.remember(context) { dev.asfakul.yumeiro.stream.TorrentSettings.init(context); 0 }
    val saved by dev.asfakul.yumeiro.stream.TorrentSettings.server.collectAsState()
    var text by androidx.compose.runtime.saveable.rememberSaveable(saved) { mutableStateOf(saved) }
    var error by remember { mutableStateOf<String?>(null) }
    // B-31: the built-in engine (flag + engine for this phone); the privacy notice must be accepted first.
    val offered = remember(context) { dev.asfakul.yumeiro.stream.torrent.TorrentStreams.offered(context) }
    if (offered) {
        val accepted by dev.asfakul.yumeiro.stream.TorrentSettings.engineAccepted.collectAsState()
        var asking by remember { mutableStateOf(false) }
        SettingsCard {
            // B31-ON-1: the whole row toggles (one large touch target, read as one switch by TalkBack).
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = accepted, role = androidx.compose.ui.semantics.Role.Switch,
                    onValueChange = { on -> if (on) asking = true else dev.asfakul.yumeiro.stream.TorrentSettings.setEngineAccepted(context, false) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.built_in_torrent_engine), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        if (accepted) tr(R.string.magnet_and_torrent_links_play_in, "Magnet and .torrent links play in Yumeiro. TorrServer below is only a fallback.")
                        else tr(R.string.play_magnet_and_torrent_links_in, "Play magnet and .torrent links in Yumeiro, no TorrServer needed."),
                        color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = accepted,
                    onCheckedChange = null,
                    colors = dev.asfakul.yumeiro.ui.theme.yumeSwitchColors(),
                    modifier = Modifier.testTag("torrent_engine_switch"),
                )
            }
        }
        if (asking) androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.before_you_turn_it_on)) },
            text = { Text(dev.asfakul.yumeiro.stream.torrent.TorrentStreams.PRIVACY_NOTICE, modifier = Modifier.testTag("torrent_engine_notice")) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { dev.asfakul.yumeiro.stream.TorrentSettings.setEngineAccepted(context, true); asking = false },
                    modifier = Modifier.testTag("torrent_engine_accept"),
                ) { Text(stringResource(R.string.i_understand_turn_on), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { Text(stringResource(R.string.not_now)) } },
        )
        Spacer(Modifier.height(10.dp))
    }
    SettingsCard {
        Text(stringResource(R.string.torrserver_address), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            tr(R.string.magnet_and_torrent_links_stream_through, "Magnet and .torrent links stream through a TorrServer you run on this phone or your network (default port 8090). ") +
                tr(R.string.leave_empty_to_open_torrent_links, "Leave empty to open torrent links in another app."),
            color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.OutlinedTextField(
            value = text, onValueChange = { text = it; error = null }, singleLine = true,
            placeholder = { Text(stringResource(R.string.http_127_0_0_1_8090)) }, isError = error != null,
            supportingText = error?.let { e -> { Text(e) } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag("torrserver_field"),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (saved.isNotEmpty()) androidx.compose.material3.TextButton(onClick = { dev.asfakul.yumeiro.stream.TorrentSettings.setServer(context, ""); text = "" }) { Text(stringResource(R.string.clear)) }
            androidx.compose.material3.TextButton(
                onClick = {
                    if (dev.asfakul.yumeiro.stream.TorrentSettings.setServer(context, text)) {
                        text = dev.asfakul.yumeiro.stream.TorrentSettings.server.value
                        dev.asfakul.yumeiro.ui.components.AppMessages.info(context, if (text.isEmpty()) tr(R.string.torrent_links_open_in_another_app, "Torrent links open in another app") else tr(R.string.torrserver_saved, "TorrServer saved"))
                    } else error = tr(R.string.enter_an_address_like_192_168, "Enter an address like 192.168.1.5:8090")
                },
                modifier = Modifier.testTag("torrserver_save"),
            ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
        }
    }
}
