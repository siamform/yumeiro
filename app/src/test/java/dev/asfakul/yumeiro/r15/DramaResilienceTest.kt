/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import com.lagradost.cloudstream3.MainPageRequest
import dev.asfakul.yumeiro.sources.drama.DramaCatalog
import dev.asfakul.yumeiro.sources.drama.DramaHealth
import dev.asfakul.yumeiro.sources.drama.DramaPlatform
import dev.asfakul.yumeiro.sources.drama.DramaSeries
import dev.asfakul.yumeiro.sources.drama.DramaShow
import dev.asfakul.yumeiro.sources.drama.DramaStreams
import dev.asfakul.yumeiro.sources.drama.OnShortGate
import dev.asfakul.yumeiro.sources.drama.YumeiroDramaProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * R15 Phase 2, drama: ONShort answers bursts of HTTP 502 and hangs (measured live on 2026-11-14).
 * These pin the two rules that made dramas vanish / never play: a busy host is retried, and a
 * busy host never hides a studio.
 */
class DramaResilienceTest {
    private fun gate() = OnShortGate(minGapMs = 0, backoffMs = { 0L })

    @Test fun gateRetriesBusyAnswersAndTimeouts() = runBlocking {
        val n = AtomicInteger()
        val r = gate().call("u") {
            when (n.incrementAndGet()) {
                1 -> 502 to "<html>502</html>"
                2 -> throw SocketTimeoutException("timeout")
                else -> 200 to "{\"ok\":true}"
            }
        }
        assertEquals(200, r.first); assertEquals(3, n.get())
    }

    @Test fun gateReturnsRefusalsAtOnce() = runBlocking {
        val n = AtomicInteger()
        val r = gate().call("u") { n.incrementAndGet(); 403 to "no" }
        assertEquals(403, r.first); assertEquals(1, n.get())
    }

    @Test fun gateGivesUpWithTheLastAnswer() = runBlocking {
        val n = AtomicInteger()
        val r = gate().call("u") { n.incrementAndGet(); 502 to "busy" }
        assertEquals(502, r.first); assertEquals(4, n.get())
    }

    @Test fun gateNeverRunsMoreThanTwoAtOnce() = runBlocking {
        val g = OnShortGate(minGapMs = 0, backoffMs = { 0L })
        val live = AtomicInteger(); val peak = AtomicInteger()
        kotlinx.coroutines.coroutineScope {
            repeat(8) {
                launch(kotlinx.coroutines.Dispatchers.Default) {
                    g.call("u") { val c = live.incrementAndGet(); peak.accumulateAndGet(c, ::maxOf); kotlinx.coroutines.delay(30); live.decrementAndGet(); 200 to "" }
                }
            }
        }
        assertTrue("peak ${peak.get()}", peak.get() <= 2)
    }

    private class Busy(private val error: String) : DramaCatalog {
        override val id = "onshort"; override val label = "ONShort"
        override val platforms = listOf(DramaPlatform("busy", "Busy"))
        override suspend fun shelf(platform: DramaPlatform, page: Int) = listOf(DramaShow("A", "https://x/a/", null, 5, "Busy"))
        override suspend fun search(query: String) = emptyList<DramaShow>()
        override suspend fun series(url: String) = DramaSeries("A", url, null, null, "Busy", 5, null)
        override suspend fun streams(series: DramaSeries, episode: Int): DramaStreams = throw IOException(error)
    }

    @Test fun busyHostKeepsTheShelf() {
        for (e in listOf("Episode 1 answered HTTP 502", "Episode 1 answered HTTP 503", "Episode 1 answered HTTP 429")) {
            val p = YumeiroDramaProvider(listOf(Busy(e)), DramaHealth(reach = { true }))
            val r = runBlocking { p.getMainPage(1, MainPageRequest("Busy", "onshort|busy", false)) }
            assertEquals(e, 1, r.items.first().list.size)
        }
        // Every sample hung (a dead studio): hidden for a short while.
        val hung = YumeiroDramaProvider(listOf(Busy("timeout")), DramaHealth(reach = { true }))
        assertTrue(runBlocking { hung.getMainPage(1, MainPageRequest("Busy", "onshort|busy", false)) }.items.all { it.list.isEmpty() })
        // A real refusal still hides it.
        val p = YumeiroDramaProvider(listOf(Busy("Episode 1 answered HTTP 403")), DramaHealth(reach = { true }))
        val r = runBlocking { p.getMainPage(1, MainPageRequest("Busy", "onshort|busy", false)) }
        assertTrue(r.items.all { it.list.isEmpty() })
    }

    @Test fun refusedRule() {
        assertTrue(DramaHealth.refused(IOException("Episode 1 answered HTTP 404")))
        assertTrue(DramaHealth.refused(IOException("Episode 1 answered HTTP 500")))
        assertTrue(!DramaHealth.refused(IOException("Episode 1 answered HTTP 502")))
        assertTrue(!DramaHealth.refused(IOException("Episode 1 answered HTTP 504")))
        assertTrue(!DramaHealth.refused(IOException("Episode 1 answered HTTP 429")))
    }

