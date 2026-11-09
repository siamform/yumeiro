/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trakt apps created since 2026-10-01 have no client secret (PKCE-only). */
class TraktAuthTest {
    @Test fun secretIsOmittedWhenTheAppHasNone() {
        val o = Trakt.withSecret(JSONObject().put("client_id", "id"), "")
        assertFalse(o.has("client_secret"))
    }

    @Test fun legacySecretIsStillSent() {
        assertEquals("s", Trakt.withSecret(JSONObject(), "s").getString("client_secret"))
    }

    @Test fun refreshBodyHasTheRequiredFields() {
        val o = Trakt.refreshBody("id", "", "r1")
        assertEquals("r1", o.getString("refresh_token"))
        assertEquals("id", o.getString("client_id"))
        assertEquals("refresh_token", o.getString("grant_type"))
        assertEquals(Trakt.REDIRECT, o.getString("redirect_uri"))
        assertFalse(o.has("client_secret"))
    }

    @Test fun refreshesOnlyWithinADayOfExpiry() {
        val now = 1_000_000_000_000L
        val day = 24L * 60 * 60 * 1000
        assertFalse(Trakt.needsRefresh(null, now))
        assertFalse(Trakt.needsRefresh(now + 3 * day, now))
        assertTrue(Trakt.needsRefresh(now + day / 2, now))
        assertTrue(Trakt.needsRefresh(now - 1, now))
    }
}
