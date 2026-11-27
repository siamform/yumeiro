/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.tracking.v2.TrackV2
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound

/** TRACK-2: the Tracking V2 switch, what is waiting, and the last results (Settings → Tracking). */
@Composable
internal fun TrackingV2Card() {
    val context = LocalContext.current
    val on by TrackV2.enabled.collectAsState()
    val queue by TrackV2.queue.collectAsState()
    val log by TrackV2.log.collectAsState()
    Card(Modifier.fillMaxWidth().testTag("tracking_v2_card"), shape = yuRound(14), colors = CardDefaults.cardColors(containerColor = GlassFill)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsToggleRow(
                "Tracking V2",
                if (on) "Episodes and manga / novel chapters are queued and sent until every tracker has them, even after being offline"
                else "Off: episodes are sent once while you watch (classic tracking)",
                on, { TrackV2.setEnabled(context, it) }, "tracking_v2_toggle",
            )
            if (on) {
                Text(
                    stringResource(R.string.chapters_go_to_anilist_and_myanimelist),
                    color = TextTertiary, fontSize = 12.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (queue.isEmpty()) "Nothing waiting" else "${queue.size} waiting to sync",
                        color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.weight(1f).testTag("tracking_v2_waiting"),
                    )
                    OutlinedButton(onClick = { TrackV2.syncNow(context) }, enabled = queue.isNotEmpty(), modifier = Modifier.testTag("tracking_v2_sync")) { Text(stringResource(R.string.sync_now)) }
                }
                queue.take(3).forEach { q ->
                    Text("${q.title} · ${if (q.kind.reading) "chapter" else "episode"} ${q.unit} → ${q.pending.sorted().joinToString()}" +
                        (q.lastError?.let { " (retrying: $it)" } ?: ""), color = TextSecondary, fontSize = 12.sp, maxLines = 2)
                }
                log.take(4).forEach { l ->
                    Text("${if (l.ok) "Sent" else "Failed"} · ${l.title} ${l.unit} · ${l.note}", color = if (l.ok) TextSecondary else TextTertiary, fontSize = 12.sp, maxLines = 2)
                }
            }
        }
    }
}
