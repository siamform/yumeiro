/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.youtube

import dev.asfakul.yumeiro.ui.screens.youtube.MAX_MORE_FAILS
import dev.asfakul.yumeiro.ui.screens.youtube.YmState
import dev.asfakul.yumeiro.ui.screens.youtube.afterDetailMoreFailed
import dev.asfakul.yumeiro.youtube.music.YmPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G6-PG1: on an album / playlist / artist page, one failed "load more" (a network blip past page 1)
 * cleared the continuation, so a 500-song playlist stopped at 100 for good. Home and search already
 * keep it and retry (R15); the detail page must do the same, and give up only after [MAX_MORE_FAILS].
 */
class YmDetailPagingTest {
    private val loading = YmState(page = YmPage(continuation = "tok-2"), loading = false, more = true)

    @Test fun oneFailedPageKeepsTheContinuation() {
        val after = loading.afterDetailMoreFailed()
        assertEquals("tok-2", after.page?.continuation)
        assertFalse(after.more)
        assertTrue("a blip ended the playlist", after.canLoadMore)
    }

    @Test fun givesUpOnlyAfterRepeatedFailures() {
        var s = loading
        repeat(MAX_MORE_FAILS - 1) { s = s.afterDetailMoreFailed().copy(more = true) }
        assertTrue(s.copy(more = false).canLoadMore)
        s = s.afterDetailMoreFailed()
        assertFalse("kept retrying a page that keeps failing", s.canLoadMore)
        assertEquals("tok-2", s.page?.continuation)
    }
}
