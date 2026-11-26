/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.YumixDefaults
import dev.asfakul.yumeiro.sources.SourceHub
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * B18 in the real app (phones and TV), with two local extensions (no network). Log tag YuGroupO.
 *  a: picking one extension in "Show videos from" saves it (per profile, on disk).
 *  b: a new Yumix (fresh activity, settings read back from disk) opens on that extension's own page, with the
 *     extension's own name in the header; picking another one makes that the saved default.
 *  c: reopening shows the new one; "Show all" goes back to the mix and that is saved too.
 * Fail-before: the pick lived only in the screen's state, so every new Yumix opened on All.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupOYumixPickDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private fun log(s: String) = Log.i("YuGroupO", s)
    private fun n(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun has(tag: String) = n(tag) > 0
    private fun waitTag(tag: String, ms: Long = 30_000) {
        val ok = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
        assertTrue("no '$tag' after ${ms}ms · ${FirstRun.onScreen(rule)}", ok)
    }

    private class Local(override var name: String, private val film: String) : MainAPI() {
        override var mainUrl = "https://${name.lowercase().replace(' ', '-')}.invalid"
        override val hasMainPage = true
        override val supportedTypes = setOf(TvType.Movie)
        override val mainPage = mainPageOf("$mainUrl/latest" to "Latest")
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse =
            newHomePageResponse(request.name, listOf(newMovieSearchResponse(film, "$mainUrl/film", TvType.Movie)))
        override suspend fun search(query: String, page: Int): SearchResponseList = newSearchResponseList(emptyList(), false)
        override suspend fun load(url: String): LoadResponse = newMovieLoadResponse(film, url, TvType.Movie, url)
    }

    private fun saved(): String? = ctx.getSharedPreferences(
        dev.asfakul.yumeiro.profiles.Profiles.prefsName(ctx, "yumix_defaults"), Context.MODE_PRIVATE).getString("picked", null)

    @Before fun setUp() {
        val ui = inst.uiAutomation
        runCatching { ui.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        // A new process reads the pick from disk: drop what is in memory so this test does the same.
        YumixDefaults.resetForTest()
        SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.CLOUDSTREAM, listOf(Local(A, "Yu B18 Film A"), Local(B, "Yu B18 Film B")))
        OnlineRepository.clearCaches()
        FirstRun.toTabs(rule)
    }

    @After fun tearDown() { SourceHub.remove(BRIDGE) }

    private fun openYumix() {
        FirstRun.tapVisible(rule, "nav_tab_home"); rule.waitForIdle()
        FirstRun.tapVisible(rule, "quick_card_hues"); rule.waitForIdle()
        waitTag("online_filter", 120_000)
    }

    private fun pick(name: String) {
        FirstRun.tapVisible(rule, if (has("online_filter_active")) "online_filter_active" else "online_filter")
        waitTag("online_filter_sheet")
        rule.onAllNodesWithTag("online_filter_$name", true)[0].performClick(); rule.waitForIdle()
    }

    private fun headerShows(name: String, ms: Long = 60_000): Boolean = runCatching {
        rule.waitUntil(ms) { has("online_channel_header") && rule.onAllNodesWithText(name, substring = false).fetchSemanticsNodes().isNotEmpty() }; true
    }.getOrDefault(false)

    @Test fun a_pickIsSaved() {
        inst.runOnMainSync { YumixDefaults.init(ctx); YumixDefaults.setPicked(ctx, null) }
        ctx.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(ctx, "yumix_defaults"), Context.MODE_PRIVATE).edit().remove("picked").commit()
        openYumix()
        log("GROUPO_B18 a start header=${n("online_channel_header")} saved=${saved()}")
        assertEquals("starts on All", 0, n("online_channel_header"))
        pick(A)
        assertTrue("picked $A: its page never opened · ${FirstRun.onScreen(rule)}", headerShows(A))
        runCatching { rule.waitUntil(5_000) { saved() == A } }
        log("GROUPO_B18 a picked saved=${saved()}")
        FirstRun.shot("groupo_b18_picked")
        assertEquals("B18: the pick is saved", A, saved())
        log("GROUPO_B18_RESULT a PASS")
    }

    @Test fun b_newYumixOpensOnTheSavedPick() {
        openYumix()
        val onA = headerShows(A)
        log("GROUPO_B18 b reopen header=${n("online_channel_header")} onA=$onA saved=${saved()} · ${FirstRun.onScreen(rule).take(300)}")
        FirstRun.shot("groupo_b18_reopened")
        assertTrue("B18: a new Yumix opened on All, not on the saved $A", onA)
        assertEquals("only one header, the extension's own", 1, n("online_channel_header"))
        pick(B)
        assertTrue("picked $B: its page never opened", headerShows(B))
        runCatching { rule.waitUntil(5_000) { saved() == B } }
        log("GROUPO_B18 b changed saved=${saved()}")
        assertEquals("B18: the changed pick is the new default", B, saved())
        log("GROUPO_B18_RESULT b PASS")
    }

    @Test fun c_changedPickStaysAndShowAllIsSavedToo() {
        openYumix()
        val onB = headerShows(B)
        log("GROUPO_B18 c reopen onB=$onB saved=${saved()}")
        assertTrue("B18: reopened on ${saved()} instead of $B", onB)
        FirstRun.tapVisible(rule, "online_filter_active_clear")
        val all = runCatching { rule.waitUntil(20_000) { !has("online_channel_header") }; true }.getOrDefault(false)
        runCatching { rule.waitUntil(5_000) { saved() == null } }
        log("GROUPO_B18 c show all=$all saved=${saved()}")
        assertTrue("show all returns to the mix", all)
        assertEquals("show all is saved as the default too", null, saved())
        log("GROUPO_B18_RESULT c PASS")
    }

    private companion object {
        const val BRIDGE = "yu-groupo-b18"
        const val A = "YuB18 Alpha"
        const val B = "YuB18 Beta"
    }
}
