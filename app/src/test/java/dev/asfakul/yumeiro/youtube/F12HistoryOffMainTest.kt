/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F12-HIST (ledger F-12): `YtHistory.add` reads the saved list from disk on first use and encodes JSON on every
 * call. Screens called it on the main thread (Shorts page settle, YouTube Music taps, YouTubePlayback on
 * Main.immediate): StrictMode DiskReadViolation in device run 44910278641. Screens must call `YtHistory.record`,
 * which runs on one background lane. Fail-before: five main-thread call sites.
 */
class F12HistoryOffMainTest {
    @Test fun noScreenWritesYouTubeHistoryOnTheMainThread() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val files = File(root, "dev/asfakul/yumeiro/ui").walkTopDown().filter { it.isFile && it.extension == "kt" } +
            sequenceOf(File(root, "dev/asfakul/yumeiro/youtube/YouTubePlayback.kt"))
        val hits = ArrayList<String>()
        files.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (line.contains("YtHistory.add(")) hits += "${f.name}:${i + 1}: ${line.trim().take(120)}"
            }
        }
        assertTrue("YtHistory.add on the main thread (use YtHistory.record):\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
