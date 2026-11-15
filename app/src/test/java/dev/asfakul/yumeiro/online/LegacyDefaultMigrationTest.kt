/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R16 G4: the 1.8.0 Internet Archive clean-up only touches an install that was already there. */
class LegacyDefaultMigrationTest {
    private val ia = ExtensionManager.DEFAULT_KEY

    @Test fun anUpdateFrom180WithTheAutomaticInstallStillMigrates() {
        assertTrue(ExtensionManager.legacyDefaultMigrationDue(emptySet(), listOf(ia, "other")))
    }

    @Test fun nothingToMigrateWhenInternetArchiveWasNotThere() {
        assertFalse(ExtensionManager.legacyDefaultMigrationDue(emptySet(), listOf("other")))
        assertFalse(ExtensionManager.legacyDefaultMigrationDue(emptySet(), emptyList()))
    }

    @Test fun aMigrationAlreadyDoneIsNotRepeated() {
        assertFalse(ExtensionManager.legacyDefaultMigrationDue(setOf("migration:no-default-extension"), listOf(ia)))
    }
}
