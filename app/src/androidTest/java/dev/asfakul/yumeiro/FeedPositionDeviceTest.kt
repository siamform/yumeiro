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
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.ui.feed.FeedCardKey
import dev.asfakul.yumeiro.ui.feed.FeedSlotKey
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
        FirstRun.toTabs(rule) // taps "Skip" on the welcome guide, as a user does
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

    private fun tap(tag: String) { FirstRun.tapVisible(rule, tag); idle(600) }

    private fun onScreen(): String = FirstRun.onScreen(rule)

    private val screen by lazy { rule.onAllNodes(androidx.compose.ui.test.isRoot(), true).fetchSemanticsNodes().maxByOrNull { it.size.height }!!.size }

    /** Cards that intersect the screen (pager pages beside the current one sit wholly outside it). */
    /** Keys shown more than once in the composed feed (the same title in two shelves). */
    /** Swipe steps where no card was on screen both before and after (offset unknown). */
    private val lostTrack = java.util.Collections.synchronizedList(ArrayList<String>())
    private val duplicates = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    @Volatile private var unplacedSeen = 0
    /** Every slot each title was seen in (a title in more than one shelf is reported, not failed). */
    private val titleSlots = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()

    private fun snap(): List<Card> {
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(FeedCardKey), true).fetchSemanticsNodes()
        // A lazy list composes the next items ahead of time (prefetch) and keeps a few for reuse;
        // those are not placed, nothing is drawn for them, and their reported position is stale.
        // Only cards that are placed (with every parent placed) are on the user's screen.
        val placed = nodes.filter { n -> generateSequence(n) { it.parent }.all { it.layoutInfo.isPlaced } }
        unplacedSeen += nodes.size - placed.size
        val all = placed
            .mapNotNull { n ->
                val title = n.config.getOrElseNullable(FeedCardKey) { null } ?: return@mapNotNull null
                // R16: a card's identity is the title *in its slot* (shelf / grid row). The same title
                // in two shelves is two cards; runs 43671202859–43673890077 failed because a second
                // copy further down looked like the first one jumping 1,800 px.
                val slot = n.config.getOrElseNullable(FeedSlotKey) { null }.orEmpty()
                if (slot.isNotEmpty()) titleSlots.getOrPut(title) { LinkedHashSet() }.add(slot)
                val k = if (slot.isEmpty()) title else "$title@$slot"
                val p = n.positionInRoot
                Card(k, p.x, p.y, n.size.width, n.size.height)
            }
        // Two copies of one key can't be told apart from one card that jumped: leave them out of
        // the position checks (they are reported on their own as duplicates).
        all.groupBy { it.key }.filterValues { it.size > 1 }.keys.let { duplicates += it }
        return all.filter { it.key !in duplicates }
            .filter { it.x + it.w > 0 && it.x < screen.width && it.y + it.h > 0 && it.y < screen.height && it.w > 0 && it.h > 0 }
    }

    /** Compares two snapshots; returns the problems found (empty = the step is clean). */
    private fun compare(step: String, a: List<Card>, b: List<Card>, still: Boolean, swipeUp: Boolean?): List<String> {
        val problems = ArrayList<String>()
        val ua = a.groupBy { it.key }.filterValues { it.size == 1 }.mapValues { it.value[0] }
        val ub = b.groupBy { it.key }.filterValues { it.size == 1 }.mapValues { it.value[0] }
        val common = ua.keys intersect ub.keys
        // R16: no card in both snapshots means the page offset is unknown (the swipe travelled more
        // than a screen). Judging "vanished in plain view" with an assumed offset of 0 reported every
        // old card as replaced (run 43680730905, api33 Home). Such steps are counted, not judged.
        if (common.isEmpty() && swipeUp != null && a.isNotEmpty() && b.isNotEmpty()) { lostTrack += step; return problems }
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
            if (abs(ddy) > 4f) problems += "$step: card $k moved ${cb.y - ca.y}px while the page moved ${dy}px (insert/remove above it)" +
                " · y ${ca.y.toInt()}→${cb.y.toInt()} x ${ca.x.toInt()} ${ca.w}x${ca.h} · all moves ${common.associateWith { (ub[it]!!.y - ua[it]!!.y).toInt() }}"
            // Horizontal rows only move sideways when the user swipes them; this test swipes vertically.
            if (abs(ddx) > 4f) problems += "$step: card $k slid sideways ${ddx}px"
        }
        val top = screen.height * 0.15f; val bottom = screen.height * 0.85f
        val keysB = b.map { it.key }.toSet()
        for (ca in a) {
            if (ca.key in keysB || ca.key in duplicates) continue
            val ny = ca.y + dy
            val inPlainView = ny >= top && ny + ca.h <= bottom && ca.x >= 0 && ca.x + ca.w <= screen.width
            if (inPlainView) {
                val took = b.firstOrNull { abs(it.y - ny) < ca.h / 2f && abs(it.x - ca.x) < ca.w / 2f }
                problems += "$step: card ${ca.key} vanished in plain view at y=${ny.toInt()}" + (took?.let { " — replaced by ${it.key}" } ?: "")
            }
        }
        return problems
    }

    /**
     * A real finger swipe through the system input pipeline (`input swipe`), in the middle of the
     * screen. Run 43664577766 showed Compose-injected swipes on the root did not scroll Music.
     */
    private fun swipe(up: Boolean) {
        val x = screen.width / 2
        val a = (screen.height * 0.70f).toInt(); val b = (screen.height * 0.40f).toInt()
        val (y1, y2) = if (up) a to b else b to a
        FirstRun.dismissPrompts(rule)
        // R16: a drag that stops before the finger lifts. `input swipe` lifts while still moving, so on
        // the slow api33 emulator (frames of 360 ms to 1.3 s) the system read it as a fling and the
        // page travelled about three screens (run 43680730905). A held end gives zero lift-off speed.
        val steps = 8
        if (android.os.Build.VERSION.SDK_INT < 29) { heldDragInjected(x, y1, y2, steps); return }
        FirstRun.shell("input motionevent DOWN $x $y1")
        for (k in 1..steps) FirstRun.shell("input motionevent MOVE $x ${y1 + (y2 - y1) * k / steps}")
        SystemClock.sleep(300)
        FirstRun.shell("input motionevent MOVE $x $y2")
        SystemClock.sleep(150)
        FirstRun.shell("input motionevent UP $x $y2")
    }

    private var probedMotionevent = false

    /**
     * X13-5: the same held drag below API 29. api28 43777260888: every swipe moved the feed 0/16
     * times (live, yumix, music skipped; home and youtube never reached 3 cards), because the shell
     * `input` tool on API 28 does not take `motionevent`. UiAutomation injects through the same
     * system input pipeline on every API level.
     */
    private fun heldDragInjected(x: Int, y1: Int, y2: Int, steps: Int) {
        if (!probedMotionevent) {
            probedMotionevent = true
            log("API ${android.os.Build.VERSION.SDK_INT}: shell `input motionevent` says: ${FirstRun.shell("input motionevent UP 1 1").trim().take(200).ifEmpty { "(nothing)" }}")
        }
        val ua = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Int) {
            val ev = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
            ev.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            ua.injectInputEvent(ev, true); ev.recycle()
        }
        send(android.view.MotionEvent.ACTION_DOWN, y1)
        for (k in 1..steps) { SystemClock.sleep(30); send(android.view.MotionEvent.ACTION_MOVE, y1 + (y2 - y1) * k / steps) }
        SystemClock.sleep(300)
        send(android.view.MotionEvent.ACTION_MOVE, y2)
        SystemClock.sleep(150)
        send(android.view.MotionEvent.ACTION_UP, y2)
    }

    private fun run(feed: String, open: () -> Unit) {
        log("feed $feed: device ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT} screen=${screen.width}x${screen.height}")
        open()
        // Some feeds start below the fold (Home puts shortcuts first): scroll down until cards show.
        val until = SystemClock.uptimeMillis() + 120_000
        var shown = false
        while (SystemClock.uptimeMillis() < until) {
            if (snap().size >= 3) { shown = true; break }
            idle(4_000)
            if (snap().size in 0..3) swipe(up = true)
            idle(1_500)
        }
        val first = snap()
        log("feed $feed: ${first.size} cards on screen at start: ${first.take(6).map { it.key }}")
        FirstRun.shot("feed_$feed")
        if (!shown) log("feed $feed: no cards · ${onScreen()}")
        assertTrue("$feed: the feed never showed 3 cards in 120 s (cards=${first.size}) · ${onScreen()}", shown)
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
        var moved = 0
        val steps = listOf(true, true, true, true, true, true, true, true, true, true, false, false, false, false, false, false)
        steps.forEachIndexed { i, up ->
            swipe(up); idle(1_500)
            val now = snap()
            val p = compare("$feed/swipe${i + 1}${if (up) "↑" else "↓"}", prev, now, still = false, swipeUp = up)
            problems += p
            if (p.isNotEmpty()) {
                log("feed $feed/swipe${i + 1} before: " + prev.joinToString { "${it.key}@${it.x.toInt()},${it.y.toInt()}" })
                log("feed $feed/swipe${i + 1} after:  " + now.joinToString { "${it.key}@${it.x.toInt()},${it.y.toInt()}" })
                FirstRun.shot("feed_${feed}_problem${i + 1}")
            }
            if (prev.any { c -> now.none { it.key == c.key && abs(it.y - c.y) < 2f } }) moved++
            seen.addAll(now.map { it.key })
            log("feed $feed/swipe${i + 1}: ${now.size} cards, page moved ${now.firstOrNull()?.let { c -> prev.firstOrNull { it.key == c.key }?.let { (c.y - it.y).toInt() } } ?: "?"}px, problems=${p.size}")
            prev = now
            if (i == 9) stillCheck("idle-deep")
        }
        stillCheck("idle-end")
        log("feed $feed: ${seen.size} distinct cards seen, ${problems.size} problems, $unplacedSeen unplaced (prefetched) card nodes ignored, duplicate keys ${duplicates.size} ${duplicates.toList()}")
        titleSlots.filterValues { it.size > 1 }.forEach { (t, sl) -> log("feed $feed: same title in ${sl.size} slots (allowed): $t in $sl") }
        val lost = lostTrack.filter { it.startsWith("$feed/") }
        if (lost.isNotEmpty()) log("feed $feed: ${lost.size} swipes moved more than a screen (no shared card, not judged): $lost")
        problems.forEach { log("PROBLEM $it") }
        log("FEEDPOS_RESULT $feed ${if (problems.isNotEmpty()) "FAIL" else if (moved < steps.size * 3 / 4) "SKIP(not exercised)" else "PASS"} cards=${seen.size} problems=${problems.size} moved=$moved/${steps.size}")
        val dir = File(ctx.getExternalFilesDir(null), "feedpos").apply { mkdirs() }
        File(dir, "$feed.txt").writeText(report.toString())
        assertTrue("$feed: ${problems.size} position problems: ${problems.take(5)}", problems.isEmpty())
        assertTrue("$feed: ${lost.size} swipes could not be followed (more than 2): $lost", lost.size <= 2)
        // Too few cards to scroll is not a position result either way: 43697438813 (api33) had 10
        // cards while the drama site queued calls for 150-200 s. Reported as skipped, not passed.
        org.junit.Assume.assumeTrue("$feed: only $moved of ${steps.size} swipes scrolled the feed (${seen.size} cards); not exercised, skipped", moved >= steps.size * 3 / 4)
        assertTrue("$feed: scrolling showed no new cards (seen ${seen.size})", seen.size > first.size)
    }

    // Each feed is opened through Home's own shortcut cards, as a user does.
    private fun fromHome(card: String) { tap("nav_tab_home"); tap(card) }

    @Test fun home() = run("home") { tap("nav_tab_home") }
    @Test fun yumix() = run("yumix") { fromHome("quick_card_hues") }
    @Test fun youtube() = run("youtube") { fromHome("quick_card_youtube") }
    /** NBUG-15 (ADR-028): Live TV's feed is one long grid merged from every playlist, by category. */
    @Test fun liveTv() = run("live") {
        fromHome("quick_card_live")
        rule.waitUntil(120_000) { rule.onAllNodesWithTag("live_channel", true).fetchSemanticsNodes().isNotEmpty() }
        log("live channels on screen: ${rule.onAllNodesWithTag("live_channel", true).fetchSemanticsNodes().size}")
        idle(1_000)
    }
    @Test fun onlineMusic() = run("music") { fromHome("quick_card_ytmusic") }

    /** The "Watch from" menu behind the Videos title (run 43665792255: it never showed its options). */
    @Test fun videoSourceMenuSwitchesSource() {
        tap("nav_tab_videos"); idle(1_500)
        val switches = rule.onAllNodesWithTag("video_source_switch", true).fetchSemanticsNodes().map { it.boundsInRoot }
        log("video_source_switch nodes: $switches")
        tap("video_source_switch")
        val open = runCatching { rule.waitUntil(8_000) { rule.onAllNodesWithTag("video_source_menu", true).fetchSemanticsNodes().isNotEmpty() }; true }.getOrDefault(false)
        FirstRun.shot("video_source_menu")
        log("menu open=$open · ${onScreen()}")
        assertTrue("tapping the Videos title did not open the \"Watch from\" menu · ${onScreen()}", open)
        tap("video_source_youtube")
        val yt = runCatching { rule.waitUntil(60_000) { snap().any { it.key.startsWith("youtube|") } }; true }.getOrDefault(false)
        assertTrue("picking YouTube in the menu did not show YouTube videos · ${onScreen()}", yt)
    }
}
