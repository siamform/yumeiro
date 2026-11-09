/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** Settings → Online videos: 18+ content, intro skip and subtitle search. Trackers live in Settings → Tracking. */
@Composable
fun OnlineSettingsCard() {
    val context = LocalContext.current
    remember { OnlineSettings.init(context); true }
    val adult by OnlineSettings.adult.collectAsState()
    val skip by OnlineSettings.skipIntro.collectAsState()
    val auto by OnlineSettings.autoSkip.collectAsState()
    val lang by OnlineSettings.subtitleLang.collectAsState()
    val osKey by OnlineSettings.openSubtitlesKey.collectAsState()
    val subdlKey by OnlineSettings.subdlKey.collectAsState()
    val subsourceKey by OnlineSettings.subsourceKey.collectAsState()
    var confirmAdult by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<String?>(null) }

    if (confirmAdult) TsukiDialog(
        onDismissRequest = { confirmAdult = false },
        title = { Text("Show 18+ content?") },
        text = { Text("Adult extensions and titles will appear in the market, home and search. Confirm you are 18 or older and that this content is legal where you live.") },
        confirmButton = { TextButton(onClick = { OnlineSettings.setAdult(context, true); confirmAdult = false }, modifier = Modifier.testTag("adult_confirm")) { Text("I'm 18 or older") } },
        dismissButton = { TextButton(onClick = { confirmAdult = false }) { Text("Cancel") } },
    )

    edit?.let { which ->
        var text by remember(which) { mutableStateOf(when (which) { "os" -> osKey; "subdl" -> subdlKey; "subsource" -> subsourceKey; else -> lang }) }
        TsukiDialog(
            onDismissRequest = { edit = null },
            title = { Text(when (which) { "os" -> "OpenSubtitles API key"; "subdl" -> "SubDL API key"; "subsource" -> "SubSource API key"; else -> "Subtitle language" }) },
            text = {
                Column {
                    Text(when (which) {
                        "os" -> "Free key: sign up at opensubtitles.com, open Consumers, create one and paste its API key. Used only to search subtitles."
                        "subdl" -> "Free key: sign in at subdl.com, open your account panel and copy the API key. Used only to search and download subtitles."
                        "subsource" -> "Free key: sign in at subsource.net, open your profile and create an API key. Used only to search and download subtitles."
                        else -> "Two-letter code, e.g. en, bn, hi, es, ar (pt-br for Brazilian Portuguese)."
                    }, color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("setting_key_field"))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (which) {
                        "os" -> OnlineSettings.setOpenSubtitlesKey(context, text)
                        "subdl" -> OnlineSettings.setSubdlKey(context, text)
                        "subsource" -> OnlineSettings.setSubsourceKey(context, text)
                        else -> OnlineSettings.setSubtitleLang(context, text.trim().lowercase().take(5).ifBlank { "en" })
                    }
                    edit = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } },
        )
    }

    SettingsCard {
        SettingsToggleRow("Show 18+ content", "Adult extensions and titles. Off by default.", adult, { on -> if (on) confirmAdult = true else OnlineSettings.setAdult(context, false) }, "setting_adult")
        if (adult) {
            val priv by OnlineSettings.adultPrivate.collectAsState()
            SettingsToggleRow("Keep 18+ activity private", "18+ titles you watch are saved quietly: they never show in Home, feeds, suggestions or Continue watching. Find them with search", priv,
                { OnlineSettings.setAdultPrivate(context, it) }, "setting_adult_private")
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        SettingsToggleRow("Skip intro & outro (anime)", "Shows a Skip button using AniSkip community times", skip, { OnlineSettings.setSkipIntro(context, it) }, "setting_skip_intro")
        if (skip) SettingsToggleRow("Skip automatically", "Jump past intros and outros without asking", auto, { OnlineSettings.setAutoSkip(context, it) }, "setting_auto_skip")
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        ValueRow("Subtitle search language", lang.uppercase()) { edit = "lang" }
        Text("Subtitle sites (all free, use any or all)", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        ValueRow("OpenSubtitles API key", when { osKey.isNotBlank() -> "Your key"; dev.asfakul.yumeiro.online.subtitles.OpenSubtitles.builtIn -> "Built-in"; else -> "Not set" }) { edit = "os" }
        ValueRow("SubDL API key", if (subdlKey.isBlank()) "Not set" else "Set") { edit = "subdl" }
        ValueRow("SubSource API key", if (subsourceKey.isBlank()) "Not set" else "Set") { edit = "subsource" }
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            Text(value, color = TextTertiary, fontSize = 12.sp)
        }
        TextButton(onClick = onClick) { Text("Change") }
    }
}
