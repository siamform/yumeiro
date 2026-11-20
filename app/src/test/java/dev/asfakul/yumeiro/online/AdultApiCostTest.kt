/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import dev.asfakul.yumeiro.sources.SourceHub
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FREEZE-1 (2.0.0 freeze report, Xiaomi API 33, "Input dispatching timed out"): Home and the online
 * screens filter 18+ extensions while composing, calling [AdultContent.isAdultApi] once per provider.
 * Each call rebuilt the whole provider list, so N providers cost about N² provider reads on the main
 * thread, again on every extension-load step. The answer must come from one pass per extension change,
 * and must still change when a provider is added.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdultApiCostTest {
    private var reads = 0

    private fun api(n: String, types: Set<TvType>) = object : MainAPI() {
        override var name: String
            get() { reads++; return n }
            set(_) {}
        override val supportedTypes: Set<TvType> = types
    }

    @After fun tearDown() { SourceHub.remove("yu_freeze1_a"); SourceHub.remove("yu_freeze1_b") }

    @Test fun manyProvidersAreCheckedInOnePass() {
        val n = 200
        val apis = (0 until n).map { api("YuF1 $it", if (it == 7) setOf(TvType.NSFW) else setOf(TvType.Movie)) }
        SourceHub.publishVideo("yu_freeze1_a", SourceHub.Ecosystem.JAVASCRIPT, apis)
        reads = 0
        val adult = (0 until n).filter { AdultContent.isAdultApi("YuF1 $it") }
        assertEquals(listOf(7), adult)
        // One pass is about 2N name reads; the old per-call rebuild read about 1.5 N² (60 000 here).
        assertTrue("FREEZE-1: $reads provider name reads for $n checks (one pass ≈ ${2 * n})", reads < 10 * n)
    }

    @Test fun answerFollowsANewProvider() {
        SourceHub.publishVideo("yu_freeze1_a", SourceHub.Ecosystem.JAVASCRIPT, listOf(api("YuF1 plain", setOf(TvType.Movie))))
        assertFalse(AdultContent.isAdultApi("YuF1 late"))
        SourceHub.publishVideo("yu_freeze1_b", SourceHub.Ecosystem.JAVASCRIPT, listOf(api("YuF1 late", setOf(TvType.NSFW))))
        assertTrue("a provider published after the first check must be seen", AdultContent.isAdultApi("YuF1 late"))
        assertEquals(true, AdultContent.loadedAdultApis()["YuF1 late"])
        assertEquals(false, AdultContent.loadedAdultApis()["YuF1 plain"])
    }
}
