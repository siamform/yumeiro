/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anticipation

import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineSubtitle
import dev.asfakul.yumeiro.online.OtherSources
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B23 ADR-B23-2: own extension first, other providers next, Black Source last; misses only when every source answered. */
class AnticipatorPrepareTest {
    private val ep2 = OnlineEpisode("e2", null, 1, 2, null, null, null)
    private val detail = OnlineDetail(
        api = "Own", url = "https://own/show", name = "Show", kind = OnlineKind.ANIME, type = "Anime", posterUrl = null, backgroundUrl = null,
        posterHeaders = emptyMap(), plot = null, year = 2024, tags = emptyList(), durationMin = null, score = null, contentRating = null,
        playData = null, episodes = mapOf("" to listOf(OnlineEpisode("e1", null, 1, 1, null, null, null), ep2)), seasonNames = emptyMap(), recommendations = emptyList(),
    )
    private val target = Anticipator.Target("Own", detail, "", ep2, preferName = "B")

    private fun link(url: String, name: String = "A") = OnlineLink(name, name, url, null, 1080, false, false, emptyMap())

    private class Fake(
        val own: Flow<LinkEvent>? = emptyFlow(),
        val others: Flow<OtherSources.Hit> = emptyFlow(),
        val black: Flow<OtherSources.Hit> = emptyFlow(),
        val dead: Set<String> = emptySet(),
    ) : Anticipator.Sources {
        var othersAsked = 0; var blackAsked = 0
        val probed = mutableListOf<String>()
        override fun own(t: Anticipator.Target) = own
        override fun others(t: Anticipator.Target, memo: List<PrepData.Match>): Flow<OtherSources.Hit> { othersAsked++; return others }
        override fun black(t: Anticipator.Target): Flow<OtherSources.Hit> { blackAsked++; return black }
        override suspend fun alive(link: OnlineLink): Boolean { probed += link.url; return link.url !in dead }
    }

    private fun ownDone(vararg links: OnlineLink, error: String? = null) =
        flowOf(*links.map { LinkEvent.Link(it) }.toTypedArray<LinkEvent>(), LinkEvent.Done(error == null, error))

    @Test fun theOwnExtensionIsEnoughAndTheOthersAreNotAsked() = runTest {
        val src = Fake(own = flow {
            emit(LinkEvent.Link(link("https/a.mp4", "A")))
            emit(LinkEvent.Subtitle(OnlineSubtitle("English", "https/en.vtt", emptyMap())))
            emit(LinkEvent.Link(link("https/b.mp4", "B")))
            emit(LinkEvent.Done(true))
            awaitCancellation() // the extension never closes its flow: Done must be enough
        })
        val r = Anticipator.prepare(target, src, emptyList(), { _, _ -> }, now = { 42L }) as Anticipator.Outcome.Ready
        assertTrue(r.prepared.ownHad)
        assertNull(r.prepared.via)
        assertEquals("the server playing now comes first", listOf("https/b.mp4", "https/a.mp4"), r.prepared.links.map { it.url })
        assertEquals(listOf("English"), r.prepared.subtitles.map { it.language })
        assertEquals(target.key, r.prepared.key)
        assertEquals(42L, r.prepared.at)
        assertEquals(0, src.othersAsked)
        assertEquals(0, src.blackAsked)
    }

    @Test fun deadLinksAreDroppedAndAllDeadFallsThrough() = runTest {
        val dropOne = Fake(own = ownDone(link("https/dead.mp4"), link("https/ok.mp4")), dead = setOf("https/dead.mp4"))
        val r = Anticipator.prepare(target.copy(preferName = null), dropOne, emptyList(), { _, _ -> }) as Anticipator.Outcome.Ready
        assertEquals(listOf("https/ok.mp4"), r.prepared.links.map { it.url })

        val allDead = Fake(own = ownDone(link("https/dead.mp4")), dead = setOf("https/dead.mp4"))
        val f = Anticipator.prepare(target, allDead, emptyList(), { _, _ -> })
        assertTrue("$f", f is Anticipator.Outcome.Failed)
        assertEquals("dead own links send it to the other providers", 1, allDead.othersAsked)
    }

