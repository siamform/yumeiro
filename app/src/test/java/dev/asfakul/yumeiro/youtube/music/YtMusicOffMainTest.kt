/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube.music

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * B-28 (GATE-1 real clock, run 44793350371): a YouTube Music page load, request body included, must
 * not run on the caller's thread. The test calls from a named thread and checks where the load runs.
 */
class YtMusicOffMainTest {
    @Test fun theLoadRunsOffTheCallersThread() {
        var loadThread: Thread? = null
        val caller = Thread.currentThread()
        runBlocking {
            YtMusicApi.cached("test:off-main:${System.nanoTime()}", force = true) { loadThread = Thread.currentThread(); YmPage() }
        }
        assertNotNull(loadThread)
        assertFalse("the load ran on the caller's thread (${caller.name})", loadThread === caller)
    }
}
