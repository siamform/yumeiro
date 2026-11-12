/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.watch

import dev.asfakul.yumeiro.online.AudioLanguages
import dev.asfakul.yumeiro.online.AudioLanguages.Kind
import dev.asfakul.yumeiro.ui.screens.watch.AudioSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round 9: Sub / Dub and audio language read from real-world link names. */
class AudioLanguagesTest {
    private fun c(t: String, v: String = "") = AudioLanguages.classify(t, v)

    @Test fun dubsByLanguage() {
        assertEquals(AudioLanguages.Audio(Kind.DUB, "en"), c("HD-1 [ENG DUB]"))
        assertEquals(AudioLanguages.Audio(Kind.DUB, "hi"), c("Kiwi · Hindi Dub 1080p"))
        assertEquals(AudioLanguages.Audio(Kind.DUB, "es"), c("Vidstream (Latino)"))
        assertEquals(AudioLanguages.Audio(Kind.DUB, "pt"), c("Filemoon Dublado"))
        assertEquals(AudioLanguages.Audio(Kind.DUB, "ta"), c("Tamil audio 720p"))
        assertEquals(AudioLanguages.Audio(Kind.DUB, AudioLanguages.MULTI), c("Dual Audio 1080p"))
        assertEquals(Kind.DUB, c("Server 2 (dub)").kind)
        assertEquals(AudioLanguages.Audio(Kind.DUB, "en"), c("MegaCloud English", "Dub"))
    }

    @Test fun subsKeepOriginalAudio() {
        assertEquals(AudioLanguages.Audio(Kind.SUB, null), c("Vidstream Sub"))
        assertEquals(AudioLanguages.Audio(Kind.SUB, null), c("HD-2 Eng Sub"))
        assertEquals(AudioLanguages.Audio(Kind.SUB, null), c("Hardsub 720p"))
        assertEquals(Kind.SUB, c("Kwik · jpn").kind)
        assertEquals(Kind.SUB, c("Japanese audio, English subtitles").kind)
        assertEquals(Kind.SUB, c("Server 1", "Sub").kind)
    }

    @Test fun nothingToSay() {
        assertEquals(AudioLanguages.Audio(null, null), c("Vidstream 1080p"))
        assertEquals(AudioLanguages.Audio(null, null), c("Subway Surfers"))
        assertNull(AudioLanguages.languageOf("Permanent Server · March"))
        assertEquals("en", AudioLanguages.languageOf("Streamtape [EN]"))
    }

    @Test fun groupsSplitDubsByLanguage() {
        val links = listOf("HD-1 Sub", "HD-2 Sub", "HD-1 English Dub", "Kiwi Hindi Dub", "HD-2 English Dub", "Vidplay Dub", "Backup 1080p")
        val g = AudioSplit.groups(links, "Sub")
        assertEquals(listOf("Sub · original audio", "Dub · English", "Dub · Hindi", "Dub · other"), g.map { it.label })
        // Links that say nothing take the version's kind ("Sub").
        assertEquals(listOf(0, 1, 6), g[0].indices)
        assertEquals(listOf(2, 4), g[1].indices)
        // Preferred language comes right after Sub.
        assertEquals("Dub · Hindi", AudioSplit.groups(links, "Sub", preferred = "hi")[1].label)
    }

    @Test fun singleGroupLabels() {
        assertEquals("Dub · English", AudioSplit.groups(listOf("A English Dub", "B eng dub"), "Dub").single().label)
        assertEquals("Sub & Dub", AudioSplit.groups(listOf("Server 1", "Server 2"), "Sub").single().label)
        assertEquals("Sub", AudioSplit.groups(listOf("Server 1", "Server 2"), "Sub", onlyVersion = false).single().label)
        assertEquals("Sources", AudioSplit.groups(listOf("Server 1"), "").single().label)
    }

    @Test fun filterKeysAndLabels() {
        assertEquals(AudioLanguages.ORIG, AudioLanguages.filterKey(c("HD-1 Sub")))
        assertEquals("en", AudioLanguages.filterKey(c("HD-1 English Dub")))
        assertEquals("English", AudioLanguages.keyLabel("en"))
        assertEquals("Original · Sub", AudioLanguages.keyLabel(AudioLanguages.ORIG))
    }
}