    @Test fun episodeAnswerKeepsCandidatesAndExpiry() {
        val body = """{"ok":true,"url":"https://c/a.1080.mp4","quality":"1080","sources":{"1080":"https://c/a.1080.mp4","720":"https://c/a.720.mp4"},
            "candidates":[{"url":"https://c/a.1080.mp4","quality":"1080"},{"url":"https://m/b.1080.mp4","quality":"1080"},{"url":"https://m/b.540.mp4","quality":"540"}],
            "expires_at":1794675045}"""
        val st = dev.asfakul.yumeiro.sources.drama.OnShortParser.episode(body, "https://onshort.net/x/")!!
        assertEquals(setOf("1080", "720", "1080~1", "540"), st.sources.keys)
        assertEquals("https://m/b.1080.mp4", st.sources["1080~1"])
        assertEquals(1794675045L, st.expiresAt)
        assertEquals(1080, DramaStreams.qualityOf("1080~1"))
        assertTrue(dev.asfakul.yumeiro.sources.drama.OnShortParser.retryable("""{"ok":false,"message":"Media refresh failed","retryable":true}"""))
        assertTrue(!dev.asfakul.yumeiro.sources.drama.OnShortParser.retryable("""{"ok":false}"""))
    }

    @Test fun restRetriesWithForceAndAlternatesRoutes() = runBlocking {
        val calls = mutableListOf<String>()
        val page = """<html><div id="onshort-player" data-transport="rest-get" data-player-ticket="t1" data-player-endpoint="https://o/wp-json/ep"
            data-player-endpoint-alt="https://o/?rest_route=/ep" data-post="9" data-title="T"></div></html>"""
        val c = dev.asfakul.yumeiro.sources.drama.OnShortCatalog(base = "https://o", fetch = { u, _ ->
            calls += u
            when {
                !u.contains("ep") -> 200 to page
                u.startsWith("https://o/wp-json/ep") -> 502 to "<html>busy</html>"
                !u.contains("force=1") -> 424 to """{"ok":false,"retryable":true,"ticket":"t2"}"""
                else -> 200 to """{"ok":true,"url":"https://c/v.mp4","quality":"720"}"""
            }
        }, gate = OnShortGate(minGapMs = 0, backoffMs = { 0L }))
        val st = c.streams(c.series("https://o/t/"), 1)
        assertEquals("https://c/v.mp4", st.sources.values.first())
        assertTrue(calls.toString(), calls.any { it.contains("rest_route") && it.contains("force=1") })
        // The busy main route was tried once per pass, not retried four times.
        assertTrue(calls.toString(), calls.count { it.startsWith("https://o/wp-json/ep") } <= 2)
    }

    private fun res(name: String) = javaClass.classLoader!!.getResource("drama/$name")!!.readText()

    /** R15: a stalled shelf (ReelShort / DramaWave timed out on 2026-11-14) serves the last good one. */
    @Test fun stalledShelfServesTheLastGoodOne() = runBlocking {
        val html = res("platform.html")
        val n = AtomicInteger()
        var t = 1_000_000L
        val c = dev.asfakul.yumeiro.sources.drama.OnShortCatalog(
            fetch = { _, _ -> if (n.incrementAndGet() == 1) 200 to html else throw SocketTimeoutException("timeout") },
            gate = gate(), clock = { t },
        )
        val p = DramaPlatform("dramabox", "DramaBox")
        val first = c.shelf(p, 1)
        assertTrue(first.isNotEmpty())
        t += 60 * 60_000L // an hour later the shelf is stale and the site stalls
        assertEquals(first, c.shelf(p, 1))
        assertTrue(n.get() > 1)
        // No cached copy: the stall is reported, not hidden.
        assertTrue(runCatching { c.shelf(p, 2) }.isFailure)
    }

    /** R15: health probes are a background service; two quick checks must not crash ("Recursive update"). */
    @Test fun instantProbeDoesNotBreakTheRegistry() = runBlocking {
        val h = DramaHealth(reach = { true })
        val c = Busy("Episode 1 answered HTTP 403")
        repeat(3) { assertEquals(DramaHealth.State.DOWN, h.check(c, c.platforms.first(), c.shelf(c.platforms.first(), 1))) }
    }

    /** R15: our own probe budget running out (calls queued behind other studios) never hides a studio. */
    @Test fun probeBudgetIsInconclusive() = runBlocking {
        // Nothing answered at all within the budget (not even the series page): our queue, not the studio.
        val slow = object : DramaCatalog by Busy("x") {
            override suspend fun series(url: String): DramaSeries { kotlinx.coroutines.delay(5_000); error("unreachable") }
        }
        val h = DramaHealth(reach = { true }, probeMs = 100)
        assertEquals(DramaHealth.State.UNKNOWN, h.check(slow, slow.platforms.first(), slow.shelf(slow.platforms.first(), 1)))
    }

