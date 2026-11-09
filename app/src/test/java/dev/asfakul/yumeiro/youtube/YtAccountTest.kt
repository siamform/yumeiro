/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException

class YtAccountTest {
    private val login = "SID=s1; HSID=h; SAPISID=abc123; __Secure-3PSID=x"

    @Test fun `login cookies need an api sid and a session id`() {
        assertTrue(YtAccount.hasLoginCookies(login))
        assertTrue(YtAccount.hasLoginCookies("__Secure-3PAPISID=a; __Secure-1PSID=b"))
        assertFalse(YtAccount.hasLoginCookies("VISITOR_INFO1_LIVE=v; CONSENT=YES+"))
        assertFalse(YtAccount.hasLoginCookies("SAPISID=; SID=s"))
    }

    @Test fun `sanitize drops line breaks, junk and duplicates`() {
        assertEquals("A=1; B=2", YtAccount.sanitize("A=1;\r\n B=2; ; junk; A=3"))
    }

    @Test fun `merge keeps extractor cookies and adds missing account ones`() {
        assertEquals(login, YtAccount.mergeCookies(null, login))
        assertEquals("CONSENT=YES+; SID=s1", YtAccount.mergeCookies("CONSENT=YES+", "SID=s1; CONSENT=NO"))
    }

    @Test fun `account only goes to youtube web hosts`() {
        listOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "WWW.YOUTUBE.COM").forEach { assertTrue(it, YtAccount.appliesTo(it)) }
        listOf("rr1---sn-abc.googlevideo.com", "i.ytimg.com", "evil-youtube.com", "youtube.com.evil.net", "accounts.google.com").forEach { assertFalse(it, YtAccount.appliesTo(it)) }
        assertTrue(YtAccount.accountHeaders("https://rr1.googlevideo.com/videoplayback", login, null, 1L).isEmpty())
    }

    @Test fun `headers carry cookie and a valid SAPISIDHASH`() {
        val h = YtAccount.accountHeaders("https://www.youtube.com/youtubei/v1/player", login, "CONSENT=YES+", 1700000000L)
        assertTrue(h.getValue("Cookie").startsWith("CONSENT=YES+; "))
        assertTrue(h.getValue("Authorization").matches(Regex("SAPISIDHASH 1700000000_[0-9a-f]{40}")))
        assertEquals("https://www.youtube.com", h["X-Origin"])
        assertEquals("https://music.youtube.com", YtAccount.accountHeaders("https://music.youtube.com/x", login, null, 1L)["X-Origin"])
    }

    @Test fun `hash matches the known formula`() {
        // sha1("1 abc123 https://www.youtube.com")
        val expected = java.security.MessageDigest.getInstance("SHA-1").digest("1 abc123 https://www.youtube.com".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals("SAPISIDHASH 1_$expected", YtAuthHash.header("abc123", "https://www.youtube.com", 1L))
    }

    @Test fun `only sign-in walls trigger a retry with the account`() {
        assertTrue(YtAccount.accountCanHelp(SignInConfirmNotBotException("bot")))
        assertTrue(YtAccount.accountCanHelp(IOException("wrap", AgeRestrictedContentException("age"))))
        assertFalse(YtAccount.accountCanHelp(IOException("net")))
    }
}
