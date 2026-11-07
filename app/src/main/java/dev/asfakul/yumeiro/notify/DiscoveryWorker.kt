/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.asfakul.yumeiro.online.NewEpisodesWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Schedules (or cancels) all periodic background checks from the user's settings. */
object BackgroundWork {
    fun constraints(c: Context): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (NotifySettings.get(c, NotifySettings.Key.WIFI_ONLY)) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** Applies the current settings: schedules both workers, or cancels them when background work is off. */
    fun apply(c: Context, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE) {
        val wm = runCatching { WorkManager.getInstance(c.applicationContext) }.getOrNull() ?: return
        if (!NotifySettings.get(c, NotifySettings.Key.BACKGROUND)) {
            wm.cancelUniqueWork(DiscoveryWorker.WORK)
            wm.cancelUniqueWork(NewEpisodesWorker.WORK)
            return
        }
        val k = constraints(c)
        runCatching {
            wm.enqueueUniquePeriodicWork(DiscoveryWorker.WORK, policy,
                PeriodicWorkRequestBuilder<DiscoveryWorker>(6, TimeUnit.HOURS).setConstraints(k).build())
            wm.enqueueUniquePeriodicWork(NewEpisodesWorker.WORK, policy,
                PeriodicWorkRequestBuilder<NewEpisodesWorker>(6, TimeUnit.HOURS).setConstraints(k).build())
        }
    }
}

/**
 * Every 6 hours: reads the small Yumeiro feed (new extensions, new playlists, releases and
 * announcements, published by the "App feed" workflow) and now and then sends a friendly
 * suggestion. New episodes have their own worker. Nothing is installed automatically; every
 * notification opens the inbox.
 */
class DiscoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "Discovery"
        const val WORK = "discovery_check"
        private const val STATE = "discovery_state"
        private const val NUDGE_GAP_MS = 3L * 24 * 60 * 60 * 1000

        val NUDGES = listOf(
            "Feeling bored?" to "Pick a movie for tonight — something new is waiting.",
            "Need some music?" to "Put on a playlist and let Yumeiro set the mood.",
            "Anime break?" to "Pick up where you left off, or try something new.",
            "Live TV, anytime" to "Browse streams from around the world.",
            "Quiet evening?" to "A good show and a cup of tea. Open Yumeiro.",
        )

        fun schedule(context: Context) = BackgroundWork.apply(context, ExistingPeriodicWorkPolicy.KEEP)

        /** Items in [now] that were not in [seen]. An empty [seen] is the first run: nothing is "new". */
        fun newItems(seen: Set<String>, now: Collection<String>): List<String> =
            if (seen.isEmpty()) emptyList() else now.filter { it !in seen }

        /** Nudges only in the evening (18–22h), at most once every 3 days. */
        fun nudgeDue(nowMs: Long, lastMs: Long, hour: Int): Boolean = hour in 18..21 && nowMs - lastMs >= NUDGE_GAP_MS

        fun summary(names: List<String>): String {
            val shown = names.take(3).joinToString(", ")
            return if (names.size > 3) "$shown and ${names.size - 3} more" else shown
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        NotifySettings.init(ctx)
        runCatching { AppFeed.sync(ctx) }.onFailure { Log.w(TAG, "feed", it) }

        if (NotifySettings.get(ctx, NotifySettings.Key.NUDGES)) {
            val st = ctx.getSharedPreferences(STATE, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = st.getLong("nudge_at", 0L)
            if (last == 0L) st.edit().putLong("nudge_at", now).apply() // no nudge on the very first day
            else if (nudgeDue(now, last, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) {
                val (title, text) = NUDGES[((now / 3_600_000) % NUDGES.size).toInt()]
                AppNotifier.post(ctx, Inbox.Item("nudge-${now / 3_600_000}", Inbox.Kind.NUDGE, title, text, now), notificationId = 0x7103)
                st.edit().putLong("nudge_at", now).apply()
            }
        }
        Result.success()
    }
}
