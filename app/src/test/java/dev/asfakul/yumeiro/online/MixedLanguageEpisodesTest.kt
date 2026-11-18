/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NBUG-5 (signal D, probe run 44203720877): some extensions put several dub languages in one DubStatus
 * list, each language as its own run ("Ep (Spanish)" 1..12, then "Ep (Dub)" 1..12). Yumeiro sorted the
 * whole list by number, which interleaved the runs: the next episode after Dub 1 was Spanish 1, a
 * Sub → Dub switch could land on the Spanish copy, and the grid showed 1, 1, 1, 2, 2, 2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MixedLanguageEpisodesTest {
    private fun ep(n: Int, lang: String, run: Int = 1) =
        OnlineEpisode("https://k/ep-$n-$run?lang=$lang", "Title $n ($lang)", null, n, null, null, null)

    private fun detail(sub: List<OnlineEpisode>, dub: List<OnlineEpisode>) = OnlineDetail(
        api = "Kaa", url = "https://k/show", name = "Show", kind = OnlineKind.ANIME, type = "Anime", posterUrl = null, backgroundUrl = null,
        posterHeaders = emptyMap(), plot = null, year = null, tags = emptyList(), durationMin = null, score = null, contentRating = null,
        playData = null, episodes = mapOf("Sub" to sub, "Dub" to dub), seasonNames = emptyMap(), recommendations = emptyList(),
    )

    // Solo Leveling shape: Spanish run first, English run second.
    private val sub = (1..12).map { OnlineEpisode("s$it", "Title $it", null, it, null, null, null) }
    private val solo = detail(sub, (1..12).map { ep(it, "Spanish") } + (1..12).map { ep(it, "Spanish", 2) } + (1..12).map { ep(it, "Dub") })

    @Test fun nextEpisodeStaysInTheSameLanguage() {
        val list = OnlineSession.episodesOf(solo, "Dub")
        assertEquals(36, list.size) // nothing is dropped
        fun tag(e: OnlineEpisode) = e.name!!.substringAfterLast('(').removeSuffix(")")
        // Inside a run every step is +1; a new run starts again at 1.
        list.zipWithNext().forEachIndexed { i, (a, b) ->
            if (b.episode != 1) { assertEquals("index $i", tag(a), tag(b)); assertEquals("index $i", a.episode!! + 1, b.episode) }
        }
        // No two neighbours share a number: the grid no longer reads 1, 1, 1, 2, 2, 2.
        assertTrue(list.zipWithNext().none { (a, b) -> a.episode == b.episode })
    }

    @Test fun runNamedLikeTheVersionComesFirst() {
        val list = OnlineSession.episodesOf(solo, "Dub")
        assertEquals((1..12).map { "Title $it (Dub)" }, list.take(12).map { it.name })
        // A Sub → Dub switch on episode 3 lands on the English dub, not a Spanish copy.
        val i = EpisodeMatcher.match(sub[2], list)
        assertEquals("Title 3 (Dub)", list[i].name)
    }

    @Test fun labelsAndCountName() {
        val labelled = OnlineSession.labelledEpisodes(solo, "Dub")
        assertEquals(listOf("Dub", "Spanish", "Spanish 2"), labelled.mapNotNull { it.second }.distinct())
        assertEquals(12, OnlineSession.distinctEpisodeCount(labelled.map { it.first }))
        // Header: "12 episodes · 3 versions", not "36 episodes".
        assertEquals("12 episodes · 3 versions", dev.asfakul.yumeiro.ui.screens.online.episodeCountText(labelled.map { it.first }, 3))
        assertEquals("12 episodes", dev.asfakul.yumeiro.ui.screens.online.episodeCountText(sub, 0))
    }

    @Test fun cleanListsAreUnchanged() {
        // One language (One Piece / Anichi shape) or no tags: plain number order as before, no labels.
        val clean = detail(sub, (1..12).reversed().map { ep(it, "Dub") })
        assertEquals((1..12).toList(), OnlineSession.episodesOf(clean, "Dub").map { it.episode })
        assertTrue(OnlineSession.labelledEpisodes(clean, "Dub").all { it.second == null })
        assertEquals((1..12).toList(), OnlineSession.episodesOf(clean, "Sub").map { it.episode })
    }
}
