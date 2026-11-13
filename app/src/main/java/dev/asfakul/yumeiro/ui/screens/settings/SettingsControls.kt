/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.asfakul.yumeiro.notify.Inbox
import dev.asfakul.yumeiro.notify.NotifySettings
import dev.asfakul.yumeiro.notify.NotifySettings.Key
import dev.asfakul.yumeiro.notify.Reminders
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.screens.extras.AppPages
import dev.asfakul.yumeiro.ui.screens.extras.Links
import dev.asfakul.yumeiro.ui.screens.extras.PermissionCenter
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun keyState(k: Key) = NotifySettings.flow(k).collectAsState()

/** A tappable row with a title, subtitle and a right-side status/arrow. */
@Composable
internal fun SettingsActionRow(
    title: String, subtitle: String, testTag: String,
    status: String? = null, ok: Boolean? = null, danger: Boolean = false, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp).testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, color = if (danger) dev.asfakul.yumeiro.ui.theme.WarnTone else TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.5.sp, lineHeight = 17.sp, color = TextTertiary)
        }
        Spacer(Modifier.width(10.dp))
        when {
            status != null -> StatusChip(status, ok)
            ok != null -> Icon(
                if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null,
                tint = if (ok) SpotifyGreen else dev.asfakul.yumeiro.ui.theme.WarnTone, modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary)
    }
}

