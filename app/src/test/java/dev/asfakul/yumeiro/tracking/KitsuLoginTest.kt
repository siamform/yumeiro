/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking

import okhttp3.FormBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADR-013: Kitsu needs no app credential, so the built-in login sends none. */
class KitsuLoginTest {
    private fun FormBody.names() = (0 until size).map { name(it) }

    @Test fun builtInLoginSendsNoClientFields() {
        val b = Kitsu.loginBody(" me@example.org ", "p w", null)
        assertEquals(listOf("grant_type", "username", "password"), b.names())
        assertEquals("me@example.org", b.value(1))
    }

    @Test fun ownClientPairIsSentWhenTheUserHasOne() {
        val b = Kitsu.loginBody("me", "p", Kitsu.userClient("id", "s"))
        assertTrue(b.names().containsAll(listOf("client_id", "client_secret")))
    }

    @Test fun halfAPairCountsAsNone() {
        assertNull(Kitsu.userClient("id", ""))
        assertNull(Kitsu.userClient(null, "s"))
        assertTrue(Kitsu.configured)
    }
}
