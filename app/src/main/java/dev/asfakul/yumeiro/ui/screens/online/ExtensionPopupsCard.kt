/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.ExtensionPopups
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound

/** ADR-014: Extensions → "Pop-ups from extensions" (Show / Quiet / Block promotional) and held messages. */
@Composable
internal fun ExtensionPopupsCard() {
    val context = LocalContext.current
    val mode by ExtensionPopups.mode.collectAsState()
    val held by ExtensionPopups.held.collectAsState()
    val heldIds = held.map { it.id }
    // ADR-014: this list replaces the quiet card while it is on screen (the card covered Dismiss).
    androidx.compose.runtime.LaunchedEffect(heldIds) { if (heldIds.isNotEmpty()) ExtensionPopups.onListShown() }
    Surface(shape = yuRound(18), color = GlassFill, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).testTag("extensions_popups")) {
        Column(Modifier.padding(14.dp)) {
            Text("Pop-ups from extensions", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                when (mode) {
                    ExtensionPopups.Mode.SHOW -> "Every window an extension opens is shown."
                    ExtensionPopups.Mode.QUIET -> "Logins and choices are shown. Donation and promo messages wait in a quiet note, at most once a week per extension, never during a video."
                    ExtensionPopups.Mode.BLOCK_PROMOTIONAL -> "Logins and choices are shown. Donation and promo messages are not shown."
                },
                color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtensionPopups.Mode.entries.forEach { m ->
                    OnlineChip(m.label, mode == m, { ExtensionPopups.setMode(context, m) }, Modifier.testTag("extensions_popups_${m.name.lowercase()}"))
                }
            }
            held.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).testTag("extensions_popup_held"), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ExtensionPopups.cardText(h.extName, h.functional), color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (h.title.isNotBlank()) Text(h.title, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { ExtensionPopups.reveal(h.id) }, modifier = Modifier.testTag("extensions_popup_show")) { Text("Show", color = SpotifyGreen) }
                    TextButton(onClick = { ExtensionPopups.discard(h.id) }, modifier = Modifier.testTag("extensions_popup_discard")) { Text("Dismiss", color = TextSecondary) }
                }
            }
        }
    }
}
