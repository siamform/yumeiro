/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.drama

import com.lagradost.cloudstream3.MainPageRequest
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Round 2, order 1: tap to play against the live ONShort site, through the same calls the app makes
 * (feed shelf → [OnlineRepository.load] → [OnlineRepository.links]). Writes the resolved stream URLs to
 * `build/drama-live/links.tsv` so the full episode can be decoded with ffmpeg afterwards.
 * Runs only with `-Dyumeiro.live=true` (needs network).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YumeiroDramaLiveTest {
    @Test fun feedItemOpensAndPlays() = runBlocking {
        assumeTrue(System.getProperty("yumeiro.live") == "true")
        // Same as the app: one health service, prewarmed at start (the app does it in YumeiroDrama.start).
        val catalog = OnShortCatalog()
        val health = DramaHealth()
        val provider = YumeiroDramaProvider(listOf(catalog), health)
        // Waits for every studio's verdict (bounded), as the app has them from the previous run.
        kotlinx.coroutines.withTimeoutOrNull(System.getProperty("yumeiro.prewarmMs")?.toLongOrNull() ?: 240_000L) { health.prewarm(catalog).join() }
        val out = File("build/drama-live").apply { mkdirs() }
        val report = StringBuilder()
        var played = 0
        var shown = 0
        var refusedAtSource = 0
        for (page in provider.mainPage) {
            val home = runCatching { provider.getMainPage(1, MainPageRequest(page.name, page.data, false)) }
                .onFailure { report.append("shelf ${page.name}: FAIL ${it.message}\n") }.getOrNull() ?: continue
            val items = home.items.flatMap { it.list }.take(2)
            report.append("shelf ${page.name}: ${home.items.sumOf { it.list.size }} titles\n")
            for (item in items) {
                // The id the feed hands to the detail screen must stay a drama id.
                assertTrue("feed id was rewritten: ${item.url}", item.url.startsWith("drama:onshort|"))
                for (id in listOf(item.url, "https://yumeiro.click/drama/" + item.url)) { // + the 1.8.0 saved form
                    val detail = runCatching { OnlineRepository.load(provider, id, force = true) }
                        .onFailure { report.append("  ${item.name}: load FAILED ${it.javaClass.simpleName}: ${it.message}\n"); shown++ }.getOrNull() ?: continue
                    val ep = detail.episodes.values.flatten().first()
                    assertTrue("episode data rewritten: ${ep.data}", id != item.url || ep.data.startsWith("drama:onshort|"))
                    val events = OnlineRepository.links(provider, ep.data).toList()
                    val links = events.filterIsInstance<LinkEvent.Link>().map { it.link }
                    val done = events.filterIsInstance<LinkEvent.Done>().last()
                    report.append("  ${item.name} [${if (id == item.url) "new id" else "1.8.0 id"}]: ${detail.episodes.values.sumOf { it.size }} eps, " +
                        "${links.size} links, done=${done.ok} ${done.error.orEmpty()}\n")
                    shown++
                    // R15: a studio that refuses one title (ONShort answers HTTP 424 "unlock failed")
                    // is the source's state, not ours: counted apart, and the title leaves the shelf.
                    if (links.isEmpty() && done.error.orEmpty().contains("isn't serving this episode") && done.error.orEmpty().contains("HTTP 4")) refusedAtSource++
                    if (links.isNotEmpty()) {
                        played++
                        File(out, "links.tsv").appendText("${item.name}\t${links.first().quality}\t${links.first().referer.orEmpty()}\t${links.first().url}\n")
                    }
                }
            }
        }
        File(out, "report.txt").writeText(report.toString())
        println(report)
        // Every title the feed shows must play (shelves of studios that are down are hidden).
        assertTrue("nothing shown\n$report", shown >= 8)
        report.append("shown=$shown played=$played refusedAtSource=$refusedAtSource\n")
        File(out, "report.txt").writeText(report.toString())
        assertEquals("titles shown vs played (+ refused by the studio itself)\n$report", shown, played + refusedAtSource)
        assertEquals(true, YumeiroDramaProvider.parseId("https://yumeiro.click/drama/drama:onshort|https://onshort.net/x/").first == "onshort")
    }
}
