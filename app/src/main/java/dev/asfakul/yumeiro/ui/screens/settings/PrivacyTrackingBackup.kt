/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.anime.AniList
import dev.asfakul.yumeiro.online.anime.MyAnimeList
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.tracking.Kitsu
import dev.asfakul.yumeiro.tracking.Trakt
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 14.dp))
    if (hint != null) Text(hint, color = TextTertiary, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = GlassFill)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { content() }
    }
}

// region Privacy

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val incognito by Privacy.incognito.collectAsState()
    val lock by Privacy.lockEnabled.collectAsState()
    val after by Privacy.lockAfter.collectAsState()
    val hide by Privacy.hideInRecents.collectAsState()
    Column(Modifier.fillMaxSize()) {
        SubPageHeader(title = "Privacy", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 48.dp)) {
            Section("Incognito mode", "While on, nothing you watch, read or listen to is saved: no history, no Continue watching, no tracker updates.") {
                SettingsToggleRow("Incognito", if (incognito) "On — history is paused" else "Off", incognito, { Privacy.setIncognito(context, it) }, "privacy_incognito")
            }
            Section("App lock", "Ask for your fingerprint, face or screen lock when Yumeiro opens.") {
                SettingsToggleRow("Lock Yumeiro", if (Privacy.canAuthenticate(context)) "Uses the device's own lock" else "Set a screen lock in Android settings first", lock, { on ->
                    if (activity != null) Privacy.setLockEnabled(activity, on) { ok -> if (!ok && on) AppMessages.error(context, "App lock wasn't turned on") }
                }, "privacy_lock")
                if (lock) {
                    val options = listOf(0 to "Every time", 60 to "After 1 minute", 300 to "After 5 minutes", 1800 to "After 30 minutes")
                    Text("Lock again", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        options.forEach { (sec, label) ->
                            dev.asfakul.yumeiro.ui.components.FilterPill(label.removePrefix("After "), after == sec, { Privacy.setLockAfter(context, sec) })
                        }
                    }
                    SettingsToggleRow("Hide content in recents", "Blank preview in the app switcher and no screenshots", hide, { on -> activity?.let { Privacy.setHideInRecents(it, on) } }, "privacy_hide_recents")
                }
            }
        }
    }
}

// endregion

// region Tracking

private val AniListBlue = androidx.compose.ui.graphics.Color(0xFF3DB4F2)
private val MalBlue = androidx.compose.ui.graphics.Color(0xFF6C8EEF)
private val KitsuOrange = androidx.compose.ui.graphics.Color(0xFFFD755C)
private val TraktRed = androidx.compose.ui.graphics.Color(0xFFED1C24)

