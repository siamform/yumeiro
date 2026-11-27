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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.network.AppProxy
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NBUG-13 (ADR-045): Settings → Network → Proxy. The user's own HTTP or SOCKS5 proxy for streams,
 * extensions, images, downloads, subtitles, updates and web pages. Off by default.
 */
@Composable
fun ProxySettingsCard() {
    val context = LocalContext.current
    val saved by AppProxy.config.collectAsState()
    val web by AppProxy.webState.collectAsState()
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(AppProxy.Type.OFF) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val c = withContext(Dispatchers.IO) { AppProxy.load(context) }
        type = c.type; host = c.host; port = if (c.port > 0) c.port.toString() else ""; user = c.user; pass = c.pass
        loaded = true
    }
    fun draft() = AppProxy.Config(type, host.trim(), port.trim().toIntOrNull() ?: 0, if (type == AppProxy.Type.HTTP || type == AppProxy.Type.SOCKS) user.trim() else "", pass)

    SettingsCard {
        Text(stringResource(R.string.proxy), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(
            tr(R.string.send_yumeiros_traffic_through_your_own, "Send Yumeiro's traffic through your own proxy: streams, extensions, images, downloads, subtitles, updates and web pages. ") +
                tr(R.string.use_it_when_your_network_blocks, "Use it when your network blocks a site by its address (DNS can't fix that). Local devices (casting, home servers) stay direct."),
            color = TextSecondary, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppProxy.Type.entries.forEach { t ->
                FilterChip(
                    selected = type == t, onClick = { type = t; result = null },
                    label = { Text(t.label, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SpotifyGreen, selectedLabelColor = OnAccent, labelColor = TextPrimary),
                    modifier = Modifier.testTag("proxy_type_${t.name.lowercase()}"),
                )
            }
        }
        if (type != AppProxy.Type.OFF) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(host, { host = it; result = null }, singleLine = true, label = { Text(stringResource(R.string.address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(1f).testTag("proxy_host"))
                OutlinedTextField(port, { v -> port = v.filter { it.isDigit() }.take(5); result = null }, singleLine = true, label = { Text(stringResource(R.string.port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(110.dp).testTag("proxy_port"))
            }
            OutlinedTextField(user, { user = it; result = null }, singleLine = true, label = { Text(stringResource(R.string.username_optional)) },
                modifier = Modifier.fillMaxWidth().testTag("proxy_user"))
            OutlinedTextField(pass, { pass = it; result = null }, singleLine = true, label = { Text(stringResource(R.string.password_optional)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().testTag("proxy_pass"))
            if (type == AppProxy.Type.SOCKS && user.isNotBlank()) {
                Text(stringResource(R.string.web_pages_cant_use_a_socks5), color = TextTertiary, fontSize = 11.sp)
            }
        }
        SettingsDivider()
        Text(
            result ?: if (saved.on) "In use: ${saved.summary}" else tr(R.string.not_in_use, "Not in use."),
            color = if (result?.startsWith("Failed") == true) MaterialTheme.colorScheme.error else TextSecondary,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().testTag("proxy_status"),
        )
        if (saved.on) {
            Text(
                when (web) {
                    AppProxy.WebState.ON -> tr(R.string.web_pages_use_the_proxy_too, "Web pages use the proxy too.")
                    AppProxy.WebState.UNSUPPORTED -> tr(R.string.this_devices_webview_cant_use_a, "This device's WebView can't use a proxy, so web pages (site browser, checks) go direct. Update Android System WebView to fix it.")
                    AppProxy.WebState.FAILED -> tr(R.string.web_pages_couldnt_be_set_to, "Web pages couldn't be set to use the proxy; they go direct.")
                    else -> tr(R.string.setting_up_web_pages, "Setting up web pages…")
                },
                color = TextTertiary, fontSize = 11.sp, modifier = Modifier.testTag("proxy_web_state"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (busy) CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            if (type != AppProxy.Type.OFF) TextButton(
                enabled = loaded && !busy,
                onClick = {
                    busy = true; result = null
                    val d = draft()
                    scope.launch { result = withContext(Dispatchers.IO) { AppProxy.test(context, d) }; busy = false }
                },
                modifier = Modifier.testTag("proxy_test"),
            ) { Text(stringResource(R.string.test), color = SpotifyGreen, fontWeight = FontWeight.SemiBold) }
            TextButton(
                enabled = loaded && !busy,
                onClick = {
                    val d = draft()
                    scope.launch {
                        val err = withContext(Dispatchers.IO) { AppProxy.save(context, d) }
                        result = err?.let { "Failed · $it" } ?: if (d.type == AppProxy.Type.OFF) tr(R.string.proxy_turned_off, "Proxy turned off.") else "Saved. New connections use ${d.type.label} ${d.host}:${d.port}."
                    }
                },
                modifier = Modifier.testTag("proxy_save"),
            ) { Text(stringResource(R.string.save), color = SpotifyGreen, fontWeight = FontWeight.SemiBold) }
        }
        Text(
            tr(R.string.not_covered_torrent_peers_and_dns, "Not covered: torrent peers and DNS-over-TLS / plain DNS lookups (with a proxy on, sites are looked up by the proxy). ") +
                tr(R.string.your_proxy_sees_which_sites_you, "Your proxy sees which sites you open; use one you trust."),
            color = TextTertiary, fontSize = 11.sp,
        )
    }
}
