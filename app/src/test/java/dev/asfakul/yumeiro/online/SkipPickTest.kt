/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.online.anime.AniSkip
import dev.asfakul.yumeiro.online.anime.AniSkip.Kind
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SKIP-1 (owner list 18). The fixtures are AniSkip's real answers (2026-11-24, `episodeLength=0`):
 * Frieren 1 (MAL 52991), One Piece 1000 (MAL 21), Attack on Titan 1 (MAL 16498).
 */
class SkipPickTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("aniskip/$name")!!.readText()
    private fun all(name: String) = AniSkip.parse(fixture(name))

    @Test fun `the request asks for every submission, not only the playing length`() {
        val asked = ArrayList<String>()
        val c = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            asked += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(fixture("mal_52991_ep1.json").toResponseBody("application/json".toMediaType())).build()
        }).build()
        // Before: a 1560 s video sent episodeLength=1560 and the 1510 s outro submission was never returned;
        // an unknown length (C.TIME_UNSET) sent a negative number, which AniSkip rejects with 400.
        val got = AniSkip.segments(c, 52991, 1, Long.MIN_VALUE + 1)
        assertTrue(asked.single(), asked.single().endsWith("episodeLength=0"))
        assertEquals(listOf(Kind.INTRO, Kind.OUTRO), got.map { it.kind })
    }

    @Test fun `intro and outro both come from Frieren's submissions`() {
        val s = AniSkip.pick(all("mal_52991_ep1.json"), 1_559_949L)
        val intro = s.first { it.kind == Kind.INTRO }
        assertEquals(3_221L, intro.startMs); assertEquals(93_221L, intro.endMs); assertTrue(intro.fits)
        // The mixed opening (58.7 s, from a 1470 s cut) overlaps the opening: not offered twice.
        assertTrue(s.none { it.kind == Kind.MIXED_INTRO })
        // Skip outro: submitted for a 1510.1 s cut, 49.8 s shorter than this video. Credits sit at the end, so
        // they move with it (1417.1 s + 49.8 s), and they are a button, never an automatic jump.
        val outro = s.first { it.kind == Kind.OUTRO }
        assertEquals(1_417_135L + 49_847L, outro.startMs); assertFalse(outro.fits)
        assertEquals("Skip outro", outro.kind.label)
    }

    @Test fun `the submission closest to the video's length wins`() {
        val a = all("mal_16498_ep1.json")
        // A 1420 s video: the mixed opening submitted for exactly 1420 s beats the 1540 s opening.
        val s = AniSkip.pick(a, 1_420_000L)
        val opening = s.first { it.kind == Kind.INTRO || it.kind == Kind.MIXED_INTRO }
        assertEquals(Kind.MIXED_INTRO, opening.kind); assertEquals(75_000L, opening.startMs); assertTrue(opening.fits)
        // The ending submitted for 1447 s is the closest (27 s longer): moved with the end, offered, not skipped by itself.
        val ending = s.first { it.kind == Kind.OUTRO }
        assertFalse(ending.fits); assertEquals(1_342_795L - 26_997L, ending.startMs)
        // Segments never overlap and stay inside the video.
        s.zipWithNext().forEach { (x, y) -> assertTrue(x.endMs <= y.startMs) }
        assertTrue(s.all { it.endMs <= 1_420_000L })
    }

    @Test fun `unknown length takes the earliest and marks everything as fitting`() {
        val s = AniSkip.pick(all("mal_21_ep1000.json"), 0)
        assertEquals(1, s.size)
        assertEquals(13_891L, s[0].startMs); assertTrue(s[0].fits)
        // Within AniSkip's own 15 s tolerance it fits; beyond it, it is only offered.
        assertTrue(AniSkip.pick(all("mal_21_ep1000.json"), 1_440_000L)[0].fits)
        assertFalse(AniSkip.pick(all("mal_21_ep1000.json"), 1_500_000L)[0].fits)
    }

    @Test fun `a segment after the end of a much shorter video is dropped`() {
        val s = AniSkip.pick(all("mal_52991_ep1.json"), 1_000_000L)
        assertNull(s.firstOrNull { it.kind == Kind.OUTRO })
    }

    @Test fun `IntroDB gives shows and movies their intro and credits`() {
        fun introdb(name: String, movie: Boolean) = dev.asfakul.yumeiro.online.anime.IntroDb.parse(
            javaClass.classLoader!!.getResource("introdb/$name")!!.readText(), movie)
        // Game of Thrones 1x2: intro 6-105 s confirmed by 2 people (skipped automatically when asked).
        val e2 = introdb("got_s1e2.json", false)
        val intro = e2.first { it.kind == Kind.INTRO }
        assertEquals(6_000L, intro.startMs); assertEquals(105_000L, intro.endMs); assertTrue(intro.fits)
        // 1x1: no intro; the end credits from 2 submissions.
        val e1 = introdb("got_s1e1.json", false)
        assertTrue(e1.none { it.kind == Kind.INTRO })
        assertEquals(3_631_500L, e1.single { it.kind == Kind.OUTRO }.startMs)
        // A movie: end credits only (one submission: a button), landing on the post-credit scene at 7540 s.
        val m = introdb("movie_tt0371746.json", true)
        assertEquals(listOf(Kind.OUTRO), m.map { it.kind }); assertEquals(7_540_000L, m[0].endMs); assertFalse(m[0].fits)
        assertTrue(introdb("none.json", false).isEmpty())
        // The player's pick keeps them (no submitted length to compare).
        assertEquals(e2, AniSkip.pick(e2, 3_400_000L))
    }
}