@Composable
fun TrackingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var kitsuForm by remember { mutableStateOf(false) }
    var traktKeys by remember { mutableStateOf(false) }
    var traktCode by remember { mutableStateOf<Trakt.DeviceCode?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { AppMessages.error(context, "No browser to sign in with") }

    Column(Modifier.fillMaxSize().testTag("tracking_screen")) {
        SubPageHeader(title = "Tracking", onBack = onBack, subtitle = "Watched episodes update your lists automatically")
        androidx.compose.runtime.key(refresh) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Every tracker is free. AniList, MyAnimeList and Trakt each need a free key you create once. Yumeiro has no server, so your sign-in goes straight to the tracker. Each card has step-by-step instructions.",
                    color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

                GroupLabel("Anime")
                val aniId = AniList.clientId().isNotBlank()
                TrackerCard(
                    name = "AniList", tint = AniListBlue, user = AniList.user(), configured = aniId, tag = "anilist",
                    onSetUp = { editId = "anilist" }, onSignIn = { AniList.loginUrl()?.let { open(it) } },
                    onSignOut = { AniList.signOut(context); refresh++ },
                )
                GuideCard(SetupGuides.anilist, startExpanded = !aniId && AniList.user() == null)

                val malId = MyAnimeList.clientId().isNotBlank()
                TrackerCard(
                    name = "MyAnimeList", tint = MalBlue, user = MyAnimeList.user(), configured = malId, tag = "mal",
                    onSetUp = { editId = "mal" }, onSignIn = { MyAnimeList.loginUrl(context)?.let { open(it) } },
                    onSignOut = { MyAnimeList.signOut(context); refresh++ },
                )
                GuideCard(SetupGuides.myAnimeList)

                TrackerCard(
                    name = "Kitsu", tint = KitsuOrange, user = Kitsu.user(), configured = true, tag = "kitsu",
                    onSetUp = null, onSignIn = { kitsuForm = true }, onSignOut = { Kitsu.signOut(context); refresh++ },
                )
                GuideCard(SetupGuides.kitsu)

                GroupLabel("Movies & shows")
                TrackerCard(
                    name = "Trakt", tint = TraktRed, user = Trakt.user(), configured = Trakt.configured, tag = "trakt",
                    onSetUp = { traktKeys = true },
                    onSignIn = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { Trakt.deviceCode(NetworkClient.get(context)) } }
                                .onSuccess { c -> traktCode = c; open(c.verificationUrl)
                                    runCatching { Trakt.awaitToken(context, NetworkClient.get(context), c) }
                                        .onSuccess { AppMessages.success(context, "Signed in to Trakt as $it") }
                                        .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                                    traktCode = null; refresh++ }
                                .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                        }
                    },
                    onSignOut = { Trakt.signOut(context); refresh++ },
                )
                GuideCard(SetupGuides.trakt)

                Text("Tokens and keys are encrypted with the Android Keystore and never leave the device except to talk to the tracker. They're not included in backups.",
                    color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    editId?.let { which ->
        val isAni = which == "anilist"
        var text by remember(which) { mutableStateOf(OnlineSettings.secret(if (isAni) "anilist_client" else "mal_client").orEmpty()) }
        TsukiDialog(
            onDismissRequest = { editId = null },
            title = { Text(if (isAni) "AniList client ID" else "MyAnimeList client ID") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isAni) "Paste the Client ID from anilist.co/settings/developer. Its redirect URL must be:" else "Paste the Client ID from myanimelist.net/apiconfig (App Type: Android). Its redirect URL must be:",
                        color = TextSecondary, fontSize = 13.sp)
                    val redirect = if (isAni) AniList.REDIRECT else MyAnimeList.REDIRECT
                    TextButton(onClick = { copyToClipboard(context, "Redirect URL", redirect) }) { Text(redirect + "  ·  Copy", color = SpotifyGreen, fontSize = 13.sp) }
                    OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Client ID") }, modifier = Modifier.fillMaxWidth().testTag("tracker_client_id"))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    OnlineSettings.setSecret(context, if (isAni) "anilist_client" else "mal_client", text.trim().ifBlank { null })
                    editId = null; refresh++
                    if (text.isNotBlank()) AppMessages.success(context, "Saved. Now tap Sign in")
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editId = null }) { Text("Cancel") } },
        )
    }
    if (kitsuForm) {
        var email by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        TsukiDialog(
            onDismissRequest = { if (!busy) kitsuForm = false },
            title = { Text("Sign in to Kitsu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(email, { email = it }, singleLine = true, label = { Text("E-mail") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pass, { pass = it }, singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    Text("Your password goes to Kitsu once; only the token is kept.", color = TextTertiary, fontSize = 12.sp)
                    if (busy) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && email.isNotBlank() && pass.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { Kitsu.login(context, NetworkClient.get(context), email, pass) }
                            .onSuccess { AppMessages.success(context, "Signed in to Kitsu as $it"); kitsuForm = false; refresh++ }
                            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                        busy = false
                    }
                }) { Text("Sign in") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { kitsuForm = false }) { Text("Cancel") } },
        )
    }
    if (traktKeys) {
        var id by remember { mutableStateOf(OnlineSettings.secret("trakt_client").orEmpty()) }
        var secret by remember { mutableStateOf(OnlineSettings.secret("trakt_secret").orEmpty()) }
        TsukiDialog(
            onDismissRequest = { traktKeys = false },
            title = { Text("Trakt API app") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create a free app at trakt.tv/oauth/applications/new with this redirect URI, then paste its Client ID. New Trakt apps have no client secret; fill it in only if Trakt shows one.", color = TextSecondary, fontSize = 13.sp)
                    TextButton(onClick = { copyToClipboard(context, "Redirect URI", SetupGuides.TRAKT_REDIRECT) }) { Text(SetupGuides.TRAKT_REDIRECT + "  ·  Copy", color = SpotifyGreen, fontSize = 13.sp) }
                    OutlinedTextField(id, { id = it }, singleLine = true, label = { Text("Client ID") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(secret, { secret = it }, singleLine = true, label = { Text("Client secret (optional)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = {
                OnlineSettings.setSecret(context, "trakt_client", id.trim().ifBlank { null })
                OnlineSettings.setSecret(context, "trakt_secret", secret.trim().ifBlank { null })
                traktKeys = false; refresh++
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { traktKeys = false }) { Text("Cancel") } },
        )
    }
    traktCode?.let { c ->
        TsukiDialog(
            onDismissRequest = {},
            title = { Text("Approve on Trakt") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Open ${c.verificationUrl} on any device and enter", color = TextSecondary)
                Text(c.userCode, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(onClick = { open(c.verificationUrl) }) { Text("Open Trakt") } },
        )
    }
}

@Composable
private fun TrackerCard(
    name: String, tint: androidx.compose.ui.graphics.Color, user: String?, configured: Boolean, tag: String,
    onSetUp: (() -> Unit)?, onSignIn: () -> Unit, onSignOut: () -> Unit,
) {
    GlassGroup(Modifier.testTag("tracker_$tag")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.22f))
                    .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(name.take(1), color = tint, fontWeight = FontWeight.Black, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when { user != null -> "Signed in as $user"; !configured -> "Needs a free key. See the guide below"; else -> "Ready to sign in" },
                    color = TextTertiary, fontSize = 12.sp,
                )
            }
            StatusChip(when { user != null -> "Linked"; !configured -> "Set up"; else -> "Ready" }, when { user != null -> true; !configured -> false; else -> null })
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (onSetUp != null && user == null) {
                if (configured) TextButton(onClick = onSetUp, modifier = Modifier.testTag("tracker_${tag}_change")) { Text("Change key", color = TextSecondary) }
                else Button(onClick = onSetUp, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                    modifier = Modifier.testTag("tracker_${tag}_setup")) { Text("Set up") }
            }
            when {
                user != null -> OutlinedButton(onClick = onSignOut, shape = RoundedCornerShape(18.dp), modifier = Modifier.testTag("tracker_${tag}_signout")) { Text("Sign out") }
                configured -> Button(onClick = onSignIn, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                    modifier = Modifier.testTag("tracker_${tag}_signin")) { Text("Sign in") }
            }
        }
    }
}

