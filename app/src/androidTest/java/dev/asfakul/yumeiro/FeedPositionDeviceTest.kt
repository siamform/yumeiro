/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnboardingGate
import dev.asfakul.yumeiro.ui.feed.FeedCardKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * R16 Phase 3 evidence: while a user scrolls an endless feed, no card on screen is ever replaced
 * by another one (a stale cached row swapped in for the live one), and nothing moves under the
 * finger. Runs on a real (emulated) device against the real network: Home, Yumix (online),
 * YouTube, YouTube Music (online music) and Live TV.
 *
 * Every feed card carries its identity (`Modifier.feedCard`, semantics key [FeedCardKey]). After
 * each swipe and each idle wait the test takes a snapshot of (identity, unclipped position) and
 * checks, against the snapshot before:
 *  1. Rigid motion — every card seen in both moved by the same offset, horizontally zero. A card
 *     moving differently means something was inserted, removed or resized above it.
 *  2. No replacement — a card whose expected new position is still well inside the screen must
 *     still be there. A card that vanishes in plain view was replaced.
 *  3. Direction — a swipe up never moves content down (no jump back to the top).
 *  4. Stillness — after 8 s with no touch, every card is exactly where it was and none vanished
 *     (new cards may only appear where there was none: appended below or images arriving).
 * Logs: tag `YuFeedPos`, plus `files/feedpos/<feed>.txt` pulled with the artifacts.
 */
