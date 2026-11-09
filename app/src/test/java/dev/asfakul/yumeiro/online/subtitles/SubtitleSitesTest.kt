/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.subtitles

import dev.asfakul.yumeiro.subtitles.SubtitleSitesProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubtitleSitesTest {
    private val srt = "1\n00:00:01,000 --> 00:00:02,000\nHello\n"

    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun languageCodesMapPerSite() {
        assertEquals("en", SubtitleLanguages.normalize("EN"))
        assertEquals("en", SubtitleLanguages.normalize("en_US"))
        assertEquals("fr", SubtitleLanguages.normalize("fre"))
        assertEquals("pt-br", SubtitleLanguages.normalize("pob"))
        assertEquals("pt-br", SubtitleLanguages.normalize("pt-BR"))
        assertEquals("EN", SubtitleLanguages.subdl("en"))
        assertEquals("BN", SubtitleLanguages.subdl("ben"))
        assertEquals("BR_PT", SubtitleLanguages.subdl("pt-br"))
        assertEquals("english", SubtitleLanguages.subsource("eng"))
        assertEquals("farsi_persian", SubtitleLanguages.subsource("fa"))
        assertEquals("brazilian_portuguese", SubtitleLanguages.subsource("pob"))
        assertNull(SubtitleLanguages.subsource("xx"))
    }

    @Test fun subdlParsesResultsAndSkipsUnsafePaths() {
        val json = """{"status":true,"results":[{"name":"Show"}],"subtitles":[
            {"release_name":"Show.S01E02.1080p.WEB","name":"x","lang":"english","language":"EN","author":"ann","url":"/subtitle/123-456.zip","hi":true},
            {"release_name":"Show.S01.Complete","url":"/subtitle/9-9.zip","full_season":true},
            {"release_name":"bad","url":"https://evil.example/x.zip"},
            {"release_name":"bad2","url":"/subtitle/../../etc"}]}"""
        val r = SubDL.parse(json, "en")
        assertEquals(2, r.size)
        assertEquals("/subtitle/123-456.zip", r[0].id)
        assertEquals("en", r[0].language)
        assertTrue(r[0].hearingImpaired)
        assertEquals("ann", r[0].uploader)
        assertTrue(r[1].seasonPack)
        assertTrue(SubDL.parse("""{"status":false,"error":"can't find"}""", "en").isEmpty())
        assertTrue(SubDL.parse("not json", "en").isEmpty())
        assertEquals("Grey s Anatomy", SubDL.sanitizeTitle("Grey's Anatomy"))
    }

    @Test fun subsourceParsesTitlesAndSubtitles() {
        val titles = SubSource.parseTitles("""{"success":true,"data":[{"movieId":11,"title":"Dune Part Two","releaseYear":2024},{"movieId":12,"title":"Dune","alternateTitle":"Dune: Part One","releaseYear":2021}]}""")
        assertEquals(2, titles.size)
        assertEquals(12L, SubSource.bestTitle(titles, "dune")!!.id)
        assertEquals(11L, SubSource.bestTitle(titles, "something else")!!.id)
        val subs = SubSource.parseSubtitles("""{"success":true,"data":[
            {"subtitleId":501,"language":"english","releaseInfo":["Dune.2021.1080p"],"commentary":"SDH","hearingImpaired":false,"uploaderId":7,"contributors":[{"id":7,"displayname":"moon"}]},
            {"subtitleId":0,"language":"english"}]}""", "en")
        assertEquals(1, subs.size)
        assertEquals("501", subs[0].id)
        assertEquals("Dune.2021.1080p", subs[0].release)
        assertTrue(subs[0].hearingImpaired)
        assertEquals("moon", subs[0].uploader)
        assertTrue(SubSource.parseSubtitles("""{"success":false}""", "en").isEmpty())
    }

    @Test fun archivePicksTheEpisodeFromASeasonPack() {
        val pack = zip(
            "Show.S01E01.srt" to srt.toByteArray(), "Show.S01E02.srt" to "1\n00:00:01,000 --> 00:00:02,000\nTwo\n".toByteArray(),
            "readme.txt" to "hi".toByteArray(), "__MACOSX/._Show.S01E02.srt" to ByteArray(10),
        )
        val e = SubtitleArchive.pick(pack, 1, 2)
        assertNotNull(e)
        assertEquals("Show.S01E02.srt", e!!.name)
        assertTrue(e.text.contains("Two"))
        // Anime style naming
        val anime = zip("[Grp] Show - 06 [1080p].ass" to "[Script Info]\n".toByteArray(), "[Grp] Show - 07 [1080p].ass" to "[Script Info]\nseven".toByteArray())
        assertTrue(SubtitleArchive.pick(anime, 1, 7)!!.text.contains("seven"))
        // A bare file, not a zip
        assertEquals("srt", SubtitleArchive.pick(srt.toByteArray())!!.extension)
        // Nothing usable
        assertNull(SubtitleArchive.pick(zip("movie.sub" to ByteArray(5))))
        assertNull(SubtitleArchive.pick("<html>Not found</html>".toByteArray()))
    }

    @Test fun archiveDecodesLegacyEncodings() {
        val cp1252 = "1\n00:00:01,000 --> 00:00:02,000\nCaf\u00e9\n".toByteArray(charset("windows-1252"))
        assertTrue(SubtitleArchive.decode(cp1252).contains("Café"))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + srt.toByteArray()
        assertTrue(SubtitleArchive.decode(bom).startsWith("1"))
    }

    @Test fun hubInterleavesSites() {
        val a = listOf(SubtitleHit("OpenSubtitles", "1", "en", "a1"), SubtitleHit("OpenSubtitles", "2", "en", "a2"))
        val b = listOf(SubtitleHit("SubDL", "x", "en", "b1"))
        assertEquals(listOf("a1", "b1", "a2"), SubtitleHub.merge(listOf(a, b)).map { it.release })
    }

    @Test fun localTitleSplitsEpisodeMarker() {
        assertEquals(Triple("Show Name", 2, 5), SubtitleSitesProvider.splitEpisode("Show Name S02E05"))
        assertEquals(Triple("Show", 1, 12), SubtitleSitesProvider.splitEpisode("Show 1x12"))
        val movie = SubtitleSitesProvider.splitEpisode("Dune 2021")
        assertEquals("Dune 2021", movie.first); assertNull(movie.second)
    }
}
