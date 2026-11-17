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
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.sources.manga.MangaLibrary
import dev.asfakul.yumeiro.ui.screens.library.ReadingLibraryContent
import dev.asfakul.yumeiro.ui.screens.library.VideoLibraryContent
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * G5 LB-7 (tabs): the Video and Reading library tabs load four stores (bookmarks, downloads,
 * history, manga library) when they open. They used to read the files on the main thread. With the
 * stores unloaded, both tabs are composed under a main-thread StrictMode listener; any disk call
 * whose stack goes through one of the four stores fails the test. Log tag YuLibTabs.
 */
@RunWith(AndroidJUnit4::class)
class LibraryTabsMainThreadDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val stores = listOf("OnlineBookmarks", "OnlineDownloads", "OnlineHistory", "MangaLibrary")

    @Test fun tabsLoadStoresOffMain() {
        assumeTrue("penaltyListener needs API 28", Build.VERSION.SDK_INT >= 28)
        SystemClock.sleep(3000) // let the start-up warm-up finish before the stores are unloaded
        MangaLibrary.unloadForTests(); OnlineBookmarks.unloadForTests(); OnlineDownloads.unloadForTests(); OnlineHistory.unloadForTests()
        val seen = Collections.synchronizedList(mutableListOf<String>())
        var old: StrictMode.ThreadPolicy? = null
        inst.runOnMainSync {
            old = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .penaltyListener({ it.run() }) { v: Violation -> seen += Log.getStackTraceString(v) }.build())
        }
        try {
            rule.setContent {
                Column {
                    VideoLibraryContent(deviceContinue = emptyList(), onOpenTitle = {}, onPlayVideo = {}, onBrowseOnline = {}, onOpenHistory = {})
                    ReadingLibraryContent(onBrowse = {}, onOpenTitle = {}, onOpenSources = {})
                }
            }
            rule.waitForIdle()
            SystemClock.sleep(1500)
            rule.waitForIdle()
        } finally {
            inst.runOnMainSync { old?.let { StrictMode.setThreadPolicy(it) } }
        }
        val ours = seen.filter { st -> stores.any { it in st } }
        Log.i("YuLibTabs", "LIB_TABS violations from the stores: ${ours.size} (all: ${seen.size})")
        ours.take(3).forEach { Log.i("YuLibTabs", "LIB_TABS stack: " + it.lines().take(14).joinToString(" < ")) }
        assertTrue("LB-7: ${ours.size} main-thread disk calls from the library stores:\n" + ours.firstOrNull().orEmpty().take(2500), ours.isEmpty())
        Log.i("YuLibTabs", "LIB_TABS_RESULT PASS")
    }
}
