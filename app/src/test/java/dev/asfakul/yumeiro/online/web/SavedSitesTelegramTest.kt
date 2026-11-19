/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FINAL-3 (F-02, final test run 44593053433): `SavedSites.add(ctx, "@yutest")` answered "That doesn't look like a
 * web address" because only the Sites screen turned "@name" into the channel page. Every caller now saves the same
 * public page, `https://t.me/s/<name>`, named "@<name>".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedSitesTelegramTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { SavedSites.resetForTest() }
    @After fun tearDown() { SavedSites.resetForTest() }

    @Test fun aChannelHandleOrLinkIsSavedAsItsPublicPage() {
        for (input in listOf("@yutest", "t.me/yutest", "https://t.me/s/yutest", "tg://resolve?domain=yutest")) {
            SavedSites.resetForTest()
            assertNull(input, SavedSites.add(ctx, input))
            val s = SavedSites.sites.value.single()
            assertEquals(input, "https://t.me/s/yutest", s.url)
            assertEquals(input, "@yutest", s.name)
        }
    }

    @Test fun ordinarySitesAndBadInputAreUnchanged() {
        assertNull(SavedSites.add(ctx, "example.com"))
        assertEquals("https://example.com", SavedSites.sites.value.single().url)
        assertEquals("That doesn't look like a web address", SavedSites.add(ctx, "@a"))
    }
}
