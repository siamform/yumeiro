/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Build
import android.os.StrictMode
import android.os.SystemClock
import android.os.strictmode.Violation
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import dev.asfakul.yumeiro.cache.DataCache
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineSettings
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * G2 E12 (re-audit) and ST-22. A fake extension answers every search with one normal and one
 * adult (TvType.NSFW) result:
 *  - adult content off → only the normal one;
 *  - switched on (on the main thread, as the Settings switch does) → both, not the cached answer;
 *  - switched off again → the adult result is gone, even though it was saved on disk.
 * The page cache holds 300 saved pages when the switch is flipped. Before ST-22 the switch deleted
 * that whole folder on the main thread; the StrictMode listener counts those disk calls.
 * Log tag YuAdultTest, E12_CASE / E12_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class AdultSearchFilterDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext.applicationContext
    private val before = OnlineSettings.adult.value
    private fun log(s: String) = Log.i("YuAdultTest", s)

    private class Fake : MainAPI() {
        override var name = "YuE12 fake"
        override var mainUrl = "https://example.invalid"
        override val supportedTypes = setOf(TvType.Movie, TvType.NSFW)
        var calls = 0
        override suspend fun search(query: String): List<SearchResponse> {
            calls++
            return listOf(
                newMovieSearchResponse("Yu Safe Film", "$mainUrl/safe", TvType.Movie),
                newMovieSearchResponse("Yu Adult Film", "$mainUrl/adult", TvType.NSFW),
            )
        }
    }

    private fun names(api: MainAPI, q: String): List<String> =
        runBlocking { OnlineRepository.search(q, listOf(api)).toList() }.flatMap { u -> u.items.map { it.name } }.sorted()

    /** Runs [block] on the main thread and returns the cache's main-thread disk calls and the time taken. */
    private fun onMain(block: () -> Unit): Pair<List<String>, Long> {
        val seen = Collections.synchronizedList(mutableListOf<String>())
        var ms = 0L
        inst.runOnMainSync {
            val old = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .penaltyListener({ it.run() }) { v: Violation -> seen += Log.getStackTraceString(v) }.build())
            val t = SystemClock.elapsedRealtime()
            try { block() } finally { ms = SystemClock.elapsedRealtime() - t; StrictMode.setThreadPolicy(old) }
        }
        return synchronized(seen) { seen.filter { "dev.asfakul.yumeiro.cache.DataCache" in it } } to ms
    }

    @After fun tearDown() {
        OnlineSettings.setAdult(ctx, before)
    }

    @Test fun adultResultsFollowTheSwitchAndTheSwitchDoesNoDiskWorkOnMain() {
        assumeTrue("penaltyListener needs API 28", Build.VERSION.SDK_INT >= 28)
        DataCache.init(ctx)
        OnlineSettings.setAdult(ctx, false) // first call loads the prefs here, not on main
        SystemClock.sleep(500)
        val page = "p".repeat(4096)
        repeat(300) { DataCache.write("e12fill", "k$it", page) }
        val api = Fake()
        val q = "yu e12 " + System.currentTimeMillis()

        val off = names(api, q)
        log("E12_CASE a_off results=$off calls=${api.calls}")
        assertEquals(listOf("Yu Safe Film"), off)

        val (onCalls, onMs) = onMain { OnlineSettings.setAdult(ctx, true) }
        val on = names(api, q)
        log("E12_CASE b_on results=$on calls=${api.calls} switchMs=$onMs mainDiskCalls=${onCalls.size}")
        onCalls.take(2).forEach { log("E12_CASE b_on stack: " + it.lines().take(14).joinToString(" < ")) }
        assertEquals("adult result missing when allowed (stale cache?)", listOf("Yu Adult Film", "Yu Safe Film"), on)

        val (offCalls, offMs) = onMain { OnlineSettings.setAdult(ctx, false) }
        val offAgain = names(api, q)
        log("E12_CASE c_off_again results=$offAgain calls=${api.calls} switchMs=$offMs mainDiskCalls=${offCalls.size}")
        assertEquals("adult result shown after switching off", listOf("Yu Safe Film"), offAgain)

        assertTrue("the switch did ${onCalls.size + offCalls.size} disk calls on the main thread:\n" +
            (onCalls + offCalls).firstOrNull().orEmpty().take(2000), onCalls.isEmpty() && offCalls.isEmpty())
        DataCache.awaitClearForTests()
        val left = (0 until 300).count { DataCache.fileFor("e12fill", "k$it")?.exists() == true }
        log("E12_CASE d_files_left=$left")
        assertEquals("old cache files not deleted in the background", 0, left)
        log("E12_RESULT PASS")
    }
}
