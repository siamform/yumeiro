/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Build
import android.os.StrictMode
import android.os.SystemClock
import android.os.strictmode.Violation
import android.util.Log
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Item
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadsScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * G8 D8 (Downloads screen) on a device. The page is opened with the list unloaded and then used
 * (open a folder, play an episode, keep one forever, delete a folder) under a main-thread
 * StrictMode listener. Before the fix the list was read on the main thread when the page opened,
 * and every tap saved the whole list with an fsync on the main thread (once per item for a
 * folder) and removed the files there too. Then the list is read back from disk: every change made
 * on the screen must be in the file. Log tag YuDlScreen, DLSCREEN_CASE / DLSCREEN_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class DownloadsScreenDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext.applicationContext
    private val seen = Collections.synchronizedList(mutableListOf<String>())
    private var old: StrictMode.ThreadPolicy? = null
    private var before: List<Item> = emptyList()
    private var tipsBefore = true
    private val ours = listOf("dev.asfakul.yumeiro.online.download.", "dev.asfakul.yumeiro.ui.screens.downloads.", "dev.asfakul.yumeiro.data.SafeJsonFile")

    private fun ep(id: String, label: String, folder: String, order: Int) = Item(
        id, folder, "D8Api", "u-$folder", label, null, emptyMap(), "", false, emptyMap(), "d8-$id.mp4", status = Status.DONE,
        bytes = 4096, total = 4096, kind = OnlineDownloads.Kind.EPISODE, folder = folder, order = order,
        finishedAt = System.currentTimeMillis() - 60_000, quality = "720p",
    )

    @Before fun setUp() {
        // G8 regression (tv-api24 44299923467): the whole class was skipped below API 28. Only the
        // StrictMode listener needs 28; the page itself (open, play, keep, delete) runs on every image.
        SystemClock.sleep(3000) // let the start-up warm-up finish first
        // The tips card pushes the folders down; on a small screen the opened rows would sit below
        // the fold and a lazy grid does not build them. Hide it (restored after) and scroll as a user would.
        DownloadSettings.init(ctx); tipsBefore = DownloadSettings.showTips.value; DownloadSettings.setShowTips(ctx, false)
        OnlineDownloads.ensureLoaded(ctx)
        before = OnlineDownloads.items.value
        val items = listOf(ep("e1", "Episode 1", "D8 Show", 0), ep("e2", "Episode 2", "D8 Show", 1), ep("o1", "Episode 1", "D8 Other", 0))
        items.forEach { OnlineDownloads.fileOf(ctx, it).writeBytes(ByteArray(4096)) }
        // Instrumentation thread: this save is done before it returns, so the file holds the three.
        OnlineDownloads.setItemsForTest(ctx, items)
        OnlineDownloads.setKeep(ctx, "e1", false)
        OnlineDownloads.unloadForTests()
        if (strict) inst.runOnMainSync {
            old = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .penaltyListener({ it.run() }) { v: Violation -> seen += Log.getStackTraceString(v) }.build())
        }
    }

    @After fun tearDown() {
        inst.runOnMainSync { old?.let { StrictMode.setThreadPolicy(it) } }
        DownloadSettings.setShowTips(ctx, tipsBefore)
        runCatching { OnlineDownloads.flushForTests() }
        listOf("e1", "e2", "o1").forEach { id -> OnlineDownloads.get(id)?.let { runCatching { OnlineDownloads.fileOf(ctx, it).delete() } } }
        OnlineDownloads.setItemsForTest(ctx, before)
        OnlineDownloads.setKeep(ctx, before.firstOrNull()?.id ?: "-", before.firstOrNull()?.keep ?: false) // saves `before`
    }

    private fun list() = rule.onNodeWithTag("downloads_list")

    private fun ourViolations() = synchronized(seen) { seen.filter { st -> ours.any { it in st } } }

    private val strict = Build.VERSION.SDK_INT >= 28

    private fun report(case: String) {
        if (!strict) { Log.i(TAG, "DLSCREEN_CASE $case main-thread disk: not measured (API ${Build.VERSION.SDK_INT} < 28)"); return }
        val mine = ourViolations()
        Log.i(TAG, "DLSCREEN_CASE $case main-thread disk calls from downloads code: ${mine.size} (all: ${seen.size})")
        mine.take(3).forEach { Log.i(TAG, "DLSCREEN_CASE $case stack: " + it.lines().take(16).joinToString(" < ")) }
        assertTrue("$case: ${mine.size} main-thread disk calls from the downloads code:\n" + mine.firstOrNull().orEmpty().take(2500), mine.isEmpty())
    }

    @Test fun pageOpensAndActsWithoutMainThreadDiskAndKeepsEveryChange() {
        var played: VideoItem? = null
        rule.setContent { MTPlayerTheme { DownloadsScreen(onBack = {}, onPlayVideo = { played = it }, onBrowse = {}) } }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("library_download_folder").fetchSemanticsNodes().size == 2 }
        rule.onNodeWithTag("downloads_screen").assertExists()
        report("a_open")

        // Open "D8 Show" and play its first episode.
        rule.onNodeWithText("D8 Show").performClick()
        rule.waitForIdle()
        list().performScrollToNode(hasText("Episode 2"))
        rule.waitForIdle()
        Log.i(TAG, "DLSCREEN_CASE b_open rows=${rule.onAllNodesWithTag("library_download").fetchSemanticsNodes().size} folders=${rule.onAllNodesWithTag("library_download_folder").fetchSemanticsNodes().size}")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("library_download").fetchSemanticsNodes().size == 2 }
        rule.onAllNodesWithTag("library_download")[0].performClick()
        rule.waitUntil(5_000) { played != null }
        Log.i(TAG, "DLSCREEN_CASE b_play uri=${played?.uri}")
        assertTrue("plays the saved file: ${played?.uri}", played!!.uri.startsWith("file:") && played!!.uri.endsWith("d8-e1.mp4"))

        // Keep episode 2 forever.
        rule.onAllNodesWithTag("library_download_keep")[1].performClick()
        rule.waitUntil(5_000) { OnlineDownloads.get("e2")?.keep == true }

        // Delete the other folder (asks first).
        list().performScrollToNode(hasText("D8 Other"))
        // The delete button is a child of the folder row (merged tree), not a sibling of its title.
        rule.onNode(hasContentDescription("Delete folder") and hasParent(hasTestTag("library_download_folder") and hasText("D8 Other", substring = true))).performClick()
        rule.onNodeWithText("Delete", substring = false).performClick()
        rule.waitUntil(5_000) { OnlineDownloads.get("o1") == null }
        rule.waitForIdle()
        report("b_actions")

        // Every change is on disk, and the deleted episode's file is gone.
        OnlineDownloads.flushForTests()
        val o1File = OnlineDownloads.fileOf(ctx, ep("o1", "Episode 1", "D8 Other", 0))
        OnlineDownloads.unloadForTests(); OnlineDownloads.ensureLoaded(ctx)
        val back = OnlineDownloads.items.value.associateBy { it.id }
        Log.i(TAG, "DLSCREEN_CASE c_reload ids=${back.keys} e2.keep=${back["e2"]?.keep} o1File=${o1File.exists()}")
        assertNotNull("e1 kept", back["e1"])
        assertEquals("keep saved", true, back["e2"]?.keep)
        assertFalse("deleted item stays deleted", "o1" in back)
        assertFalse("its file is removed", o1File.exists())
        Log.i(TAG, "DLSCREEN_RESULT PASS")
    }

    private companion object { const val TAG = "YuDlScreen" }
}