@RunWith(AndroidJUnit4::class)
class FeedPositionDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val report = StringBuilder()

    private data class Card(val key: String, val x: Float, val y: Float, val w: Int, val h: Int)

    private fun log(line: String) { Log.i("YuFeedPos", line); report.append(line).append('\n') }

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { ui.grantRuntimePermission(ctx.packageName, it) } }
        rule.waitForIdle()
        OnboardingGate.release() // exactly what "Skip" on the welcome guide does
        runCatching {
            runBlocking {
                kotlinx.coroutines.withTimeout(180_000) {
                    ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
                    if (ExtensionManager.provider("Internet Archive") == null) {
                        val remote = ExtensionSources.all(ctx, force = true).items.first { it.internalName == "InternetArchiveProvider" }
                        ExtensionManager.install(ctx, remote, dev.asfakul.yumeiro.online.ExtensionReview.Mode.AUTO)
                    }
                }
            }
        }.onFailure { log("extension setup failed: $it") }
    }

    private fun idle(ms: Long) {
        val until = SystemClock.uptimeMillis() + ms
        runCatching { rule.waitUntil(ms + 5_000) { SystemClock.uptimeMillis() >= until } }
    }

    private fun tap(tag: String) {
        rule.waitUntil(30_000) { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag(tag, true)[0].performClick() // the title switch and the inline pills can share a tag
        idle(600)
    }

    private val screen by lazy { rule.onRoot().fetchSemanticsNode().size }

    /** Cards that intersect the screen (pager pages beside the current one sit wholly outside it). */
    private fun snap(): List<Card> = rule.onAllNodes(SemanticsMatcher.keyIsDefined(FeedCardKey), true).fetchSemanticsNodes()
        .mapNotNull { n ->
            val k = n.config.getOrElseNullable(FeedCardKey) { null } ?: return@mapNotNull null
            val p = n.positionInRoot
            Card(k, p.x, p.y, n.size.width, n.size.height)
        }
        .filter { it.x + it.w > 0 && it.x < screen.width && it.y + it.h > 0 && it.y < screen.height && it.w > 0 && it.h > 0 }

    /** Compares two snapshots; returns the problems found (empty = the step is clean). */
    private fun compare(step: String, a: List<Card>, b: List<Card>, still: Boolean, swipeUp: Boolean?): List<String> {
        val problems = ArrayList<String>()
        val ua = a.groupBy { it.key }.filterValues { it.size == 1 }.mapValues { it.value[0] }
        val ub = b.groupBy { it.key }.filterValues { it.size == 1 }.mapValues { it.value[0] }
        val common = ua.keys intersect ub.keys
        val dys = common.map { (ub[it]!!.y - ua[it]!!.y) }
        // The motion of the page: the most common offset (to the pixel).
        val dy = dys.groupingBy { Math.round(it) }.eachCount().maxByOrNull { it.value }?.key?.toFloat() ?: 0f
        if (still && common.isNotEmpty() && abs(dy) > 1f) problems += "$step: feed moved ${dy}px with no touch"
        if (swipeUp == true && dy > 4f) problems += "$step: swipe up moved content DOWN by ${dy}px (jump back)"
        if (swipeUp == false && dy < -4f) problems += "$step: swipe down moved content UP by ${-dy}px"
        for (k in common) {
            val ca = ua[k]!!; val cb = ub[k]!!
            val ddy = (cb.y - ca.y) - dy
            val ddx = cb.x - ca.x
            if (abs(ddy) > 4f) problems += "$step: card $k moved ${cb.y - ca.y}px while the page moved ${dy}px (insert/remove above it)"
            // Horizontal rows only move sideways when the user swipes them; this test swipes vertically.
            if (abs(ddx) > 4f) problems += "$step: card $k slid sideways ${ddx}px"
        }
        val top = screen.height * 0.15f; val bottom = screen.height * 0.85f
        val keysB = b.map { it.key }.toSet()
        for (ca in a) {
            if (ca.key in keysB) continue
            val ny = ca.y + dy
            val inPlainView = ny >= top && ny + ca.h <= bottom && ca.x >= 0 && ca.x + ca.w <= screen.width
            if (inPlainView) {
                val took = b.firstOrNull { abs(it.y - ny) < ca.h / 2f && abs(it.x - ca.x) < ca.w / 2f }
                problems += "$step: card ${ca.key} vanished in plain view at y=${ny.toInt()}" + (took?.let { " — replaced by ${it.key}" } ?: "")
            }
        }
        return problems
    }

    private fun swipe(up: Boolean) {
        rule.onRoot(useUnmergedTree = true).performTouchInput {
            if (up) swipeUp(startY = height * 0.72f, endY = height * 0.38f, durationMillis = 450)
            else swipeDown(startY = height * 0.38f, endY = height * 0.72f, durationMillis = 450)
        }
    }

    private fun run(feed: String, open: () -> Unit) {
        log("feed $feed: device ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT} screen=${screen.width}x${screen.height}")
        open()
        val shown = runCatching { rule.waitUntil(120_000) { snap().size >= 4 }; true }.getOrDefault(false)
        val first = snap()
        log("feed $feed: ${first.size} cards on screen at start: ${first.take(6).map { it.key }}")
        assertTrue("$feed: the feed never showed 4 cards in 120 s (cards=${first.size})", shown)
        val problems = ArrayList<String>()
        var seen = HashSet<String>().apply { addAll(first.map { it.key }) }
        fun stillCheck(name: String) {
            val a = snap(); idle(8_000); val b = snap()
            problems += compare("$feed/$name", a, b, still = true, swipeUp = null)
            log("feed $feed/$name: still ${a.size}→${b.size} cards")
        }
        idle(2_000)
        stillCheck("idle-start")
        var prev = snap()
        val steps = listOf(true, true, true, true, true, true, true, true, true, true, false, false, false, false, false, false)
        steps.forEachIndexed { i, up ->
            swipe(up); idle(1_500)
            val now = snap()
            val p = compare("$feed/swipe${i + 1}${if (up) "↑" else "↓"}", prev, now, still = false, swipeUp = up)
            problems += p
            seen.addAll(now.map { it.key })
            log("feed $feed/swipe${i + 1}: ${now.size} cards, page moved ${now.firstOrNull()?.let { c -> prev.firstOrNull { it.key == c.key }?.let { (c.y - it.y).toInt() } } ?: "?"}px, problems=${p.size}")
            prev = now
            if (i == 9) stillCheck("idle-deep")
        }
        stillCheck("idle-end")
        log("feed $feed: ${seen.size} distinct cards seen, ${problems.size} problems")
        problems.forEach { log("PROBLEM $it") }
        log("FEEDPOS_RESULT $feed ${if (problems.isEmpty()) "PASS" else "FAIL"} cards=${seen.size} problems=${problems.size}")
        val dir = File(ctx.getExternalFilesDir(null), "feedpos").apply { mkdirs() }
        File(dir, "$feed.txt").writeText(report.toString())
        assertTrue("$feed: ${problems.size} position problems: ${problems.take(5)}", problems.isEmpty())
        assertTrue("$feed: scrolling showed no new cards (seen ${seen.size})", seen.size > first.size)
    }

    private fun videos(source: String) { tap("nav_tab_videos"); tap("video_source_switch"); tap("video_source_$source") }

    @Test fun home() = run("home") { tap("nav_tab_home") }
    @Test fun yumix() = run("yumix") { videos("online") }
    @Test fun youtube() = run("youtube") { videos("youtube") }
    @Test fun liveTv() = run("live") { videos("live") }
    @Test fun onlineMusic() = run("music") { tap("nav_tab_music"); tap("music_source_switch"); tap("music_source_online") }
}
