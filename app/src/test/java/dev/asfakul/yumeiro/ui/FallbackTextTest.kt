/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import dev.asfakul.yumeiro.data.repository.displayFolderName
import dev.asfakul.yumeiro.player.equalizer.EqualizerBand
import dev.asfakul.yumeiro.ui.screens.music.shortFolderPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Text people see when the system gives us nothing useful: never "<unknown>", "0Hz" or a raw path. */
class FallbackTextTest {
    @Test fun folderPathsAreShortAndNeverUnknown() {
        assertEquals("Music/Anime", shortFolderPath("/storage/emulated/0/Music/Anime/"))
        assertEquals("SD card/Music", shortFolderPath("/storage/1A2B-3C4D/Music"))
        assertEquals("SD card", shortFolderPath("/storage/1A2B-3C4D/<unknown>"))
        assertNull(shortFolderPath("<unknown>"))
        assertNull(shortFolderPath("/storage/emulated/0/"))
        assertNull(shortFolderPath(""))
    }

    @Test fun folderNamesHaveReadableFallbacks() {
        assertEquals("Internal storage", displayFolderName("<unknown>", ""))
        assertEquals("Internal storage", displayFolderName("", ""))
        assertEquals("Anime", displayFolderName("Anime", "/storage/emulated/0/Anime"))
    }

    @Test fun bandWithoutFrequencyShowsItsNumber() {
        assertEquals("Band 3", EqualizerBand(2, 0, 0).displayFrequency)
        assertEquals("60Hz", EqualizerBand(0, 60, 0).displayFrequency)
    }
}
