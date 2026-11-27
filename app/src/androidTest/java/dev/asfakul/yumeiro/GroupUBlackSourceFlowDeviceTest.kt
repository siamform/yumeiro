/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.blacksource.BlackSource
import dev.asfakul.yumeiro.ui.screens.online.BlackSourceScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * GROUP U (F14 BlackSource), the search on a device: a local server answers as Yumeiro Cloud (the Worker's own
 * logic is tested in server/test/blacksource.test.ts), one fake source answers searches and title pages.
 * Taps Fantasy + Shounen, types words, searches: the request carries only the chosen values; the AniList title is
 * listed with its genres and demographic; the source's copy of it is shown under it; a source title AniList did not
 * return is kept because the source's own genres fit; tapping a match opens it. Log tag YuGroupU, GROUPU_b_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class GroupUBlackSourceFlowDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    private val asked = CopyOnWriteArrayList<String>()
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, what: () -> Boolean) = runCatching { compose.waitUntil(ms) { what() }; true }.getOrDefault(false)

    private class FakeSource : MainAPI() {
        override var name = "YuFakeSource"
        override var mainUrl = "https://yu-fake.invalid"
        override val supportedTypes = setOf(TvType.Anime)
        override suspend fun search(query: String, page: Int): SearchResponseList {
            val names = when {
                query.contains("Frieren", true) -> listOf("Sousou no Frieren (Sub)", "Unrelated Show")
                query.equals("yu", true) -> listOf("Yu Hidden Gem", "Yu Plain Drama")
                else -> emptyList()
            }
            return newSearchResponseList(names.map { newMovieSearchResponse(it, "$mainUrl/t/${it.replace(' ', '-')}", TvType.Anime) }, false)
        }
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
        override suspend fun load(url: String): LoadResponse = newMovieLoadResponse(url.substringAfterLast('/').replace('-', ' '), url, TvType.Anime, url) {
            tags = if (url.contains("Hidden-Gem")) listOf("Fantasy", "Shounen", "Adventure") else listOf("Drama")
        }
    }

    private val answer = """{"items":[{"id":154587,"title":"Sousou no Frieren","english":"Frieren: Beyond Journey's End","synonyms":["Frieren"],
        "format":"TV","year":2023,"episodes":28,"genres":["Adventure","Drama","Fantasy"],"tags":["Elf"],"demographic":"Shounen"}],"more":false}"""

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request.url.toString()
                return if (request.url.encodedPath == "/v1/blacksource/search")
                    MockResponse.Builder().addHeader("Content-Type", "application/json").body(answer).build()
                else MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        BlackSource.baseForTest = server.url("/v1/blacksource").toString().trimEnd('/')
        ctx.getSharedPreferences("blacksource", Context.MODE_PRIVATE).edit().putBoolean("opted_in", true).commit()
    }

    @After fun tearDown() {
        BlackSource.baseForTest = null
        ctx.getSharedPreferences("blacksource", Context.MODE_PRIVATE).edit().clear().commit()
        runCatching { server.close() }
    }

    @Test fun b_genreSearchFindsTitlesInSources() {
        val opened = CopyOnWriteArrayList<OnlineItem>()
        val source = FakeSource()
        compose.setContent {
            CompositionLocalProvider(LocalTvMode provides Edition.isTv) {
                MTPlayerTheme { BlackSourceScreen(onBack = {}, onOpenTitle = { opened += it }, onSearchWords = {}, providers = { listOf(source) }) }
            }
        }
        compose.waitForIdle()
        assertTrue("Opt-in alert shown although already opted in", !has("blacksource_optin"))
        compose.onNodeWithTag("blacksource_words", true).performTextInput("yu")
        compose.onNodeWithTag("bs_genre_Fantasy", true).performScrollTo().performClick()
        compose.onNodeWithTag("bs_demo_Shounen", true).performScrollTo().performClick()
        compose.onNodeWithTag("blacksource_search", true).performScrollTo().performClick()
        val listed = waitFor(15_000) { has("blacksource_title") }
        val req = asked.firstOrNull { it.contains("/v1/blacksource/search") }.orEmpty()
        Log.i("YuGroupU", "GROUPU_b listed=$listed request=${req.substringAfter("/v1/")}")
        assertTrue("No AniList title listed", listed)
        assertTrue("Request did not carry the picked values: $req", req.contains("q=yu") && req.contains("genres=Fantasy") && req.contains("demographic=Shounen"))
        val shown = compose.onAllNodesWithText("Frieren: Beyond Journey's End", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        val facts = compose.onAllNodesWithText("Shounen", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        val hit = waitFor(30_000) { has("blacksource_hit") }
        val own = waitFor(30_000) { has("blacksource_own_hit") }
        val ownName = compose.onAllNodesWithText("Yu Hidden Gem", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        val plainKept = compose.onAllNodesWithText("Yu Plain Drama", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        val unrelated = compose.onAllNodesWithText("Unrelated Show", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        FirstRun.shot("groupu_results")
        compose.onAllNodesWithTag("blacksource_hit", true).onFirst().performScrollTo().performClick()
        compose.waitForIdle()
        val openedName = opened.firstOrNull()?.name
        val pass = shown && facts && hit && own && ownName && !plainKept && !unrelated && openedName == "Sousou no Frieren (Sub)"
        Log.i("YuGroupU", "GROUPU_b_RESULT ${if (pass) "PASS" else "FAIL"} shown=$shown facts=$facts hit=$hit own=$own ownName=$ownName plainKept=$plainKept unrelated=$unrelated opened=$openedName")
        assertTrue("Title or its facts missing", shown && facts)
        assertTrue("The source's copy of the title is not listed", hit)
        assertTrue("A source title with fitting own genres is not kept", own && ownName)
        assertTrue("A non-matching source result was shown", !plainKept && !unrelated)
        assertEquals("Sousou no Frieren (Sub)", openedName)
    }
}
