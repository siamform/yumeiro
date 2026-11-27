/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * Turns any error text into short, practical "Try this" suggestions. Messages that already carry
 * their own bullet list ("• …") keep it; everything else is matched by keyword (HTTP code, network,
 * decoder, DRM, region, storage, …). Pure logic — unit tested.
 */
object ErrorHelp {

    data class Parsed(val summary: String, val tips: List<String>)

    private val BULLET = Regex("^\\s*(?:[•\\-*]|\\d+[.)])\\s+")

    fun parse(text: String): Parsed {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val bullets = lines.filter { BULLET.containsMatchIn(it) }.map { it.replace(BULLET, "") }
        val summary = lines.filterNot { BULLET.containsMatchIn(it) }.joinToString(" ")
        val tips = (bullets.ifEmpty { suggestionsFor(text) }).distinct().take(5)
        return Parsed(summary, tips)
    }

    fun suggestionsFor(text: String): List<String> {
        val t = text.lowercase()
        fun has(vararg k: String) = k.any { it in t }
        val out = mutableListOf<String>()
        when {
            // G10 ER-21: before "firewall" (browser check) and "refused" (403 tips).
            has("blocking yumeiro's internet") -> {
                out += tr(R.string.open_settings_apps_yumeiro_mobile_data, "Open Settings › Apps › Yumeiro › Mobile data & Wi-Fi and allow background and mobile data")
                out += tr(R.string.turn_off_data_saver_or_allow, "Turn off Data Saver, or allow Yumeiro in it")
                out += tr(R.string.if_you_use_a_firewall_ad, "If you use a firewall, ad-block or VPN app, allow Yumeiro there")
            }
            has("on device", "on this phone", "file not found", "filenotfound", "file was moved", "enoent") -> {
                out += tr(R.string.the_file_was_moved_or_deleted, "The file was moved or deleted — rescan your music or videos")
                out += tr(R.string.check_that_the_sd_card_or, "Check that the SD card or USB storage is still connected")
            }
            has("broken link", "expected url scheme", "no scheme was found", "unexpected url") -> {
                out += tr(R.string.the_site_sent_a_broken_link_2, "The site sent a broken link for this source — pick another source from the list")
                out += tr(R.string.retry_in_a_moment_sites_often, "Retry in a moment — sites often fix their links quickly")
            }
            has("firewall", "captcha", "browser check", "checking your browser", "verify you are human", "sign in", "log in", "login") -> {
                out += tr(R.string.tap_open_site_to_pass_the, "Tap Open site to pass the check (or sign in), then tap Done — the source retries by itself")
                out += tr(R.string.turn_off_a_vpn_custom_dns, "Turn off a VPN / custom DNS — firewalls often block shared IPs")
                out += tr(R.string.try_another_source_while_the_site, "Try another source while the site cools down")
            }
            has("451", "geo", "region", "country", "not available in your") -> {
                out += tr(R.string.this_content_is_limited_to_some, "This content is limited to some countries — pick another source or server")
                out += tr(R.string.yumeiro_doesnt_bypass_region_locks, "Yumeiro doesn't bypass region locks")
            }
            // R17 G10 (ER-1): the shared mapper's own texts (OnlineErrors.friendly) must get the matching
            // tips. "Your network refused the connection" got the 403 header tips, "Secure connection
            // failed" the offline tips, "The server took too long to answer" only the generic ones.
            has("refused the connection") -> {
                out += tr(R.string.the_site_may_be_blocked_on, "The site may be blocked on this network — turn a VPN on or off, or try another network")
                out += tr(R.string.retry_yumeiro_also_retries_automatically, "Retry — Yumeiro also retries automatically")
            }
            has("secure connection", "ssl", "certificate verif", "handshake", "tls") && !has("certificate mismatch") -> {
                out += tr(R.string.check_the_phones_date_time_are, "Check the phone's date & time are correct")
                out += tr(R.string.the_sites_security_certificate_is_broken, "The site's security certificate is broken — try another source")
            }
            has("401", "403", "access denied", "forbidden", "refused") -> {
                out += tr(R.string.refresh_the_playlist_or_get_a, "Refresh the playlist or get a fresh link — tokens in links expire")
                out += tr(R.string.add_the_sites_referer_origin_under, "Add the site's Referer / Origin under Headers & DRM")
                out += tr(R.string.if_the_site_needs_a_login, "If the site needs a login, add its Cookie header")
                out += tr(R.string.try_another_server_or_source, "Try another server or source")
            }
            has("404", "410", "not found", "removed", "gone") -> {
                out += tr(R.string.the_link_was_removed_or_moved, "The link was removed or moved — refresh the playlist")
                out += tr(R.string.try_another_server_mirror_or_episode, "Try another server, mirror or episode source")
            }
            has("429", "too many") -> out += tr(R.string.the_server_is_rate_limiting_wait, "The server is rate-limiting — wait a minute and retry")
            has("500", "502", "503", "504", "server error", "unavailable") -> {
                out += tr(R.string.the_server_is_having_trouble_retry, "The server is having trouble — retry in a moment")
                out += tr(R.string.switch_to_another_server_or_source, "Switch to another server or source")
            }
            has("timeout", "timed out", "took too long") -> {
                out += tr(R.string.the_connection_is_slow_retry_or, "The connection is slow — retry, or switch between Wi-Fi and mobile data")
                out += tr(R.string.turn_off_any_vpn_or_data, "Turn off any VPN or data saver that may block streaming")
            }
            has("no internet", "offline", "unable to resolve", "unknownhost", "network", "connection", "dns") -> {
                out += tr(R.string.check_that_youre_online_wi_fi, "Check that you're online (Wi-Fi or mobile data)")
                out += tr(R.string.turn_off_a_vpn_private_dns, "Turn off a VPN / private DNS, or try another network")
                out += tr(R.string.retry_yumeiro_also_retries_automatically, "Retry — Yumeiro also retries automatically")
            }
            has("ssl", "certificate", "handshake", "tls") && !has("certificate mismatch") -> {
                out += tr(R.string.check_the_phones_date_time_are, "Check the phone's date & time are correct")
                out += tr(R.string.the_sites_security_certificate_is_broken, "The site's security certificate is broken — try another source")
            }
            has("drm", "widevine", "license", "clearkey") -> {
                out += tr(R.string.add_the_licence_url_clearkey_under, "Add the licence URL / ClearKey under Headers & DRM")
                out += tr(R.string.some_drm_streams_only_play_in, "Some DRM streams only play in the provider's own app")
            }
            has("decoder", "codec", "unsupported", "format", "hevc", "av1", "renderer") -> {
                out += tr(R.string.your_device_cant_decode_this_format, "Your device can't decode this format — try a lower quality or another source")
                out += tr(R.string.open_it_in_an_external_player, "Open it in an external player from the ⋮ menu")
            }
            has("extension", "plugin", "provider", "hue", "unreadable data") -> {
                out += tr(R.string.update_the_extension_in_yumix_installed, "Update the extension in Yumix → Installed")
                out += tr(R.string.try_another_server_or_another_extension, "Try another server or another extension")
            }
            has("storage", "space", "disk", "enospc") -> out += tr(R.string.free_some_storage_space_and_retry, "Free some storage space and retry")
            has("permission") -> out += tr(R.string.allow_the_permission_in_android_settings, "Allow the permission in Android settings → Apps → Yumeiro")
            has("signature", "certificate mismatch", "install") -> out += tr(R.string.download_the_update_again_from_the, "Download the update again from the official GitHub releases page")
            has("empty", "no results", "nothing") -> out += tr(R.string.try_another_search_category_or_source, "Try another search, category or source")
        }
        if (out.isEmpty()) {
            out += tr(R.string.retry_many_problems_are_temporary, "Retry — many problems are temporary")
            out += tr(R.string.try_another_source_server_or_quality, "Try another source, server or quality")
            out += tr(R.string.check_your_connection_and_turn_off_2, "Check your connection and turn off VPN / data saver")
        }
        return out
    }

