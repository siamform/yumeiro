/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OL-10 (owner): the newer pages can be pinned to the nav bar (Settings -> Navigation bar). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavMoreDestinationsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val newer = listOf("profiles", "stats", "upcoming", "reading", "tracking")

    @After fun reset() { FeatureFlags.set(ctx, FeatureFlags.Flag.NAV_MORE_DESTINATIONS, true); FeatureFlags.on(ctx, FeatureFlags.Flag.NAV_MORE_DESTINATIONS) }

    @Test fun ol10_navBarCanPinTheNewerPages() {
        for (id in newer + "telegram") assertNotNull("Navigation bar cannot pin '$id'", NavPrefs.Shortcut.of(id))
        val tags = NavPrefs.Shortcut.entries.map { it.item.testTag }
        assertEquals("nav tags must be unique", tags.size, tags.toSet().size)
        val offered = NavPrefs.Shortcut.offered().map { it.id }
        for (id in newer) assertTrue("'$id' is not offered in Settings -> Navigation bar", id in offered)
    }

    @Test fun ol10_flagOffHidesOnlyTheNewerOnes() {
        FeatureFlags.set(ctx, FeatureFlags.Flag.NAV_MORE_DESTINATIONS, false)
        assertFalse(FeatureFlags.on(ctx, FeatureFlags.Flag.NAV_MORE_DESTINATIONS))
        val offered = NavPrefs.Shortcut.offered().map { it.id }
        for (id in newer) assertFalse("'$id' offered with the flag off", id in offered)
        assertTrue("older shortcuts must stay", "telegram" in offered && "inbox" in offered)
    }
}
