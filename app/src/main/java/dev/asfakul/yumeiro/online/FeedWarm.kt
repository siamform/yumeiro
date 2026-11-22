/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * FE-5 (docs/FEED-ENGINE-2.1.md, owner: "a reserve cache engine", "open like Instagram / TikTok"): while the phone
 * is on Wi-Fi with a healthy battery, the home pages of the working set (pinned, then fastest measured; quarantined
 * ones skipped) are refreshed on the phone every few hours, so the next open, including the first open after an app
 * update, shows a recent feed at once (FE-1) instead of waiting for live calls. Instagram and TikTok do the same:
 * the next feed is computed and fetched before the app is opened.
 *
 * Budget (2 GB phone / TV): at most [maxFor] extensions per run, 2 at a time, 30 s each, 2 min per run; skipped
 * while something plays; pages younger than [FRESH_ENOUGH_MS] are not asked again. Kill switch: flag fe5_feed_warm.
 */
object FeedWarm {
    const val WORK = "feed_warm"
    const val PERIOD_HOURS = 6L
    const val FRESH_ENOUGH_MS = 3L * 3_600_000L
    private const val PER_HOME_MS = 30_000L
    private const val RUN_BUDGET_MS = 120_000L
    private const val TAG = "FeedWarm"

    fun maxFor(lowEnd: Boolean): Int = if (lowEnd) 6 else 12

    /**
     * Pure: which extensions to refresh now. Pinned first, then fastest measured; quarantined ones and pages saved
     * less than [FRESH_ENOUGH_MS] ago are left out; at most [max]. [ageMs] = age of the saved home, null = none.
     */
    fun <T> pick(
        items: List<T>, name: (T) -> String, pins: List<String>, stat: (String) -> ExtensionSpeed.Stat?,
        ageMs: (T) -> Long?, now: Long, max: Int,
    ): List<T> = StartupSpeed.loadOrder(items, name, pins, stat, now)
        .filter { !ExtensionSpeed.quarantined(stat(name(it)), now) }
        .filter { (ageMs(it) ?: Long.MAX_VALUE) >= FRESH_ENOUGH_MS }
        .take(max)

    /** One warm-up run; returns how many homes were saved. Network + disk: Dispatchers.IO. */
    suspend fun run(context: Context): Int = withContext(Dispatchers.IO + PriorityGate.Lane(PriorityGate.BACKGROUND)) {
        if (!dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.FE5_FEED_WARM)) return@withContext 0
        if (PlayingNow.active.value) return@withContext 0
        runCatching { ExtensionManager.start(context); ExtensionManager.ensureLoaded(context) }.onFailure { Log.w(TAG, "load", it) }
        runCatching { ExtensionSpeed.init(context) }
        val pins = runCatching { PinnedExtensions.init(context); PinnedExtensions.pins.value }.getOrDefault(emptyList())
        val withHome = ExtensionManager.providers().filter { runCatching { it.hasMainPage }.getOrDefault(false) }
        val now = System.currentTimeMillis()
        val picked = pick(withHome, { it.name }, pins, ExtensionSpeed::stat, { OnlineRepository.homeAgeMs(it) }, now,
            maxFor(dev.asfakul.yumeiro.DeviceClass.lowEnd))
        val two = Semaphore(2)
        val saved = withTimeoutOrNull(RUN_BUDGET_MS) {
            coroutineScope {
                picked.map { api ->
                    async {
                        two.withPermit {
                            if (PlayingNow.active.value) return@withPermit false
                            withTimeoutOrNull(PER_HOME_MS) { runCatching { OnlineRepository.home(api, force = true) }.getOrNull() }.orEmpty().isNotEmpty()
                        }
                    }
                }.awaitAll().count { it }
            }
        } ?: 0
        Log.i(TAG, "warmed $saved of ${picked.size} (installed with home ${withHome.size})")
        saved
    }
}

/** FE-5: the periodic worker behind [FeedWarm] (scheduled by BackgroundWork.apply). */
class FeedWarmWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { FeedWarm.run(applicationContext) }.onFailure { Log.w("FeedWarm", "run", it) }
        return Result.success()
    }
}
