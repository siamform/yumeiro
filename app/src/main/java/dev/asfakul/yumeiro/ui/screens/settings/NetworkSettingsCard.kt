/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.network.DnsMode
import dev.asfakul.yumeiro.network.DnsProvider
import dev.asfakul.yumeiro.network.DnsTransport
import dev.asfakul.yumeiro.network.NetworkSettings
import dev.asfakul.yumeiro.network.SmartDns
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Network: optional public DNS resolver for streams (Cloudflare 1.1.1.1, Quad9, Google).
 * System DNS stays the default; by default the alternative is only used when the system resolver fails.
 */
@Composable
fun NetworkSettingsCard() {
    val context = LocalContext.current
    remember(context) { NetworkSettings.init(context); NetworkSettings }
    val config by NetworkSettings.dns.collectAsState()
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    SettingsCard {
        Text(stringResource(R.string.dns_for_streams), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.used_for_playlists_stream_links_and),
            color = TextSecondary, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(DnsProvider.entries, key = { it.name }) { p ->
                Chip(
                    if (p == DnsProvider.SYSTEM) tr(R.string.system_default, "System default") else "${p.label} ${p.primaryIp}",
                    config.provider == p, "dns_provider_${p.name.lowercase()}",
                ) { NetworkSettings.setDns(context, config.copy(provider = p)); testResult = null }
            }
        }
        if (!config.usesAlternative) {
            SettingsDivider()
            SettingsToggleRow(
                title = tr(R.string.get_around_blocked_sites_automatically, "Get around blocked sites automatically"),
                subtitle = tr(R.string.if_your_network_blocks_a_sites, "If your network blocks a site's name (no answer, a block page or a reset connection), Yumeiro looks it up over encrypted DNS instead — Cloudflare, then Quad9, then Google — and remembers it. Sites that work are untouched"),
                checked = config.auto,
                onCheckedChange = { NetworkSettings.setDns(context, config.copy(auto = it)) },
                testTag = "toggle_dns_auto",
            )
            // NBUG-6 (ADR-046): default Off; hidden when the kill switch is off.
            if (remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.DNS_FALLBACK) }) {
                SettingsDivider()
                SettingsToggleRow(
                    title = tr(R.string.automatic_dns_fallback, "Automatic DNS fallback"),
                    subtitle = tr(R.string.if_your_networks_dns_stops_answering, "If your network's DNS stops answering, Yumeiro asks Cloudflare (1.1.1.1), then Quad9 (9.9.9.9), one at a time, and keeps the one that works until the network changes. Off: system DNS only"),
                    checked = config.fallback,
                    onCheckedChange = { NetworkSettings.setDns(context, config.copy(fallback = it)) },
                    testTag = "toggle_dns_fallback_auto",
                )
            }
            val vpn = remember { dev.asfakul.yumeiro.network.Connectivity.vpnActive(context) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (vpn) tr(R.string.a_vpn_is_on_if_an, "A VPN is on. If an extension fails only with it, turn it off here.") else tr(R.string.some_sources_block_whole_regions_dns, "Some sources block whole regions; DNS can't fix that — a VPN can."),
                    color = TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { dev.asfakul.yumeiro.network.Connectivity.openVpnSettings(context) }, modifier = Modifier.testTag("dns_vpn_settings")) {
                    Text(stringResource(R.string.vpn_settings), color = SpotifyGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (config.usesAlternative) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.connection), color = TextSecondary, fontSize = 12.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DnsTransport.entries, key = { it.name }) { t ->
                    Chip(t.label, config.transport == t, "dns_transport_${t.name.lowercase()}") {
                        NetworkSettings.setDns(context, config.copy(transport = t)); testResult = null
                    }
                }
            }
            Text(config.transport.description, color = TextTertiary, fontSize = 11.sp)
            SettingsDivider()
            SettingsToggleRow(
                title = tr(R.string.only_when_system_dns_fails, "Only when system DNS fails"),
                subtitle = "Recommended. Lookups go to the system DNS first; ${config.provider.label} is asked only if a host can't be resolved. Off: ${config.provider.label} answers every lookup",
                checked = config.mode == DnsMode.FALLBACK,
                onCheckedChange = { NetworkSettings.setDns(context, config.copy(mode = if (it) DnsMode.FALLBACK else DnsMode.ALWAYS)) },
                testTag = "toggle_dns_fallback",
            )
        }
        SettingsDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                testResult ?: tr(R.string.check_that_the_selected_resolver_answers, "Check that the selected resolver answers on this network."),
                color = if (testResult?.startsWith("Failed") == true) MaterialTheme.colorScheme.error else TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).testTag("dns_test_result"),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = !testing,
                onClick = {
                    testing = true; testResult = null
                    val cfg = config
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { SmartDns.test("example.com", cfg) }
                        testResult = r.fold(
                            { (addrs, ms) -> "${cfg.provider.label}: example.com → ${addrs.firstOrNull()?.hostAddress ?: "?"} in $ms ms" },
                            { "Failed · ${dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "no answer")}" },
                        )
                        testing = false
                    }
                },
                modifier = Modifier.testTag("dns_test_btn"),
            ) {
                if (testing) CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                else Text(stringResource(R.string.test), color = SpotifyGreen, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tr(R.string.this_only_changes_name_lookups_it, "This only changes name lookups — it isn't a VPN and doesn't change your IP address or region. ") +
                tr(R.string.cloudflare_dns_1_1_1_1, "Cloudflare DNS (1.1.1.1) is free and separate from Cloudflare WARP; no account or subscription is needed. ") +
                tr(R.string.for_the_whole_device_use_androids, "For the whole device, use Android's Settings → Network → Private DNS."),
            color = TextTertiary, fontSize = 11.sp,
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SpotifyGreen,
            selectedLabelColor = OnAccent,
            labelColor = TextPrimary,
        ),
        modifier = Modifier.testTag(tag),
    )
}
