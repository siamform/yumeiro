/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import dev.asfakul.yumeiro.diagnostics.CrashLog
import dev.asfakul.yumeiro.diagnostics.MemoryWatch
import dev.asfakul.yumeiro.online.ReleaseTags
import dev.asfakul.yumeiro.text.RegexCache
import dev.asfakul.yumeiro.ui.components.Kana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** R15 Phase 1: the regex-free accent lookup keeps the old word-boundary rules; caches are bounded. */
class KanaAndCachesTest {
    /** The pattern Kana used before R15, kept here only to prove the new scan matches it. */
    private fun old(title: String, key: String) = Regex("(^|[^a-z])" + Regex.escape(key) + "($|[^a-z])").containsMatchIn(title.lowercase())

    @Test fun wordScanMatchesOldRegexSemantics() {
        val titles = listOf("Home", "Settings", "Watch together", "Live TV", "Downloads (3)", "My downloads", "Homework", "youtube music",
            "Look & feel", "Source status", "sources", "re-search", "Lyrics:", "", "A", "Equalizer!", "homes", "playlists", "TV")
        val keys = listOf("home", "settings", "watch together", "live tv", "downloads", "download", "look & feel", "source", "sources", "search", "lyrics", "equalizer", "playlist", "music", "youtube")
        for (t in titles) for (k in keys) assertEquals("$t / $k", old(t, k), Kana.containsWord(t.lowercase(), k))
    }

    @Test fun forTitleResults() {
        assertEquals("夢色", Kana.forTitle("Home"))
        assertEquals("一緒に", Kana.forTitle("Watch together"))
        assertEquals("保存", Kana.forTitle("My downloads"))
        assertNull(Kana.forTitle("Homework"))
        assertNull(Kana.forTitle(""))
        // Cached answer is stable.
        assertEquals(Kana.forTitle("Settings"), Kana.forTitle("Settings"))
    }

    @Test fun forTitleIsCheapEnoughForComposition() {
        // 10 000 lookups across 50 titles: the old version compiled ~60 regexes per call.
        val titles = (0 until 50).map { "Section $it trending now" }
        val t0 = System.nanoTime()
        repeat(200) { titles.forEach { Kana.forTitle(it) } }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("10k lookups took ${ms}ms", ms < 500)
    }

    @Test fun regexCacheReusesAndIsBounded() {
        RegexCache.clear()
        val a = RegexCache.get("""s0*1e0*2(?!\d)""")
        assertSame(a, RegexCache.get("""s0*1e0*2(?!\d)"""))
        repeat(500) { RegexCache.get("x$it") }
        assertTrue(RegexCache.size() <= 128)
    }

    @Test fun releaseTagsAreCachedAndUnchanged() {
        val first = ReleaseTags.parse("Show S01E01 1080p WEB-DL Dual Audio Hindi English")
        val again = ReleaseTags.parse("Show S01E01 1080p WEB-DL Dual Audio Hindi English")
        assertSame(first, again)
        assertTrue(first.any { it.label.contains("1080") })
    }

    @Test fun backgroundMemoryKillIsNotReportedAsACrash() {
        assertEquals("Out of memory", CrashLog.exitKind(ApplicationExitInfo.REASON_LOW_MEMORY, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND))
        assertEquals("Out of memory", CrashLog.exitKind(ApplicationExitInfo.REASON_LOW_MEMORY, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE))
        assertEquals("Out of memory", CrashLog.exitKind(ApplicationExitInfo.REASON_LOW_MEMORY, ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE))
        // FINAL-50: Android reclaiming a cached / background process is not a crash report at all.
        assertNull(CrashLog.exitKind(ApplicationExitInfo.REASON_LOW_MEMORY, ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED))
        assertNull(CrashLog.exitKind(ApplicationExitInfo.REASON_LOW_MEMORY, ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE))
        assertEquals("Freeze", CrashLog.exitKind(ApplicationExitInfo.REASON_ANR, 100))
        assertNull(CrashLog.exitKind(ApplicationExitInfo.REASON_USER_REQUESTED, 100))
    }

    @Test fun repeatedTrimCallbacksAreSampledOnce() {
        // FINAL-50: ~15 TRIM_MEMORY_BACKGROUND callbacks within one second gave ~15 samples.
        val gate = MemoryWatch.TrimGate(5_000L)
        assertEquals(0, gate.admit("background", 1_000L))
        repeat(14) { assertNull(gate.admit("background", 1_000L + it * 60L)) }
        assertEquals(0, gate.admit("ui_hidden", 1_500L))
        assertEquals(14, gate.admit("background", 6_100L))
        assertEquals(0, gate.admit("background", 11_200L))
    }

    @Test fun threadFamilies() {
        assertEquals("OkHttp", MemoryWatch.groupOf("OkHttp https://example.com/..."))
        assertEquals("Dispatcher", MemoryWatch.groupOf("DefaultDispatch"))
        assertEquals("Yumeiro", MemoryWatch.groupOf("yu-memwatch"))
    }
}
