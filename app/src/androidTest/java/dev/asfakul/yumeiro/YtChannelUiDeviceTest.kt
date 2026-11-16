/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.youtube.YtChannelLaunch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-2 owner flow: open a channel, list its videos, scroll, open a video, back to the channel,
 * and (E1) the watch page's channel row opens the channel again. Needs network to YouTube.
 */
@RunWith(AndroidJUnit4::class)
class YtChannelUiDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun log(s: String) = Log.i("YuChannel", s)

    private fun visible(tag: String): Int {
        val root = rule.onAllNodes(isRoot(), true).fetchSemanticsNodes().maxByOrNull { it.size.height } ?: return 0
        return runCatching {
            rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().count { n ->
                val b = n.boundsInRoot
                b.width > 1f && b.height > 1f && b.top >= -1f && b.bottom <= root.size.height + 1f
            }
        }.getOrDefault(0)
    }
    private fun waitVisible(tag: String, ms: Long): Boolean =
        runCatching { rule.waitUntil(ms) { visible(tag) > 0 }; true }.getOrDefault(false)

    @Test fun openChannelListScrollPlayAndBack() {
        FirstRun.toTabs(rule)
        val url = "https://www.youtube.com/@GoogleDevelopers"
        rule.runOnUiThread { YtChannelLaunch.open(url, "Google for Developers") }
        assertTrue("channel page never opened · ${FirstRun.onScreen(rule)}", waitVisible("youtube_channel", 30_000))
        val listed = waitVisible("youtube_card", 60_000)
        FirstRun.shot("channel_1_listed")
        log("CHANNEL_UI listed=$listed cards=${visible("youtube_card")}")
        assertTrue("no channel videos · ${FirstRun.onScreen(rule).take(800)}", listed)

        repeat(3) { FirstRun.shell("input swipe 540 1600 540 500 300"); Thread.sleep(700) }
        rule.waitForIdle()
        val afterScroll = visible("youtube_card")
        FirstRun.shot("channel_2_scrolled")
        log("CHANNEL_UI scrolled cards=$afterScroll")
        assertTrue("cards gone after scrolling", afterScroll > 0)

        FirstRun.tapVisible(rule, "youtube_card")
        val watch = waitVisible("yt_watch_panel", 30_000)
        FirstRun.shot("channel_3_video")
        log("CHANNEL_UI video watch=$watch")
        assertTrue("watch page did not open from the channel", watch)

        // E1: the watch page's channel row opens the channel (the video minimises).
        val row = waitVisible("yt_watch_channel", 20_000)
        if (row) rule.onAllNodesWithTag("yt_watch_channel", true)[0].performClick()
        val viaRow = waitVisible("youtube_channel", 20_000)
        FirstRun.shot("channel_4_from_row")
        log("CHANNEL_UI row=$row channelAgain=$viaRow")
        assertTrue("channel row did not bring the channel back", viaRow)

        // Back from the video (open it again, then Back) lands on the channel, not Home.
        FirstRun.tapVisible(rule, "youtube_card")
        waitVisible("yt_watch_panel", 30_000)
        FirstRun.shell("input keyevent 4"); Thread.sleep(1_500); rule.waitForIdle()
        if (visible("yt_watch_panel") > 0) { FirstRun.shell("input keyevent 4"); Thread.sleep(1_500); rule.waitForIdle() }
        val back = waitVisible("youtube_channel", 15_000)
        FirstRun.shot("channel_5_back")
        log("CHANNEL_UI_RESULT ${if (back) "PASS" else "FAIL"} back=$back")
        assertTrue("Back did not return to the channel · ${FirstRun.onScreen(rule).take(800)}", back)
    }
}
