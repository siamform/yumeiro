/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** B16: Vega / Nuvio / SkyStream plugins are part of a backup (list = sources, files = code). */
@RunWith(RobolectricTestRunner::class)
class GroupMBackupScriptsTest {
    @Test fun scriptPluginsAreBackedUp() {
        assertEquals(Backup.Section.SOURCES, Backup.sectionOf("files/scripts/registry.json"))
        assertFalse(Backup.isCode("files/scripts/registry.json"))
        val code = "files/scripts/vega/0123456789ab/openfilms/catalog.js"
        assertTrue(Backup.isCode(code))
        assertEquals(Backup.Section.EXTENSION_FILES, Backup.sectionOf(code))
        assertEquals(Backup.Section.EXTENSION_FILES, Backup.sectionOf("files/scripts/skystream/0123456789ab/p/plugin.sky"))
        assertTrue("scripts is a data folder", "scripts" in Backup.dataDirs)
        assertEquals(Backup.Section.SOURCES, PrefSections.sectionOf("script_kv_vega_0123456789ab_openfilms", "token"))
    }

    @Test fun restoreScreenCountsScriptPlugins() {
        val m = JSONObject().put("extensions", JSONArray().put(JSONObject())).put("tachiyomi", JSONArray())
            .put("scripts", JSONArray().put(JSONObject()).put(JSONObject()))
        assertEquals(3, Backup.infoOf(m, emptySet(), false).extensions)
    }
}
