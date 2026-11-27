/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groupo

import dev.asfakul.yumeiro.online.YumixDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B18: what Yumix opens on (pure rule; the device test checks the saved value and the page). */
class GroupOYumixPickTest {
    private val all = listOf("A", "B", "C")

    @Test fun savedPickOpensWhileInstalled() {
        assertEquals("A", YumixDefaults.openOn("A", all, ready = true, defaults = emptySet()))
    }

    @Test fun slowStartKeepsTheSavedPick() {
        // The engine has not published its extensions yet: the pick must not be dropped.
        assertEquals("A", YumixDefaults.openOn("A", emptyList(), ready = false, defaults = emptySet()))
    }

    @Test fun goneExtensionFallsBackToAll() {
        assertNull(YumixDefaults.openOn("Z", all, ready = true, defaults = emptySet()))
    }

    @Test fun oneDefaultOpensOnItsOwnPage() {
        assertEquals("B", YumixDefaults.openOn(null, all, ready = true, defaults = setOf("B")))
        assertNull("several defaults open the mix", YumixDefaults.openOn(null, all, ready = true, defaults = setOf("A", "B")))
        assertNull("nothing saved, no defaults: All", YumixDefaults.openOn(null, all, ready = true, defaults = emptySet()))
    }
}
