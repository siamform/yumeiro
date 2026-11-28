/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
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

/** Which rows of the online-video settings to show (FINAL-47: each part lives on its own page). */
enum class OnlinePart { ADULT, SKIP, SUBTITLES }

/**
 * 18+ content (Settings → Profiles, it is per profile), intro skip and subtitle search (both on
 * Settings → Subtitles & intros). One composable so the dialogs and state stay in one place.
 */
@Composable
fun OnlineSettingsCard(part: OnlinePart) {
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
    var adultMainCheck by remember { mutableStateOf(false) }
    // PR-1: with the main profile locked, another profile turns 18+ on only after the main profile's lock.
    if (adultMainCheck) (context as? android.app.Activity)?.let { act ->
        dev.asfakul.yumeiro.profiles.Profiles.find(context, dev.asfakul.yumeiro.profiles.Profiles.DEFAULT)?.let { main ->
            dev.asfakul.yumeiro.ui.screens.profiles.UnlockProfileDialog(act, main, title = "${main.name}'s lock is needed for 18+",
                onDismiss = { adultMainCheck = false }, onUnlocked = { adultMainCheck = false; confirmAdult = true })
        }
    }
    var edit by remember { mutableStateOf<String?>(null) }

    if (confirmAdult) TsukiDialog(
        tone = dev.asfakul.yumeiro.ui.components.DialogTone.WARNING,
        onDismissRequest = { confirmAdult = false },
        title = { Text(stringResource(R.string.show_18_content)) },
        text = { Text(stringResource(R.string.adult_extensions_and_titles_will_appear)) },
        confirmButton = { TextButton(onClick = { OnlineSettings.setAdult(context, true); confirmAdult = false }, modifier = Modifier.testTag("adult_confirm")) { Text(stringResource(R.string.im_18_or_older)) } },
        dismissButton = { TextButton(onClick = { confirmAdult = false }) { Text(stringResource(R.string.cancel)) } },
    )

    // B19: the SubDL key is checked with SubDL before it's saved (same form as the Tracking page).
    if (edit == "subdl") SubDlKeyDialog(onDismiss = { edit = null }) { edit = null }
    edit?.takeIf { it != "subdl" }?.let { which ->
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
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    SettingsCard {
        when (part) {
            OnlinePart.ADULT -> {
                SettingsToggleRow("Show 18+ content", "Adult extensions and titles, for the profile you are using. Off by default.", adult, { on ->
                    if (!on) OnlineSettings.setAdult(context, false)
                    else if (dev.asfakul.yumeiro.profiles.Profiles.needsMainUnlock(context)) adultMainCheck = true
                    else confirmAdult = true
                }, "setting_adult")
                if (adult) {
                    val priv by OnlineSettings.adultPrivate.collectAsState()
                    SettingsToggleRow("Keep 18+ activity private", "18+ titles you watch are saved quietly: they never show in Home, feeds, suggestions or Continue watching. Find them with search", priv,
                        { OnlineSettings.setAdultPrivate(context, it) }, "setting_adult_private")
                }
            }
            OnlinePart.SKIP -> {
                SettingsToggleRow("Skip intro & outro", "Community times: AniSkip for anime, IntroDB for shows and movies", skip, { OnlineSettings.setSkipIntro(context, it) }, "setting_skip_intro")
                if (skip) SettingsToggleRow("Skip automatically", "Jump past intros and outros without asking", auto, { OnlineSettings.setAutoSkip(context, it) }, "setting_auto_skip")
            }
            OnlinePart.SUBTITLES -> {
                ValueRow("Subtitle search language", lang.uppercase()) { edit = "lang" }
                Text(stringResource(R.string.subtitle_sites_all_free_use_any), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                ValueRow("OpenSubtitles API key", when { osKey.isNotBlank() -> "Your key"; dev.asfakul.yumeiro.online.subtitles.OpenSubtitles.builtIn -> "Built-in"; else -> "Not set" }) { edit = "os" }
                ValueRow("SubDL API key", if (subdlKey.isBlank()) "Not set" else dev.asfakul.yumeiro.online.subtitles.SubDL.account() ?: "Set") { edit = "subdl" }
                ValueRow("SubSource API key", if (subsourceKey.isBlank()) "Not set" else "Set") { edit = "subsource" }
            }
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            Text(value, color = TextTertiary, fontSize = 12.sp)
        }
        TextButton(onClick = onClick) { Text(stringResource(R.string.change)) }
    }
}
