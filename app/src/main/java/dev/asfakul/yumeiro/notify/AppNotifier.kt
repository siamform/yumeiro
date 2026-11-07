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

    /** Channels (v2 = with the Yumeiro chime). Old silent/default-sound channels are removed. */
    enum class Channel(val id: String, val title: String, val description: String, val importance: Int) {
        EPISODES("yumeiro_episodes", "New episodes", "Saved series with new episodes", NotificationManager.IMPORTANCE_DEFAULT),
        DISCOVER("yumeiro_discover", "New extensions & streams", "New extensions in the market and new stream playlists", NotificationManager.IMPORTANCE_DEFAULT),
        NEWS("yumeiro_news", "What's new", "New features, releases and announcements", NotificationManager.IMPORTANCE_DEFAULT),
        NUDGE("yumeiro_suggestions", "Suggestions", "An occasional idea for something to watch or listen to", NotificationManager.IMPORTANCE_LOW),
    }
    private val OLD_CHANNELS = listOf("new_episodes", "discover_new", "discover_nudge")

    fun soundUri(c: Context): Uri = Uri.parse("android.resource://${c.packageName}/${R.raw.yumeiro_chime}")

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

    private fun settingFor(kind: Inbox.Kind): NotifySettings.Key? = when (kind) {
        Inbox.Kind.EPISODE -> NotifySettings.Key.EPISODES
        Inbox.Kind.EXTENSION -> NotifySettings.Key.EXTENSIONS
        Inbox.Kind.PLAYLIST -> NotifySettings.Key.PLAYLISTS
        Inbox.Kind.NEWS, Inbox.Kind.INFO -> NotifySettings.Key.NEWS
        Inbox.Kind.NUDGE -> NotifySettings.Key.NUDGES
        Inbox.Kind.UPDATE -> null
    }

    private fun channelFor(kind: Inbox.Kind): Channel = when (kind) {
        Inbox.Kind.EPISODE -> Channel.EPISODES
        Inbox.Kind.EXTENSION, Inbox.Kind.PLAYLIST -> Channel.DISCOVER
        Inbox.Kind.NUDGE -> Channel.NUDGE
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
        if (inboxOnly || !NotifySettings.get(c, NotifySettings.Key.ENABLED) || !systemAllowed(c)) return true
        ensureChannels(c)
        val open = Intent(c, MainActivity::class.java).setAction(ACTION_OPEN_INBOX).putExtra(EXTRA_ITEM, item.id)
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
        try { NotificationManagerCompat.from(c).notify(notificationId, b.build()) }
        catch (e: SecurityException) { Log.w(TAG, "notify", e) }
        return true
    }
}
