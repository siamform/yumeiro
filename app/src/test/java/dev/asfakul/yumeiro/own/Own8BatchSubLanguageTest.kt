/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.own

import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.download.DownloadTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** OWN-8 (owner 2026-11-22): pick the subtitle language for an episode batch (before: only None or all). */
class Own8BatchSubLanguageTest {
    @Test fun batchOffersTheSettingsLanguageThenEnglish() {
        assertEquals(listOf(java.util.Locale.forLanguageTag("bn").getDisplayLanguage(java.util.Locale.ENGLISH), "English"), DownloadTracks.batchLanguages("bn"))
        assertEquals(listOf("English"), DownloadTracks.batchLanguages("en"))
    }

    @Test fun languageMatchesNamesAndCodes() {
        assertTrue(DownloadTracks.sameLanguage("English (CC)", "English"))
        assertTrue(DownloadTracks.sameLanguage("en", "English"))
        assertTrue(DownloadTracks.sameLanguage("eng", "English"))
        assertTrue(DownloadTracks.sameLanguage("es-419", "Spanish"))
        assertFalse(DownloadTracks.sameLanguage("Spanish", "English"))
        assertFalse(DownloadTracks.sameLanguage("fr", "English"))
    }

    @Test fun aBatchSavesOnlyThePickedLanguage() {
        val found = listOf("Spanish" to "https://s/es.vtt", "eng" to "https://s/en.vtt", "fr" to "https://s/fr.vtt")
        assertEquals(listOf("eng" to "https://s/en.vtt"), DownloadTracks.pickNamed(found, "English"))
        assertEquals(3, DownloadTracks.pickNamed(found, DownloadTracks.SUBS_ALL).size)
        assertTrue(DownloadTracks.pickNamed(found, "German").isEmpty())
    }

    @Test fun flagIsOn() { assertTrue(FeatureFlags.Flag.BATCH_SUB_LANGUAGE.default) }
}
