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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaLibrary
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * G5 LB-2 on the device: the reader saves reading progress on every page turn, on the main
 * thread. After the library is loaded (the screens load it on IO), 20 page turns on main must not
 * touch the disk from MangaLibrary. A main-thread StrictMode policy with a listener records every
 * disk read or write; only stacks through MangaLibrary count. Log tag YuMangaMain.
 */
@RunWith(AndroidJUnit4::class)
class MangaLibraryMainThreadDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext

    @Test fun pageTurnsDoNoDiskWorkOnMain() {
        assumeTrue("penaltyListener needs API 28", Build.VERSION.SDK_INT >= 28)
        val item = MangaItem("yu_test_provider", "https://example.invalid/yu-manga-main", "Main thread test", null)
        val chapter = MangaChapter(url = "https://example.invalid/yu-manga-main/1", name = "Chapter 1")
        MangaLibrary.load(ctx) // off main, as the screens do
        val seen = Collections.synchronizedList(mutableListOf<String>())
        var old: StrictMode.ThreadPolicy? = null
        inst.runOnMainSync {
            old = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .penaltyListener({ it.run() }) { v: Violation -> seen += Log.getStackTraceString(v) }.build())
        }
        try {
            repeat(20) { page ->
                inst.runOnMainSync { MangaLibrary.progress(ctx, item, chapter, page, finished = page == 19) }
                SystemClock.sleep(50)
            }
            SystemClock.sleep(500)
        } finally {
            inst.runOnMainSync { old?.let { StrictMode.setThreadPolicy(it) } }
            MangaLibrary.flush()
            MangaLibrary.remove(ctx, MangaLibrary.get(item.providerId, item.url)?.key ?: "")
            MangaLibrary.flush()
        }
        val ours = seen.filter { "MangaLibrary" in it }
        Log.i("YuMangaMain", "MANGA_MAIN violations from MangaLibrary: ${ours.size} (all: ${seen.size})")
        ours.take(2).forEach { Log.i("YuMangaMain", "MANGA_MAIN stack: " + it.lines().take(12).joinToString(" < ")) }
        assertTrue("LB-2: ${ours.size} main-thread disk calls from MangaLibrary:\n" + ours.firstOrNull().orEmpty().take(2500), ours.isEmpty())
        Log.i("YuMangaMain", "MANGA_MAIN_RESULT PASS")
    }
}
