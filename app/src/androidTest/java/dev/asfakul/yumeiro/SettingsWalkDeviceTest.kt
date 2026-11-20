/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * G9 (R17): every switch in Settings, through the real UI, across a process death.
 *
 * Phase a (`-e phase a`): opens every Settings page that has switches (the categories, Quality and
 * audio, App lock & incognito, Tracking, Backup), finds every switch on it (all list items, not
 * only the first screen), flips each one and checks the switch really moved. Children first, so a
 * parent that hides them is flipped after them; switches that appear after a flip are walked too.
 * Then it writes what it flipped and kills its own process 3 s later, like Android does to a
 * backgrounded app (no clean stop).
 * Phase b (`-e phase b`, a new process): opens the same pages, checks every flipped switch shows
 * its new value (saved and read back at start), then flips it back to how it was.
 *
 * Not walked (they open system prompts or need an account): 18+ (confirm dialog), App lock and
 * Hide in recents (device lock prompt), UnifiedPush (needs the ntfy app), Last.fm scrobbling (only
 * shown when signed in), navigation-bar tabs (change the bar the test uses).
 * `scripts/device-check.sh` runs both phases. Log tag YuSettingsWalk.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class SettingsWalkDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val phase: String? get() = InstrumentationRegistry.getArguments().getString("phase")
    private val state get() = File(ctx.filesDir, "yu_settings_walk.txt")

    private fun log(s: String) = Log.i("YuSettingsWalk", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun pause(ms: Long) { val until = System.currentTimeMillis() + ms; runCatching { rule.waitUntil(ms + 2_000) { System.currentTimeMillis() > until } } }

    private val skip = setOf("setting_adult", "privacy_lock", "privacy_hide_recents", "toggle_push_alerts", "lastfm_scrobble_toggle")
    private fun skipped(tag: String) = tag in skip || tag.startsWith("nav_settings_tab_")

    private class Page(val name: String, val open: List<String>, val backs: Int)
    private val pages = listOf(
        // FINAL-47: one subject per page (Profiles, Video player, Video quality, Subtitles, Music, Downloads).
        Page("profiles", listOf("settings_cat_profiles"), 1),
        Page("appearance", listOf("settings_cat_appearance"), 1),
        Page("player", listOf("settings_cat_player"), 1),
        Page("video_quality", listOf("settings_cat_video_quality"), 1),
        Page("subtitles", listOf("settings_cat_subtitles"), 1),
        Page("music", listOf("settings_cat_music"), 1),
        Page("downloads", listOf("settings_cat_downloads"), 1),
        Page("notifications", listOf("settings_cat_notifications"), 1),
        Page("background", listOf("settings_cat_background"), 1),
        Page("network", listOf("settings_cat_network"), 1),
        Page("privacy", listOf("settings_cat_privacy"), 1),
        Page("tracking", listOf("settings_cat_tracking"), 1),
        Page("backup", listOf("settings_cat_backup"), 1),
    )

    // Run 43875418000: Settings home keeps its scroll, so the first category can be off the
    // list while home shows; the list's own tag says it is there.
    private fun atHome() = has("settings_home")

    private fun toSettingsHome() {
        if (atHome()) return
        FirstRun.tapVisible(rule, "topbar_settings_action")
        runCatching { rule.waitUntil(20_000) { atHome() } }
            .onFailure { throw AssertionError("Settings home never showed · ${FirstRun.onScreen(rule)}") }
    }

    private fun open(p: Page) {
        toSettingsHome()
        p.open.forEach { FirstRun.tapVisible(rule, it); pause(1_200) }
    }

    private fun close(p: Page) {
        repeat(p.backs) { FirstRun.shell("input keyevent 4"); pause(900) }
        if (!runCatching { rule.waitUntil(10_000) { atHome() }; true }.getOrDefault(false)) {
            log("SW back from ${p.name} did not reach Settings home; one more Back · ${FirstRun.onScreen(rule)}")
            FirstRun.shell("input keyevent 4"); pause(900)
            toSettingsHome()
        }
    }

    /** The vertical lists on screen that can scroll to an item index (the page's LazyColumn). */
    private val lists = hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    /** Every switch on the page (tag → on), walking all items of every list. */
    private fun switches(): LinkedHashMap<String, Boolean> {
        val out = LinkedHashMap<String, Boolean>()
        fun read() = runCatching {
            rule.onAllNodes(isToggleable(), true).fetchSemanticsNodes().forEach { n ->
                val tag = n.config.getOrElseNullable(SemanticsProperties.TestTag) { null } ?: return@forEach
                val st = n.config.getOrElseNullable(SemanticsProperties.ToggleableState) { null } ?: return@forEach
                if (tag !in out) out[tag] = st == ToggleableState.On
            }
        }
        read()
        val n = runCatching { rule.onAllNodes(lists, true).fetchSemanticsNodes().size }.getOrDefault(0)
        for (l in 0 until n) {
            var i = 0
            while (i < 40 && runCatching { rule.onAllNodes(lists, true)[l].performScrollToIndex(i); true }.getOrDefault(false)) {
                rule.waitForIdle(); read(); i++
            }
            runCatching { rule.onAllNodes(lists, true)[l].performScrollToIndex(0) }
        }
        rule.waitForIdle()
        return out
    }

    /** Brings [tag] on screen (scrolls the lists item by item) and returns its state, or null. */
    private fun find(tag: String): Boolean? {
        fun now(): Boolean? = runCatching {
            rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(SemanticsProperties.ToggleableState) { null }?.let { it == ToggleableState.On }
        }.getOrNull()
        now()?.let { return it }
        val n = runCatching { rule.onAllNodes(lists, true).fetchSemanticsNodes().size }.getOrDefault(0)
        for (l in 0 until n) {
            var i = 0
            while (i < 40 && runCatching { rule.onAllNodes(lists, true)[l].performScrollToIndex(i); true }.getOrDefault(false)) {
                rule.waitForIdle(); now()?.let { return it }; i++
            }
        }
        return null
    }

    /** Clicks [tag] and returns the state it shows 0.8 s later (null if it went away). */
    private fun flip(tag: String): Boolean? {
        find(tag) ?: return null
        // Run 43875418000: a click on a switch below the fold landed outside the window and did
        // nothing ("did not move" for every switch). tapVisible scrolls it on screen first.
        // Run 43877623222: Tracking and Backup are plain scrolling Columns (no list index to
        // scroll to), so the three switches below their fold never came on screen. performScrollTo
        // works for both kinds of parent.
        runCatching { rule.onAllNodesWithTag(tag, true)[0].performScrollTo(); rule.waitForIdle() }
        runCatching { FirstRun.tapVisible(rule, tag, 10_000) }.onFailure { log("SW click $tag failed: ${it.message?.take(160)}") }
        rule.waitForIdle(); pause(800)
        // A dialog the app opened by itself (permission rationale, guide) is closed like a user would.
        FirstRun.dismissPrompts(rule)
        return find(tag)
    }

    @Test fun a_flipEverySwitchThenDie() {
        assumeTrue("phase a runs from scripts/device-check.sh", phase == "a")
        state.delete()
        FirstRun.toTabs(rule)
        val lines = ArrayList<String>()
        val problems = ArrayList<String>()
        for (p in pages) {
            open(p)
            val first = switches()
            log("SW_PAGE ${p.name} switches=${first.map { (t, v) -> "$t=${if (v) "on" else "off"}" }}")
            FirstRun.shot("settings_walk_a_${p.name}")
            val done = HashSet<String>()
            var guard = 0
            while (guard++ < 30) {
                // Children first: the last switch on the page that is not done yet.
                val todo = switches().keys.filter { it !in done && !skipped(it) }
                val t = todo.lastOrNull() ?: break
                done += t
                val before = find(t) ?: continue
                val after = flip(t)
                when {
                    after == null -> { problems += "${p.name}/$t: gone after the click"; log("SW_FLIP ${p.name} $t ${before} → gone") }
                    after == before -> { problems += "${p.name}/$t: did not move"; log("SW_FLIP ${p.name} $t ${before} → $after (did not move)") }
                    else -> { lines += "${p.name}|$t|$before|$after"; log("SW_FLIP ${p.name} $t $before → $after") }
                }
            }
            first.keys.filter(::skipped).forEach { log("SW_SKIP ${p.name} $it (see the class comment)") }
            close(p)
        }
        state.writeText(lines.joinToString("\n") + "\n")
        log("SW_A flipped=${lines.size} problems=$problems")
        assertTrue("switches that did not work: $problems", problems.isEmpty())
        assertTrue("no switch found at all · ${FirstRun.onScreen(rule)}", lines.size >= 20)
        pause(3_000) // a backgrounded app lives on for a while; then Android kills it
        log("SW_A killing the process now")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun b_everySwitchKeptItsValue() {
        assumeTrue("phase b runs from scripts/device-check.sh", phase == "b")
        assumeTrue("phase a left no state", state.exists())
        val rows = state.readLines().filter { it.isNotBlank() }.map { it.split('|') }
        state.delete()
        FirstRun.toTabs(rule)
        val problems = ArrayList<String>()
        var verified = 0
        for (p in pages) {
            val mine = rows.filter { it[0] == p.name }
            if (mine.isEmpty()) continue
            open(p)
            FirstRun.shot("settings_walk_b_${p.name}")
            val pending = mine.map { it[1] }.toMutableList()
            val restored = HashSet<String>()
            var rounds = 0
            while (pending.isNotEmpty() && rounds++ < 8) {
                for (r in mine) {
                    val t = r[1]; if (t !in pending) continue
                    val now = find(t) ?: continue
                    pending -= t; verified++
                    val want = r[3].toBoolean()
                    if (now != want) { problems += "${p.name}/$t: shows $now after restart, set to $want"; log("SW_KEPT ${p.name} $t FAIL shows=$now set=$want") }
                    else log("SW_KEPT ${p.name} $t ok ($now)")
                }
                if (pending.isEmpty()) break
                // A parent hides the rest: put the last flipped (a parent) back first, then look again.
                mine.reversed().firstOrNull { it[1] !in pending && it[1] !in restored }?.let { r ->
                    restored += r[1]; val back = flip(r[1]); log("SW_RESTORE ${p.name} ${r[1]} → $back (to reveal $pending)")
                } ?: break
            }
            pending.forEach { problems += "${p.name}/$it: not on the page after restart"; log("SW_KEPT ${p.name} $it MISSING") }
            // Back to how the phone had it.
            for (r in mine) {
                val t = r[1]; if (t in restored) continue
                val orig = r[2].toBoolean()
                val now = find(t) ?: continue
                if (now != orig) flip(t)?.let { if (it != orig) log("SW_RESTORE ${p.name} $t could not go back to $orig") }
            }
            close(p)
        }
        log("SW_B verified=$verified of ${rows.size} problems=$problems")
        log("SW_RESULT ${if (problems.isEmpty()) "PASS" else "FAIL"} verified=$verified")
        assertTrue("switches that did not keep their value: $problems", problems.isEmpty())
    }
}
