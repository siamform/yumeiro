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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionScanner
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

@Composable
internal fun levelColor(l: ExtensionScanner.Level): Color = when (l) {
    ExtensionScanner.Level.SAFE -> SpotifyGreen
    ExtensionScanner.Level.CAUTION -> WarnColor
    ExtensionScanner.Level.DANGER -> ErrorColor
}

internal fun levelLabel(l: ExtensionScanner.Level): String = when (l) {
    ExtensionScanner.Level.SAFE -> "Safe"
    ExtensionScanner.Level.CAUTION -> "Caution"
    ExtensionScanner.Level.DANGER -> "Danger"
}

/** The safety review shown between download and install (see [ExtensionReview]). Add once at the app root. */
@Composable
fun ExtensionReviewHost() {
    DisposableEffect(Unit) {
        ExtensionReview.hostAttached = true
        onDispose { ExtensionReview.hostAttached = false; ExtensionReview.answer(false) }
    }
    val req by ExtensionReview.pending.collectAsState()
    val r = req ?: return
    var confirmDanger by remember(r) { mutableStateOf(false) }
    val danger = r.report.level == ExtensionScanner.Level.DANGER || !r.report.readable
    TsukiDialog(
        tone = (if (danger) dev.asfakul.yumeiro.ui.components.DialogTone.CRITICAL else if (r.report.level == ExtensionScanner.Level.SAFE) dev.asfakul.yumeiro.ui.components.DialogTone.SUCCESS else dev.asfakul.yumeiro.ui.components.DialogTone.WARNING),
        onDismissRequest = { ExtensionReview.answer(false) },
        modifier = Modifier.testTag("extension_review"),
        icon = {
            Icon(
                when (r.report.level) {
                    ExtensionScanner.Level.SAFE -> Icons.Default.GppGood
                    ExtensionScanner.Level.CAUTION -> Icons.Default.GppMaybe
                    ExtensionScanner.Level.DANGER -> Icons.Default.GppBad
                }, null, tint = levelColor(r.report.level), modifier = Modifier.size(32.dp),
            )
        },
        title = { Text(if (r.isUpdate) "Update ${r.name}?" else "Install ${r.name}?") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    (r.version?.let { "v$it · " } ?: "") + "from ${r.source}",
                    color = TextTertiary, fontSize = 12.sp,
                )
                Surface(shape = RoundedCornerShape(14.dp), color = levelColor(r.report.level).copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Safety check: ${levelLabel(r.report.level)}", color = levelColor(r.report.level), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(r.report.summary, color = TextPrimary, fontSize = 13.sp)
                    }
                }
                r.report.findings.forEach { f ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•", color = levelColor(f.level), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(f.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(f.detail, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
                Text(
                    "Yumeiro reads the extension's code without running it. It catches obvious risks, not every one — only install extensions from people you trust.",
                    color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp,
                )
                if (danger && confirmDanger) Text(
                    "Installing anyway gives this code the risks listed above. Tap Install anyway again to confirm.",
                    color = ErrorColor, fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            if (!r.report.readable) Unit
            else if (danger) TextButton(onClick = { if (confirmDanger) ExtensionReview.answer(true) else confirmDanger = true },
                modifier = Modifier.testTag("extension_review_install_anyway")) { Text("Install anyway", color = ErrorColor) }
            else TextButton(onClick = { ExtensionReview.answer(true) }, modifier = Modifier.testTag("extension_review_install")) {
                Text(if (r.isUpdate) "Update" else "Install", color = SpotifyGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = { ExtensionReview.answer(false) }, modifier = Modifier.testTag("extension_review_cancel")) { Text(if (danger) "Don't install" else "Cancel") } },
    )
}
