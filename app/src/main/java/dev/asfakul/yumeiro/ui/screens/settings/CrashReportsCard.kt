/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.diagnostics.CrashLog
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import java.text.DateFormat
import java.util.Date

/** Round 2, order 7: the crash reports saved on this phone; read, copy, share or delete. */
@Composable
internal fun CrashReportsCard() {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val reports = remember(version) { CrashLog.list(context) }
    var open by remember { mutableStateOf<CrashLog.Report?>(null) }
    fun share(text: String, subject: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text.take(90_000))
        context.startActivity(Intent.createChooser(send, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    SettingsCard {
        if (reports.isEmpty()) {
            Text("No crashes recorded. If Yumeiro ever closes by itself or freezes, the report is saved here.",
                color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp).testTag("crash_empty"))
        } else {
            reports.forEachIndexed { i, r ->
                SettingsActionRow(r.headline.take(90), "${r.kind} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.time))}", "crash_item_$i") { open = r }
            }
            SettingsActionRow("Share all", "Every report in one message", "crash_share_all") {
                share(reports.joinToString("\n\n──────────\n\n") { runCatching { it.file.readText() }.getOrDefault("") }, "Yumeiro crash reports (${reports.size})")
            }
            SettingsActionRow("Delete all", "Remove the saved reports from this phone", "crash_clear", danger = true) { CrashLog.clear(context); version++ }
        }
        Text("Reports stay on this phone until you share them. They hold the app version, phone model, Android version and the error, and leave out links with keys, tokens and e-mail addresses.",
            color = TextTertiary, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    }
    open?.let { r ->
        val text = remember(r) { runCatching { r.file.readText() }.getOrDefault("This report can't be read.") }
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(r.kind) },
            text = {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).testTag("crash_text")) {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Text(text, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { share(text, "Yumeiro crash report"); open = null }, modifier = Modifier.testTag("crash_share")) { Text("Share") } },
            dismissButton = { TextButton(onClick = { open = null }) { Text("Close") } },
        )
    }
}
