/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.upcoming.Upcoming
import dev.asfakul.yumeiro.ui.screens.online.UpcomingScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * F-08 device check: the Upcoming page loads Simkl's public calendar on the device (live network, the
 * ~2.5 MB series file streamed), shows entries for Series, Movies and Anime, a tap hands the title to search,
 * and with the network gone the saved calendar still shows with the offline note. Heap growth while the
 * series file is parsed is logged (2 GB phone budget: the full file is never held).
 */
@RunWith(AndroidJUnit4::class)
class UpcomingDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("F08", s)
    private val field = NetworkClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private var original: Any? = null

    @After fun tearDown() { if (original != null) field.set(NetworkClient, original) }

    private fun simklUp(): Boolean = runCatching {
        OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(20)).build()
            .newCall(okhttp3.Request.Builder().url("https://data.simkl.in/calendar/anime.json").head().build()).execute().use { it.code < 500 }
    }.getOrDefault(false)

    private fun count(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun waitEntries(what: String) {
        val ok = runCatching { rule.waitUntil(90_000) { count("upcoming_entry") > 0 || count("upcoming_error") > 0 || count("upcoming_empty") > 0 }; true }.getOrDefault(false)
        assertTrue("$what: nothing after 90 s", ok)
        assertTrue("$what: error=${count("upcoming_error")} empty=${count("upcoming_empty")}", count("upcoming_entry") > 0)
    }

    @Test fun calendarLoadsSwitchesTapsAndWorksOffline() {
        assumeTrue("data.simkl.in is unreachable from this machine", simklUp())
        Upcoming.Kind.entries.forEach { File(ctx.cacheDir, "upcoming_${it.file}.json").delete() }
        var searched: String? = null
        val rt = Runtime.getRuntime(); System.gc()
        val heap0 = rt.totalMemory() - rt.freeMemory()
        val t0 = System.currentTimeMillis()
        rule.setContent { MTPlayerTheme { UpcomingScreen(onBack = {}, onSearch = { searched = it }) } }
        rule.waitUntil(5_000) { count("upcoming_kind_series") > 0 }
        rule.onAllNodesWithTag("upcoming_kind_series", true)[0].performClick()
        waitEntries("series")
        val heap1 = rt.totalMemory() - rt.freeMemory()
        log("F08_RESULT series ${System.currentTimeMillis() - t0}ms entries=${count("upcoming_entry")} heapDeltaMB=${(heap1 - heap0) / 1048576}")
        for (k in listOf("movies", "anime")) {
            val t = System.currentTimeMillis()
            rule.onAllNodesWithTag("upcoming_kind_$k", true)[0].performClick()
            rule.waitForIdle()
            waitEntries(k)
            log("F08_RESULT $k ${System.currentTimeMillis() - t}ms entries=${count("upcoming_entry")}")
        }
        rule.onAllNodesWithTag("upcoming_entry", true)[0].performClick()
        rule.waitUntil(5_000) { !searched.isNullOrBlank() }
        log("tap hands '$searched' to search")
        assertTrue("saved calendar missing", File(ctx.cacheDir, "upcoming_anime.json").length() > 0)

        // Offline: every request fails; the page keeps the saved calendar and says so.
        original = field.get(NetworkClient)
        field.set(NetworkClient, OkHttpClient.Builder().addInterceptor(Interceptor { throw java.io.IOException("offline (test)") }).build())
        rule.onAllNodesWithTag("upcoming_kind_series", true)[0].performClick()
        waitEntries("series cached")
        rule.onAllNodesWithTag("upcoming_kind_anime", true)[0].performClick()
        waitEntries("anime cached")
        // Force a refresh (cache is fresh, so a plain switch reads it): the retry path marks it stale.
        File(ctx.cacheDir, "upcoming_anime.json").setLastModified(System.currentTimeMillis() - 7 * 3600_000L)
        rule.onAllNodesWithTag("upcoming_kind_movies", true)[0].performClick(); waitEntries("movies cached")
        rule.onAllNodesWithTag("upcoming_kind_anime", true)[0].performClick()
        val stale = runCatching { rule.waitUntil(30_000) { count("upcoming_stale") > 0 }; true }.getOrDefault(false)
        assertTrue("offline: no 'showing the last saved calendar' note", stale)
        assertTrue("offline: entries gone", count("upcoming_entry") > 0)
        log("F08_RESULT PASS offline stale note shown")
    }
}
