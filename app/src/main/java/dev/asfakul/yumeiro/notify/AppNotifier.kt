/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.asfakul.yumeiro.MainActivity
import dev.asfakul.yumeiro.R

/**
 * One place that posts Yumeiro notifications: every one goes to the in-app inbox and, when the
 * user allows it, to the system shade on its own channel with the Yumeiro chime.
 */
object AppNotifier {
    private const val TAG = "AppNotifier"
    const val ACTION_OPEN_INBOX = "dev.asfakul.yumeiro.action.OPEN_INBOX"
    const val EXTRA_ITEM = "inbox_item"
    /** Optional deep link of the tapped notice: an https URL or an in-app action (see FcmPlan.ACTIONS). */
    const val EXTRA_LINK = "inbox_link"

    /**
     * Channels (v3 = the soft "tsuki" chime). Android never changes a channel's sound after it is
     * created, so a new sound needs new channel ids; the old ones are removed.
     */
    enum class Channel(val id: String, val title: String, val description: String, val importance: Int) {
        EPISODES("yumeiro_episodes_v3", "New episodes", "Saved series with new episodes", NotificationManager.IMPORTANCE_DEFAULT),
        DISCOVER("yumeiro_discover_v3", "New extensions & streams", "New extensions in the market and new stream playlists", NotificationManager.IMPORTANCE_DEFAULT),
        NEWS("yumeiro_news_v3", "What's new", "New features, releases and announcements", NotificationManager.IMPORTANCE_DEFAULT),
        NUDGE("yumeiro_suggestions_v3", "Suggestions", "An occasional idea for something to watch or listen to", NotificationManager.IMPORTANCE_LOW),
        ALERTS("yumeiro_alerts_v3", "Service alerts", "Maintenance, outages and messages for this device", NotificationManager.IMPORTANCE_HIGH),
    }
    private val OLD_CHANNELS = listOf(
        "new_episodes", "discover_new", "discover_nudge",
        // v2 (the first, brighter chime)
        "yumeiro_episodes", "yumeiro_discover", "yumeiro_news", "yumeiro_suggestions",
    )

    fun soundUri(c: Context): Uri = Uri.parse("android.resource://${c.packageName}/raw/yumeiro_tsuki")

    fun ensureChannels(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        Channel.entries.forEach { ch ->
            if (nm.getNotificationChannel(ch.id) == null) runCatching {
                nm.createNotificationChannel(NotificationChannel(ch.id, ch.title, ch.importance).apply {
                    description = ch.description
                    setShowBadge(true)
                    if (ch.importance >= NotificationManager.IMPORTANCE_DEFAULT) setSound(soundUri(c), attrs)
                })
            }
        }
        OLD_CHANNELS.forEach { id -> runCatching { if (nm.getNotificationChannel(id) != null) nm.deleteNotificationChannel(id) } }
    }

    /** True when Android lets Yumeiro show notifications at all. */
    fun systemAllowed(c: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(c).areNotificationsEnabled()
    }

    /** Why a notification would stay in the inbox instead of reaching the status bar. */
    enum class Block(val title: String, val hint: String) {
        NONE("", ""),
        PERMISSION("Notifications are not allowed", "Android hasn't given Yumeiro permission yet. Tap Turn on and allow notifications."),
        APP_OFF("Notifications are off for Yumeiro", "They're turned off in Android settings. Tap Turn on to switch them back on."),
        MASTER_OFF("Show notifications is off", "Settings → Notifications → Show notifications keeps everything in the inbox only."),
        CHANNEL_OFF("Some notification types are off", "One or more Yumeiro categories are turned off in Android settings. Tap Turn on to check them."),
    }

