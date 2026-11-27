/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import dev.asfakul.yumeiro.online.runCancellable
import dev.asfakul.yumeiro.online.anime.AniList
import dev.asfakul.yumeiro.online.anime.MyAnimeList
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.tracking.Kitsu
import dev.asfakul.yumeiro.tracking.Trakt
import dev.asfakul.yumeiro.tracking.Simkl
import dev.asfakul.yumeiro.scrobble.LastFm
import dev.asfakul.yumeiro.scrobble.Scrobbler
import dev.asfakul.yumeiro.lyrics.LrcLib
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
import dev.asfakul.yumeiro.ui.theme.yuRound

@Composable
private fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 14.dp))
    if (hint != null) Text(hint, color = TextTertiary, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth(), shape = yuRound(14), colors = CardDefaults.cardColors(containerColor = GlassFill)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { content() }
    }
}

// region Privacy

/** Privacy → the Privacy screen (Home shortcut) and the Privacy & security page show the same lock rows. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubPageHeader(title = tr(R.string.privacy, "Privacy"), onBack = onBack)
        Column(Modifier.verticalScroll(dev.asfakul.yumeiro.ui.components.ScrollMemory.scroll("privacy")).padding(horizontal = 16.dp).padding(bottom = 48.dp)) {
            PrivacyLockSections()
        }
    }
}

/**
 * Incognito, app lock and hide-in-recents. FINAL-47: Profiles and Private downloads used to be
 * here too; they are now on Settings → Profiles and Settings → Downloads.
 */
@Composable
internal fun PrivacyLockSections() {
    val context = LocalContext.current
    val activity = context as? Activity
    val incognito by Privacy.incognito.collectAsState()
    val lock by Privacy.lockEnabled.collectAsState()
    val after by Privacy.lockAfter.collectAsState()
    val hide by Privacy.hideInRecents.collectAsState()
    Column(Modifier.testTag("privacy_lock_sections")) {
        Section(tr(R.string.incognito_mode, "Incognito mode"), tr(R.string.while_on_nothing_you_watch_read, "While on, nothing you watch, read or listen to is saved: no history, no Continue watching, no tracker updates.")) {
            SettingsToggleRow("Incognito", if (incognito) tr(R.string.on_history_is_paused, "On — history is paused") else "Off", incognito, { Privacy.setIncognito(context, it) }, "privacy_incognito")
        }
        Section(tr(R.string.app_lock, "App lock"), tr(R.string.ask_for_your_fingerprint_face_or, "Ask for your fingerprint, face or screen lock when Yumeiro opens.")) {
            SettingsToggleRow(tr(R.string.lock_yumeiro, "Lock Yumeiro"), if (Privacy.canAuthenticate(context)) tr(R.string.uses_the_devices_own_lock, "Uses the device's own lock") else tr(R.string.set_a_screen_lock_in_android, "Set a screen lock in Android settings first"), lock, { on ->
                if (activity != null) Privacy.setLockEnabled(activity, on) { ok -> if (!ok && on) AppMessages.error(context, "App lock wasn't turned on") }
            }, "privacy_lock")
            if (lock) {
                val options = listOf(0 to tr(R.string.every_time, "Every time"), 60 to tr(R.string.after_1_minute, "After 1 minute"), 300 to tr(R.string.after_5_minutes, "After 5 minutes"), 1800 to tr(R.string.after_30_minutes, "After 30 minutes"))
                Text(stringResource(R.string.lock_again), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    options.forEach { (sec, label) ->
                        dev.asfakul.yumeiro.ui.components.FilterPill(label.removePrefix("After "), after == sec, { Privacy.setLockAfter(context, sec) })
                    }
                }
                SettingsToggleRow(tr(R.string.hide_content_in_recents, "Hide content in recents"), tr(R.string.blank_preview_in_the_app_switcher, "Blank preview in the app switcher and no screenshots"), hide, { on -> activity?.let { Privacy.setHideInRecents(it, on) } }, "privacy_hide_recents")
            }
        }
    }
}

