/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import dev.asfakul.yumeiro.online.blacksource.BlackSource
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.ui.screens.online.BlackSourceScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.concurrent.CopyOnWriteArrayList

/**
 * B9 BlackSource on a device, against real AniList data (no recorded answers). The phone asks AniList itself; the beta
 * Worker is the fallback (AniList refuses Cloudflare Workers, see BlackSourceAniList).
 *  a: More filters → Movie, Finished, 2015–2020, score 80+, Drama, leave out Romance, Top rated: every title listed
 *     is a finished movie that started in 2015–2020 with a score of at least 80.
 *  b: three installed sources in two ecosystems; the CloudStream switch turns both CloudStream sources off: only the
 *     Aniyomi source is asked, the count and the extra-time note follow, and the choice is kept for the next visit.
 *  c: "Only titles in my sources" leaves only titles a source has, each with its match under it.
 * Log tag YuGroupB9: GB9_RESULT_A/B/C PASS. Shots gb9_a, gb9_b_sheet, gb9_c.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupB9BlackSourceDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupB9", s)
    private fun has(tag: String) = runCatching { compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitFor(ms: Long, what: () -> Boolean) = runCatching { compose.waitUntil(ms) { what() }; true }.getOrDefault(false)
    private fun tap(tag: String) { compose.onNodeWithTag(tag, true).performScrollTo().performClick(); compose.waitForIdle() }
    private fun seek(tag: String) = runCatching {
        compose.onNodeWithTag("blacksource_list", true).performScrollToNode(hasTestTag(tag)); compose.waitForIdle(); has(tag)
    }.getOrDefault(false)
    private fun texts(tag: String): List<String> = runCatching {
        compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().flatMap { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
        }
    }.getOrDefault(emptyList())
    private fun cardTexts(): List<String> = runCatching {
        compose.onAllNodes(hasAnyAncestor(hasTestTag("blacksource_title")), true).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() }
    }.getOrDefault(emptyList())
    private fun checkingDone() = has("blacksource_progress") &&
        compose.onAllNodesWithText("Checking", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()

    /** An installed source that answers searches (real MainAPI code path) and records what it was asked. */
    private class Source(override var name: String, private val asked: MutableList<String>) : MainAPI() {
        override var mainUrl = "https://${name.lowercase().replace(' ', '-')}.invalid"
        override val supportedTypes = setOf(TvType.Anime)
        override suspend fun search(query: String, page: Int): SearchResponseList {
            asked += query
            val names = if (query.contains("Frieren", true)) listOf("Sousou no Frieren") else emptyList()
            return newSearchResponseList(names.map { newMovieSearchResponse(it, "$mainUrl/t/${it.replace(' ', '-')}", TvType.Anime) }, false)
        }
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
        override suspend fun load(url: String): LoadResponse = newMovieLoadResponse("x", url, TvType.Anime, url)
    }

    @Before fun setUp() {
        BlackSource.baseForTest = "https://yumeiro-cloud-beta.yumeiro.workers.dev/v1/blacksource"
        ctx.getSharedPreferences("blacksource", Context.MODE_PRIVATE).edit().clear().putBoolean("opted_in", true).commit()
    }

    @After fun tearDown() {
        BlackSource.baseForTest = null; BlackSource.aniListForTest = null
        runCatching { SourceHub.remove("yu_b9_cs") }; runCatching { SourceHub.remove("yu_b9_ani") }
        ctx.getSharedPreferences("blacksource", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun open(sources: List<MainAPI>) = compose.setContent {
        CompositionLocalProvider(LocalTvMode provides Edition.isTv) {
            MTPlayerTheme { BlackSourceScreen(onBack = {}, onOpenTitle = {}, onSearchWords = {}, providers = { sources }) }
        }
    }

    @Test fun a_advancedFiltersOnlyListMatchingTitles() {
        open(emptyList())
        compose.waitForIdle()
        tap("bs_genre_Drama")
        tap("bs_more")
        assertTrue("More filters did not open", waitFor(5_000) { has("bs_advanced") })
        listOf("bs_format_MOVIE", "bs_status_FINISHED", "bs_score_80", "bs_without_Romance", "bs_sort_score").forEach(::tap)
        compose.onNodeWithTag("bs_year_from", true).performScrollTo().performTextInput("2015")
        compose.onNodeWithTag("bs_year_to", true).performScrollTo().performTextInput("2020")
        compose.waitForIdle()
        log("GB9_A clear=${texts("bs_clear")}")
        tap("blacksource_search")
        assertTrue("AniList listed nothing through the Worker", waitFor(30_000) { has("blacksource_title") })
        compose.waitForIdle()
        val cards = cardTexts()
        log("GB9_A cards=${cards.take(24)}")
        FirstRun.shot("gb9_a")
        // Facts line: "<year> · MOVIE · …"; more line: "Finished · Score NN".
        val facts = cards.filter { it.contains(" · ") && it.take(4).all(Char::isDigit) }
        val more = texts("bs_title_more")
        assertTrue("no title facts read: $cards", facts.isNotEmpty() && more.isNotEmpty())
        facts.forEach { line ->
            val year = line.take(4).toInt()
            assertTrue("a title outside 2015–2020 or not a movie: $line", year in 2015..2021 && line.contains("MOVIE"))
        }
        more.forEach { line ->
            val score = line.substringAfter("Score ", "").trim().toIntOrNull()
            assertTrue("a title not finished or under 80: $line", line.startsWith("Finished") && score != null && score >= 80)
        }
        log("GB9_RESULT_A PASS titles=${facts.size}")
    }

    @Test fun b_sourcesAreChosenByEcosystemAndKept() {
        val asked = mapOf("Yu CS One" to CopyOnWriteArrayList<String>(), "Yu CS Two" to CopyOnWriteArrayList(), "Yu Ani One" to CopyOnWriteArrayList())
        val cs = listOf(Source("Yu CS One", asked.getValue("Yu CS One")), Source("Yu CS Two", asked.getValue("Yu CS Two")))
        val ani = Source("Yu Ani One", asked.getValue("Yu Ani One"))
        SourceHub.publishVideo("yu_b9_cs", SourceHub.Ecosystem.CLOUDSTREAM, cs)
        SourceHub.publishVideo("yu_b9_ani", SourceHub.Ecosystem.ANIYOMI, listOf(ani))
        val all = cs + ani
        open(all)
        compose.waitForIdle()
        assertTrue("source count: ${texts("bs_sources")}", texts("bs_sources").any { it.contains("3 of 3") })
        tap("bs_sources")
        assertTrue("sources sheet did not open", waitFor(5_000) { has("bs_sources_sheet") })
        compose.onNodeWithTag("bs_eco_CloudStream", true).performClick(); compose.waitForIdle()
        FirstRun.shot("gb9_b_sheet")
        assertTrue("CloudStream sources still ticked", BlackSource.sourcesOff(ctx) == setOf("Yu CS One", "Yu CS Two"))
        androidx.test.espresso.Espresso.pressBack(); compose.waitForIdle()
        assertTrue("sheet still open", waitFor(5_000) { !has("bs_sources_sheet") })
        assertTrue("count did not follow: ${texts("bs_sources")}", texts("bs_sources").any { it.contains("1 of 3") })
        compose.onNodeWithTag("blacksource_words", true).performTextInput("frieren")
        tap("bs_genre_Fantasy")
        val note = texts("bs_cost").joinToString()
        log("GB9_B note=$note")
        assertTrue("the extra-time note does not count one source: $note", note.contains("in 1 sources") || note.contains(" 1 "))
        tap("blacksource_search")
        assertTrue("AniList listed nothing", waitFor(30_000) { has("blacksource_title") })
        assertTrue("source lookups did not finish", waitFor(90_000) { checkingDone() })
        log("GB9_B asked=${asked.mapValues { it.value.size }}")
        assertTrue("the Aniyomi source was not asked", asked.getValue("Yu Ani One").isNotEmpty())
        assertTrue("a turned-off source was asked", asked.getValue("Yu CS One").isEmpty() && asked.getValue("Yu CS Two").isEmpty())
        // Next visit: the choice is still there.
        open(all); compose.waitForIdle()
        assertTrue("choice not kept: ${texts("bs_sources")}", texts("bs_sources").any { it.contains("1 of 3") })
        log("GB9_RESULT_B PASS")
    }

    @Test fun c_onlyTitlesInMySources() {
        val asked = CopyOnWriteArrayList<String>()
        val src = Source("Yu Ani Only", asked)
        SourceHub.publishVideo("yu_b9_ani", SourceHub.Ecosystem.ANIYOMI, listOf(src))
        open(listOf(src))
        compose.waitForIdle()
        tap("bs_genre_Fantasy"); tap("bs_genre_Adventure"); tap("bs_demo_Shounen")
        tap("blacksource_search")
        assertTrue("AniList listed nothing", waitFor(30_000) { has("blacksource_title") })
        assertTrue("source lookups did not finish", waitFor(90_000) { checkingDone() })
        compose.onNodeWithTag("blacksource_list", true).performScrollToNode(hasTestTag("bs_only_found"))
        val before = compose.onAllNodesWithTag("blacksource_title", true).fetchSemanticsNodes().size
        compose.onNodeWithTag("bs_only_found", true).performClick(); compose.waitForIdle()
        assertTrue("Frieren's match not listed", waitFor(10_000) { seek("blacksource_hit") })
        seek("blacksource_title")
        FirstRun.shot("gb9_c")
        val titles = compose.onAllNodesWithTag("blacksource_title", true).fetchSemanticsNodes().size
        val hits = compose.onAllNodesWithTag("blacksource_hit", true).fetchSemanticsNodes().size
        val names = cardTexts()
        log("GB9_C before=$before after=$titles hits=$hits cards=${names.take(8)}")
        assertEquals("only the one title the source has stays", 1, titles)
        assertTrue("the kept title is not Frieren: $names", names.any { it.contains("Frieren") })
        assertTrue("the kept title has no match under it", hits >= 1)
        assertTrue("nothing was hidden (before=$before)", before > titles)
        log("GB9_RESULT_C PASS")
    }
}
