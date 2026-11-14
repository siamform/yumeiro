package dev.asfakul.yumeiro.r15

import dev.asfakul.yumeiro.player.QualityEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/** R15 Phase 2: the player's quality list = the stream's ladder + the downloader's missing heights. */
class QualityParityTest {
    private fun alt(h: Int, engine: Int? = null, note: String? = null) =
        QualityEngine.Alt(QualityEngine.label(h), h, note = note, engineHeight = engine) { _, _ -> null }

    @Test fun addsDownloaderHeightsTheLadderLacks() {
        val ladder = listOf(360, 480, 720)
        val downloader = listOf(alt(2160), alt(1080), alt(720), alt(480), alt(360), alt(144))
        assertEquals(listOf(2160, 1080, 144), QualityEngine.extraHeights(ladder, downloader).map { it.height })
    }

    @Test fun nearHeightsCountAsTheSame() {
        // 1080x1920 portrait / 1088 encoded heights snap to 1080
        assertEquals(emptyList<Int>(), QualityEngine.extraHeights(listOf(1088, 718), listOf(alt(1080), alt(720))).map { it.height })
    }

    @Test fun engineLevelsNeverMixIntoARealLadder() {
        val alts = listOf(alt(480, engine = 480), alt(1080, note = "Original from the source"), alt(1440))
        assertEquals(listOf(1440), QualityEngine.extraHeights(listOf(720), alts).map { it.height })
    }
}
