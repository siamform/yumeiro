/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.online

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.mtrevanced.ui.screens.online.relativeAge
import dev.asfakul.mtrevanced.ui.screens.online.topPicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlineFeaturesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setUp() {
        java.io.File(context.filesDir, "online_history.json").delete()
        OnlineHistory.resetForTests()
    }

    private val indexV2 = """
        {"version":2,"generated":"2026-11-01T00:00:00Z",
         "repositories":[
           {"url":"https://a.example/repo.json","name":"Alpha","stars":120,"count":2,"origin":"community","github":"alpha/ext","pushed":"2026-10-30T00:00:00Z"},
           {"url":"https://b.example/repo.json","name":"Beta","count":1}
         ],
         "extensions":[
           {"internalName":"AnimeOne","name":"Anime One","url":"https://a.example/AnimeOne.cs3","version":5,"apiVersion":1,"status":1,
            "tvTypes":["Anime","OVA"],"repoUrl":"https://a.example/repo.json","score":9.5,"rank":1,"primary":true,"copies":3,"categories":["Anime"]},
           {"internalName":"AnimeOne","name":"Anime One","url":"https://b.example/AnimeOne.cs3","version":4,"tvTypes":["Anime"],
            "repoUrl":"https://b.example/repo.json","score":3.0,"rank":40,"primary":false},
           {"internalName":"MovieDown","name":"Movie Down","url":"https://a.example/MovieDown.cs3","version":1,"status":0,
            "tvTypes":["Movie"],"repoUrl":"https://a.example/repo.json","score":8.0,"rank":2},
           {"internalName":"Bad name!","url":"https://a.example/x.cs3","repoUrl":"https://a.example/repo.json"},
           {"internalName":"Plain","url":"http://insecure/x.cs3","repoUrl":"https://a.example/repo.json"}
         ]}
    """.trimIndent()

    @Test fun parsesMarketV2Fields() {
        val c = ExtensionMarket.parse(indexV2)
        assertEquals(2, c.repos.size)
        val alpha = c.repos.first { it.name == "Alpha" }
        assertEquals("community", alpha.origin)
        assertFalse("community repos are not verified by default", alpha.verified)
        assertEquals("alpha/ext", alpha.github)
        assertTrue(alpha.pushed > 0)
        val beta = c.repos.first { it.name == "Beta" }
        assertEquals("curated", beta.origin)
        assertTrue(beta.verified)

        // Invalid names and non-https files are dropped.
        assertEquals(3, c.extensions.size)
        val best = c.extensions.first { it.repoUrl.startsWith("https://a.") && it.internalName == "AnimeOne" }
        assertEquals(9.5, best.score, 0.0001)
        assertEquals(1, best.rank)
        assertTrue(best.primary)
        assertEquals(3, best.copies)
        assertEquals(listOf("Anime"), best.categories)
        assertEquals("community", best.origin)
        assertEquals(120, best.stars)
        val copy = c.extensions.first { it.repoUrl.startsWith("https://b.") }
        assertFalse(copy.primary)
        // Categories fall back to tvTypes when the index has none.
        assertTrue("Movies" in c.extensions.first { it.internalName == "MovieDown" }.categories)
    }

    @Test fun topPicksSkipsDuplicatesAndBrokenExtensions() {
        val c = ExtensionMarket.parse(indexV2)
        val anime = topPicks(c.extensions, "Anime", 10)
        assertEquals(1, anime.size)
        assertEquals("https://a.example/AnimeOne.cs3", anime.single().url)
        assertTrue("down extensions are never suggested", topPicks(c.extensions, "Movies", 10).isEmpty())
    }

    @Test fun relativeAgeIsShort() {
        val now = 10_000_000_000L
        assertEquals("1m ago", relativeAge(now, now))
        assertEquals("5h ago", relativeAge(now - 5 * 3_600_000L, now))
        assertEquals("3d ago", relativeAge(now - 3 * 86_400_000L, now))
        assertEquals("2mo ago", relativeAge(now - 61 * 86_400_000L, now))
        assertEquals("1y ago", relativeAge(now - 400 * 86_400_000L, now))
    }

    private fun ep(data: String, s: Int?, e: Int?, name: String? = null) = OnlineEpisode(data, name, s, e, null, null, null)

    private fun series(eps: Map<String, List<OnlineEpisode>>) = OnlineDetail(
        api = "Prov", url = "https://p/show", name = "Show", kind = OnlineKind.ANIME, type = "Anime", posterUrl = null, backgroundUrl = null,
        posterHeaders = emptyMap(), plot = null, year = null, tags = emptyList(), durationMin = null, score = null, contentRating = null,
        playData = null, episodes = eps, seasonNames = emptyMap(), recommendations = emptyList(),
    )

    @Test fun episodesAreInWatchOrderAndTitled() {
        val d = series(mapOf("Sub" to listOf(ep("s2e1", 2, 1), ep("s1e2", 1, 2), ep("s1e1", 1, 1, "Pilot")), "Dub" to listOf(ep("d1", 1, 1))))
        assertEquals(listOf("s1e1", "s1e2", "s2e1"), OnlineSession.episodesOf(d, "Sub").map { it.data })
        assertEquals(listOf("d1"), OnlineSession.episodesOf(d, "Dub").map { it.data })
        assertEquals("Show · S1E1 · Pilot", OnlineSession.titleFor(d, OnlineSession.episodesOf(d, "Sub").first()))
        assertEquals("Show", OnlineSession.titleFor(d, null))
        // Unnumbered episodes keep the source order.
        val u = series(mapOf("" to listOf(ep("b", null, null), ep("a", null, null))))
        assertEquals(listOf("b", "a"), OnlineSession.episodesOf(u, "").map { it.data })
    }

    @Test fun historyRecordsProgressAndContinueWatching() {
        val d = series(mapOf("Sub" to listOf(ep("e1", 1, 1), ep("e2", 1, 2))))
        OnlineHistory.record(context, d, "Sub", d.episodes["Sub"]!![0], 600_000, 1_400_000)
        val p = OnlineHistory.progressOf("Prov", "e1")
        assertNotNull(p)
        assertFalse(p!!.finished)
        assertEquals(600_000f / 1_400_000f, p.fraction, 0.001f)
        val entry = OnlineHistory.entry("Prov", "https://p/show")
        assertNotNull(entry)
        assertEquals("e1", entry!!.episodeData)

        OnlineHistory.record(context, d, "Sub", d.episodes["Sub"]!![0], 1_350_000, 1_400_000)
        assertTrue(OnlineHistory.progressOf("Prov", "e1")!!.finished)
        assertEquals(1, OnlineHistory.entries.value.size)

        // Survives a restart.
        OnlineHistory.resetForTests()
        OnlineHistory.ensureLoaded(context)
        assertTrue(OnlineHistory.progressOf("Prov", "e1")!!.finished)
        assertEquals("Show", OnlineHistory.entries.value.single().name)

        OnlineHistory.remove(context, "Prov", "https://p/show")
        assertNull(OnlineHistory.entry("Prov", "https://p/show"))
    }

    @Test fun finishedMovieLeavesContinueWatching() {
        val m = series(emptyMap()).copy(kind = OnlineKind.MOVIE, type = "Movie", playData = "movie-data")
        OnlineHistory.record(context, m, "", null, 100_000, 6_000_000)
        assertEquals(1, OnlineHistory.entries.value.size)
        OnlineHistory.record(context, m, "", null, 5_900_000, 6_000_000)
        assertTrue(OnlineHistory.entries.value.isEmpty())
        assertTrue(OnlineHistory.progressOf("Prov", "movie-data")!!.finished)
    }
}
