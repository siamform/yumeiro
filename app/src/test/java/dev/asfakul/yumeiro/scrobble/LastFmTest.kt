/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LastFmTest {
    @Test fun signatureSortsParamsAndAppendsSecret() {
        // md5("api_keykmethodauth.getSessiontokents")
        assertEquals("4b1fe62eb056c03672443be528ff2633", LastFmSignature.sign(mapOf("token" to "t", "method" to "auth.getSession", "api_key" to "k"), "s"))
    }

    @Test fun signatureIgnoresFormatAndCallbackAndIsUtf8() {
        val p = mapOf("method" to "track.scrobble", "api_key" to "k", "sk" to "SK", "artist[0]" to "Björk", "track[0]" to "Jóga")
        assertEquals("0886adad51e1f072f531d9f437427cd8", LastFmSignature.sign(p + ("format" to "json") + ("callback" to "x"), "s"))
    }

    @Test fun signedAddsSigAndJsonFormat() {
        val s = LastFm.signed(mapOf("method" to "auth.getToken", "api_key" to "k"), "s")
        assertEquals("json", s["format"])
        assertEquals(LastFmSignature.sign(mapOf("method" to "auth.getToken", "api_key" to "k"), "s"), s["api_sig"])
    }

    @Test fun scrobbleParamsAreIndexedAndCapped() {
        val plays = List(60) { ScrobblePlay("A", "T$it", if (it == 0) "Al" else "", 200, 1000L + it) }
        val p = LastFm.scrobbleParams(plays, "k", "sk")
        assertEquals("T0", p["track[0]"]); assertEquals("1000", p["timestamp[0]"]); assertEquals("Al", p["album[0]"])
        assertFalse(p.containsKey("album[1]"))
        assertTrue(p.containsKey("track[49]")); assertFalse(p.containsKey("track[50]"))
    }

    @Test fun errorsAreTypedByCode() {
        try { LastFm.check("""{"error":9,"message":"Invalid session key"}"""); fail() } catch (e: LastFm.ApiException) { assertTrue(e.badSession); assertFalse(e.transient) }
        try { LastFm.check("""{"error":16,"message":"Temporarily unavailable"}"""); fail() } catch (e: LastFm.ApiException) { assertTrue(e.transient) }
        assertEquals("t", LastFm.check("""{"token":"t"}""").getString("token"))
    }
}
