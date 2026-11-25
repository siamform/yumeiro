/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** EXT-SETUP-1 (A3): Jellyfin / Ultima before setup are "needs setup", not "didn't load". */
class ExtensionSetupTest {
    @Test fun unsetExtensionsWithSettingsNeedSetup() {
        assertTrue("Jellyfin: no server yet", ExtensionSetup.needsSetup(true, "", 3, "Expected URL scheme 'http' or 'https' but no scheme was found"))
        assertTrue("Jellyfin: placeholder", ExtensionSetup.needsSetup(true, "https://", 3, null))
        assertTrue("Ultima: no sections picked", ExtensionSetup.needsSetup(true, "https://ultima.example", 0, null))
        assertTrue("empty home from a configurable extension", ExtensionSetup.needsSetup(true, "https://x.example", 2, null))
        assertTrue(ExtensionSetup.needsSetup(true, "https://x.example", 2, "Please login in the extension settings"))
        assertTrue(ExtensionSetup.needsSetup(true, "https://x.example", 2, "HTTP 401 Unauthorized"))
    }

    @Test fun realFailuresStayFailures() {
        assertFalse("no settings: a normal failure", ExtensionSetup.needsSetup(false, "", 0, null))
        assertFalse(ExtensionSetup.needsSetup(true, "https://x.example", 2, "timeout after 25 s"))
        assertFalse(ExtensionSetup.needsSetup(true, "https://x.example", 2, "Unable to resolve host"))
    }

    @Test fun words() {
        assertEquals("Jellyfin needs setting up before it shows anything", ExtensionSetup.message(listOf("Jellyfin")))
        assertEquals("2 extensions need setting up: Jellyfin · Ultima", ExtensionSetup.message(listOf("Jellyfin", "Ultima")))
    }
}
