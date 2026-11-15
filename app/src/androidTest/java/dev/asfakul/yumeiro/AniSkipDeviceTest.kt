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
import dev.asfakul.yumeiro.online.anime.AniSkip
import okhttp3.Interceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * G3 P15 / PL-14 on a device: a lookup made with the network off must not be cached as "no
 * segments". The phone really goes offline (Wi-Fi and mobile data off), the app's own HTTP client
 * is used against the live api.aniskip.com, and every request that leaves the app is counted.
 */
@RunWith(AndroidJUnit4::class)
class AniSkipDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuAniSkip", s)

    @After fun online() { FirstRun.shell("svc wifi enable"); FirstRun.shell("svc data enable") }

    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(500) }
        return cond()
    }

    /** True when a plain request to the AniSkip host gets any HTTP answer. */
    private fun reachable(): Boolean = runCatching {
        NetworkClient.get(ctx).newBuilder().callTimeout(java.time.Duration.ofSeconds(6)).build()
            .newCall(okhttp3.Request.Builder().url("https://api.aniskip.com/").head().build()).execute().use { true }
    }.getOrDefault(false)

    @Test fun offlineFailureIsNotCachedAndIsRetriedOnline() {
        val calls = AtomicInteger()
        val client = NetworkClient.get(ctx).newBuilder()
            .addInterceptor(Interceptor { chain -> calls.incrementAndGet(); chain.proceed(chain.request()) })
            .callTimeout(java.time.Duration.ofSeconds(15)).build()
        // Frieren episode 1 (MAL 52991); a fresh process has nothing cached for it.
        val mal = 52991; val ep = 1

        FirstRun.shell("svc wifi disable"); FirstRun.shell("svc data disable")
        assertTrue("network did not go offline", waitFor(20_000) { !reachable() })
        val offline = AniSkip.segments(client, mal, ep, 24 * 60_000L)
        log("offline: ${offline.size} segments, calls=${calls.get()}")
        assertEquals("no segments while offline", 0, offline.size)
        assertEquals("one attempt while offline", 1, calls.get())

        online()
        assertTrue("network did not come back", waitFor(45_000) { reachable() })
        val back = AniSkip.segments(client, mal, ep, 24 * 60_000L)
        log("online: ${back.size} segments ${back.map { it.kind }}, calls=${calls.get()}")
        assertEquals("the offline failure was not cached: a new request was made", 2, calls.get())

        val again = AniSkip.segments(client, mal, ep, 24 * 60_000L)
        log("again: ${again.size} segments, calls=${calls.get()}")
        // The online answer (segments, or a 404 meaning none) is cached: no third request.
        assertEquals("a real answer is cached", 2, calls.get())
        assertEquals(back, again)
        log("ANISKIP_RESULT PASS segments=${back.size}")
    }
}
