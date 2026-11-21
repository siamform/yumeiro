/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRuntime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SPEED-T (owner 2026-11-21: feed loading, extension loading, load speed and search are mandatory): measures on the
 * emulator, with real extensions from the market and the real network,
 * 1. extension relaunch load: time to the first usable extension and to all loaded;
 * 2. Home: time to the first row from any extension, live and then from the saved copy;
 * 3. search: time to the first result and to every extension answered, live and then from the saved copy.
 * Every number is logged under tag "SPEED" as `SPEED <what> <ms>`. Budgets are added after the baseline run
 * (docs/GOAL-TOP-OF-CLASS.md); until then the test only asserts that each step finishes.
 */
@RunWith(AndroidJUnit4::class)
class SpeedDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(what: String, ms: Long, extra: String = "") = Log.i("SPEED", "SPEED $what $ms $extra".trim())

    @Before fun setUp() = runBlocking {
        withTimeout(300_000) {
            ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
            val have = ExtensionManager.state.value.installed.count { it.enabled }
            if (have < WANT) {
                val items = ExtensionSources.all(ctx, force = true).items.filter {
                    it.primary && !it.isNsfw && !it.isDown && it.apiVersion <= OnlineRuntime.API_VERSION
                }
                var n = have
                for (c in items) {
                    if (n >= WANT) break
                    if (ExtensionManager.state.value.installed.any { it.internalName == c.internalName }) continue
                    runCatching { ExtensionManager.install(ctx, c, ExtensionReview.Mode.AUTO) }
                        .onSuccess { n++; Log.i("SPEED", "installed ${c.internalName}") }
                        .onFailure { Log.i("SPEED", "install ${c.internalName} failed: ${it.message}") }
                }
            }
        }
    }

    @Test fun extensionsHomeAndSearchSpeed() = runBlocking<Unit> {
        val installed = ExtensionManager.state.value.installed.count { it.enabled }
        assertTrue("need at least 3 extensions, have $installed", installed >= 3)

        // 1. Extension relaunch load.
        ExtensionManager.unloadAllForSpeedTest()
        val t0 = System.currentTimeMillis()
        val loading = async { ExtensionManager.ensureLoaded(ctx) }
        withTimeout(120_000) { ExtensionManager.state.first { it.usable && ExtensionManager.providers().isNotEmpty() } }
        log("ext_first_usable", System.currentTimeMillis() - t0)
        withTimeout(240_000) { loading.await() }
        val loaded = ExtensionManager.state.value.runtime.values.count { it.loaded }
        log("ext_all_loaded", System.currentTimeMillis() - t0, "extensions=$loaded providers=${ExtensionManager.providers().size}")
        assertTrue("no extension loaded", loaded > 0)

        // 2. Home: first row, live, then from the saved copy.
        val homeApis = ExtensionManager.providers().filter { runCatching { it.hasMainPage }.getOrDefault(false) }.take(8)
        assertTrue("no extension has a home page", homeApis.isNotEmpty())
        val live = timeFirstRow(homeApis, force = true)
        log("home_first_row_live", live.first, "apis=${homeApis.size} answered=${live.second}")
        val cached = timeFirstRow(homeApis, force = false, cachedOnly = true)
        log("home_first_row_cached", cached.first, "answered=${cached.second}")

        // 3. Search: first result and all answered, live, then from the saved copy.
        val searchApis = ExtensionManager.providers().take(12)
        for (pass in listOf("live", "cached")) {
            val s0 = System.currentTimeMillis()
            var first = -1L; var answered = 0; var withItems = 0
            withTimeoutOrNull(90_000) {
                // One update per extension; take() ends the flow when every extension has answered.
                OnlineRepository.search(QUERY, searchApis).take(searchApis.size).collect { u ->
                    answered++
                    if (u.items.isNotEmpty()) { withItems++; if (first < 0) first = System.currentTimeMillis() - s0 }
                }
            }
            log("search_first_result_$pass", first, "apis=${searchApis.size}")
            log("search_all_answered_$pass", System.currentTimeMillis() - s0, "answered=$answered withItems=$withItems")
            assertTrue("search ($pass) got no result from ${searchApis.size} extensions", first >= 0)
        }

        // 4. FEED-ENGINE FE-RC1: the Yumix pattern. Every extension home through the same three priority slots, with
        //    first rows handed over (FE-3). Logs when the first extension shows, half of them, and all of them.
        val gate = dev.asfakul.yumeiro.online.PriorityGate(3)
        val feedApis = ExtensionManager.providers().filter { runCatching { it.hasMainPage }.getOrDefault(false) }
        val f0 = System.currentTimeMillis()
        val shownAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
        withTimeoutOrNull(600_000) {
            kotlinx.coroutines.coroutineScope {
                feedApis.map { api ->
                    async {
                        gate.run(api.name, dev.asfakul.yumeiro.online.PriorityGate.BACKGROUND) {
                            runCatching {
                                OnlineRepository.home(api, force = true) { rows -> if (rows.isNotEmpty()) shownAt.putIfAbsent(api.name, System.currentTimeMillis() - f0) }
                            }.getOrNull()
                        }
                    }
                }.awaitAll()
            }
        }
        val done = System.currentTimeMillis() - f0
        val times = shownAt.values.sorted()
        log("feed_first_extension_shown", times.firstOrNull() ?: -1, "apis=${feedApis.size} shown=${times.size}")
        log("feed_half_shown", times.getOrNull((feedApis.size - 1) / 2) ?: -1)
        log("feed_all_done", done, "shown=${times.size}")

        // 5. FE-7: YouTube and YouTube Music cold start (memory cache dropped; saved copy, then live).
        val yt = dev.asfakul.yumeiro.youtube.YtFeed(dev.asfakul.yumeiro.youtube.YtFeed.Kind.QUERY, "lofi music", "Lofi", "Test", music = false)
        val y0 = System.currentTimeMillis()
        val ytLive = withTimeoutOrNull(60_000) { runCatching { dev.asfakul.yumeiro.youtube.YouTube.feed(ctx, yt, force = true) }.getOrNull() }
        log("youtube_shelf_live", System.currentTimeMillis() - y0, "items=${ytLive?.items?.size ?: -1}")
        val y1 = System.currentTimeMillis()
        val ytSaved = dev.asfakul.yumeiro.youtube.YouTube.savedFeed(yt)
        log("youtube_shelf_saved", System.currentTimeMillis() - y1, "items=${ytSaved?.size ?: -1}")
        val m0 = System.currentTimeMillis()
        val ymLive = withTimeoutOrNull(60_000) { runCatching { dev.asfakul.yumeiro.youtube.music.YtMusicApi.home(ctx, force = true) }.getOrNull() }
        log("ytmusic_home_live", System.currentTimeMillis() - m0, "shelves=${ymLive?.shelves?.size ?: -1}")
        val m1 = System.currentTimeMillis()
        val ymSaved = dev.asfakul.yumeiro.youtube.music.YtMusicApi.savedBrowse(dev.asfakul.yumeiro.youtube.music.YmBrowse(dev.asfakul.yumeiro.youtube.music.YtMusicApi.HOME))
        log("ytmusic_home_saved", System.currentTimeMillis() - m1, "shelves=${ymSaved?.shelves?.size ?: -1}")
    }

    /** ms to the first non-empty home from [apis] (all in parallel, like Home), and how many answered with rows. */
    private suspend fun timeFirstRow(apis: List<MainAPI>, force: Boolean, cachedOnly: Boolean = false): Pair<Long, Int> {
        val t = System.currentTimeMillis()
        var first = -1L
        val got = kotlinx.coroutines.coroutineScope {
            apis.map { api ->
                async {
                    val rows = withTimeoutOrNull(60_000) {
                        runCatching { if (cachedOnly) OnlineRepository.cachedHome(api) else OnlineRepository.home(api, force) }.getOrNull()
                    }.orEmpty()
                    if (rows.isNotEmpty()) synchronized(this@SpeedDeviceTest) { if (first < 0) first = System.currentTimeMillis() - t }
                    rows.isNotEmpty()
                }
            }.awaitAll()
        }
        return first to got.count { it }
    }

    private companion object {
        const val WANT = 20
        const val QUERY = "love"
    }
}
