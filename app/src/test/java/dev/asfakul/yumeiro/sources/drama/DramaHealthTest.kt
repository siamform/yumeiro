/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.drama

import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Round 2, order 1: drama ids, studio health and the cross-studio fallback (no network). */
class DramaHealthTest {
    private class Fake(private val down: Set<String>) : DramaCatalog {
        override val id = "onshort"
        override val label = "ONShort"
        override val platforms = listOf(DramaPlatform("good", "Good"), DramaPlatform("bad", "Bad"))
        val shows = mapOf(
            "good" to listOf(DramaShow("Lightbringer", "https://x/good-1/", null, 10, "Good")),
            "bad" to listOf(DramaShow("Lightbringer", "https://x/bad-1/", null, 10, "Bad"), DramaShow("Only Here", "https://x/bad-2/", null, 10, "Bad")),
        )
        override suspend fun shelf(platform: DramaPlatform, page: Int) = shows.getValue(platform.slug)
        override suspend fun search(query: String) = shows.values.flatten().filter { it.title == query }
        override suspend fun series(url: String): DramaSeries {
            val s = shows.values.flatten().first { it.url == url }
            return DramaSeries(s.title, s.url, null, null, s.platform, 10, null)
        }
        override suspend fun streams(series: DramaSeries, episode: Int): DramaStreams {
            if (series.platform!!.lowercase() in down) throw IOException("Episode $episode answered HTTP 424")
            return DramaStreams(mapOf("720" to "https://cdn/${series.url.hashCode()}/$episode.m3u8"), emptyList(), series.url)
        }
    }

    private fun links(p: YumeiroDramaProvider, data: String): List<ExtractorLink> = runBlocking {
        val out = mutableListOf<ExtractorLink>()
        p.loadLinks(data, false, { _: SubtitleFile -> }, { out += it })
        out
    }

    @Test fun idsSurviveFixUrlAndOldForm() {
        assertEquals("onshort" to "https://onshort.net/a/", YumeiroDramaProvider.parseId("drama:onshort|https://onshort.net/a/"))
        assertEquals("onshort" to "https://onshort.net/a/", YumeiroDramaProvider.parseId("https://yumeiro.click/drama/drama:onshort|https://onshort.net/a/"))
        val p = YumeiroDramaProvider(listOf(Fake(emptySet())))
        val home = runBlocking { p.getMainPage(1, MainPageRequest("Good", "onshort|good", false)) }
        val item = home.items.first().list.first()
        assertTrue(item.url, item.url.startsWith("drama:onshort|"))
        val load = runBlocking { p.load(item.url) } as com.lagradost.cloudstream3.TvSeriesLoadResponse
        assertTrue(load.episodes.first().data, load.episodes.first().data.startsWith("drama:onshort|"))
        assertTrue(links(p, load.episodes.first().data).isNotEmpty())
        // 1.8.0 saved the rewritten form in history and bookmarks: it still plays.
        assertTrue(links(p, "https://yumeiro.click/drama/" + load.episodes.first().data).isNotEmpty())
    }

    @Test fun downStudioHasNoShelfAndItsTitlesPlayFromAMirror() {
        val p = YumeiroDramaProvider(listOf(Fake(setOf("bad"))))
        val bad = runBlocking { p.getMainPage(1, MainPageRequest("Bad", "onshort|bad", false)) }
        assertTrue(bad.items.all { it.list.isEmpty() })
        val good = runBlocking { p.getMainPage(1, MainPageRequest("Good", "onshort|good", false)) }
        assertEquals(1, good.items.first().list.size)
        // A saved "Bad" title plays the same drama from "Good".
        val l = links(p, "drama:onshort|https://x/bad-1/|3")
        assertEquals(1, l.size)
        assertTrue(l.first().name + l.first().source, (l.first().name).contains("Good"))
        // Without a mirror: a clear reason, not a crash or an unknown-catalog error.
        val e = runCatching { links(p, "drama:onshort|https://x/bad-2/|1") }.exceptionOrNull()
        assertTrue(e?.message.orEmpty(), e?.message.orEmpty().contains("isn't serving this episode"))
    }

    @Test fun titleKeyIgnoresQuotesAndDubTags() {
        assertEquals(DramaHealth.titleKey("The Lightbringer’s Return"), DramaHealth.titleKey("The Lightbringer's Return (Dubbed)"))
        assertTrue(DramaHealth.refused(IOException("Episode 1 answered HTTP 503")))
        assertTrue(!DramaHealth.refused(IOException("timeout")))
    }
}
