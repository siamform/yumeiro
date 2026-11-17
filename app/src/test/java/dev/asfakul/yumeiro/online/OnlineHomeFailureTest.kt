/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.mainPageOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G7-L1: an extension whose every home page throws (QuickIPTV: "Header doesn't start with #EXTM3U")
 * used to come back as an empty list, so the app said "Nothing came back from this extension" /
 * "Home page is empty". home() now passes the first page error on; a source that is only empty
 * still returns an empty list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineHomeFailureTest {
    private class Fake(override var name: String, private val fail: Boolean) : MainAPI() {
        override var mainUrl = "https://example.invalid"
        override val hasMainPage = true
        override val mainPage = mainPageOf("a" to "Sports", "b" to "News")
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? =
            if (fail) throw IllegalStateException("Header doesn't start with #EXTM3U") else null
    }

    @Test fun everyPageFailingPassesTheReasonOn() {
        val err = runCatching { runBlocking { OnlineRepository.home(Fake("YuL1 failing", fail = true), force = true) } }.exceptionOrNull()
        assertTrue("home() must fail, not return an empty list", err != null)
        assertEquals("The site sent something that isn't a playlist. It may be down or blocked; try again later", OnlineErrors.friendly(err!!))
    }

    @Test fun anEmptySourceIsStillJustEmpty() {
        val rows = runBlocking { OnlineRepository.home(Fake("YuL1 empty", fail = false), force = true) }
        assertTrue(rows.isEmpty())
    }
}