    /**
     * Tips for an extension/source error. Unlike [suggestionsFor] with an "extension" keyword, this
     * only suggests updating the extension when the error looks like an outdated extension (broken
     * parsing / missing API) — network, firewall and broken-link errors aren't fixed by an update.
     */
    fun forExtension(text: String): List<String> {
        val t = text.lowercase()
        val outdated = listOf("outdated", "parse", "selector", "nosuchmethod", "noclassdef", "abstractmethod",
            "null", "index", "doesn't support", "unreadable").any { it in t }
        val specific = suggestionsFor(text).takeUnless { it.firstOrNull()?.startsWith("Retry — many") == true }
        val out = (specific ?: listOf("Retry — many problems are temporary", tr(R.string.try_another_source_or_server, "Try another source or server"))).toMutableList()
        if (outdated) out += tr(R.string.check_for_an_update_in_yumix, "Check for an update in Yumix → Installed (tap Check for update)")
        return out.distinct().take(4)
    }
}

/** Compact "Try this" list for error cards (dark or light). */
@Composable
fun ErrorSuggestions(tips: List<String>, modifier: Modifier = Modifier, onDark: Boolean = true) {
    if (tips.isEmpty()) return
    val accent = LocalMtPalette.current.accent
    val text = if (onDark) Color.White.copy(alpha = 0.85f) else LocalMtPalette.current.textPrimary
    Column(
        modifier.fillMaxWidth().clip(yuRound(14))
            .background((if (onDark) Color.White else Color.Black).copy(alpha = 0.06f))
            .padding(12.dp).testTag("error_suggestions"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.try_this), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
        tips.forEach { tip ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(tip, color = text, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}
