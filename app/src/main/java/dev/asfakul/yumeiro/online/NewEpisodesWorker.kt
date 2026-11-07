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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Checks saved series (My List) a few times a day and notifies about new episodes. Only titles
 * with notifications on and status Watching / Plan to watch / On hold are checked.
 */
class NewEpisodesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NewEpisodes"
        const val WORK = "new_episodes_check"

        fun schedule(context: Context) = dev.asfakul.yumeiro.notify.BackgroundWork.apply(context, ExistingPeriodicWorkPolicy.KEEP)

        /** Highest episode count across dub/sub groups. */
        fun episodeCount(detail: OnlineDetail): Int = detail.episodes.values.maxOfOrNull { it.size } ?: 0
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        OnlineBookmarks.ensureLoaded(ctx)
        val watch = OnlineBookmarks.items.value.filter {
            it.notify && it.status != OnlineBookmarks.Status.COMPLETED && it.status != OnlineBookmarks.Status.DROPPED &&
                it.type?.contains("movie", true) != true
        }
        if (watch.isEmpty()) return@withContext Result.success()
        runCatching { ExtensionManager.ensureLoaded(ctx) }
        val found = ArrayList<Pair<OnlineBookmarks.Bookmark, Int>>()
        for (b in watch.take(60)) {
            val api = ExtensionManager.provider(b.api) ?: continue
            val detail = withTimeoutOrNull(30_000) { runCatching { OnlineRepository.load(api, b.url, force = true) }.getOrNull() } ?: continue
            if (detail.kind != OnlineKind.SERIES && detail.episodes.isEmpty()) continue
            val added = OnlineBookmarks.checked(ctx, b.api, b.url, episodeCount(detail))
            if (added > 0) found += b to added
        }
        if (found.isNotEmpty()) notify(ctx, found)
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