    /**
     * FINAL-20: the same, many times at once on a busy pool, where the probe's own timeout can race the
     * block's return (CI 44643496869 saw DOWN once). Every verdict must stay UNKNOWN.
     */
    @Test fun probeBudgetIsInconclusiveUnderLoad() = runBlocking {
        val verdicts = (1..200).map {
            async(kotlinx.coroutines.Dispatchers.Default) {
                val slow = object : DramaCatalog by Busy("x$it") {
                    override suspend fun series(url: String): DramaSeries {
                        // busy-spin a little before suspending, so the timeout often lands while the block runs
                        val until = System.nanoTime() + 2_000_000L * (it % 7)
                        while (System.nanoTime() < until) { /* spin */ }
                        kotlinx.coroutines.delay(5_000); error("unreachable")
                    }
                }
                DramaHealth(reach = { true }, probeMs = 5L + it % 9).check(slow, slow.platforms.first(), slow.shelf(slow.platforms.first(), 1))
            }
        }.map { it.await() }
        val wrong = verdicts.withIndex().filter { it.value != DramaHealth.State.UNKNOWN }
        assertTrue("probe budget read as a verdict in ${wrong.size} of 200: ${wrong.take(5)}", wrong.isEmpty())
    }

    /** R15: timeouts don't mark the host busy (a hung studio must not look like a busy host). */
    @Test fun timeoutsDoNotMarkTheHostBusy() = runBlocking {
        val g = gate()
        runCatching { g.call("u") { throw SocketTimeoutException("timeout") } }
        assertTrue(!g.recentlyBusy())
        g.call("u") { 200 to "" }
        runCatching { g.call("u", tries = 1) { 502 to "" } }
        assertTrue(g.recentlyBusy())
    }

    /** R15: series page answers, episode 1 hangs for the whole budget (ShortMax): hidden. */
    @Test fun studioThatHangsOnEpisodesIsHidden() = runBlocking {
        val hangs = object : DramaCatalog by Busy("x") {
            override suspend fun streams(series: DramaSeries, episode: Int): DramaStreams { kotlinx.coroutines.delay(5_000); error("unreachable") }
        }
        val p = YumeiroDramaProvider(listOf(hangs), DramaHealth(reach = { true }, probeMs = 100))
        assertTrue(runBlocking { p.getMainPage(1, MainPageRequest("Busy", "onshort|busy", false)) }.items.all { it.list.isEmpty() })
    }

    /** R15: a stalled series page doesn't fail the detail screen; it is built from the shelf card. */
    @Test fun stalledSeriesPageUsesTheShelfCard() = runBlocking {
        val stalls = object : DramaCatalog by Busy("x") {
            override suspend fun series(url: String): DramaSeries { kotlinx.coroutines.delay(60_000); error("unreachable") }
        }
        val p = YumeiroDramaProvider(listOf(stalls), DramaHealth(reach = { true }, probeMs = 100))
        val shelf = p.getMainPage(1, MainPageRequest("Busy", "onshort|busy", false)).items.first().list
        val t0 = System.currentTimeMillis()
        val d = p.load(shelf.first().url) as com.lagradost.cloudstream3.TvSeriesLoadResponse
        assertTrue(System.currentTimeMillis() - t0 < YumeiroDramaProvider.SERIES_WAIT_MS + 3_000)
        assertEquals("A", d.name); assertEquals(5, d.episodes.size)
    }

    /** R15: a hung episode answer is retried inside the links budget instead of failing the tap. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun hungEpisodeIsRetriedWithinTheBudget() = kotlinx.coroutines.test.runTest {
        val calls = AtomicInteger()
        val flaky = object : DramaCatalog by Busy("x") {
            override suspend fun streams(series: DramaSeries, episode: Int): DramaStreams {
                if (calls.incrementAndGet() == 1) kotlinx.coroutines.delay(120_000)
                return DramaStreams(mapOf("720" to "https://cdn/x.m3u8"), emptyList(), "https://x/a/")
            }
        }
        val p = YumeiroDramaProvider(listOf(flaky), DramaHealth(reach = { true }), clock = { testScheduler.currentTime })
        val links = ArrayList<String>()
        val ok = p.loadLinks("drama:onshort|https://x/a/|1", false, {}) { links += it.url }
        assertTrue(ok)
        assertEquals(listOf("https://cdn/x.m3u8"), links)
        assertEquals(2, calls.get())
        assertTrue("stays inside OnlineRepository's 75 s", testScheduler.currentTime < 75_000)
    }

    /** R15: a studio that refuses is not retried (the answer won't change), it fails fast. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun refusedEpisodeIsNotRetried() = kotlinx.coroutines.test.runTest {
        val calls = AtomicInteger()
        val refuses = object : DramaCatalog by Busy("x") {
            override suspend fun streams(series: DramaSeries, episode: Int): DramaStreams { calls.incrementAndGet(); throw IOException("Episode 1 answered HTTP 424") }
        }
        val p = YumeiroDramaProvider(listOf(refuses), DramaHealth(reach = { true }), clock = { testScheduler.currentTime })
        val r = runCatching { p.loadLinks("drama:onshort|https://x/a/|1", false, {}) {} }
        assertTrue(r.exceptionOrNull()?.message.orEmpty().contains("HTTP 424"))
        assertEquals(1, calls.get())
    }
}