/** The Private downloads switch, on Settings → Downloads (FINAL-47; was inside Privacy). */
@Composable
internal fun PrivateDownloadsRows() {
    val context = LocalContext.current
    if (!dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.PRIVATE_DOWNLOADS)) return
    val stealth by dev.asfakul.yumeiro.online.download.PrivateDownloads.stealth.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { dev.asfakul.yumeiro.online.download.PrivateDownloads.loadStealth(context) }
    SettingsToggleRow(tr(R.string.hide_the_private_entry, "Hide the Private entry"), if (stealth) tr(R.string.open_it_by_long_pressing_the, "Open it by long-pressing the storage card in Downloads") else tr(R.string.private_is_the_lock_at_the, "Private is the lock at the top of Downloads"), stealth,
        { dev.asfakul.yumeiro.online.download.PrivateDownloads.setStealth(context, it) }, "privacy_vault_stealth")
}

/** "Find lyrics online" (LRCLIB), on Settings → Music & sound (FINAL-47; was in Tracking). */
@Composable
internal fun LyricsOnlineRow() {
    val context = LocalContext.current
    remember { LrcLib.init(context); true }
    val lyricsOnline by LrcLib.enabled.collectAsState()
    SettingsToggleRow(tr(R.string.find_lyrics_online, "Find lyrics online"), tr(R.string.from_lrclib_a_free_open_lyrics, "From LRCLIB, a free open lyrics library. Sends only the song's title, artist, album and length"), lyricsOnline, { LrcLib.setEnabled(context, it) }, "lyrics_online_toggle")
}

// endregion

// region Tracking

private val AniListBlue = androidx.compose.ui.graphics.Color(0xFF3DB4F2)
private val MalBlue = androidx.compose.ui.graphics.Color(0xFF6C8EEF)
private val KitsuOrange = androidx.compose.ui.graphics.Color(0xFFFD755C)
private val TraktRed = androidx.compose.ui.graphics.Color(0xFFED1C24)
private val SimklBlue = androidx.compose.ui.graphics.Color(0xFF3D9BE9)
private val LastFmRed = androidx.compose.ui.graphics.Color(0xFFD51007)

