/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.asfakul.yumeiro.online.NewEpisodesWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules (or cancels) all periodic background checks from the user's settings.
 *
 * Yumeiro has no push server (and won't ship Google's FCM), so "push" is reliable polling:
 *  - the small app feed every [FEED_HOURS] h, the heavier new-episode check every [EPISODE_HOURS] h;
 *  - a one-off catch-up each time the app comes to the foreground (throttled), so the inbox is
 *    fresh the moment it opens and missed windows are recovered;
 *  - failed network runs are retried with backoff instead of waiting for the next window;
 *  - a heartbeat ([BackgroundHealth]) spots OEM battery killers so the app can ask for an exemption.
 */
object BackgroundWork {
    const val FEED_HOURS = 2L
    const val EPISODE_HOURS = 4L
    /** Bump when periods/constraints change so existing installs pick up the new schedule. */
    private const val SCHEDULE_VERSION = 2
    private const val CATCH_UP = "background_catch_up"
    private const val CATCH_UP_GAP_MS = 15L * 60 * 1000
    private const val PREFS = "background_work"

    fun constraints(c: Context): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (NotifySettings.get(c, NotifySettings.Key.WIFI_ONLY)) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    /** Applies the current settings: schedules both workers, or cancels them when background work is off. */
    fun apply(c: Context, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE) {
        val wm = runCatching { WorkManager.getInstance(c.applicationContext) }.getOrNull() ?: return
        if (!NotifySettings.get(c, NotifySettings.Key.BACKGROUND)) {
            wm.cancelUniqueWork(DiscoveryWorker.WORK)
            wm.cancelUniqueWork(NewEpisodesWorker.WORK)
            return
        }
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val effective = if (prefs.getInt("schedule_version", 0) != SCHEDULE_VERSION) ExistingPeriodicWorkPolicy.UPDATE else policy
        val k = constraints(c)
        runCatching {
            wm.enqueueUniquePeriodicWork(DiscoveryWorker.WORK, effective,
                PeriodicWorkRequestBuilder<DiscoveryWorker>(FEED_HOURS, TimeUnit.HOURS).setConstraints(k)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build())
            wm.enqueueUniquePeriodicWork(NewEpisodesWorker.WORK, effective,
                PeriodicWorkRequestBuilder<NewEpisodesWorker>(EPISODE_HOURS, TimeUnit.HOURS).setConstraints(k)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build())
            prefs.edit().putInt("schedule_version", SCHEDULE_VERSION).apply()
        }
    }

    /** The app came to the foreground: catch up on the feed + episodes now (at most every 15 min). */
    fun catchUp(c: Context) {
        if (!NotifySettings.get(c, NotifySettings.Key.BACKGROUND)) return
        BackgroundHealth.snapshot(c) // before the catch-up run refreshes the heartbeat
        runCatching { PushWake.onForeground(c) }
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!catchUpDue(now, prefs.getLong("catch_up_at", 0L))) return
        prefs.edit().putLong("catch_up_at", now).apply()
        val wm = runCatching { WorkManager.getInstance(c.applicationContext) }.getOrNull() ?: return
        val k = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        runCatching {
            wm.beginUniqueWork(CATCH_UP, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<DiscoveryWorker>().setConstraints(k).build())
                .then(OneTimeWorkRequestBuilder<NewEpisodesWorker>().setConstraints(k).build())
                .enqueue()
        }
    }

    private const val PUSH_CATCH_UP = "push_catch_up"
    private const val PUSH_GAP_MS = 20L * 60 * 1000

    /**
     * A UnifiedPush wake-up arrived ([PushWake]): run the feed + episode checks now, as expedited
     * work so Doze doesn't hold them back (at most every 20 min).
     */
    fun wake(c: Context) {
        if (!NotifySettings.get(c, NotifySettings.Key.BACKGROUND)) return
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("push_wake_at", 0L) in 0 until PUSH_GAP_MS) return
        prefs.edit().putLong("push_wake_at", now).apply()
        val wm = runCatching { WorkManager.getInstance(c.applicationContext) }.getOrNull() ?: return
        val k = constraints(c)
        val fast = androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
        runCatching {
            wm.beginUniqueWork(PUSH_CATCH_UP, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<DiscoveryWorker>().setConstraints(k).setExpedited(fast).build())
                .then(OneTimeWorkRequestBuilder<NewEpisodesWorker>().setConstraints(k).setExpedited(fast).build())
                .enqueue()
        }
    }

    fun catchUpDue(nowMs: Long, lastMs: Long): Boolean = nowMs - lastMs >= CATCH_UP_GAP_MS || nowMs < lastMs
}

/**
 * Heartbeat of the background checks. If they stop running for much longer than their period
 * while background work is on, the phone's battery manager is killing them (dontkillmyapp.com),
 * and the app asks the user to let Yumeiro run in the background.
 */
object BackgroundHealth {
    private const val PREFS = "background_work"

    fun markRan(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last_run_at", System.currentTimeMillis()).apply()

    fun lastRun(c: Context): Long = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("last_run_at", 0L)

    /** Stalled = never ran in 3 periods since it was first scheduled (or since the last run). */
    fun stalled(nowMs: Long, lastRunMs: Long, firstSeenMs: Long, periodHours: Long = BackgroundWork.FEED_HOURS): Boolean {
        val since = if (lastRunMs > 0) lastRunMs else firstSeenMs
        return since > 0 && nowMs - since > periodHours * 3 * 3_600_000L
    }

    /** Heartbeat state as it was when the app came to the foreground (the catch-up run resets it). */
    @Volatile var stalledAtLaunch: Boolean = false
        private set

    fun snapshot(c: Context) { if (!stalledAtLaunch) stalledAtLaunch = runCatching { looksStalled(c) }.getOrDefault(false) }

    /** Clears the flag once the user has been told (or fixed it). */
    fun acknowledge() { stalledAtLaunch = false }

    fun looksStalled(c: Context): Boolean {
        if (!NotifySettings.get(c, NotifySettings.Key.BACKGROUND)) return false
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!p.contains("first_seen_at")) p.edit().putLong("first_seen_at", now).apply()
        return stalled(now, lastRun(c), p.getLong("first_seen_at", now))
    }
}

/**
 * Every [BackgroundWork.FEED_HOURS] hours (and when the app opens): reads the small Yumeiro feed (new extensions, new playlists, releases and
 * announcements, published by the "App feed" workflow) and now and then sends a friendly
 * suggestion. New episodes have their own worker. Nothing is installed automatically; every
 * notification opens the inbox.
 */
class DiscoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** Only used on Android 9-11 when a push wake-up runs this as expedited work. */
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo = PushWake.foregroundInfo(applicationContext)

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
        val feed = runCatching { AppFeed.sync(ctx) }.onFailure { Log.w(TAG, "feed", it) }
        // A network hiccup retries soon (with backoff) instead of silently waiting hours.
        if (feed.exceptionOrNull() is java.io.IOException && runAttemptCount < 3) return@withContext Result.retry()
        BackgroundHealth.markRan(ctx)
        runCatching { PushWake.refillIfDue(ctx) }

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
