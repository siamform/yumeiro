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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.asfakul.yumeiro.diagnostics.CrashLog
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import java.text.DateFormat
import java.util.Date

/** Longest part of a report shown in the dialog; the full file is what gets shared. */
internal const val CRASH_PREVIEW_CHARS = 48_000

/** Reads at most [max] characters, so a multi-megabyte report never lands in one Text. */
internal fun crashPreview(file: java.io.File, max: Int = CRASH_PREVIEW_CHARS): String = runCatching {
    file.bufferedReader().use { r ->
        val buf = CharArray(max)
        var n = 0
        while (n < max) { val k = r.read(buf, n, max - n); if (k < 0) break; n += k }
        val more = r.read() >= 0
        String(buf, 0, n) + if (more) "\n\n… (the report is longer; Share sends the whole file)" else ""
    }
}.getOrDefault("This report can't be read.")

/** Round 2, order 7: the crash reports saved on this phone; read, copy, share or delete. */
@Composable
internal fun CrashReportsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    // R15 audit: the list, the preview and the share are all built off the main thread; a large
    // report (the case Phase 5 is about) used to be read and laid out whole during composition.
    val reports by produceState(emptyList<CrashLog.Report>(), version) {
        value = withContext(Dispatchers.IO) { CrashLog.list(context) }
    }
    var open by remember { mutableStateOf<CrashLog.Report?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    fun share(files: List<java.io.File>, subject: String) {
        val uris = files.mapNotNull { f ->
            runCatching { FileProvider.getUriForFile(context, context.packageName + ".files", f) }.getOrNull()
        }
        if (uris.isEmpty()) return
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        send.setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    SettingsCard {
        if (reports.isEmpty()) {
            Text("No crashes recorded. If Yumeiro ever closes by itself or freezes, the report is saved here.",
                color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp).testTag("crash_empty"))
        } else {
            reports.forEachIndexed { i, r ->
                SettingsActionRow(r.headline.take(90), "${r.kind} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.time))}", "crash_item_$i") { open = r }
            }
            SettingsActionRow("Share all", "Every report, attached as files", "crash_share_all") {
                share(reports.map { it.file }, "Yumeiro crash reports (${reports.size})")
            }
            // G12 X6: this used to delete at once, and said "from this phone" while the copies in
            // Download/Yumeiro/Crash reports stay. Now it asks first and says what it removes.
            SettingsActionRow("Delete all", "Remove them from Yumeiro; copies in Download stay", "crash_clear", danger = true) { confirmClear = true }
        }
        Text("Reports stay on this phone until you share them. A copy of each one is also saved as a file in Download/Yumeiro/Crash reports, ready to attach. They hold the app version, phone model, Android version and the error, and leave out links with keys, tokens and e-mail addresses.",
            color = TextTertiary, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    }
    if (confirmClear) {
        val n = reports.size
        dev.asfakul.yumeiro.ui.components.TsukiDialog(
            tone = dev.asfakul.yumeiro.ui.components.DialogTone.DANGER,
            onDismissRequest = { confirmClear = false },
            title = { Text(if (n == 1) "Delete this crash report?" else "Delete all $n crash reports?") },
            text = { Text("They are removed from Yumeiro. Copies already saved in Download/Yumeiro/Crash reports stay; delete those in your Files app. This can't be undone.") },
            confirmButton = {
                dev.asfakul.yumeiro.ui.components.DialogConfirmButton("Delete", modifier = Modifier.testTag("crash_clear_confirm"), onClick = {
                    confirmClear = false
                    scope.launch { withContext(Dispatchers.IO) { CrashLog.clear(context) }; version++ }
                })
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }, modifier = Modifier.testTag("crash_clear_cancel")) { Text("Keep") } },
        )
    }
    open?.let { r ->
        val text by produceState<String?>(null, r) { value = withContext(Dispatchers.IO) { crashPreview(r.file) } }
        dev.asfakul.yumeiro.ui.components.TsukiDialog(
            onDismissRequest = { open = null },
            title = { Text(r.kind) },
            text = {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).testTag("crash_text")) {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Text(text ?: "Loading…", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { share(listOf(r.file), "Yumeiro crash report"); open = null }, modifier = Modifier.testTag("crash_share")) { Text("Share") } },
            dismissButton = { TextButton(onClick = { open = null }, modifier = Modifier.testTag("crash_close")) { Text("Close") } },
        )
    }
}
