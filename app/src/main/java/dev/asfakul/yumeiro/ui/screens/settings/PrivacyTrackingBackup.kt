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

@Composable
fun TrackingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var kitsuForm by remember { mutableStateOf(false) }
    var traktKeys by remember { mutableStateOf(false) }
    var traktCode by remember { mutableStateOf<Trakt.DeviceCode?>(null) }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(Modifier.fillMaxSize()) {
        SubPageHeader(title = "Tracking", onBack = onBack, subtitle = "Watched episodes update your lists automatically")
        androidx.compose.runtime.key(refresh) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 48.dp)) {
                Section("Anime", "AniList, MyAnimeList and Kitsu update when you finish about 85% of an episode.") {
                    TrackerLine("AniList", AniList.user(), signIn = AniList.loginUrl()?.let { u -> { open(u) } }, hintNoId = "Add a client ID in Settings → Online videos",
                        onSignOut = { AniList.signOut(context); refresh++ })
                    TrackerLine("MyAnimeList", MyAnimeList.user(), signIn = if (MyAnimeList.clientId().isBlank()) null else ({ MyAnimeList.loginUrl(context)?.let { open(it) } }),
                        hintNoId = "Add a client ID in Settings → Online videos", onSignOut = { MyAnimeList.signOut(context); refresh++ })
                    TrackerLine("Kitsu", Kitsu.user(), signIn = { kitsuForm = true }, onSignOut = { Kitsu.signOut(context); refresh++ })
                }
                Section("Movies & shows", "Trakt gets movies and episodes you finish, from any source.") {
                    TrackerLine("Trakt", Trakt.user(), signIn = if (!Trakt.configured) null else ({
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { Trakt.deviceCode(NetworkClient.get(context)) } }
                                .onSuccess { c -> traktCode = c; open(c.verificationUrl)
                                    runCatching { Trakt.awaitToken(context, NetworkClient.get(context), c) }
                                        .onSuccess { AppMessages.success(context, "Signed in to Trakt as $it") }
                                        .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                                    traktCode = null; refresh++ }
                                .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                        }
                    }), hintNoId = "Add your free Trakt API app below", onSignOut = { Trakt.signOut(context); refresh++ })
                    TextButton(onClick = { traktKeys = true }) { Text(if (Trakt.configured) "Change Trakt API app" else "Add Trakt API app", color = SpotifyGreen) }
                }
                Text("Tokens are encrypted with the Android Keystore and never leave the device except to talk to the tracker.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
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
                    Text("Create a free app at trakt.tv/oauth/applications with redirect URI urn:ietf:wg:oauth:2.0:oob, then paste its keys.", color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(id, { id = it }, singleLine = true, label = { Text("Client ID") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(secret, { secret = it }, singleLine = true, label = { Text("Client secret") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
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
private fun TrackerLine(name: String, user: String?, signIn: (() -> Unit)?, onSignOut: () -> Unit, hintNoId: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(user?.let { "Signed in as $it" } ?: if (signIn == null) hintNoId.orEmpty() else "Not signed in", color = TextTertiary, fontSize = 12.sp)
        }
        when {
            user != null -> TextButton(onClick = onSignOut) { Text("Sign out", color = TextSecondary) }
            signIn != null -> Button(onClick = signIn, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent)) { Text("Sign in") }
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
