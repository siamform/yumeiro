/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Round 13: notifications used to arrive all at once (a catch-up run found five things and posted
 * five notifications in the same second) and mostly while the app was open.
 *
 * Two fixes live here:
 *  - **Pacing.** One notification goes to the shade right away; the rest wait in a small queue
 *    and are shown one at a time, [GAP_MS] apart, by an alarm that also fires while the app is
 *    closed. Everything is in the inbox immediately; only the shade is paced.
 *  - **Background clock.** An inexact repeating alarm ([BackgroundClock]) wakes the background
 *    checks every couple of hours even when the phone's scheduler has put WorkManager jobs on
 *    hold for a rarely opened app. It is re-armed after a reboot or an update.
 */
object NotifyPacer {
    const val GAP_MS = 12L * 60 * 1000
    /** Older queued items are dropped from the shade (they stay in the inbox). */
    const val STALE_MS = 24L * 60 * 60 * 1000
    private const val MAX_QUEUE = 12
    private const val PREFS = "notify_pacer"
    private const val TAG = "NotifyPacer"

    data class Pending(val item: Inbox.Item, val notificationId: Int, val queuedAt: Long)

    /** Pure: may something be shown now, given when the last one was shown? */
    fun canShowNow(nowMs: Long, lastShownMs: Long): Boolean = lastShownMs <= 0 || nowMs - lastShownMs >= GAP_MS || nowMs < lastShownMs

    /** Pure: when the next queued item is due. */
    fun nextAt(lastShownMs: Long): Long = lastShownMs + GAP_MS

    /** Pure: the queue after adding [p] (same id replaces, newest kept, capped, stale removed). */
    fun push(queue: List<Pending>, p: Pending, nowMs: Long): List<Pending> =
        (queue.filter { it.item.id != p.item.id && it.notificationId != p.notificationId } + p)
            .filter { nowMs - it.queuedAt < STALE_MS }
            .takeLast(MAX_QUEUE)

    @Synchronized
    fun offer(c: Context, item: Inbox.Item, notificationId: Int, show: (Inbox.Item, Int) -> Unit) {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        val last = p.getLong("last_shown", 0L)
        val queue = read(c)
        if (queue.isEmpty() && canShowNow(now, last)) {
            show(item, notificationId)
            p.edit().putLong("last_shown", now).apply()
            return
        }
        write(c, push(queue, Pending(item, notificationId, now), now))
        arm(c, nextAt(last).coerceAtLeast(now + 60_000))
    }

    /** The alarm fired: show the oldest queued item and arm the next one. */
    @Synchronized
    fun deliverNext(c: Context) {
        val now = System.currentTimeMillis()
        val queue = read(c).filter { now - it.queuedAt < STALE_MS }
        val first = queue.firstOrNull() ?: run { write(c, emptyList()); return }
        runCatching { AppNotifier.showNow(c, first.item, first.notificationId) }.onFailure { Log.w(TAG, "show", it) }
        prefs(c).edit().putLong("last_shown", now).apply()
        val rest = queue.drop(1)
        write(c, rest)
        if (rest.isNotEmpty()) arm(c, now + GAP_MS)
    }

    private fun arm(c: Context, atWallMs: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val delay = (atWallMs - System.currentTimeMillis()).coerceAtLeast(60_000)
        val pi = PendingIntent.getBroadcast(c, 0x7301, Intent(c, NotifyAlarmReceiver::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Inexact and Doze-friendly: no exact-alarm permission needed, still fires with the app closed.
        runCatching { am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pi) }
            .onFailure { Log.w(TAG, "arm", it) }
    }

    const val ACTION_NEXT = "dev.asfakul.yumeiro.notify.PACER_NEXT"
    const val ACTION_CLOCK = "dev.asfakul.yumeiro.notify.BACKGROUND_CLOCK"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun encode(queue: List<Pending>): String = JSONArray().apply {
        queue.forEach { q ->
            put(JSONObject().put("id", q.item.id).put("kind", q.item.kind.name).put("title", q.item.title).put("body", q.item.body)
                .put("at", q.item.at).put("link", q.item.link ?: "").put("nid", q.notificationId).put("q", q.queuedAt))
        }
    }.toString()

    internal fun decode(s: String?): List<Pending> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val kind = runCatching { Inbox.Kind.valueOf(o.optString("kind")) }.getOrNull() ?: return@mapNotNull null
            Pending(
                Inbox.Item(o.optString("id"), kind, o.optString("title"), o.optString("body"), o.optLong("at"), link = o.optString("link").ifBlank { null }),
                o.optInt("nid"), o.optLong("q"),
            )
        }
    }.getOrDefault(emptyList())

    private fun read(c: Context) = decode(prefs(c).getString("queue", null))
    private fun write(c: Context, q: List<Pending>) = prefs(c).edit().putString("queue", encode(q)).apply()
}

/** Wakes the background checks every couple of hours, independent of WorkManager's standby limits. */
object BackgroundClock {
    const val EVERY_MS = 3L * 60 * 60 * 1000

    fun arm(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pending(c)
        if (!NotifySettings.get(c, NotifySettings.Key.BACKGROUND)) { am.cancel(pi); return }
        runCatching {
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + EVERY_MS, EVERY_MS, pi)
        }
    }

    private fun pending(c: Context): PendingIntent =
        PendingIntent.getBroadcast(c, 0x7302, Intent(c, NotifyAlarmReceiver::class.java).setAction(NotifyPacer.ACTION_CLOCK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** Alarm target: pacing ticks, the background clock, and re-arming after a reboot or update. */
class NotifyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.applicationContext
        when (intent.action) {
            NotifyPacer.ACTION_NEXT -> NotifyPacer.deliverNext(c)
            NotifyPacer.ACTION_CLOCK -> runCatching { BackgroundWork.wake(c) }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                runCatching { BackgroundWork.apply(c, androidx.work.ExistingPeriodicWorkPolicy.KEEP) }
                runCatching { BackgroundClock.arm(c) }
                runCatching { NotifyPacer.deliverNext(c) }
            }
        }
    }
}
