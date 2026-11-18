/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.Connectivity
import dev.asfakul.yumeiro.youtube.music.YmItem
import dev.asfakul.yumeiro.youtube.music.YtMusicApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G6-PG1 on a device with real YouTube Music: a long playlist past page 1 with the emulator network
 * switched off while "load more" runs. The fix (f8564b8) keeps the continuation and retries; this
 * checks the premise on the live service: the same token, retried after the network is back, still
 * returns the next rows (so dropping it, as before, threw away a working page). Then pages 3+.
 *
 * If YouTube Music cannot be reached from the CI runner the test is skipped, never passed.
 * Logs `YuLibraryTest PG_…`.
 */
@RunWith(AndroidJUnit4::class)
class YmPagingNetworkDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuLibraryTest", s)
    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean { val t = SystemClock.elapsedRealtime(); while (!cond() && SystemClock.elapsedRealtime() - t < ms) Thread.sleep(250); return cond() }

    @After fun online() { FirstRun.shell("svc wifi enable"); FirstRun.shell("svc data enable") }

    @Test fun samePageTokenWorksAfterANetworkDrop() = runBlocking {
        Connectivity.start(ctx)
        val search = runCatching { YtMusicApi.search(ctx, "top 500 songs", YtMusicApi.Filter.PLAYLISTS) }
        log("PG_SEARCH ok=${search.isSuccess} err=${search.exceptionOrNull()?.javaClass?.simpleName}")
        assumeTrue("YouTube Music not reachable from this runner", search.isSuccess)
        val lists = search.getOrThrow().shelves.flatMap { it.items }.filterIsInstance<YmItem.Collection>().take(6)
        // First playlist whose page 1 has a continuation (more than one page).
        var picked: Pair<YmItem.Collection, dev.asfakul.yumeiro.youtube.music.YmPage>? = null
        for (c in lists) {
            val p = runCatching { YtMusicApi.browse(ctx, c.browse, force = true) }.getOrNull() ?: continue
            log("PG_CANDIDATE '${c.title}' songs=${p.shelves.sumOf { it.items.size }} more=${p.continuation != null}")
            if (p.continuation != null) { picked = c to p; break }
        }
        assumeTrue("no multi-page playlist found in ${lists.size} results", picked != null)
        val (list, page1) = picked!!
        val token = page1.continuation!!
        val rows1 = page1.shelves.sumOf { it.items.size }

        FirstRun.shell("svc wifi disable"); FirstRun.shell("svc data disable")
        val off = waitFor(20_000) { !Connectivity.online.value }
        val failed = runCatching { YtMusicApi.more(ctx, "browse", token) }
        log("PG_OFFLINE signalOff=$off moreFailed=${failed.isFailure} err=${failed.exceptionOrNull()?.javaClass?.simpleName}")

        FirstRun.shell("svc wifi enable"); FirstRun.shell("svc data enable")
        val back = waitFor(45_000) { Connectivity.online.value }
        // Same token, as the fixed screen retries after its backoff (1.5 s, then 3 s, then 6 s).
        var page2: dev.asfakul.yumeiro.youtube.music.YmPage? = null
        var tries = 0
        for (wait in listOf(1_500L, 3_000L, 6_000L, 12_000L)) {
            Thread.sleep(wait); tries++
            page2 = runCatching { YtMusicApi.more(ctx, "browse", token) }.getOrNull()
            if (page2 != null && page2.shelves.sumOf { it.items.size } > 0) break
        }
        val rows2 = page2?.shelves?.sumOf { it.items.size } ?: 0
        // Past page 2.
        var rows = rows1 + rows2; var next = page2?.continuation; var pages = if (rows2 > 0) 2 else 1
        while (next != null && pages < 5) {
            val p = runCatching { YtMusicApi.more(ctx, "browse", next!!) }.getOrNull() ?: break
            rows += p.shelves.sumOf { it.items.size }; next = p.continuation; pages++
        }
        val pass = failed.isFailure && rows2 > 0
        log("PG_RESULT ${if (pass) "PASS" else "FAIL"} list='${list.title}' page1=$rows1 offlineFailed=${failed.isFailure} back=$back retries=$tries page2=$rows2 pages=$pages rows=$rows")
        org.junit.Assert.assertTrue("load more did not fail with the network off (test precondition)", failed.isFailure)
        org.junit.Assert.assertTrue("the same token gave no rows after the network came back", rows2 > 0)
    }
}
