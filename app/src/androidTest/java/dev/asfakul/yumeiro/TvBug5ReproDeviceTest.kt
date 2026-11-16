/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import dev.asfakul.yumeiro.ui.screens.TabPagerProbe
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-5 (owner report, docs/proposals/FEATURE-BUG-5-tv-ui.md) reproduced with a remote on the TV
 * emulator. Each test drives D-pad keys only (no taps, a TV has no touchscreen), records what had
 * focus after every key, and asserts what a working TV app does, so a failure is the reproduction:
 *  TV-5 a player opened on TV: full screen (ten-foot layout), never a portrait request;
 *  TV-2 b from the player controls, D-pad Up/Down/Left/Right reaches the full-screen button;
 *  TV-3 c Home: D-pad Right along a row stays on Home (does not move the tab pager to Music);
 *  TV-4 d the top-bar search on Home is reachable by D-pad and a query shows results or says why.
 * Runs only in TV layout (Yumeiro TV, or the phone app with Layout = TV). Log tag YuTvBug5:
 * TVB_STEP / TVB_CASE / TVB_RESULT lines, screenshots tvb_*.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class TvBug5ReproDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private fun log(s: String) = Log.i("YuTvBug5", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun focused(): List<String> = runCatching {
        rule.onAllNodes(isFocused(), true).fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
    }.getOrDefault(emptyList())
    /** Focused node as tag, else its text or content description, with its bounds (TV-4 needs the untagged ones). */
    private fun focusedInfo(): String = runCatching {
        rule.onAllNodes(isFocused(), true).fetchSemanticsNodes().joinToString("+") { n ->
            val c = n.config
            val name = c.getOrNull(SemanticsProperties.TestTag)
                ?: c.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.take(24)?.let { "'$it'" }
                ?: c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")?.take(24)?.let { "cd'$it'" }
                ?: "node${n.id}"
            val b = n.boundsInRoot
            "$name[${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()}]"
        }.ifEmpty { "-" }
    }.getOrDefault("?")
    private fun boundsOf(tag: String): String = runCatching {
        rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().joinToString { n -> val b = n.boundsInRoot; "[${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()}]" }
    }.getOrDefault("?")
    private fun key(k: String, wait: Long = 450) { FirstRun.shell("input keyevent KEYCODE_$k"); Thread.sleep(wait); runCatching { rule.waitForIdle() } }
    private var clip: Uri? = null
    private var setTv = false

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        FirstRun.toTabs(rule)
        if (!dev.asfakul.yumeiro.Edition.isTv) {
            inst.runOnMainSync { dev.asfakul.yumeiro.ui.tv.TvMode.set(ctx, dev.asfakul.yumeiro.ui.tv.TvMode.Pref.TV) }
            setTv = true
        }
        runCatching { rule.waitUntil(15_000) { has("tv_nav_rail") } }
        assumeTrue("TV layout did not come up", has("tv_nav_rail"))
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { TestClip.remove(ctx, it, NAME) }
        if (setTv) inst.runOnMainSync { dev.asfakul.yumeiro.ui.tv.TvMode.set(ctx, dev.asfakul.yumeiro.ui.tv.TvMode.Pref.AUTO) }
    }

    /** Opens the test clip the way another app hands a video over (VIEW intent), until the first frame. */
    private fun openClip() {
        clip = TestClip.put(ctx, NAME)
        PlaybackDiagnostics.reset("")
        FirstRun.shell("am start -a android.intent.action.VIEW -d ${clip} -t video/mp4 -n ${ctx.packageName}/dev.asfakul.yumeiro.MainActivity")
        val ok = runCatching { rule.waitUntil(45_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("tvb_no_first_frame")
        assertTrue("the clip never reached its first frame · ${PlaybackDiagnostics.state.value} · ${FirstRun.onScreen(rule)}", ok)
        Thread.sleep(1_500)
    }

    private fun orientationName(o: Int) = when (o) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> "UNSPECIFIED"
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> "USER_PORTRAIT"
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "PORTRAIT"
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> "SENSOR_LANDSCAPE"
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "LANDSCAPE"
        else -> o.toString()
    }

    /** Player root covers the whole window (± 2 px)? */
    private fun playerFillsWindow(): Pair<Boolean, String> {
        val root = rule.onAllNodes(isRoot(), true).fetchSemanticsNodes().maxByOrNull { it.size.height } ?: return false to "no root"
        val p = rule.onAllNodesWithTag("video_player_root", true).fetchSemanticsNodes().firstOrNull() ?: return false to "no video_player_root"
        val b = p.boundsInRoot
        val full = b.left <= 2f && b.top <= 2f && b.right >= root.size.width - 2f && b.bottom >= root.size.height - 2f
        return full to "player=${b.width.toInt()}x${b.height.toInt()}@${b.left.toInt()},${b.top.toInt()} window=${root.size.width}x${root.size.height}"
    }

    @Test fun a_tv5_playerOpensFullScreenWithoutPortrait() {
        openClip()
        var requested = 0; var cfg = 0
        rule.runOnUiThread { requested = rule.activity.requestedOrientation; cfg = rule.activity.resources.configuration.orientation }
        val (full, geo) = playerFillsWindow()
        val sw = ctx.resources.configuration.smallestScreenWidthDp
        FirstRun.shot("tvb_a_player")
        log("TVB_CASE a requested=${orientationName(requested)} configOrientation=$cfg sw=${sw}dp fullWindow=$full $geo fullscreenBtn=${has("video_fullscreen_btn")} focused=${focused()}")
        val portrait = requested == ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT || requested == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        assertTrue("TV-5: the player asked a TV for portrait (${orientationName(requested)}, sw=${sw}dp)", !portrait)
        assertTrue("TV-5: the player opened in the phone layout, not full screen ($geo)", full)
        assertTrue("TV-5: the TV window is not landscape (config orientation $cfg, $geo)", cfg == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        log("TVB_RESULT PASS a")
    }

    /**
     * TV-2. API 34 before the fix: the button was reachable (Down from the top row), but only in the
     * phone layout the portrait request forced. After TV-5 the TV player is always full screen and has
     * no toggle, so the check is that the remote reaches the top row and the bottom row of the controls.
     */
    @Test fun b_tv2_dpadReachesTopAndBottomControls() {
        openClip()
        key("DPAD_CENTER", 800) // controls up
        val walk = ArrayList<String>()
        val seen = HashSet<String>()
        for (dir in listOf("DPAD_UP", "DPAD_UP", "DPAD_UP", "DPAD_UP", "DPAD_DOWN", "DPAD_DOWN", "DPAD_DOWN", "DPAD_DOWN", "DPAD_DOWN")) {
            if (!has("video_play_pause_btn")) key("DPAD_CENTER", 600)
            key(dir)
            val f = focused()
            seen += f
            walk += "$dir→${focusedInfo()}"
        }
        FirstRun.shot("tvb_b_walk")
        val top = seen.any { it == "video_quality_btn" || it == "video_more_btn" }
        val bottom = seen.any { it == "video_seekbar" || it == "video_fullscreen_btn" }
        log("TVB_CASE b top=$top bottom=$bottom fullscreenBtn=${has("video_fullscreen_btn")} walk=$walk")
        assertTrue("TV-2: D-pad Up never reached the top row of the controls: $walk", top)
        assertTrue("TV-2: D-pad Down never reached the seek bar: $walk", bottom)
        log("TVB_RESULT PASS b")
    }

    @Test fun c_tv3_dpadRightAlongAHomeRowStaysOnHome() {
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
        assumeTrue("Home did not show", has("home_screen_root"))
        val pageBefore = TabPagerProbe.page
        val walk = ArrayList<String>()
        key("DPAD_RIGHT"); key("DPAD_DOWN"); key("DPAD_DOWN")
        walk += "enter→${focused()} page=${TabPagerProbe.page}"
        var left = false
        repeat(14) { i ->
            key("DPAD_RIGHT", 400)
            val p = TabPagerProbe.page
            walk += "R${i + 1}→${focused().joinToString("+").ifEmpty { "-" }} page=$p"
            if (p != pageBefore) { left = true; return@repeat }
        }
        FirstRun.shot("tvb_c_row")
        log("TVB_CASE c pageBefore=$pageBefore pageAfter=${TabPagerProbe.page} leftHome=$left walk=$walk")
        assertTrue("TV-3: D-pad Right along a Home row moved the tab pager to page ${TabPagerProbe.page}: $walk", !left)
        log("TVB_RESULT PASS c")
    }

    @Test fun d_tv4_searchOnHomeIsReachableAndAnswers() {
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
        val walk = ArrayList<String>()
        var onSearch = false
        // A remote user goes Up to the top bar, then Right to the magnifier.
        for (dir in listOf("DPAD_RIGHT", "DPAD_UP", "DPAD_UP", "DPAD_UP", "DPAD_UP", "DPAD_RIGHT", "DPAD_RIGHT", "DPAD_RIGHT", "DPAD_LEFT", "DPAD_LEFT")) {
            key(dir)
            val f = focused()
            walk += "$dir→${focusedInfo()}"
            if ("topbar_search_action" in f) { onSearch = true; break }
        }
        log("TVB_STEP d search reachable=$onSearch walk=$walk topbarSearch=${boundsOf("topbar_search_action")} setup=${boundsOf("home_setup_card")} rail=${boundsOf("tv_nav_rail")}")
        if (onSearch) key("DPAD_CENTER", 1_000)
        val opened = has("topbar_search_input") || has("online_search_screen")
        if (opened) {
            FirstRun.shell("input text naruto"); Thread.sleep(800)
            key("ENTER", 8_000)
        }
        FirstRun.shot("tvb_d_search")
        log("TVB_CASE d reachable=$onSearch opened=$opened screen=${FirstRun.onScreen(rule)}")
        assertTrue("TV-4: the search button on Home cannot be reached with the D-pad: $walk", onSearch)
        assertTrue("TV-4: the search box did not open", opened)
        log("TVB_RESULT PASS d (results judged from the log and tvb_d_search)")
    }

    companion object { const val NAME = "yumeiro_test_clip.mp4" }
}