// endregion

// region Backup

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includeExt by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<Uri, org.json.JSONObject>?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { Backup.export(context, uri, includeExt) }
                .onSuccess { AppMessages.success(context, "Backup saved · ${it.entries} items") }
                .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
            busy = false
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { Backup.inspect(context, uri) }.onSuccess { pending = uri to it }.onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        SubPageHeader(title = "Backup & restore", onBack = onBack)
        if (busy) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 48.dp)) {
            Section("Create a backup", "Settings, history, My List, playlists, sources, repositories, home servers and manga progress in one .zip. Save it to Drive, an SD card or anywhere you like.") {
                SettingsToggleRow("Include installed extensions", "Bigger file, but sources work right after restoring", includeExt, { includeExt = it }, "backup_include_ext")
                Button(onClick = { create.launch(Backup.suggestedName()) }, enabled = !busy, shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), modifier = Modifier.padding(vertical = 8.dp)) { Text("Save backup") }
            }
            Section("Restore", "Replaces matching data on this phone with the backup, then restarts Yumeiro.") {
                OutlinedButton(onClick = { pick.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(vertical = 8.dp)) { Text("Choose backup file") }
            }
            Text("Not included: downloaded videos, the updater state and account tokens (they're tied to this phone's secure storage — sign in to trackers and servers again after restoring on a new phone).",
                color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
    pending?.let { (uri, m) ->
        TsukiDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore this backup?") },
            text = { Text("Made with Yumeiro ${m.optString("version")} on ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(m.optLong("createdAt")))}." +
                (if (m.optBoolean("extensions")) " Includes extensions." else "") + " Yumeiro restarts afterwards.") },
            confirmButton = { TextButton(onClick = {
                pending = null
                scope.launch {
                    busy = true
                    runCatching { Backup.restore(context, uri) }
                        .onSuccess { Backup.restartApp(context) }
                        .onFailure { busy = false; AppMessages.error(context, OnlineErrors.friendly(it)) }
                }
            }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

// endregion
