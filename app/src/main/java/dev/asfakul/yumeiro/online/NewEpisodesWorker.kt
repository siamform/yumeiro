/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Checks saved series (My List) a few times a day, and when Home opens, for new episodes. Every
 * series with status Watching / Plan to watch / On hold is checked (Home badges it); only titles
 * with their bell on also get a notification.
 */
class NewEpisodesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NewEpisodes"
        const val WORK = "new_episodes_check"

        fun schedule(context: Context) = dev.asfakul.yumeiro.notify.BackgroundWork.apply(context, ExistingPeriodicWorkPolicy.KEEP)

        private const val MAX_CHECKS = 60
        private const val PREFS = "new_episodes"
        private const val KEY_LAST = "last_check_at"
        private const val FOREGROUND_GAP_MS = 30L * 60 * 1000

        /** Series worth checking: not movies, not finished or dropped. */
        fun due(all: List<OnlineBookmarks.Bookmark>): List<OnlineBookmarks.Bookmark> = all.filter {
            it.status != OnlineBookmarks.Status.COMPLETED && it.status != OnlineBookmarks.Status.DROPPED &&
                it.type?.contains("movie", true) != true
        }.sortedByDescending { it.status == OnlineBookmarks.Status.WATCHING }

        /**
         * Home is open: check followed series now if the last check is older than 30 minutes. This
         * also works when background checks are turned off, because the app is in use.
         */
        fun checkSoon(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = prefs.getLong(KEY_LAST, 0L)
            if (now - last in 0 until FOREGROUND_GAP_MS) return
            prefs.edit().putLong(KEY_LAST, now).apply()
            val wm = runCatching { androidx.work.WorkManager.getInstance(context.applicationContext) }.getOrNull() ?: return
            val k = androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()
            runCatching {
                wm.enqueueUniqueWork("new_episodes_foreground", androidx.work.ExistingWorkPolicy.KEEP,
                    androidx.work.OneTimeWorkRequestBuilder<NewEpisodesWorker>().setConstraints(k).build())
            }
        }

        /** Highest episode count across dub/sub groups. */
        fun episodeCount(detail: OnlineDetail): Int = detail.episodes.values.maxOfOrNull { it.size } ?: 0
    }

    /** Only used on Android 9-11 when a push wake-up runs this as expedited work. */
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo = dev.asfakul.yumeiro.notify.PushWake.foregroundInfo(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        OnlineBookmarks.ensureLoaded(ctx)
        // Every followed series is checked so Home can badge it; only titles with the bell on notify.
        val watch = due(OnlineBookmarks.items.value)
        if (watch.isEmpty()) return@withContext Result.success()
        runCatching { ExtensionManager.ensureLoaded(ctx) }
        val gate = kotlinx.coroutines.sync.Semaphore(3)
        val found = kotlinx.coroutines.coroutineScope {
            watch.take(MAX_CHECKS).map { b ->
                async {
                    gate.withPermit {
                        val api = ExtensionManager.provider(b.api) ?: return@withPermit null
                        val detail = withTimeoutOrNull(30_000) { runCatching { OnlineRepository.load(api, b.url, force = true) }.getOrNull() } ?: return@withPermit null
                        if (detail.kind != OnlineKind.SERIES && detail.episodes.isEmpty()) return@withPermit null
                        val added = OnlineBookmarks.checked(ctx, b.api, b.url, episodeCount(detail))
                        if (added > 0) b to added else null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
        val toNotify = found.filter { it.first.notify }
        if (toNotify.isNotEmpty()) notify(ctx, toNotify)
        Result.success()
    }

    private fun notify(ctx: Context, found: List<Pair<OnlineBookmarks.Bookmark, Int>>) {
        val now = System.currentTimeMillis()
        found.forEach { (b, n) ->
            runCatching {
                dev.asfakul.yumeiro.notify.AppNotifier.post(
                    ctx,
                    dev.asfakul.yumeiro.notify.Inbox.Item(
                        id = "ep-${b.key}-${now / 3_600_000}", kind = dev.asfakul.yumeiro.notify.Inbox.Kind.EPISODE,
                        title = "${b.name} has a new episode".let { if (n > 1) "${b.name}: $n new episodes" else it },
                        body = if (n == 1) "A new episode is out. Tap to watch." else "$n new episodes are out. Tap to watch.",
                        at = now,
                    ),
                    notificationId = 0x6000 + (b.key.hashCode() and 0xfff),
                )
            }.onFailure { Log.w(TAG, "notify", it) }
        }
    }
}
