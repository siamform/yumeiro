/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.anime.AniList
import dev.asfakul.yumeiro.online.anime.MyAnimeList
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** Settings → Online videos: 18+ content, intro skip, subtitle search and anime trackers. */
@Composable
fun OnlineSettingsCard() {
    val context = LocalContext.current
    remember { OnlineSettings.init(context); true }
    val adult by OnlineSettings.adult.collectAsState()
    val skip by OnlineSettings.skipIntro.collectAsState()
    val auto by OnlineSettings.autoSkip.collectAsState()
    val lang by OnlineSettings.subtitleLang.collectAsState()
    val osKey by OnlineSettings.openSubtitlesKey.collectAsState()
    var confirmAdult by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    if (confirmAdult) TsukiDialog(
        onDismissRequest = { confirmAdult = false },
        title = { Text("Show 18+ content?") },
        text = { Text("Adult extensions and titles will appear in the market, home and search. Confirm you are 18 or older and that this content is legal where you live.") },
        confirmButton = { TextButton(onClick = { OnlineSettings.setAdult(context, true); confirmAdult = false }, modifier = Modifier.testTag("adult_confirm")) { Text("I'm 18 or older") } },
        dismissButton = { TextButton(onClick = { confirmAdult = false }) { Text("Cancel") } },
    )

    edit?.let { which ->
        var text by remember(which) { mutableStateOf(when (which) { "os" -> osKey; "lang" -> lang; "anilist" -> OnlineSettings.secret("anilist_client").orEmpty(); else -> OnlineSettings.secret("mal_client").orEmpty() }) }
        TsukiDialog(
            onDismissRequest = { edit = null },
            title = { Text(when (which) { "os" -> "OpenSubtitles API key"; "lang" -> "Subtitle language"; "anilist" -> "AniList client ID"; else -> "MyAnimeList client ID" }) },
            text = {
                Column {
                    Text(when (which) {
                        "os" -> "Free key from opensubtitles.com → Consumers. Used only to search subtitles."
                        "lang" -> "Two-letter code, e.g. en, bn, hi, es, ar."
                        "anilist" -> "Create a client at anilist.co/settings/developer with redirect URL ${AniList.REDIRECT}."
                        else -> "Create an app at myanimelist.net/apiconfig (type: Android) with redirect URL ${MyAnimeList.REDIRECT}."
                    }, color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (which) {
                        "os" -> OnlineSettings.setOpenSubtitlesKey(context, text)
                        "lang" -> OnlineSettings.setSubtitleLang(context, text.trim().lowercase().take(3).ifBlank { "en" })
                        "anilist" -> OnlineSettings.setSecret(context, "anilist_client", text.trim())
                        else -> OnlineSettings.setSecret(context, "mal_client", text.trim())
                    }
                    edit = null; refresh++
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
        ValueRow("OpenSubtitles API key", if (osKey.isBlank()) "Not set — needed for subtitle search" else "Set") { edit = "os" }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Anime tracking", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("Watched episodes update your list automatically.", color = TextTertiary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        androidx.compose.runtime.key(refresh) {
            TrackerRow("AniList", AniList.user(), AniList.loginUrl(), onEditId = { edit = "anilist" }, onSignOut = { AniList.signOut(context); refresh++ })
            TrackerRow("MyAnimeList", MyAnimeList.user(), if (MyAnimeList.clientId().isBlank()) null else "mal", onEditId = { edit = "mal" }, onSignOut = { MyAnimeList.signOut(context); refresh++ }) {
                MyAnimeList.loginUrl(context)
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
        TextButton(onClick = onClick) { Text("Change") }
    }
}

@Composable
private fun TrackerRow(name: String, user: String?, loginUrl: String?, onEditId: () -> Unit, onSignOut: () -> Unit, buildUrl: (() -> String?)? = null) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontSize = 15.sp)
            Text(user?.let { "Signed in as $it" } ?: if (loginUrl == null) "Needs a client ID" else "Not signed in", color = TextTertiary, fontSize = 12.sp)
        }
        when {
            user != null -> OutlinedButton(onClick = onSignOut) { Text("Sign out") }
            loginUrl == null -> OutlinedButton(onClick = onEditId) { Text("Set up") }
            else -> OutlinedButton(onClick = {
                val url = buildUrl?.invoke() ?: loginUrl
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { AppMessages.error(context, "No browser to sign in with") }
            }) { Text("Sign in") }
        }
    }
}
