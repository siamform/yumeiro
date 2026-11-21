/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.screens.youtube.ShortsPlayerDialog
import dev.asfakul.yumeiro.ui.screens.youtube.ShortsWindow
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.youtube.YtItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File

/**
 * F-12 swipe (ADR-018, phones only): three local 9:16-player pages. Swipe up plays the next Short, swipe down
 * the previous one, ten fast swipes settle on one page that plays, and every page uses the one shared player.
 * First frame after a page settles: budget 600 ms on api36, 1200 ms on the 2 GB phone (proposal section 6).
 * Old code (pager not swipeable): page 1 never shows a frame. Log tag YuF12.
 */
@RunWith(AndroidJUnit4::class)
class F12ShortsSwipeDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext.applicationContext
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 20_000) = runCatching { compose.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun log(s: String) = Log.i("YuF12", s)

    @After fun tearDown() {
        ShortsWindow.testResolver = null
        FeatureFlags.set(ctx, FeatureFlags.Flag.SHORTS_VERTICAL, FeatureFlags.Flag.SHORTS_VERTICAL.default)
    }

    @Test fun swipeUpAndDownPlaysEachShortInTheOneSharedPlayer() {
        assumeFalse("Shorts player is phones only (ADR-018)", Edition.isTv)
        val clip = inst.context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
        val items = (0..2).map { i ->
            File(ctx.cacheDir, "f12_short_$i.mp4").writeBytes(clip)
            YtItem("f12short00$i", "https://www.youtube.com/shorts/f12short00$i", "Local Short $i", "Yumeiro test")
        }
        ShortsWindow.testResolver = { item ->
            val i = items.indexOf(item)
            StreamRequest(url = Uri.fromFile(File(ctx.cacheDir, "f12_short_$i.mp4")).toString(), title = item.title)
        }
        ShortsWindow.playersUsed.clear(); ShortsWindow.firstFrameMs.clear(); ShortsWindow.lastResize = -1
        FeatureFlags.set(ctx, FeatureFlags.Flag.SHORTS_VERTICAL, true)
        val (cw, ch) = android.media.MediaMetadataRetriever().run {
            try {
                setDataSource(File(ctx.cacheDir, "f12_short_0.mp4").path)
                (extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0) to
                    (extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0)
            } finally { release() }
        }
        var closed = false
        compose.setContent {
            MTPlayerTheme {
                var open by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    if (open) ShortsPlayerDialog(items, 0, onDismiss = { open = false; closed = true }, onFullPlayer = {})
                }
            }
        }
        assertTrue("no shorts_player", waitTag("shorts_player"))
        assertTrue("page 0 never showed a frame", waitTag("shorts_frame_0"))
        val wantResize = ShortsWindow.resizeFor(cw, ch, fitLandscape = true)
        runCatching { compose.waitUntil(5_000) { ShortsWindow.lastResize == wantResize } }
        log("F12 clip=${cw}x$ch resize=${ShortsWindow.lastResize} want=$wantResize (0 FIT, 4 ZOOM)")
        assertEquals("resize mode for a ${cw}x$ch clip", wantResize, ShortsWindow.lastResize)

        fun swipe(up: Boolean) = compose.onAllNodesWithTag("shorts_player", true).onFirst().performTouchInput { if (up) swipeUp() else swipeDown() }
        // A first frame is recorded only by the player after the new page's media is set (never a stale one).
        fun frameAfter(up: Boolean, page: Int): Long? {
            ShortsWindow.firstFrameMs.clear(); swipe(up)
            val id = items[page].id
            runCatching { compose.waitUntil(20_000) { ShortsWindow.firstFrameMs.containsKey(id) } }
            return ShortsWindow.firstFrameMs[id]
        }
        val ms1up = frameAfter(true, 1); log("F12 swipe up -> page1 ms=$ms1up")
        assertTrue("swipe up: page 1 never showed a frame", ms1up != null)
        val ms2 = frameAfter(true, 2); log("F12 swipe up -> page2 ms=$ms2")
        assertTrue("swipe up: page 2 never showed a frame", ms2 != null)
        val ms1 = frameAfter(false, 1); log("F12 swipe down -> page1 ms=$ms1")
        assertTrue("swipe down: page 1 never showed a frame again", ms1 != null)
        FirstRun.shot("f12_shorts_swipe")

        ShortsWindow.firstFrameMs.clear()
        repeat(10) { k -> swipe(k % 2 == 0) }
        compose.waitForIdle()
        val settled = runCatching { compose.waitUntil(20_000) { ShortsWindow.firstFrameMs.isNotEmpty() }; true }.getOrDefault(false)
        val budget = if (DeviceClass.lowEnd) 1200L else 600L
        log("F12_RESULT frameMs page1=$ms1up page2=$ms2 page1back=$ms1 budget=$budget players=${ShortsWindow.playersUsed.size} afterFastSwipes=$settled lowEnd=${DeviceClass.lowEnd}")
        assertTrue("after 10 fast swipes no page played", settled)
        assertEquals("one shared player for every page", 1, ShortsWindow.playersUsed.size)
        assertTrue("page 2 first frame $ms2 ms > $budget ms", ms2 != null && ms2 <= budget)
        assertTrue("page 1 (back) first frame $ms1 ms > $budget ms", ms1 != null && ms1 <= budget)

        compose.onAllNodesWithTag("shorts_close", true).onFirst().performClick()
        compose.waitUntil(5_000) { closed }
    }
}