@Composable
fun NotificationSettingsCard() {
    val context = LocalContext.current
    remember(context) { NotifySettings.init(context); 0 }
    val ns = NotifySettings
    val enabled by keyState(Key.ENABLED)
    val episodes by keyState(Key.EPISODES)
    val ext by keyState(Key.EXTENSIONS)
    val pl by keyState(Key.PLAYLISTS)
    val news by keyState(Key.NEWS)
    val nudge by keyState(Key.NUDGES)
    val sound by keyState(Key.SOUND)
    val reminders by keyState(Key.REMINDERS)
    val service by keyState(Key.SERVICE)
    val instant by keyState(Key.FCM)
    val pushStatus by dev.asfakul.yumeiro.notify.FcmPush.status.collectAsState()
    val pushBuilt = remember { dev.asfakul.yumeiro.notify.FcmPush.configured() }
    val unread by Inbox.unread.collectAsState()
    var systemOn by remember { mutableStateOf(PermissionCenter.notificationsAllowed(context)) }
    var block by remember { mutableStateOf(dev.asfakul.yumeiro.notify.AppNotifier.block(context)) }
    LifecycleResumeEffect(Unit) {
        systemOn = PermissionCenter.notificationsAllowed(context)
        block = dev.asfakul.yumeiro.notify.AppNotifier.block(context)
        onPauseOrDispose { }
    }
    SettingsCard {
        SettingsActionRow(
            "Notification inbox", if (unread > 0) "$unread new · everything Yumeiro told you" else "Everything Yumeiro told you, in one place",
            "settings_open_inbox", onClick = { AppPages.open(AppPages.Page.INBOX) },
        )
        SettingsDivider()
        SettingsActionRow(
            "System notifications",
            when {
                !systemOn -> "Blocked by Android. Tap to turn them on"
                block == dev.asfakul.yumeiro.notify.AppNotifier.Block.CHANNEL_OFF -> "Some categories are off in Android. Tap to check them"
                else -> "Allowed by Android. Change sound, pop-up and lock-screen style here"
            },
            "settings_system_notifications", status = if (!systemOn) "Off" else if (block == dev.asfakul.yumeiro.notify.AppNotifier.Block.CHANNEL_OFF) "Partly" else "On",
            ok = systemOn && block != dev.asfakul.yumeiro.notify.AppNotifier.Block.CHANNEL_OFF,
            onClick = { PermissionCenter.openNotificationSettings(context) },
        )
        SettingsDivider()
        SettingsToggleRow("Show notifications", "Off keeps everything quietly in the inbox only", enabled,
            { ns.set(context, Key.ENABLED, it); block = dev.asfakul.yumeiro.notify.AppNotifier.block(context) }, "toggle_notify_enabled")
        SettingsDivider()
        SettingsActionRow("Send a test notification", "Check that Yumeiro shows up in the status bar and plays its chime", "settings_test_notification") {
            val why = dev.asfakul.yumeiro.notify.AppNotifier.sendTest(context)
            if (why == dev.asfakul.yumeiro.notify.AppNotifier.Block.NONE) AppMessages.success(context, "Test sent. Check your status bar")
            else {
                AppMessages.error(context, "${why.title}. ${why.hint}")
                if (why != dev.asfakul.yumeiro.notify.AppNotifier.Block.MASTER_OFF) PermissionCenter.openNotificationSettings(context)
            }
            block = why
        }
        if (enabled) {
            SettingsDivider()
            SettingsToggleRow("New episodes", "For shows you follow with the bell in My List", episodes,
                { ns.set(context, Key.EPISODES, it) }, "toggle_notify_episodes")
            SettingsDivider()
            SettingsToggleRow("New extensions", "When new extensions arrive in the extension market", ext,
                { ns.setExtensions(context, it) }, "toggle_notify_extensions")
            SettingsDivider()
            SettingsToggleRow("New stream playlists", "When new playlists are added to the stream market", pl,
                { ns.setPlaylists(context, it) }, "toggle_notify_playlists")
            SettingsDivider()
            SettingsToggleRow("Yumeiro news", "New features, releases and announcements", news,
                { ns.set(context, Key.NEWS, it) }, "toggle_notify_news")
            SettingsDivider()
            SettingsToggleRow("Service alerts", "Planned maintenance, outages and messages for this device. Rare, but worth knowing", service,
                { ns.set(context, Key.SERVICE, it) }, "toggle_notify_service")
            SettingsDivider()
            SettingsToggleRow("Suggestions", "An occasional evening idea, like a movie or some music (at most every 3 days)", nudge,
                { ns.setNudges(context, it) }, "toggle_notify_nudges")
            SettingsDivider()
            SettingsToggleRow("Yumeiro chime", "Play Yumeiro's own sound. Off makes notifications silent", sound,
                { ns.set(context, Key.SOUND, it) }, "toggle_notify_sound")
        }
        if (pushBuilt) {
            SettingsDivider()
            // Instant notices over Firebase Cloud Messaging. Opt-out for anyone who wants no Google push at all.
            SettingsToggleRow(
                "Instant alerts",
                if (!instant) "Off. News, updates and service alerts still arrive with the background checks"
                else "News, updates and service alerts the moment they're sent (Firebase Cloud Messaging) · ${pushStatus.label}",
                instant, { ns.set(context, Key.FCM, it) }, "toggle_notify_instant",
            )
            val supportId = remember(pushStatus) { if (instant) dev.asfakul.yumeiro.notify.FcmPush.supportId() else null }
            if (supportId != null) {
                TextButton(
                    onClick = { copyToClipboard(context, "Yumeiro support ID", supportId) },
                    modifier = Modifier.testTag("notify_support_id"),
                ) { Text("Copy support ID · $supportId", color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }
        SettingsDivider()
        SettingsToggleRow("In-app reminders", "Now and then: join the Telegram community, star on GitHub, support the app", reminders,
            { ns.set(context, Key.REMINDERS, it) }, "toggle_notify_reminders")
        Text(if (pushBuilt && instant) "App updates are always announced, instantly and when you open the app." else "App updates are always announced when you open the app.",
            color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
fun BackgroundActivityCard() {
    val context = LocalContext.current
    remember(context) { NotifySettings.init(context); 0 }
    val bg by keyState(Key.BACKGROUND)
    val wifi by keyState(Key.WIFI_ONLY)
    var battery by remember { mutableStateOf(PermissionCenter.batteryUnrestricted(context)) }
    LifecycleResumeEffect(Unit) { battery = PermissionCenter.batteryUnrestricted(context); onPauseOrDispose { } }
    SettingsCard {
        SettingsToggleRow("Background checks", "Look for Yumeiro news every 2 hours and new episodes every 4 hours, even when the app is closed. Opening the app catches up right away", bg,
            { NotifySettings.set(context, Key.BACKGROUND, it) }, "toggle_background")
        if (bg) {
            SettingsDivider()
            SettingsToggleRow("Wi-Fi only", "Skip background checks on mobile data", wifi,
                { NotifySettings.set(context, Key.WIFI_ONLY, it) }, "toggle_background_wifi")
        }
        SettingsDivider()
        SettingsActionRow(
            "Battery optimisation",
            if (battery) "Unrestricted. Tap for your phone's extra auto-start or battery page, if it has one" else "Optimised. Tap to allow in one step, so notifications arrive while the app is closed",
            "settings_battery", status = if (battery) "Unrestricted" else "Optimised", ok = battery,
            onClick = { PermissionCenter.openBatterySettings(context) },
        )
        Text(if (dev.asfakul.yumeiro.notify.FcmPush.configured()) "News, updates and service alerts arrive instantly with Instant alerts. New episodes still rely on these checks being allowed to run; for on-time episode alerts, turn on Reliable alerts under Notifications (free, with ntfy)."
            else "Yumeiro has no push server of its own, so notifications rely on these checks being allowed to run. For on-time alerts, turn on Reliable alerts under Notifications (free, with ntfy).", color = TextSecondary,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
fun PermissionsCard(onMediaGranted: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val notif = remember(tick) { PermissionCenter.notificationsAllowed(context) }
    val media = remember(tick) { PermissionCenter.mediaAllowed(context) }
    val install = remember(tick) { PermissionCenter.installAllowed(context) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        if (!ok) PermissionCenter.openNotificationSettings(context)
    }
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick++
        if (r.values.any { it }) onMediaGranted() else PermissionCenter.openAppSettings(context)
    }
    SettingsCard {
        SettingsActionRow("Notifications", "New episodes, extensions and app updates", "perm_notifications",
            status = if (notif) "Allowed" else "Not allowed", ok = notif) {
            if (notif) PermissionCenter.openNotificationSettings(context)
            else if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else PermissionCenter.openNotificationSettings(context)
        }
        SettingsDivider()
        SettingsActionRow("Music & videos", "Find the songs and videos on this device", "perm_media",
            status = if (media) "Allowed" else "Not allowed", ok = media) {
            if (media) PermissionCenter.openAppSettings(context) else mediaLauncher.launch(PermissionCenter.mediaPermissions())
        }
        SettingsDivider()
        SettingsActionRow("Install app updates", "Lets Yumeiro install its own updates after you tap Update", "perm_install",
            status = if (install) "Allowed" else "Ask when needed", ok = if (install) true else null) {
            PermissionCenter.openInstallSettings(context)
        }
        SettingsDivider()
        SettingsActionRow("All app permissions", "Open Yumeiro in Android settings", "perm_app_settings") {
            PermissionCenter.openAppSettings(context)
        }
    }
}

private data class ClearAction(val title: String, val body: String, val done: String, val run: suspend () -> Unit)

@Composable
fun PrivacyDataCard(onClearWatchHistory: () -> Unit, onClearListeningHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ClearAction?>(null) }
    val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()

    pending?.let { a ->
        TsukiDialog(
            tone = dev.asfakul.yumeiro.ui.components.DialogTone.DANGER,
            onDismissRequest = { pending = null },
            title = { Text(a.title) },
            text = { Text(a.body) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        runCatching { a.run() }
                            .onSuccess { AppMessages.success(context, a.done) }
                            .onFailure { AppMessages.error(context, "Couldn't clear. Try again") }
                    }
                }, modifier = Modifier.testTag("privacy_confirm")) { Text("Clear", color = dev.asfakul.yumeiro.ui.theme.DangerTone) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
    val io: suspend (() -> Unit) -> Unit = { f -> withContext(Dispatchers.IO) { f() } }
    SettingsCard {
        SettingsActionRow("Clear watch history", "Continue watching, resume points and watched marks (device, online and YouTube)", "privacy_clear_watch") {
            pending = ClearAction("Clear watch history?", "Continue watching, resume positions and watched marks are removed everywhere.", "Watch history cleared") {
                onClearWatchHistory()
                io { dev.asfakul.yumeiro.online.OnlineHistory.clear(context); dev.asfakul.yumeiro.youtube.YtHistory.clear(context) }
            }
        }
        if (adultOn) {
            SettingsDivider()
            SettingsActionRow("Clear 18+ history", "Only the 18+ titles you watched. Everything else stays", "privacy_clear_adult") {
                pending = ClearAction("Clear 18+ history?", "Every 18+ title is removed from your history. Other history stays.", "18+ history cleared") {
                    io { dev.asfakul.yumeiro.online.OnlineHistory.clearAdult(context) }
                }
            }
        }
        SettingsDivider()
        SettingsActionRow("Clear listening history", "Recently played songs and play counts", "privacy_clear_listening") {
            pending = ClearAction("Clear listening history?", "Recently played and play counts are reset. Playlists and favourites stay.", "Listening history cleared") {
                onClearListeningHistory()
            }
        }
        SettingsDivider()
        SettingsActionRow("Clear search history", "Recent searches in every search box", "privacy_clear_search") {
            pending = ClearAction("Clear search history?", "All recent searches are forgotten.", "Search history cleared") {
                io { dev.asfakul.yumeiro.ui.components.RecentSearches.clearAll(context); dev.asfakul.yumeiro.ui.screens.online.SearchHistory.clear(context) }
            }
        }
        SettingsDivider()
        SettingsActionRow("Clear notifications", "Empty the notification inbox", "privacy_clear_inbox") {
            pending = ClearAction("Clear notifications?", "The inbox is emptied. Notification settings stay the same.", "Notifications cleared") {
                Inbox.clear(context)
            }
        }
        SettingsDivider()
        SettingsActionRow("Clear cache", "Thumbnails and temporary files. Frees space; nothing personal is lost", "privacy_clear_cache") {
            pending = ClearAction("Clear cache?", "Images and temporary files are downloaded again when needed.", "Cache cleared") {
                io { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                dev.asfakul.yumeiro.online.OnlineRepository.clearCaches()
            }
        }
    }
}

@Composable
fun SocialMediaCard() {
    val context = LocalContext.current
    SettingsCard {
        Text("Yumeiro has its own website (yumeiro.click) and community on Telegram: news in the channel, help and ideas in the group.", color = TextSecondary,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
        Reminders.Social.entries.forEachIndexed { i, s ->
            if (i > 0) SettingsDivider()
            SettingsActionRow(s.label, "${s.detail} · ${s.url.removePrefix("https://")}", "social_${s.name.lowercase()}") {
                if (Links.open(context, s.url)) Reminders.recordFollow(context, s)
                else AppMessages.error(context, "Couldn't open ${s.label}")
            }
        }
        SettingsDivider()
        SettingsActionRow("Website", "Downloads, news and guides · yumeiro.click", "social_website") {
            if (!Links.open(context, Reminders.WEBSITE_URL)) AppMessages.error(context, "Couldn't open the website")
        }
        SettingsDivider()
        SettingsActionRow("Star on GitHub", "github.com/siamform/yumeiro", "social_github") {
            if (Links.open(context, Reminders.GITHUB_URL)) Reminders.recordStar(context)
            else AppMessages.error(context, "Couldn't open GitHub")
        }
    }
}