    @Test fun anotherProviderFillsTheGapAndItsMatchIsRemembered() = runTest {
        val matches = mutableListOf<Pair<String, String>>()
        val src = Fake(
            own = ownDone(),
            others = flowOf(OtherSources.Hit.Matched("Other", "Show", "https://other/show"), OtherSources.Hit.Event("Other", LinkEvent.Link(link("https/o2.mp4")))),
        )
        val r = Anticipator.prepare(target, src, emptyList(), { a, u -> matches += a to u }) as Anticipator.Outcome.Ready
        assertFalse(r.prepared.ownHad)
        assertEquals("Other", r.prepared.via)
        assertEquals(listOf("Other"), r.prepared.others)
        assertEquals(listOf("Other" to "https://other/show"), matches)
        assertEquals("Black Source is only the last resort", 0, src.blackAsked)
    }

    @Test fun blackSourceIsAskedLast() = runTest {
        val src = Fake(own = ownDone(), black = flowOf(OtherSources.Hit.Event("Black", LinkEvent.Link(link("https/bl.mp4")))))
        val r = Anticipator.prepare(target, src, emptyList(), { _, _ -> }) as Anticipator.Outcome.Ready
        assertEquals("Black", r.prepared.via)
        assertEquals(1, src.othersAsked)
        assertEquals(1, src.blackAsked)
    }

    @Test fun aMissOnlyWhenEverySourceAnsweredCleanly() = runTest {
        assertEquals(Anticipator.Outcome.Miss, Anticipator.prepare(target, Fake(own = ownDone()), emptyList(), { _, _ -> }))
        val failed = Anticipator.prepare(target, Fake(own = ownDone(error = "HTTP 503")), emptyList(), { _, _ -> })
        assertTrue("an error is not a miss: $failed", failed is Anticipator.Outcome.Failed)
        val missing = Anticipator.prepare(target, Fake(own = null), emptyList(), { _, _ -> })
        assertTrue("$missing", missing is Anticipator.Outcome.Failed)
    }

    @Test fun aSlowExtensionTimesOutInsteadOfHanging() = runTest {
        val slow = Fake(own = flow { awaitCancellation() })
        val r = Anticipator.prepare(target, slow, emptyList(), { _, _ -> })
        assertTrue("$r", r is Anticipator.Outcome.Failed)
        val slowOthers = Fake(own = ownDone(), others = flow { awaitCancellation() })
        val r2 = Anticipator.prepare(target, slowOthers, emptyList(), { _, _ -> })
        assertTrue("a timed-out search is not a miss: $r2", r2 is Anticipator.Outcome.Failed)
    }

    @Test fun onlyTheFirstThreeLinksAreProbed() = runTest {
        val links = (1..6).map { link("https/l$it.mp4") }.toTypedArray()
        val src = Fake(own = ownDone(*links), dead = setOf("https/l1.mp4", "https/l2.mp4", "https/l3.mp4", "https/l4.mp4"))
        val r = Anticipator.prepare(target.copy(preferName = null), src, emptyList(), { _, _ -> }) as Anticipator.Outcome.Ready
        assertEquals(listOf("https/l1.mp4", "https/l2.mp4", "https/l3.mp4"), src.probed)
        assertEquals(listOf("https/l4.mp4", "https/l5.mp4", "https/l6.mp4"), r.prepared.links.map { it.url })
    }

    @Test fun takeHandsOutAFreshPreparationOnce() {
        val key = Anticipator.episodeKey("Own", "https://own/show", "", "e2")
        Anticipator.clear()
        Anticipator.putForTest(Anticipator.Prepared(key, listOf(link("https/a.mp4")), emptyList(), emptyList(), ownHad = true, at = 1_000))
        assertNull("stale", Anticipator.take("Own", "https://own/show", "", "e2", now = 1_000 + AnticipationPolicy.FRESH_MS))
        Anticipator.putForTest(Anticipator.Prepared(key, listOf(link("https/a.mp4")), emptyList(), emptyList(), ownHad = true, at = 1_000))
        assertTrue(Anticipator.isReady("Own", "https://own/show", "", "e2", now = 2_000))
        assertEquals(key, Anticipator.take("Own", "https://own/show", "", "e2", now = 2_000)?.key)
        assertNull("no duplicate use", Anticipator.take("Own", "https://own/show", "", "e2", now = 2_000))
    }
}
