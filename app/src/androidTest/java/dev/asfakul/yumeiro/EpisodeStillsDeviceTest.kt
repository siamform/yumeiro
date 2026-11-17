/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.EpisodeStills
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * IG-5 (G11): episode thumbnails from TVmaze for a series whose extension sends none.
 * The shared client is swapped for one that plays TVmaze (slowly in case a, failing once in case b).
 *  a. The screen asking for the lookup goes away mid-lookup (its scope is cancelled, as when the user
 *     backs out of the detail page). Pass: the thumbnails still arrive. Before: dropped, and never
 *     tried again in this process.
 *  b. The first lookup fails (offline). Pass: the next visit looks up again and gets them. Before: the
 *     title was marked as tried and never looked up again.
 */
@RunWith(AndroidJUnit4::class)
class EpisodeStillsDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(m: String) = Log.i("YuStillsTest", m)
    private val field = NetworkClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private var original: Any? = null
    private val run = System.currentTimeMillis()
    private val episodeCalls = AtomicInteger()
    @Volatile private var failFirst = false
    @Volatile private var slowMs = 0L

    private val fake = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val url = chain.request().url
        val body = when {
            url.host != "api.tvmaze.com" -> null
            url.encodedPath == "/lookup/shows" -> """{"id":77,"name":"Yumeiro Test Show"}"""
            url.encodedPath == "/shows/77/episodes" -> {
                if (episodeCalls.incrementAndGet() == 1 && failFirst) throw IOException("test: offline")
                if (slowMs > 0) Thread.sleep(slowMs)
                """[{"season":1,"number":1,"image":{"medium":"https://static.tvmaze.com/e1.jpg"}},{"season":1,"number":2,"image":{"medium":"https://static.tvmaze.com/e2.jpg"}}]"""
            }
            else -> null
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (body != null) 200 else 404).message("x")
            .body((body ?: "").toResponseBody("application/json".toMediaType())).build()
    }).build()

    @Before fun setUp() {
        NetworkClient.get(ctx)
        original = field.get(NetworkClient)
        field.set(NetworkClient, fake)
    }

    @After fun tearDown() { field.set(NetworkClient, original) }

    private fun detail(case: String) = OnlineDetail(
        api = "yu-test-stills", url = "u-stills-$run-$case", name = "Yumeiro Test Show", kind = OnlineKind.SERIES, type = "TV",
        posterUrl = null, backgroundUrl = null, posterHeaders = emptyMap(), plot = null, year = null, tags = emptyList(),
        durationMin = null, score = null, contentRating = null, playData = null,
        episodes = mapOf("" to listOf(OnlineEpisode("e1", null, 1, 1, null, null, null), OnlineEpisode("e2", null, 1, 2, null, null, null))),
        seasonNames = emptyMap(), recommendations = emptyList(), imdbId = "tt0000077",
    )

    /** Calls ensure the way the screens do: from a scope that belongs to the screen. */
    private fun visit(d: OnlineDetail, leaveAfterMs: Long?) {
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        screen.launch { EpisodeStills.ensure(ctx, d) }
        if (leaveAfterMs != null) { Thread.sleep(leaveAfterMs); screen.cancel() }
    }

    private fun stillsFor(d: OnlineDetail, waitMs: Long): Map<String, String>? {
        val until = System.currentTimeMillis() + waitMs
        while (System.currentTimeMillis() < until) {
            EpisodeStills.stills.value[EpisodeStills.keyOf(d)]?.let { return it }
            Thread.sleep(100)
        }
        return EpisodeStills.stills.value[EpisodeStills.keyOf(d)]
    }

    @Test fun a_leavingTheScreenMidLookupKeepsTheResult() {
        slowMs = 1_500
        val d = detail("a")
        visit(d, leaveAfterMs = 300)
        val got = stillsFor(d, 8_000)
        log("STILLS_CASE a episodeCalls=${episodeCalls.get()} stills=$got")
        assertEquals("https://static.tvmaze.com/e1.jpg", got?.get("1:1"))
        log("STILLS_RESULT PASS a")
    }

    @Test fun b_anOfflineLookupIsTriedAgainOnTheNextVisit() {
        failFirst = true
        val d = detail("b")
        visit(d, leaveAfterMs = null)
        val first = stillsFor(d, 3_000)
        Thread.sleep(500)
        visit(d, leaveAfterMs = null)
        val second = stillsFor(d, 8_000)
        log("STILLS_CASE b episodeCalls=${episodeCalls.get()} first=$first second=$second")
        assertTrue("the first (offline) lookup gives nothing", first == null)
        assertEquals("the second visit looks up again", 2, episodeCalls.get())
        assertEquals("https://static.tvmaze.com/e2.jpg", second?.get("1:2"))
        log("STILLS_RESULT PASS b")
    }
}
