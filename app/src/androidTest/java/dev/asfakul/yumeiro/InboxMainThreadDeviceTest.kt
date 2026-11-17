/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.StrictMode
import android.os.strictmode.Violation
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.notify.Inbox
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/**
 * G13 N7-1: the inbox taps (mark read, mark all read, remove) must not write the inbox file on the
 * main thread, and what they change must still reach the file. StrictMode is set in setUp and
 * restored in tearDown (separate main-thread messages, so violations are delivered).
 * Old code: SafeJsonFile.write + fsync on main for every tap. Log tag YuInboxTest, INBOX_.
 */
@RunWith(AndroidJUnit4::class)
class InboxMainThreadDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext.applicationContext
    private val seen = Collections.synchronizedList(mutableListOf<String>())
    private var old: StrictMode.ThreadPolicy? = null
    private fun log(s: String) = Log.i("YuInboxTest", s)
    private val ids = (1..3).map { "n7-test-$it-${System.nanoTime()}" }

    @Before fun setUp() {
        assumeTrue("penaltyListener needs API 28", android.os.Build.VERSION.SDK_INT >= 28)
        Inbox.ensureLoaded(ctx) // load off main, as the app's warm-up does
        ids.forEachIndexed { i, id -> Inbox.add(ctx, Inbox.Item(id, Inbox.Kind.INFO, "N7 test $i", "body", System.currentTimeMillis() + i)) }
        inst.runOnMainSync {
            old = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .penaltyListener({ it.run() }) { v: Violation -> seen += Log.getStackTraceString(v) }.build())
        }
    }

    @After fun tearDown() {
        inst.runOnMainSync { old?.let { StrictMode.setThreadPolicy(it) } }
        ids.forEach { Inbox.remove(ctx, it) }
    }

    private fun fileItems(): Map<String, Boolean> {
        val a = JSONArray(File(ctx.filesDir, "inbox.json").readText())
        return (0 until a.length()).map { a.getJSONObject(it) }.associate { it.getString("id") to it.optBoolean("read") }
    }

    @Test fun inboxTapsWriteOffTheMainThreadAndStillSave() {
        seen.clear()
        inst.runOnMainSync { Inbox.markRead(ctx, ids[0]) }
        inst.runOnMainSync { Inbox.remove(ctx, ids[1]) }
        inst.runOnMainSync { Inbox.markAllRead(ctx) }
        inst.waitForIdleSync()
        Inbox.awaitSavedForTests()
        inst.waitForIdleSync()
        val mine = synchronized(seen) { seen.filter { "dev.asfakul.yumeiro.notify.Inbox" in it } }
        val f = fileItems()
        log("INBOX_CASE a mainDisk=${mine.size} (all: ${seen.size}) file: ${ids.map { it.takeLast(6) + "=" + f[it] }}")
        mine.take(2).forEach { log("INBOX_CASE a stack: " + it.lines().take(14).joinToString(" < ")) }
        assertEquals("a: read mark not saved", true, f[ids[0]])
        assertTrue("a: removed item still saved", ids[1] !in f)
        assertEquals("a: mark-all-read not saved", true, f[ids[2]])
        assertTrue("a: ${mine.size} main-thread disk calls from the inbox:\n" + mine.firstOrNull().orEmpty().take(2500), mine.isEmpty())
        log("INBOX_RESULT PASS")
    }
}
