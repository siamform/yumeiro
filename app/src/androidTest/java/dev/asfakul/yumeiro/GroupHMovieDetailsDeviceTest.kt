/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.meta.TitleFacts
import dev.asfakul.yumeiro.ui.screens.online.titleFactsItems
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP H-11 (owner item 11, "Enhanced movie details: ratings, detailed information, cast and trailers").
 * Many extensions give a title without an IMDb id, and then the page showed none of those. Here a movie
 * comes in the way such an extension gives it (name and year only, no ids, no cast, no trailers); the
 * real TitleFacts load runs against the real free sources (Cinemeta, Simkl, Wikidata) and the page's
 * own About / Cast / Trailers sections are drawn from the result.
 * Fail-before: before H-11 the load stopped without an IMDb id, so nothing beyond the extension's facts.
 * Log tag YuGroupH; GROUPH_DETAILS_RESULT PASS is the evidence.
 */
@RunWith(AndroidJUnit4::class)
class GroupHMovieDetailsDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun has(tag: String): Boolean {
        runCatching { rule.onNodeWithTag("details_host").performScrollToNode(hasTestTag(tag)) }
        return rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun movie(name: String, year: Int) = OnlineDetail(
        api = "test-no-ids", url = "test://$name", name = name, kind = OnlineKind.MOVIE, type = "Movie",
        posterUrl = null, backgroundUrl = null, posterHeaders = emptyMap(), plot = null, year = year,
        tags = emptyList(), durationMin = null, score = null, contentRating = null, playData = "x",
        episodes = emptyMap(), seasonNames = emptyMap(), recommendations = emptyList(),
    )

    @Test fun aMovieWithoutIdsGetsRatingCastAndTrailers() {
        val d = movie("Inception", 2010)
        val t0 = System.currentTimeMillis()
        val facts = runBlocking { withTimeout(60_000) { TitleFacts.load(ctx, d) } }
        val ms = System.currentTimeMillis() - t0
        Log.i("YuGroupH", "details: ${ms}ms rating=${facts.rating} votes=${facts.votes} cast=${facts.cast.size} trailers=${facts.trailers.size} " +
            "directors=${facts.directors} released=${facts.released} runtime=${facts.runtimeMin} rows=${TitleFacts.rows(facts, d.kind)}")
        rule.setContent {
            MTPlayerTheme {
                LazyColumn(Modifier.testTag("details_host")) {
                    item { facts.rating?.let { Text("IMDb ${"%.1f".format(it)}" + (facts.votes?.let { v -> " · " + TitleFacts.votesText(v) } ?: ""), Modifier.testTag("details_rating")) } }
                    titleFactsItems(ctx, d, facts, tagPrefix = "online_detail") { title, trailing -> Text(title + (trailing?.let { " · $it" } ?: "")) }
                }
            }
        }
        rule.waitForIdle()
        FirstRun.shot("grouph_details_inception")
        assertTrue("a rating (${facts.rating})", facts.rating != null && facts.rating!! > 5.0)
        assertTrue("cast (${facts.cast.size})", facts.cast.size >= 3)
        assertTrue("trailers (${facts.trailers.size})", facts.trailers.isNotEmpty())
        assertTrue("director (${facts.directors})", facts.directors.any { it.contains("Nolan") })
        assertTrue("About section drawn", has("online_detail_about"))
        assertTrue("Cast section drawn", has("online_detail_cast"))
        assertTrue("Trailers section drawn", has("online_detail_trailers"))
        Log.i("YuGroupH", "GROUPH_DETAILS_RESULT PASS")
    }
}
