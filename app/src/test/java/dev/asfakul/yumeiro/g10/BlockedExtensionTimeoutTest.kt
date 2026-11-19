/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g10

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.mainPageOf
import dev.asfakul.yumeiro.online.OnlineRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G10 ER-30 (owner report NBUG-8: "opening one extension's content stays in a loading state, or takes
 * far too long, for a single extension").
 * OnlineRepository bounds every extension call with `withTimeout`, but a coroutine timeout only fires
 * when the code inside suspends. An extension that blocks its thread (a blocking Jsoup/OkHttp call,
 * `runBlocking`, `Thread.sleep`) keeps the call running past the timeout, and leaving the page (which
 * cancels the load) waits for it too. kotlinx.coroutines `Timeout.kt` KDoc: "the code inside the block
 * must be cancellable". CloudStream master 2dcc25c `ui/APIRepository.kt` 84–155 has the same plain
 * `withTimeout`.
 * Here the fake blocks for 8 s and the caller gives up after 1.5 s. Old code: the call returns after
 * ~8 s. Fixed: it returns at once when the caller (or the timeout) cancels it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockedExtensionTimeoutTest {
    private class Blocking(override var name: String) : MainAPI() {
        override var mainUrl = "https://example.invalid"
        override val hasMainPage = true
        override val mainPage = mainPageOf("a" to "Popular")
        override suspend fun load(url: String): LoadResponse? { Thread.sleep(BLOCK_MS); return null }
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? { Thread.sleep(BLOCK_MS); return null }
    }

    private fun elapsedMs(call: suspend () -> Unit): Long {
        val t0 = System.nanoTime()
        runCatching { runBlocking { withTimeoutOrNull(GIVE_UP_MS) { call() } } }
        return (System.nanoTime() - t0) / 1_000_000
    }

    @Test fun detailsOfABlockedExtensionStopWhenTheCallerGivesUp() {
        val ms = elapsedMs { OnlineRepository.load(Blocking("YuER30 load"), "https://example.invalid/t", force = true) }
        assertTrue("load() returned after $ms ms; must stop within ${LIMIT_MS} ms of being cancelled", ms < LIMIT_MS)
    }

    @Test fun homeOfABlockedExtensionStopsWhenTheCallerGivesUp() {
        val ms = elapsedMs { OnlineRepository.home(Blocking("YuER30 home"), force = true) }
        assertTrue("home() returned after $ms ms; must stop within ${LIMIT_MS} ms of being cancelled", ms < LIMIT_MS)
    }

    private companion object {
        const val BLOCK_MS = 8_000L
        const val GIVE_UP_MS = 1_500L
        const val LIMIT_MS = 4_000L
    }
}
