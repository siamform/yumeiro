/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.subtitles.translate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.g11.FakeHttp
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.OnlineSubtitle
import dev.asfakul.yumeiro.online.subtitles.SubDL
import dev.asfakul.yumeiro.online.subtitles.SubtitleHit
import dev.asfakul.yumeiro.profiles.Profiles
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** B20 S6: reading and writing subtitles, translation units, romanisation, the source pick and the temp folders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubtitleTranslateTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { Profiles.resetForTests(ctx); SubtitleTemp.root(ctx).deleteRecursively() }
    @After fun tearDown() { SubtitleTemp.root(ctx).deleteRecursively(); Profiles.resetForTests(ctx) }

    private val srt = "1\n00:00:01,000 --> 00:00:02,500\n<i>Hello there.</i>\n\n2\n00:00:03,000 --> 00:00:04,000\n- Hi.\n- Hello &amp; bye.\n"
    private val vtt = "WEBVTT\n\nNOTE made by hand\n\n00:01.000 --> 00:02.000 align:start\nI wanted to tell you\n\n00:02.500 --> 00:04.000\nthat I'm leaving.\n\n00:09.000 --> 00:10.000\nWhy?\n"
    private val ass = """[Script Info]
Title: x

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,{\i1}Look out!{\i0}\NNow, run.
Dialogue: 0,0:00:02.00,0:00:05.00,Sign,,0,0,0,,{\p1}m 0 0 l 100 0{\p0}
"""

    // region parsing and writing

    @Test fun srtTimesItalicsAndEntities() {
        val c = SubCues.parse(srt)
        assertEquals(2, c.size)
        assertEquals(1_000L, c[0].startMs); assertEquals(2_500L, c[0].endMs)
        assertTrue(c[0].italic); assertEquals("Hello there.", c[0].text)
        assertEquals("- Hi.\n- Hello & bye.", c[1].text); assertFalse(c[1].italic)
    }

    @Test fun vttSkipsNotesAndCueSettings() {
        val c = SubCues.parse(vtt)
        assertEquals(listOf("I wanted to tell you", "that I'm leaving.", "Why?"), c.map { it.text })
        assertEquals(1_000L, c[0].startMs); assertEquals(2_000L, c[0].endMs)
        assertEquals(3_723_400L, SubCues.time("1:02:03.4"))
        assertNull(SubCues.time("nope"))
    }

    @Test fun assKeepsLineBreaksAndSkipsDrawings() {
        val c = SubCues.parse(ass)
        assertEquals(1, c.size)
        assertEquals(1_500L, c[0].startMs); assertEquals(3_000L, c[0].endMs)
        assertEquals("Look out!\nNow, run.", c[0].text); assertTrue(c[0].italic)
    }

    @Test fun vttOutputReadsBackTheSame() {
        val c = SubCues.parse(srt)
        val out = SubCues.toVtt(c)
        assertTrue(out.startsWith("WEBVTT\n\n"))
        assertTrue(out.contains("00:00:01.000 --> 00:00:02.500"))
        assertTrue(out.contains("Hello &amp; bye."))
        assertEquals(c, SubCues.parse(out))
    }

    @Test fun badInputGivesNoCues() {
        assertTrue(SubCues.parse("").isEmpty())
        assertTrue(SubCues.parse("just some text\nwith no times").isEmpty())
        assertTrue(SubCues.parse("1\n00:00:05,000 --> 00:00:04,000\nbackwards\n").isEmpty())
    }

    // endregion

    // region units

    @Test fun unfinishedSentencesJoinAndSpeakerTurnsSplit() {
        val v = SubCues.units(SubCues.parse(vtt))
        assertEquals(2, v.size)
        assertEquals("I wanted to tell you that I'm leaving.", v[0].text)
        assertEquals(listOf(0 to 0, 1 to 0), v[0].parts)
        assertEquals("Why?", v[1].text) // 5 s later: never joined
        val s = SubCues.units(SubCues.parse(srt))
        assertEquals(listOf("Hello there.", "Hi.", "Hello & bye."), s.map { it.text })
        assertEquals(listOf(1 to 0), s[1].parts); assertEquals(listOf(1 to 1), s[2].parts)
    }

    @Test fun atMostThreeCuesJoin() {
        val text = (0 until 5).joinToString("\n\n") { i -> "${i + 1}\n00:00:0${i},000 --> 00:00:0${i},900\nword$i" }
        val u = SubCues.units(SubCues.parse(text))
        assertEquals(listOf(3, 2), u.map { it.parts.size })
    }

    @Test fun splitBackFollowsTheWeights() {
        assertEquals(listOf("one two", "three four"), SubCues.splitBack("one two three four", listOf(1, 1)))
        assertEquals(listOf("あい", "うえ"), SubCues.splitBack("あいうえ", listOf(1, 1)))
        assertEquals(listOf("solo"), SubCues.splitBack(" solo ", listOf(4)))
        // Every piece gets something even when one weight is tiny.
        assertTrue(SubCues.splitBack("a b", listOf(100, 1)).all { it.isNotEmpty() })
    }

    @Test fun longLinesWrapNearTheMiddle() {
        val long = "This is a fairly long subtitle line that needs wrapping"
        val w = SubCues.wrap(long)
        val parts = w.split("\n")
        assertEquals(2, parts.size)
        assertTrue(parts.all { it.length <= 42 })
        assertEquals(long, parts.joinToString(" "))
        assertEquals("Short.", SubCues.wrap("Short."))
    }

    @Test fun applyPutsTranslationsBackAndKeepsUntranslatedLines() {
        val cues = SubCues.parse(srt + "\n3\n00:00:05,000 --> 00:00:06,000\nLeft alone.\n")
        val units = SubCues.units(cues)
        val out = SubCues.apply(cues, units, listOf("Hola.", "Hi!", "Adiós.", null))
        assertEquals("Hola.", out[0].text); assertTrue(out[0].italic)
        assertEquals("- Hi!\n- Adiós.", out[1].text)
        assertEquals("Left alone.", out[2].text)
        assertEquals(cues.map { it.startMs to it.endMs }, out.map { it.startMs to it.endMs })
        val both = SubCues.apply(cues, units, listOf("Hola.", null, null, null)) { it.uppercase() }
        assertEquals("Hola.\nHOLA.", both[0].text)
        val joined = SubCues.parse(vtt).let { c -> SubCues.apply(c, SubCues.units(c), listOf("Quería decirte que me voy.", null)) }
        assertEquals("Quería decirte", joined[0].text); assertEquals("que me voy.", joined[1].text)
    }

    // endregion

    // region romanisation

    @Test fun bengaliReadsTheWayItIsSpoken() {
        assertTrue(Romanize.supports("bn"))
        assertEquals("tumi ki korcho?", Romanize.text("তুমি কি করছো?", "bn"))
        assertEquals("ami tomake bhalobasi.", Romanize.text("আমি তোমাকে ভালোবাসি।", "bn"))
        assertEquals("dhonnobad", Romanize.text("ধন্যবাদ", "bn"))
        assertEquals("kolom", Romanize.text("কলম", "bn"))
        assertEquals("somoy", Romanize.text("সময়", "bn"))
        assertEquals("123", Romanize.text("১২৩", "bn"))
    }

    @Test fun hindiReadsTheWayItIsSpoken() {
        assertTrue(Romanize.supports("hi"))
        assertEquals("kya kar rahe ho?", Romanize.text("क्या कर रहे हो?", "hi"))
        assertEquals("namaste", Romanize.text("नमस्ते", "hi"))
        assertEquals("main thik hun", Romanize.text("मैं ठीक हूँ", "hi"))
        assertEquals("zindagi", Romanize.text("ज़िंदगी", "hi"))
    }

    @Test fun latinScriptsHaveNoRomanisedMode() {
        assertFalse(Romanize.supports("es")); assertFalse(Romanize.supports("fr"))
        assertEquals("Hello 42", Romanize.text("Hello 42", "bn"))
    }

    // endregion

    // region the source pick and the AI contract

    @Test fun englishTrackIgnoresTranslatedOnes() {
        val made = OnlineSubtitle("English (translated)", "file:///a.vtt", emptyMap(), auto = true)
        val fr = OnlineSubtitle("French", "https://x/fr.vtt", emptyMap())
        val en = OnlineSubtitle("English", "https://x/en.vtt", emptyMap())
        assertEquals(en, SubtitleTranslate.englishTrack(listOf(made, fr, en)))
        assertNull(SubtitleTranslate.englishTrack(listOf(made, fr)))
    }

    @Test fun releaseMatchBeatsDownloads() {
        val link = "Show.S01E02.1080p.WEB-DL.GROUP"
        val same = SubtitleHit("SubDL", "/subtitle/1", "en", "Show.S01E02.1080p.WEB-DL.GROUP", downloads = 10)
        val other = SubtitleHit("SubDL", "/subtitle/2", "en", "Show.S01E02.720p.HDTV.OTHER", downloads = 5_000)
        val hi = same.copy(id = "/subtitle/3", hearingImpaired = true)
        assertTrue(SubtitleTranslate.score(same, link) > SubtitleTranslate.score(other, link))
        assertTrue(SubtitleTranslate.score(same, link) > SubtitleTranslate.score(hi, link))
    }

    @Test fun batchesStartWhereTheViewerIs() {
        val cues = SubCues.parse(vtt); val units = SubCues.units(cues)
        assertEquals(0, SubtitleTranslate.startUnit(cues, units, 0))
        assertEquals(1, SubtitleTranslate.startUnit(cues, units, 5_000))
        assertEquals(0, SubtitleTranslate.startUnit(cues, units, 99_000))
        assertEquals(listOf(80 until 100, 0 until 40, 40 until 80), SubtitleTranslate.order(100, 80, 40))
        assertEquals(listOf(0 until 40, 40 until 50), SubtitleTranslate.order(50, 0, 40))
    }

    @Test fun aiAnswersWithTheWrongCountAreRejected() {
        assertEquals(listOf("Hola", "Adiós"), SubtitleTranslate.parseAi("""```json
{"t": ["Hola", " Adiós "]}
```""", 2))
        assertNull(SubtitleTranslate.parseAi("""{"t": ["Hola"]}""", 2))
        assertNull(SubtitleTranslate.parseAi("not json", 1))
        val r = SubtitleTranslate.aiRequest(listOf("Hi.", "Bye."), "bn", "Some Show")
        assertTrue(r.json); assertTrue(r.system.contains("Bengali"))
        assertEquals(2, JSONObject(r.messages.single().text).getJSONArray("lines").length())
    }

    // endregion

    // region temp folders

    private fun folder(key: String, ageMs: Long, data: String, bytes: Int = 10): File {
        val f = SubtitleTemp.dir(ctx, key, "api", data)
        File(f, "meta.json").writeText(JSONObject().put("api", "api").put("data", data).put("at", System.currentTimeMillis() - ageMs).toString())
        File(f, "lines.json").writeBytes(ByteArray(bytes))
        return f
    }

    @Test fun sweepKeepsTheEpisodeToResumeAndDropsOldOnes() {
        val hour = 3_600_000L
        val old = folder("old", 50 * hour, "ep1")
        val resume = folder("resume", 50 * hour, "ep2")
        val fresh = folder("fresh", 1 * hour, "ep3")
        SubtitleTemp.sweep(ctx) { _, data -> data == "ep2" }
        assertFalse(old.exists()); assertTrue(resume.exists()); assertTrue(fresh.exists())
    }

    @Test fun sweepCapsTheTotalSize() {
        val a = folder("a", 0, "a", 20 * 1024 * 1024).apply { setLastModified(System.currentTimeMillis() - 10_000) }
        val b = folder("b", 0, "b", 20 * 1024 * 1024)
        SubtitleTemp.sweep(ctx) { _, _ -> false }
        assertFalse(a.exists()); assertTrue(b.exists())
    }

    @Test fun keysAreStableAndHideTheTitle() {
        val k = SubtitleTemp.keyFor("api", "https://site/show", "ep-1")
        assertEquals(k, SubtitleTemp.keyFor("api", "https://site/show", "ep-1"))
        assertFalse(k == SubtitleTemp.keyFor("api", "https://site/show", "ep-2"))
        assertEquals(24, k.length); assertTrue(k.all { it in "0123456789abcdef" })
    }

    @Test fun foldersArePerProfile() {
        val mine = SubtitleTemp.dir(ctx, "k", "api", "d")
        assertTrue(mine.path.contains("/sub-tl/${Profiles.currentId(ctx)}/"))
    }

    // endregion

    // region SubDL by TMDB id and year

    @Test fun subdlSearchesByTmdbIdThenNameAndYear() {
        OnlineSettings.resetForTests(); OnlineSettings.init(ctx)
        OnlineSettings.setSubdlKey(ctx, "b20-test-key-123")
        try {
            val http = FakeHttp { """{"status":true,"subtitles":[{"url":"/subtitle/9-1.zip","release_name":"Movie.2020.1080p","language":"EN"}]}""" }
            val hits = SubDL.search(http.client, "Movie", null, null, null, "en", tmdbId = 1234, year = 2020)
            assertEquals("/subtitle/9-1.zip", hits.single().id)
            val first = http.seen.last().url
            assertTrue(first.contains("tmdb_id=1234")); assertFalse(first.contains("film_name"))
            SubDL.search(http.client, "Movie", null, null, null, "en", year = 2020)
            val second = http.seen.last().url
            assertTrue(second.contains("film_name=Movie")); assertTrue(second.contains("year=2020"))
            SubDL.search(http.client, "Movie", "tt0000001", null, null, "en", tmdbId = 1234)
            assertTrue(http.seen.last().url.contains("imdb_id=tt0000001")); assertFalse(http.seen.last().url.contains("tmdb_id"))
        } finally { SubDL.unlink(ctx); OnlineSettings.resetForTests() }
    }

    // endregion
}
