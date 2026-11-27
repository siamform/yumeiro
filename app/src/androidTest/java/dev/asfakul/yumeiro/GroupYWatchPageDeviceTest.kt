/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.online.OnlineCast
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.ui.screens.online.OnlineWatchPanel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group Y (B7): the page under the player shows what you need while watching (controls, sources, synopsis,
 * episodes) and no longer the title page's cast, About rows or trailers, even when the extension sends them.
 * Log tag YuGroupY: GY_RESULT_E PASS. Shot gy_e_watch.
 */
@RunWith(AndroidJUnit4::class)
class GroupYWatchPageDeviceTest {
    @get:Rule val compose = createComposeRule()

    private val eps = (1..6).map { OnlineEpisode("yu-ep$it", "Chapter $it", 1, it, null, null, 24) }
    private val detail = OnlineDetail(
        api = "YuDemo", url = "https://yu.test/show", name = "Yu Lantern Road", kind = OnlineKind.ANIME, type = "Anime",
        posterUrl = null, backgroundUrl = null, posterHeaders = emptyMap(), plot = "A lantern maker walks the night road.",
        year = 2026, tags = listOf("Drama"), durationMin = 24, score = 8.1, contentRating = null, playData = null,
        episodes = mapOf("" to eps), seasonNames = emptyMap(), recommendations = emptyList(),
        cast = listOf(OnlineCast("Yu Actor One", "Lead"), OnlineCast("Yu Actor Two", "Friend"), OnlineCast("Yu Actor Three")),
        trailers = listOf("https://www.youtube.com/watch?v=aaaaaaaaaaa"),
    )

    @After fun tearDown() { OnlineSession.setStateForTest(null) }

    private fun has(tag: String) = runCatching { compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun textShown(s: String) = runCatching { compose.onAllNodesWithText(s, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    @Test fun e_watchPageHasNoCastDetailsOrTrailers() {
        OnlineSession.setStateForTest(OnlineSession.State(api = "YuDemo", detail = detail, version = "", episodes = eps, index = 0, linksDone = true))
        compose.setContent { FirstRun.AppBackground { Box(Modifier.fillMaxSize()) { OnlineWatchPanel(player = null, onOpenItem = {}) } } }
        val end = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < end && !has("online_watch_panel")) { compose.waitForIdle(); SystemClock.sleep(100) }
        // Give a late fetch time to land, so a section that appears after loading is caught too.
        SystemClock.sleep(2_500); compose.waitForIdle()
        FirstRun.shot("gy_e_watch")
        assertTrue("the panel is there", has("online_watch_panel"))
        assertTrue("synopsis stays", has("watch_description"))
        assertTrue("title stays", textShown("Yu Lantern Road"))
        val shown = listOf("online_watch_cast_list" to has("online_watch_cast"), "about" to has("online_watch_about"), "trailers" to has("online_watch_trailers"), "actor" to textShown("Yu Actor One"))
        Log.i("YuGroupY", "GY_E sections=$shown")
        // "online_watch_cast" is also the Chromecast action's tag; with no player that action is not offered.
        assertFalse("no cast list", has("online_watch_cast"))
        assertFalse("no About rows", has("online_watch_about"))
        assertFalse("no trailers", has("online_watch_trailers"))
        assertFalse("no actor names", textShown("Yu Actor One"))
        Log.i("YuGroupY", "GY_RESULT_E PASS")
    }
}
