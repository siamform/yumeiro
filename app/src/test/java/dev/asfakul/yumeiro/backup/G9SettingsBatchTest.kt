/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G9 v1.8.2 audit (docs/groups/GROUP-9-SETTINGS.md, ADR-021). Each test fails on the code at d3c46a0.
 */
class G9SettingsBatchTest {

    /**
     * G9-1: the Mangayomi JS repository list and the list of installed JS sources are source lists,
     * not code. Before, every `js/` path counted as code, so a default backup (Extension files off)
     * left both lists out and a restore came back with no JS repositories and no JS sources.
     */
    @Test fun g9_1_jsListsTravelWithSources() {
        assertEquals("js/repos.json", Backup.Section.SOURCES, Backup.sectionOf("files/js/repos.json"))
        assertEquals("js/sources.json", Backup.Section.SOURCES, Backup.sectionOf("files/js/sources.json"))
        assertFalse("js/repos.json is not code", Backup.isCode("files/js/repos.json"))
        assertFalse("js/sources.json is not code", Backup.isCode("files/js/sources.json"))
        // The downloaded JavaScript itself is still code (downloaded again after a restore).
        assertTrue("js/<id>.js is code", Backup.isCode("files/js/9911.js"))
        assertEquals("js/<id>.js", Backup.Section.EXTENSION_FILES, Backup.sectionOf("files/js/9911.js"))
    }
}
