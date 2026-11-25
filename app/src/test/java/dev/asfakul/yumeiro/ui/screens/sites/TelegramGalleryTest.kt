/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import dev.asfakul.yumeiro.online.telegram.td.TgVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TG-GRID-1 / TG-AVATAR-1 (owner list 8A, 8C): what a gallery tile and a photo-less avatar show. */
class TelegramGalleryTest {
    private fun v(sec: Int, h: Int, audio: Boolean = false) =
        TgVideo(1, 2, 3, "a.mp4", "video/mp4", 0, sec, 0, h, 0, "", 0, audio)

    @Test fun badgeIsLengthAndQuality() {
        assertEquals("12:04 · 720p", tileBadges(v(724, 720)))
        assertEquals("1:02:03 · 1080p", tileBadges(v(3723, 1080)))
        assertEquals("3:30", tileBadges(v(210, 0, audio = true)))
        assertEquals("", tileBadges(v(0, 0)))
    }

    @Test fun sizeReadsLikeTheFileManager() {
        assertNull(sizeText(0))
        assertEquals("1 MB", sizeText(300_000))
        assertEquals("734 MB", sizeText(734_000_000))
        assertEquals("1.5 GB", sizeText(1_500_000_000))
    }

    @Test fun avatarLetterSkipsSymbols() {
        assertEquals("M", initialOf("@movies_hd"))
        assertEquals("ব", initialOf("বাংলা মুভি"))
        assertEquals("?", initialOf("  ✈ "))
        assertEquals("C", initialOf("cinema"))
    }
}
