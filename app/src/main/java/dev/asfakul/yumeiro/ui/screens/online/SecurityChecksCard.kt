/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.web.VerificationCenter
import dev.asfakul.yumeiro.online.web.VerificationPolicy
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.WarningTone
import kotlinx.coroutines.delay

/**
 * ADR-059: Extensions → "Security checks". One row per site that asked to confirm a person is there,
 * with what that means now (an icon and words, never colour alone) and Check now / Forget. Hidden
 * until a site has asked.
 */
@Composable
internal fun SecurityChecksCard() {
    val sites by VerificationCenter.list.collectAsState()
    if (sites.isEmpty()) return
    // The "for 25 min" wording counts down while the card is on screen (once a minute, no work otherwise).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000L); now = System.currentTimeMillis() } }
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).testTag("security_checks")) {
        Column(Modifier.padding(14.dp)) {
            Text("Security checks", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "Sites that asked to confirm you're a person. Each one asks once; Yumeiro reuses the pass, and a check you close stays closed for a while.",
                color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
            )
            sites.forEach { s ->
                val (icon, tint) = when (s.state) {
                    VerificationCenter.State.CLEAR -> Icons.Default.VerifiedUser to SpotifyGreen
                    VerificationCenter.State.NEEDS_YOU -> Icons.Default.TouchApp to WarningTone
                    VerificationCenter.State.PAUSED -> Icons.Default.PauseCircle to TextSecondary
                    VerificationCenter.State.BLOCKED -> Icons.Default.Block to WarningTone
                    VerificationCenter.State.FAILED -> Icons.Default.ErrorOutline to TextSecondary
                }
                val line = VerificationPolicy.describe(s, now)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).testTag("security_check_${s.host}")
                        .semantics(mergeDescendants = false) { contentDescription = "${s.label}, ${s.host}. $line" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        val title = if (s.label == s.host) s.host else "${s.label} · ${s.host}"
                        Text(title, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(line, color = TextTertiary, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (s.state != VerificationCenter.State.CLEAR) {
                        TextButton(onClick = { VerificationCenter.checkNow(s.host) }, modifier = Modifier.testTag("security_check_now")) {
                            Text("Check now", color = SpotifyGreen)
                        }
                    }
                    TextButton(onClick = { VerificationCenter.forget(s.host) }, modifier = Modifier.testTag("security_check_forget")) {
                        Text("Forget", color = TextSecondary)
                    }
                }
            }
        }
    }
}