    /** True when the user (or the system) turned this channel off in Android settings. */
    fun channelBlocked(c: Context, ch: Channel): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val nm = c.getSystemService(NotificationManager::class.java) ?: return false
        val channel = nm.getNotificationChannel(ch.id) ?: return false
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val group = channel.group?.let { nm.getNotificationChannelGroup(it) }
            if (group?.isBlocked == true) return true
        }
        return false
    }

    /** The first reason notifications of [kind] (or any kind) can't reach the shade. */
    fun block(c: Context, kind: Inbox.Kind? = null): Block {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Block.PERMISSION
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return Block.APP_OFF
        if (!NotifySettings.get(c, NotifySettings.Key.ENABLED)) return Block.MASTER_OFF
        val channels = if (kind != null) listOf(channelFor(kind)) else listOf(Channel.EPISODES, Channel.DISCOVER, Channel.NEWS)
        if (channels.any { channelBlocked(c, it) }) return Block.CHANNEL_OFF
        return Block.NONE
    }

    private fun settingFor(kind: Inbox.Kind): NotifySettings.Key? = when (kind) {
        Inbox.Kind.EPISODE -> NotifySettings.Key.EPISODES
        Inbox.Kind.EXTENSION -> NotifySettings.Key.EXTENSIONS
        Inbox.Kind.PLAYLIST -> NotifySettings.Key.PLAYLISTS
        Inbox.Kind.NEWS, Inbox.Kind.INFO -> NotifySettings.Key.NEWS
        Inbox.Kind.NUDGE -> NotifySettings.Key.NUDGES
        Inbox.Kind.ALERT -> NotifySettings.Key.SERVICE
        Inbox.Kind.UPDATE -> null
    }

    private fun channelFor(kind: Inbox.Kind): Channel = when (kind) {
        Inbox.Kind.EPISODE -> Channel.EPISODES
        Inbox.Kind.EXTENSION, Inbox.Kind.PLAYLIST -> Channel.DISCOVER
        Inbox.Kind.NUDGE -> Channel.NUDGE
        Inbox.Kind.ALERT -> Channel.ALERTS
        else -> Channel.NEWS
    }

    /**
     * Adds [item] to the inbox and, unless [inboxOnly], shows it as a system notification.
     * Nothing happens when the user switched this kind off. Returns true when it was new.
     */
    fun post(c: Context, item: Inbox.Item, notificationId: Int = item.id.hashCode(), inboxOnly: Boolean = false): Boolean {
        NotifySettings.init(c)
        settingFor(item.kind)?.let { if (!NotifySettings.get(c, it)) return false }
        if (!Inbox.add(c, item)) return false
        if (inboxOnly) return true
        ensureChannels(c)
        val why = block(c, item.kind)
        if (why != Block.NONE) { Log.i(TAG, "inbox only (${why.name}): ${item.id}"); return true }
        // Round 13: paced, so a catch-up run never drops a pile of notifications at once.
        NotifyPacer.offer(c, item, notificationId) { i, n -> show(c, i, n) }
        return true
    }

    /** Shows a queued item now ([NotifyPacer]); settings and system blocks are checked again. */
    fun showNow(c: Context, item: Inbox.Item, notificationId: Int) {
        NotifySettings.init(c)
        settingFor(item.kind)?.let { if (!NotifySettings.get(c, it)) return }
        ensureChannels(c)
        if (block(c, item.kind) != Block.NONE) return
        show(c, item, notificationId)
    }

    /**
     * Sends a sample notification so the user can check sound, pop-up and status-bar icon.
     * Returns the reason when Android would hide it.
     */
    fun sendTest(c: Context): Block {
        val why = block(c, Inbox.Kind.NEWS)
        if (why != Block.NONE) return why
        ensureChannels(c)
        val now = System.currentTimeMillis()
        show(c, Inbox.Item("test-$now", Inbox.Kind.NEWS, "Notifications work",
            "This is how Yumeiro tells you about new episodes, extensions and news.", now), 0x7199)
        return Block.NONE
    }

    /** "N more in your inbox" when a sync found more than fits in the shade. */
    fun postSummary(c: Context, more: Int) {
        if (more <= 0 || block(c, Inbox.Kind.NEWS) != Block.NONE) return
        ensureChannels(c)
        val now = System.currentTimeMillis()
        NotifyPacer.offer(c, Inbox.Item("summary-$now", Inbox.Kind.NEWS, "$more more updates",
            "Open your Yumeiro inbox to see everything that's new.", now), 0x7198) { i, n -> show(c, i, n) }
    }

    private fun show(c: Context, item: Inbox.Item, notificationId: Int) {
        val open = Intent(c, MainActivity::class.java).setAction(ACTION_OPEN_INBOX).putExtra(EXTRA_ITEM, item.id)
            .apply { item.link?.let { putExtra(EXTRA_LINK, it) } }
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(c, notificationId, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(c, channelFor(item.kind).id)
            .setSmallIcon(R.drawable.ic_stat_yumeiro)
            .setContentTitle(item.title)
            .setContentText(item.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(0xFF8B5CF6.toInt())
        if (NotifySettings.get(c, NotifySettings.Key.SOUND)) b.setSound(soundUri(c)) else b.setSilent(true)
        b.setPriority(when (item.kind) {
            Inbox.Kind.NUDGE -> NotificationCompat.PRIORITY_LOW
            Inbox.Kind.ALERT -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        })
            .setWhen(item.at).setShowWhen(true)
            .setCategory(when (item.kind) {
                Inbox.Kind.EPISODE -> NotificationCompat.CATEGORY_RECOMMENDATION
                Inbox.Kind.ALERT -> NotificationCompat.CATEGORY_STATUS
                Inbox.Kind.UPDATE -> NotificationCompat.CATEGORY_RECOMMENDATION
                else -> NotificationCompat.CATEGORY_SOCIAL
            })
        // Never let a bad notification crash a background worker.
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try { NotificationManagerCompat.from(c).notify(notificationId, b.build()) }
        catch (e: Exception) { Log.w(TAG, "notify", e) }
    }
}
