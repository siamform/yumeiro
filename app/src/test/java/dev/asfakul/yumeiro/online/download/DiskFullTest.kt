/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskFullTest {
    @Test fun recognisesEnospcAnywhereInTheCauseChain() {
        val errno = java.io.IOException("write failed: ENOSPC (No space left on device)")
        assertTrue(DiskFull.isDiskFull(errno))
        assertTrue(DiskFull.isDiskFull(RuntimeException("wrapped", errno)))
    }

    @Test fun networkErrorsAreNotDiskFull() {
        assertFalse(DiskFull.isDiskFull(java.net.SocketTimeoutException("timeout")))
        assertFalse(DiskFull.isDiskFull(java.io.IOException("Connection closed early")))
    }

    @Test fun messageSaysHowMuchIsMissing() {
        val m = DiskFull(300L * 1024 * 1024, 100L * 1024 * 1024).message.orEmpty()
        assertTrue(m, m.contains("300 MB") && m.contains("100 MB"))
    }
}
