/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.screens.sources.SourcesHubScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * GROUP D on the device (phones and TV): the Source page is a flow of pages, not tabs inside tabs.
 * The overview lists every ecosystem; each opens its own page; Browse and Repositories open as pages
 * from there; the real Back key (`input keyevent 4`) walks back one page at a time and leaves the
 * Source page only from the overview. Test d drives it with remote keys and checks focus comes back
 * to the row the user opened. Before Group D the page was a pill tab row (6 tabs) with a second
 * Installed / Browse / Repositories tab row inside. Log tag YuGroupD.
 */
@RunWith(AndroidJUnit4::class)
class GroupDSourcePagesDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val tv = inst.targetContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val left = AtomicInteger(0)

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onNodeWithTag(tag, true).performClick(); rule.waitForIdle() }
    private fun waitTag(tag: String) = rule.waitUntil(15_000) { has(tag) }
    private fun waitGone(tag: String) = rule.waitUntil(15_000) { !has(tag) }
    private fun key(code: Int) { FirstRun.shell("input keyevent $code"); Thread.sleep(600); rule.waitForIdle() }
    private fun back() = key(4)
    private fun focused(tag: String) = rule.onAllNodes(isFocused() and hasTestTag(tag), true).fetchSemanticsNodes().isNotEmpty()
    private fun focusedTags() = rule.onAllNodes(isFocused(), true).fetchSemanticsNodes().map { n ->
        n.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "?" } }
    /** The overview keeps its scroll position (ScrollMemory) across visits, so bring the row on screen first. */
    private fun row(p: String) { rule.onNodeWithTag("src_home", true).performScrollToNode(hasTestTag("src_page_$p")); rule.waitForIdle() }
    /** Focus [tag] like a remote does; the TV emulator can hand first focus to the window after our request. */
    private fun focus(tag: String): Boolean {
        repeat(10) {
            rule.onNodeWithTag(tag, true).requestFocus(); rule.waitForIdle()
            if (focused(tag)) return true
            Thread.sleep(200)
        }
        Log.i("YuGroupD", "focus $tag failed; focused=${focusedTags()}")
        return false
    }

    @After fun touch() { runCatching { inst.setInTouchMode(true) } }

    private fun show(tab: Int = -1) {
        rule.setContent {
            CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { SourcesHubScreen(onBack = { left.incrementAndGet() }, initialTab = tab) } }
        }
        rule.waitForIdle()
    }

    private val pages = listOf("cloudstream", "aniyomi", "mihon", "stremio", "js", "scripts", "servers")

    @Test fun a_overviewListsEveryEcosystem() {
        show()
        waitTag("src_home")
        assertFalse("pill tab row still on the page", has("sources_tab_0"))
        assertFalse("inner part tab row still on the page", has("hub_part_installed"))
        for (p in pages.drop(1)) {
            rule.onNodeWithTag("src_home", true).performScrollToNode(hasTestTag("src_page_$p"))
            assertTrue("no row for $p", has("src_page_$p"))
        }
        assertTrue("no + on the overview", has("sources_add_any"))
        assertTrue("no ? on the overview", has("sources_help"))
        Log.i("YuGroupD", "GROUPD_A PASS tv=$tv")
    }

    @Test fun b_drillDownAndBackOnePageAtATime() {
        show()
        waitTag("src_home"); row("aniyomi")
        click("src_page_aniyomi")
        waitTag("src_route_aniyomi")
        waitGone("src_home")
        assertFalse("tabs inside the ecosystem page", has("hub_part_installed"))
        assertTrue("+ in the page header", has("hub_tachiyomi_anime_add_plus"))
        click("src_go_repos")
        waitTag("src_route_aniyomi_repos")
        back()
        waitTag("src_route_aniyomi"); waitGone("src_route_aniyomi_repos")
        click("src_go_browse")
        waitTag("src_route_aniyomi_browse")
        back()
        waitTag("src_route_aniyomi")
        back()
        waitTag("src_home")
        assertEquals("left the Source page too early", 0, left.get())
        back()
        rule.waitUntil(5_000) { left.get() == 1 }
        Log.i("YuGroupD", "GROUPD_B PASS tv=$tv")
    }

    @Test fun c_shortcutOpensThePageAndBackGoesUp() {
        show(tab = 1) // Mihon shortcut (Manga → Sources, setup step)
        waitTag("src_route_mihon")
        assertTrue(has("hub_tachiyomi_manga_add_plus"))
        back()
        waitTag("src_home")
        assertEquals(0, left.get())
        row("stremio")
        click("src_page_stremio")
        waitTag("src_route_stremio")
        click("src_go_browse")
        waitTag("src_route_stremio_browse")
        assertTrue("Stremio Discover missing", has("hub_stremio"))
        Log.i("YuGroupD", "GROUPD_C PASS tv=$tv")
    }

    /** Remote: D-pad focus on a row, OK opens it, Back returns with focus on the same row. */
    @Test fun d_remoteKeysAndFocusReturn() {
        show()
        waitTag("src_home"); row("js")
        inst.setInTouchMode(false)
        rule.waitForIdle()
        assertTrue("row not focusable", focus("src_page_js"))
        key(23) // DPAD_CENTER
        waitTag("src_route_js")
        back()
        waitTag("src_home")
        val back = runCatching { rule.waitUntil(5_000) { focused("src_page_js") }; true }.getOrDefault(false)
        Log.i("YuGroupD", "GROUPD_D focusBack=$back focused=${focusedTags()}")
        assertTrue("focus did not return to the opened row", back)
        key(20) // DPAD_DOWN moves on from there
        val moved = !focused("src_page_js") && rule.onAllNodes(isFocused(), true).fetchSemanticsNodes().isNotEmpty()
        Log.i("YuGroupD", "GROUPD_D moved=$moved tv=$tv")
        assertTrue("focus lost after the remote key", moved)
    }
}
