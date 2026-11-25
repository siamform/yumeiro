/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** DW-BOT-1: which addresses are Telegram mini app start links. */
class TgMiniAppTest {
    @Test fun mainMiniAppLink() {
        assertEquals(TgMiniApp.Link("flezennbot", null, "6b03eaa7d1b63d653e93481c"),
            TgMiniApp.parse("https://t.me/flezennbot?startapp=6b03eaa7d1b63d653e93481c"))
    }

    @Test fun namedMiniAppLinkAndEncodedStart() {
        assertEquals(TgMiniApp.Link("sky577bot", "open", "a b"), TgMiniApp.parse("https://telegram.me/sky577bot/open?startapp=a%20b"))
        assertEquals(TgMiniApp.Link("sky577bot", "open", ""), TgMiniApp.parse("https://t.me/sky577bot/open"))
    }

    @Test fun notMiniAppLinks() {
        assertNull(TgMiniApp.parse("https://t.me/telegram/441")) // a channel post
        assertNull(TgMiniApp.parse("https://t.me/flezennbot")) // a bot chat, no mini app start
        assertNull(TgMiniApp.parse("https://t.me/flezennbot?start=abc")) // chat start, not a mini app
        assertNull(TgMiniApp.parse("https://example.com/flezennbot?startapp=x"))
        assertNull(TgMiniApp.parse("tg://resolve?domain=flezennbot&startapp=x"))
        assertNull(TgMiniApp.parse("https://t.me/a/b/c?startapp=x"))
        assertNull(TgMiniApp.parse("https://t.me/bad-name_bot?startapp=x"))
    }

    @Test fun diskWalaGoesToTheBotThatTakesIds() {
        assertEquals(TgMiniApp.Link("flezennbot", null, "6b03eaa7d1b63d653e93481c"), TgMiniApp.diskWalaLink("6b03eaa7d1b63d653e93481c"))
    }
}
