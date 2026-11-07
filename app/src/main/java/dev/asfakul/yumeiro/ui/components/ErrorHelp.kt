/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

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
            has("451", "geo", "region", "country", "not available in your") -> {
                out += "This content is limited to some countries — pick another source or server"
                out += "Yumeiro doesn't bypass region locks"
            }
            has("401", "403", "access denied", "forbidden", "refused") -> {
                out += "Refresh the playlist or get a fresh link — tokens in links expire"
                out += "Add the site's Referer / Origin under Headers & DRM"
                out += "If the site needs a login, add its Cookie header"
                out += "Try another server or source"
            }
            has("404", "410", "not found", "removed", "gone") -> {
                out += "The link was removed or moved — refresh the playlist"
                out += "Try another server, mirror or episode source"
            }
            has("429", "too many") -> out += "The server is rate-limiting — wait a minute and retry"
            has("500", "502", "503", "504", "server error", "unavailable") -> {
                out += "The server is having trouble — retry in a moment"
                out += "Switch to another server or source"
            }
            has("timeout", "timed out") -> {
                out += "The connection is slow — retry, or switch between Wi-Fi and mobile data"
                out += "Turn off any VPN or data saver that may block streaming"
            }
            has("no internet", "offline", "unable to resolve", "unknownhost", "network", "connection", "dns") -> {
                out += "Check that you're online (Wi-Fi or mobile data)"
                out += "Turn off a VPN / private DNS, or try another network"
                out += "Retry — Yumeiro also retries automatically"
            }
            has("ssl", "certificate", "handshake", "tls") -> {
                out += "Check the phone's date & time are correct"
                out += "The site's security certificate is broken — try another source"
            }
            has("drm", "widevine", "license", "clearkey") -> {
                out += "Add the licence URL / ClearKey under Headers & DRM"
                out += "Some DRM streams only play in the provider's own app"
            }
            has("decoder", "codec", "unsupported", "format", "hevc", "av1", "renderer") -> {
                out += "Your device can't decode this format — try a lower quality or another source"
                out += "Open it in an external player from the ⋮ menu"
            }
            has("extension", "plugin", "provider", "hue") -> {
                out += "Update the extension in Hues → Installed"
                out += "Try another server or another extension"
            }
            has("storage", "space", "disk", "enospc") -> out += "Free some storage space and retry"
            has("permission") -> out += "Allow the permission in Android settings → Apps → Yumeiro"
            has("signature", "certificate mismatch", "install") -> out += "Download the update again from the official GitHub releases page"
            has("empty", "no results", "nothing") -> out += "Try another search, category or source"
        }
        if (out.isEmpty()) {
            out += "Retry — many problems are temporary"
            out += "Try another source, server or quality"
            out += "Check your connection and turn off VPN / data saver"
        }
        return out
    }
}

/** Compact "Try this" list for error cards (dark or light). */
@Composable
fun ErrorSuggestions(tips: List<String>, modifier: Modifier = Modifier, onDark: Boolean = true) {
    if (tips.isEmpty()) return
    val accent = LocalMtPalette.current.accent
    val text = if (onDark) Color.White.copy(alpha = 0.85f) else LocalMtPalette.current.textPrimary
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background((if (onDark) Color.White else Color.Black).copy(alpha = 0.06f))
            .padding(12.dp).testTag("error_suggestions"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("TRY THIS", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
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
