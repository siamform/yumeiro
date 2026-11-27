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
import dev.asfakul.yumeiro.ui.theme.yuRound

@Composable
private fun keyState(k: Key) = NotifySettings.flow(k).collectAsState()

/** A tappable row with a title, subtitle and a right-side status/arrow. */
@Composable
internal fun SettingsActionRow(
    title: String, subtitle: String, testTag: String,
    status: String? = null, ok: Boolean? = null, danger: Boolean = false, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(yuRound(12)).clickable(onClick = onClick).padding(vertical = 8.dp).testTag(testTag),
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
            tr(R.string.system_notifications, "System notifications"),
            when {
                !systemOn -> tr(R.string.blocked_by_android_tap_to_turn, "Blocked by Android. Tap to turn them on")
                block == dev.asfakul.yumeiro.notify.AppNotifier.Block.CHANNEL_OFF -> tr(R.string.some_categories_are_off_in_android, "Some categories are off in Android. Tap to check them")
                else -> tr(R.string.allowed_by_android_change_sound_pop, "Allowed by Android. Change sound, pop-up and lock-screen style here")
            },
            "settings_system_notifications", status = if (!systemOn) "Off" else if (block == dev.asfakul.yumeiro.notify.AppNotifier.Block.CHANNEL_OFF) "Partly" else "On",
            ok = systemOn && block != dev.asfakul.yumeiro.notify.AppNotifier.Block.CHANNEL_OFF,
            onClick = { PermissionCenter.openNotificationSettings(context) },
        )
        SettingsDivider()
        SettingsToggleRow(tr(R.string.show_notifications, "Show notifications"), tr(R.string.off_keeps_everything_quietly_in_the, "Off keeps everything quietly in the inbox only"), enabled,
            { ns.set(context, Key.ENABLED, it); block = dev.asfakul.yumeiro.notify.AppNotifier.block(context) }, "toggle_notify_enabled")
        SettingsDivider()
        SettingsActionRow(tr(R.string.send_a_test_notification, "Send a test notification"), tr(R.string.check_that_yumeiro_shows_up_in, "Check that Yumeiro shows up in the status bar and plays its chime"), "settings_test_notification") {
            val why = dev.asfakul.yumeiro.notify.AppNotifier.sendTest(context)
            if (why == dev.asfakul.yumeiro.notify.AppNotifier.Block.NONE) AppMessages.success(context, tr(R.string.test_sent_check_your_status_bar, "Test sent. Check your status bar"))
            else {
                AppMessages.error(context, "${why.title}. ${why.hint}")
                if (why != dev.asfakul.yumeiro.notify.AppNotifier.Block.MASTER_OFF) PermissionCenter.openNotificationSettings(context)
            }
            block = why
        }
        if (enabled) {
            SettingsDivider()
            SettingsToggleRow(tr(R.string.new_episodes, "New episodes"), tr(R.string.for_shows_you_follow_with_the, "For shows you follow with the bell in My List"), episodes,
                { ns.set(context, Key.EPISODES, it) }, "toggle_notify_episodes")
            SettingsDivider()
            SettingsToggleRow(tr(R.string.new_extensions, "New extensions"), tr(R.string.when_new_extensions_arrive_in_the, "When new extensions arrive in the extension market"), ext,
                { ns.setExtensions(context, it) }, "toggle_notify_extensions")
            SettingsDivider()
            SettingsToggleRow(tr(R.string.new_stream_playlists, "New stream playlists"), tr(R.string.when_new_playlists_are_added_to, "When new playlists are added to the stream market"), pl,
                { ns.setPlaylists(context, it) }, "toggle_notify_playlists")
            SettingsDivider()
            SettingsToggleRow(tr(R.string.yumeiro_news, "Yumeiro news"), tr(R.string.new_features_releases_and_announcements, "New features, releases and announcements"), news,
                { ns.set(context, Key.NEWS, it) }, "toggle_notify_news")
            SettingsDivider()
            SettingsToggleRow(tr(R.string.service_alerts, "Service alerts"), tr(R.string.planned_maintenance_outages_and_messages_for, "Planned maintenance, outages and messages for this device. Rare, but worth knowing"), service,
                { ns.set(context, Key.SERVICE, it) }, "toggle_notify_service")
            SettingsDivider()
            SettingsToggleRow("Suggestions", "An occasional evening idea, like a movie or some music (at most every 3 days)", nudge,
                { ns.setNudges(context, it) }, "toggle_notify_nudges")
            SettingsDivider()
            SettingsToggleRow(tr(R.string.yumeiro_chime, "Yumeiro chime"), tr(R.string.play_yumeiros_own_sound_off_makes, "Play Yumeiro's own sound. Off makes notifications silent"), sound,
                { ns.set(context, Key.SOUND, it) }, "toggle_notify_sound")
        }
        if (pushBuilt) {
            SettingsDivider()
            // Instant notices over Firebase Cloud Messaging. Opt-out for anyone who wants no Google push at all.
            SettingsToggleRow(
                tr(R.string.instant_alerts, "Instant alerts"),
                if (!instant) tr(R.string.off_news_updates_and_service_alerts, "Off. News, updates and service alerts still arrive with the background checks")
                else "News, updates and service alerts the moment they're sent (Firebase Cloud Messaging) · ${pushStatus.label}",
                instant, { ns.set(context, Key.FCM, it) }, "toggle_notify_instant",
            )
            val supportId = remember(pushStatus) { if (instant) dev.asfakul.yumeiro.notify.FcmPush.supportId() else null }
            if (supportId != null) {
                TextButton(
                    onClick = { copyToClipboard(context, tr(R.string.yumeiro_support_id, "Yumeiro support ID"), supportId) },
                    modifier = Modifier.testTag("notify_support_id"),
                ) { Text(stringResource(R.string.copy_support_id, supportId), color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }
        SettingsDivider()
        SettingsToggleRow(tr(R.string.in_app_reminders, "In-app reminders"), tr(R.string.now_and_then_join_the_telegram, "Now and then: join the Telegram community, star on GitHub, support the app"), reminders,
            { ns.set(context, Key.REMINDERS, it) }, "toggle_notify_reminders")
        Text(if (pushBuilt && instant) tr(R.string.app_updates_are_always_announced_instantly, "App updates are always announced, instantly and when you open the app.") else tr(R.string.app_updates_are_always_announced_when, "App updates are always announced when you open the app."),
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
        SettingsToggleRow(tr(R.string.background_checks, "Background checks"), tr(R.string.look_for_yumeiro_news_every_2, "Look for Yumeiro news every 2 hours and new episodes every 4 hours, even when the app is closed. Opening the app catches up right away"), bg,
            { NotifySettings.set(context, Key.BACKGROUND, it) }, "toggle_background")
        if (bg) {
            SettingsDivider()
            SettingsToggleRow(tr(R.string.wi_fi_only, "Wi-Fi only"), tr(R.string.skip_background_checks_on_mobile_data, "Skip background checks on mobile data"), wifi,
                { NotifySettings.set(context, Key.WIFI_ONLY, it) }, "toggle_background_wifi")
        }
        SettingsDivider()
        SettingsActionRow(
            tr(R.string.battery_optimisation, "Battery optimisation"),
            if (battery) tr(R.string.unrestricted_tap_for_your_phones_extra, "Unrestricted. Tap for your phone's extra auto-start or battery page, if it has one") else tr(R.string.optimised_tap_to_allow_in_one, "Optimised. Tap to allow in one step, so notifications arrive while the app is closed"),
            "settings_battery", status = if (battery) "Unrestricted" else "Optimised", ok = battery,
            onClick = { PermissionCenter.openBatterySettings(context) },
        )
        // Round 2 (order 9): the full checklist plus this phone maker's own steps.
        var guide by remember { mutableStateOf(false) }
        var guideTick by remember { mutableStateOf(0) }
        LifecycleResumeEffect(Unit) { guideTick++; onPauseOrDispose { } }
        val gs = remember(guideTick, guide) { dev.asfakul.yumeiro.notify.ReliabilityGuide.status(context) }
        SettingsDivider()
        SettingsActionRow(
            tr(R.string.keep_notifications_on_time, "Keep notifications on time"),
            if (gs.vendor.aggressive) "Step-by-step for ${gs.vendor.name}: auto-start, battery and sleeping apps" else tr(R.string.check_everything_that_can_hold_notifications, "Check everything that can hold notifications back"),
            "settings_reliability_guide", status = if (gs.allGood) "All set" else "Check", ok = gs.allGood,
            onClick = { guide = true },
        )
        if (guide) dev.asfakul.yumeiro.ui.screens.extras.ReliabilityGuideDialog(onClose = { guide = false })
        Text(if (dev.asfakul.yumeiro.notify.FcmPush.configured()) tr(R.string.news_updates_and_service_alerts_arrive, "News, updates and service alerts arrive instantly with Instant alerts. New episodes still rely on these checks being allowed to run; for on-time episode alerts, turn on Reliable alerts under Notifications (free, with ntfy).")
            else tr(R.string.yumeiro_has_no_push_server_of, "Yumeiro has no push server of its own, so notifications rely on these checks being allowed to run. For on-time alerts, turn on Reliable alerts under Notifications (free, with ntfy)."), color = TextSecondary,
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
        PermissionCenter.afterNotificationRequest(context, ok)
    }
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick++
        if (r.values.any { it }) onMediaGranted()
        else PermissionCenter.explainDenied(context, PermissionCenter.mediaPermissions().first(), tr(R.string.music_videos_access, "Music & videos access"))
    }
    SettingsCard {
        SettingsActionRow("Notifications", "New episodes, extensions and app updates", "perm_notifications",
            status = if (notif) "Allowed" else "Not allowed", ok = notif) {
            if (notif) PermissionCenter.openNotificationSettings(context)
            else if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else PermissionCenter.openNotificationSettings(context)
        }
        SettingsDivider()
        SettingsActionRow(tr(R.string.music_videos, "Music & videos"), tr(R.string.find_the_songs_and_videos_on, "Find the songs and videos on this device"), "perm_media",
            status = if (media) "Allowed" else "Not allowed", ok = media) {
            if (media) PermissionCenter.openAppSettings(context) else mediaLauncher.launch(PermissionCenter.mediaPermissions())
        }
        SettingsDivider()
        SettingsActionRow(tr(R.string.install_app_updates, "Install app updates"), tr(R.string.lets_yumeiro_install_its_own_updates, "Lets Yumeiro install its own updates after you tap Update"), "perm_install",
            status = if (install) "Allowed" else "Ask when needed", ok = if (install) true else null) {
            PermissionCenter.openInstallSettings(context)
        }
        SettingsDivider()
        SettingsActionRow(tr(R.string.all_app_permissions, "All app permissions"), tr(R.string.open_yumeiro_in_android_settings, "Open Yumeiro in Android settings"), "perm_app_settings") {
            PermissionCenter.openAppSettings(context)
        }
    }
}

private data class ClearAction(val title: String, val body: String, val done: String, val run: suspend () -> Unit)

/** NB15D-OB1: what "Clear watch history" also removes: stream link and Live TV recents. */
object WatchHistoryClear {
    fun streams(context: android.content.Context) {
        dev.asfakul.yumeiro.stream.StreamRepository.clear(context)
        dev.asfakul.yumeiro.stream.StreamRepository.clearLive(context)
    }
}

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
                }, modifier = Modifier.testTag("privacy_confirm")) { Text(stringResource(R.string.clear), color = dev.asfakul.yumeiro.ui.theme.DangerTone) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val io: suspend (() -> Unit) -> Unit = { f -> withContext(Dispatchers.IO) { f() } }
    SettingsCard {
        SettingsActionRow(tr(R.string.clear_watch_history, "Clear watch history"), tr(R.string.continue_watching_resume_points_and_watched, "Continue watching, resume points and watched marks (device, online and YouTube)"), "privacy_clear_watch") {
            pending = ClearAction(tr(R.string.clear_watch_history_2, "Clear watch history?"), tr(R.string.continue_watching_resume_positions_and_watched, "Continue watching, resume positions and watched marks are removed everywhere."), tr(R.string.watch_history_cleared, "Watch history cleared")) {
                onClearWatchHistory()
                io { dev.asfakul.yumeiro.online.OnlineHistory.clear(context); dev.asfakul.yumeiro.youtube.YtHistory.clear(context); WatchHistoryClear.streams(context); dev.asfakul.yumeiro.backup.HistoryWipe.watchExtras(context) }
            }
        }
        if (adultOn) {
            SettingsDivider()
            SettingsActionRow(tr(R.string.clear_18_history, "Clear 18+ history"), tr(R.string.only_the_18_titles_you_watched, "Only the 18+ titles you watched. Everything else stays"), "privacy_clear_adult") {
                pending = ClearAction(tr(R.string.clear_18_history_2, "Clear 18+ history?"), tr(R.string.every_18_title_is_removed_from, "Every 18+ title is removed from your history. Other history stays."), "18+ history cleared") {
                    io { dev.asfakul.yumeiro.online.OnlineHistory.clearAdult(context) }
                }
            }
        }
        SettingsDivider()
        SettingsActionRow(tr(R.string.clear_listening_history, "Clear listening history"), tr(R.string.recently_played_songs_and_play_counts, "Recently played songs and play counts"), "privacy_clear_listening") {
            pending = ClearAction(tr(R.string.clear_listening_history_2, "Clear listening history?"), tr(R.string.recently_played_and_play_counts_are, "Recently played and play counts are reset. Playlists and favourites stay."), tr(R.string.listening_history_cleared, "Listening history cleared")) {
                onClearListeningHistory()
                io { dev.asfakul.yumeiro.backup.HistoryWipe.listeningExtras(context) } // B9: Jump back in and the saved queue too
            }
        }
        SettingsDivider()
        SettingsActionRow(tr(R.string.clear_search_history, "Clear search history"), tr(R.string.recent_searches_in_every_search_box, "Recent searches in every search box"), "privacy_clear_search") {
            pending = ClearAction(tr(R.string.clear_search_history_2, "Clear search history?"), tr(R.string.all_recent_searches_are_forgotten, "All recent searches are forgotten."), tr(R.string.search_history_cleared, "Search history cleared")) {
                io { dev.asfakul.yumeiro.ui.components.RecentSearches.clearAll(context); dev.asfakul.yumeiro.ui.screens.online.SearchHistory.clear(context); dev.asfakul.yumeiro.backup.HistoryWipe.dropSearchCaches(context) }
            }
        }
        SettingsDivider()
        SettingsActionRow(tr(R.string.clear_notifications, "Clear notifications"), tr(R.string.empty_the_notification_inbox, "Empty the notification inbox"), "privacy_clear_inbox") {
            pending = ClearAction(tr(R.string.clear_notifications_2, "Clear notifications?"), tr(R.string.the_inbox_is_emptied_notification_settings, "The inbox is emptied. Notification settings stay the same."), tr(R.string.notifications_cleared, "Notifications cleared")) {
                Inbox.clear(context)
            }
        }
        SettingsDivider()
        SettingsActionRow(tr(R.string.clear_cache, "Clear cache"), tr(R.string.thumbnails_and_temporary_files_frees_space, "Thumbnails and temporary files. Frees space; nothing personal is lost"), "privacy_clear_cache") {
            pending = ClearAction(tr(R.string.clear_cache_2, "Clear cache?"), tr(R.string.images_and_temporary_files_are_downloaded, "Images and temporary files are downloaded again when needed."), tr(R.string.cache_cleared, "Cache cleared")) {
                io { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                dev.asfakul.yumeiro.online.OnlineRepository.clearCaches()
            }
        }
    }
}

@Composable
fun SocialMediaCard() {
    val context = LocalContext.current
    val more = androidx.compose.runtime.remember(context) { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.SOCIAL_LINKS) }
    SettingsCard {
        Text(
            if (more) "Yumeiro has its own website (yumeiro.click), a Telegram channel and group, a Discord server, and is @${dev.asfakul.yumeiro.notify.SocialLinks.HANDLE} everywhere else."
            else tr(R.string.yumeiro_has_its_own_website_yumeiro, "Yumeiro has its own website (yumeiro.click) and community on Telegram: news in the channel, help and ideas in the group."), color = TextSecondary,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
        Reminders.Social.entries.forEachIndexed { i, s ->
            if (i > 0) SettingsDivider()
            SettingsActionRow(s.label, "${s.detail} · ${s.url.removePrefix("https://")}", "social_${s.name.lowercase()}") {
                if (Links.open(context, s.url)) Reminders.recordFollow(context, s)
                else AppMessages.error(context, "Couldn't open ${s.label}")
            }
        }
        if (more) {
            // OL-13: one tap per account, icon + name, like Mihon's About row (wraps on small phones, D-pad on TV).
            SettingsDivider()
            SocialLinkRow(dev.asfakul.yumeiro.notify.SocialLinks.yumeiro, "social_")
        }
        SettingsDivider()
        SettingsActionRow("Website", "Downloads, news and guides · yumeiro.click", "social_website") {
            Links.open(context, Reminders.WEBSITE_URL)
        }
        SettingsDivider()
        SettingsActionRow(tr(R.string.star_on_github, "Star on GitHub"), "github.com/siamform/yumeiro", "social_github") {
            if (Links.open(context, Reminders.GITHUB_URL)) Reminders.recordStar(context)
            else AppMessages.error(context, "Couldn't open GitHub")
        }
    }
}

/** OL-13: a wrapping row of account buttons (brand icon over its name). [prefix] + id is each button's test tag. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SocialLinkRow(links: List<dev.asfakul.yumeiro.notify.SocialLinks.Link>, prefix: String) {
    val context = LocalContext.current
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        links.forEach { l ->
            Column(
                Modifier.width(72.dp).clip(yuRound(12))
                    .clickable(onClickLabel = "Open ${l.label}") { Links.open(context, l.url) }
                    .padding(vertical = 6.dp).testTag(prefix + l.id),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(androidx.compose.ui.res.painterResource(l.icon), null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Text(l.label.substringBefore(" ("), color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
