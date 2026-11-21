/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import dev.asfakul.yumeiro.ui.screens.home.HomeShortcut
import org.junit.Assert.assertTrue
import org.junit.Test

/** OL-9 (owner): Home Spaces "See more" has the newer pages. */
class HomeSpacesMoreTest {
    @Test fun ol9_homeHasTheNewerShortcuts() {
        val names = HomeShortcut.entries.map { it.name }
        for (n in listOf("TELEGRAM", "PROFILES", "STATS", "UPCOMING", "READING", "INBOX")) assertTrue("Home Spaces has no $n", n in names)
    }
}