@Composable
fun TrackingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var kitsuForm by remember { mutableStateOf(false) }
    var traktKeys by remember { mutableStateOf(false) }
    var traktCode by remember { mutableStateOf<Trakt.DeviceCode?>(null) }
    var simklKeys by remember { mutableStateOf(false) }
    var simklCode by remember { mutableStateOf<Simkl.DeviceCode?>(null) }
    var lastfmUrl by remember { mutableStateOf<String?>(null) }
    var lastfmJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { AppMessages.error(context, "No browser to sign in with") }

    Column(Modifier.fillMaxSize().testTag("tracking_screen")) {
        SubPageHeader(title = tr(R.string.tracking, "Tracking"), onBack = onBack, subtitle = tr(R.string.watched_episodes_update_your_lists_automatically, "Watched episodes update your lists automatically"))
        androidx.compose.runtime.key(refresh) {
            Column(Modifier.verticalScroll(dev.asfakul.yumeiro.ui.components.ScrollMemory.scroll("tracking")).padding(horizontal = 16.dp).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.every_tracker_is_free_kitsu_simkl),
                    color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                TrackingV2Card()

                GroupLabel("Anime")
                val aniId = AniList.clientId().isNotBlank()
                TrackerCard(
                    name = "AniList", tint = AniListBlue, user = AniList.user(), configured = aniId, tag = "anilist",
                    onSetUp = { editId = "anilist" }, onSignIn = { AniList.loginUrl(context)?.let { open(it) } },
                    onSignOut = { AniList.signOut(context); refresh++ },
                )
                GuideCard(SetupGuides.anilist)

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

                GroupLabel(tr(R.string.movies_shows, "Movies & shows"))
                TrackerCard(
                    name = "Trakt", tint = TraktRed, user = Trakt.user(), configured = Trakt.configured, tag = "trakt",
                    onSetUp = { traktKeys = true },
                    onSignIn = {
                        scope.launch {
                            runCancellable { withContext(Dispatchers.IO) { Trakt.deviceCode(NetworkClient.get(context)) } }
                                .onSuccess { c -> traktCode = c; open(c.verificationUrl)
                                    runCancellable { Trakt.awaitToken(context, NetworkClient.get(context), c) }
                                        .onSuccess { AppMessages.success(context, tr(R.string.signed_in_to_trakt_as, "Signed in to Trakt as %1\$s", it)) }
                                        .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                                    traktCode = null; refresh++ }
                                .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                        }
                    },
                    onSignOut = { Trakt.signOut(context); refresh++ },
                )
                GuideCard(SetupGuides.trakt)

                TrackerCard(
                    name = "Simkl", tint = SimklBlue, user = Simkl.user(), configured = Simkl.configured, tag = "simkl",
                    onSetUp = { simklKeys = true },
                    onSignIn = {
                        scope.launch {
                            runCancellable { withContext(Dispatchers.IO) { Simkl.deviceCode(NetworkClient.get(context)) } }
                                .onSuccess { c -> simklCode = c; open(c.verificationUrl)
                                    runCancellable { Simkl.awaitToken(context, NetworkClient.get(context), c) }
                                        .onSuccess { AppMessages.success(context, tr(R.string.signed_in_to_simkl_as, "Signed in to Simkl as %1\$s", it)) }
                                        .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                                    simklCode = null; refresh++ }
                                .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                        }
                    },
                    onSignOut = { Simkl.signOut(context); refresh++ },
                )
                GuideCard(SetupGuides.simkl)

                GroupLabel("Music")
                TrackerCard(
                    name = "Last.fm", tint = LastFmRed, user = LastFm.user(), configured = LastFm.configured, tag = "lastfm",
                    onSetUp = null,
                    onSignIn = {
                        lastfmJob?.cancel()
                        lastfmJob = scope.launch {
                            runCancellable { withContext(Dispatchers.IO) { LastFm.requestToken(NetworkClient.get(context)) } }
                                .onSuccess { (token, url) -> lastfmUrl = url; open(url)
                                    runCancellable { withContext(Dispatchers.IO) { LastFm.awaitSession(context, NetworkClient.get(context), token) } }
                                        .onSuccess { AppMessages.success(context, tr(R.string.scrobbling_to_last_fm_as, "Scrobbling to Last.fm as %1\$s", it)); Scrobbler.flush(context) }
                                        .onFailure { if (it !is kotlinx.coroutines.CancellationException) AppMessages.error(context, OnlineErrors.friendly(it)) }
                                    lastfmUrl = null; refresh++ }
                                .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                        }
                    },
                    onSignOut = { LastFm.signOut(context); refresh++ },
                )
                if (LastFm.user() != null) {
                    remember { Scrobbler.init(context); true }
                    val on by Scrobbler.enabled.collectAsState()
                    val pending by Scrobbler.pending.collectAsState()
                    GlassGroup {
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            SettingsToggleRow(tr(R.string.scrobble_what_i_play, "Scrobble what I play"), if (pending > 0) tr(R.string.waiting_to_be_sent, "%1\$s waiting to be sent", pending) else tr(R.string.paused_while_incognito, "Paused while incognito"), on, { Scrobbler.setEnabled(context, it) }, "lastfm_scrobble_toggle")
                        }
                    }
                }
                GuideCard(SetupGuides.lastfm)
                Text(stringResource(R.string.tokens_and_keys_are_encrypted_with),
                    color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    editId?.let { which ->
        val isAni = which == "anilist"
        var text by remember(which) { mutableStateOf(OnlineSettings.secret(if (isAni) "anilist_client" else "mal_client").orEmpty()) }
        TsukiDialog(
            onDismissRequest = { editId = null },
            title = { Text(if (isAni) tr(R.string.anilist_client_id, "AniList client ID") else "MyAnimeList client ID") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isAni) "Paste the Client ID from anilist.co/settings/developer. Its redirect URL must be:" else "Paste the Client ID from myanimelist.net/apiconfig (App Type: Android). Its redirect URL must be:",
                        color = TextSecondary, fontSize = 13.sp)
                    val redirect = if (isAni) AniList.REDIRECT else MyAnimeList.REDIRECT
                    TextButton(onClick = { copyToClipboard(context, tr(R.string.redirect_url, "Redirect URL"), redirect) }) { Text(redirect + "  ·  Copy", color = SpotifyGreen, fontSize = 13.sp) }
                    OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(stringResource(R.string.client_id)) }, modifier = Modifier.fillMaxWidth().testTag("tracker_client_id"))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    OnlineSettings.setSecret(context, if (isAni) "anilist_client" else "mal_client", text.trim().ifBlank { null })
                    editId = null; refresh++
                    if (text.isNotBlank()) AppMessages.success(context, tr(R.string.saved_now_tap_sign_in, "Saved. Now tap Sign in"))
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (kitsuForm) {
        var email by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        // ER-19: the form stays open on failure; a bottom message would sit behind it and the keyboard.
        var error by remember { mutableStateOf<String?>(null) }
        TsukiDialog(
            onDismissRequest = { if (!busy) kitsuForm = false },
            title = { Text(stringResource(R.string.sign_in_to_kitsu)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(email, { email = it }, singleLine = true, label = { Text(stringResource(R.string.e_mail)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().testTag("kitsu_email"))
                    OutlinedTextField(pass, { pass = it }, singleLine = true, label = { Text(stringResource(R.string.password)) }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().testTag("kitsu_pass"))
                    Text(stringResource(R.string.your_password_goes_to_kitsu_once), color = TextTertiary, fontSize = 12.sp)
                    error?.let { e ->
                        Text(e, color = dev.asfakul.yumeiro.ui.theme.WarnTone, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().testTag("kitsu_error").semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                    }
                    if (busy) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && email.isNotBlank() && pass.isNotBlank(), onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        withContext(Dispatchers.IO) { Kitsu.login(context, NetworkClient.get(context), email, pass) }
                            .onSuccess { AppMessages.success(context, tr(R.string.signed_in_to_kitsu_as, "Signed in to Kitsu as %1\$s", it)); kitsuForm = false; refresh++ }
                            .onFailure { error = OnlineErrors.friendly(it) }
                        busy = false
                    }
                }) { Text(if (error != null) "Retry" else tr(R.string.sign_in, "Sign in")) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { kitsuForm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (traktKeys) {
        var id by remember { mutableStateOf(OnlineSettings.secret("trakt_client").orEmpty()) }
        var secret by remember { mutableStateOf(OnlineSettings.secret("trakt_secret").orEmpty()) }
        TsukiDialog(
            onDismissRequest = { traktKeys = false },
            title = { Text(stringResource(R.string.trakt_api_app)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.create_a_free_app_at_trakt), color = TextSecondary, fontSize = 13.sp)
                    TextButton(onClick = { copyToClipboard(context, tr(R.string.redirect_uri, "Redirect URI"), SetupGuides.TRAKT_REDIRECT) }) { Text(SetupGuides.TRAKT_REDIRECT + "  ·  Copy", color = SpotifyGreen, fontSize = 13.sp) }
                    OutlinedTextField(id, { id = it }, singleLine = true, label = { Text(stringResource(R.string.client_id)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(secret, { secret = it }, singleLine = true, label = { Text(stringResource(R.string.client_secret_optional)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = {
                OnlineSettings.setSecret(context, "trakt_client", id.trim().ifBlank { null })
                OnlineSettings.setSecret(context, "trakt_secret", secret.trim().ifBlank { null })
                traktKeys = false; refresh++
            }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { traktKeys = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (simklKeys) {
        var id by remember { mutableStateOf(OnlineSettings.secret("simkl_client").orEmpty()) }
        TsukiDialog(
            onDismissRequest = { simklKeys = false },
            title = { Text(stringResource(R.string.your_own_simkl_app)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.optional_leave_empty_to_use_yumeiros), color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(id, { id = it }, singleLine = true, label = { Text(stringResource(R.string.client_id)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = {
                OnlineSettings.setSecret(context, "simkl_client", id.trim().ifBlank { null })
                simklKeys = false; refresh++
            }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { simklKeys = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    simklCode?.let { c ->
        TsukiDialog(
            tone = dev.asfakul.yumeiro.ui.components.DialogTone.INFO,
            onDismissRequest = {},
            title = { Text(stringResource(R.string.approve_on_simkl)) },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_on_any_device_and_enter, c.verificationUrl), color = TextSecondary)
                Text(c.userCode, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(onClick = { open(c.verificationUrl) }) { Text(stringResource(R.string.open_simkl)) } },
        )
    }
    lastfmUrl?.let { url ->
        TsukiDialog(
            tone = dev.asfakul.yumeiro.ui.components.DialogTone.INFO,
            onDismissRequest = {},
            title = { Text(stringResource(R.string.approve_on_last_fm)) },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(tr(R.string.tap_yes_allow_access_on_last, "Tap \"Yes, allow access\" on Last.fm, then come back. This finishes on its own."), color = TextSecondary)
                Spacer(Modifier.size(12.dp))
                LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(onClick = { open(url) }) { Text(stringResource(R.string.open_last_fm)) } },
            dismissButton = { TextButton(onClick = { lastfmJob?.cancel(); lastfmUrl = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    traktCode?.let { c ->
        TsukiDialog(
            tone = dev.asfakul.yumeiro.ui.components.DialogTone.INFO,
            onDismissRequest = {},
            title = { Text(stringResource(R.string.approve_on_trakt)) },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_on_any_device_and_enter, c.verificationUrl), color = TextSecondary)
                Text(c.userCode, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(onClick = { open(c.verificationUrl) }) { Text(stringResource(R.string.open_trakt)) } },
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
                Modifier.size(40.dp).clip(yuRound(13)).background(tint.copy(alpha = 0.22f))
                    .border(1.dp, tint.copy(alpha = 0.45f), yuRound(13)),
                contentAlignment = Alignment.Center,
            ) { Text(name.take(1), color = tint, fontWeight = FontWeight.Black, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when { user != null -> tr(R.string.signed_in_as, "Signed in as %1\$s", user); !configured -> tr(R.string.needs_a_free_key_see_the, "Needs a free key. See the guide below"); else -> tr(R.string.ready_to_sign_in, "Ready to sign in") },
                    color = TextTertiary, fontSize = 12.sp,
                )
            }
            StatusChip(when { user != null -> "Linked"; !configured -> tr(R.string.set_up, "Set up"); else -> "Ready" }, when { user != null -> true; !configured -> false; else -> null })
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (onSetUp != null && user == null) {
                if (configured) TextButton(onClick = onSetUp, modifier = Modifier.testTag("tracker_${tag}_change")) { Text(stringResource(R.string.change_key), color = TextSecondary) }
                else Button(onClick = onSetUp, shape = yuRound(18), colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                    modifier = Modifier.testTag("tracker_${tag}_setup")) { Text(stringResource(R.string.set_up)) }
            }
            when {
                user != null -> OutlinedButton(onClick = onSignOut, shape = yuRound(18), modifier = Modifier.testTag("tracker_${tag}_signout")) { Text(stringResource(R.string.sign_out)) }
                configured -> Button(onClick = onSignIn, shape = yuRound(18), colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                    modifier = Modifier.testTag("tracker_${tag}_signin")) { Text(stringResource(R.string.sign_in)) }
            }
        }
    }
}

// endregion

// Backup moved to BackupCenterScreen.kt (Round 12).
