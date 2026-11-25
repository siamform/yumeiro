/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TG-AVATAR-1 (owner list 8A): the remembered channel photo and when it needs reading again. */
class TelegramAvatarsTest {
    @Test fun roundTripKeepsUrlsWithBars() {
        val e = TelegramAvatars.Entry("https://cdn4.telesco.pe/file/a|b.jpg", 1_700_000_000_000)
        assertEquals(e, TelegramAvatars.decode(TelegramAvatars.encode(e)))
        assertEquals("", TelegramAvatars.decode("5|")!!.url)
        assertNull(TelegramAvatars.decode("garbage"))
        assertNull(TelegramAvatars.decode(null))
    }

    @Test fun freshForThreeDaysOnly() {
        val e = TelegramAvatars.Entry("u", 1_000)
        assertTrue(TelegramAvatars.fresh(e, 1_000 + TelegramAvatars.TTL_MS - 1))
        assertFalse(TelegramAvatars.fresh(e, 1_000 + TelegramAvatars.TTL_MS))
        assertFalse("a clock set back reads again", TelegramAvatars.fresh(e, 999))
        assertFalse(TelegramAvatars.fresh(null, 1_000))
    }
}
