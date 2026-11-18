/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.lyrics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * G6 lyrics with LRCLIB mocked (no network): the whole `LrcLib.find` path — /get, fallback to
 * /search, disk cache, one-week "no lyrics" mark, server errors, a captive-portal page, instrumental,
 * unknown artist — against canned answers from an OkHttp interceptor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LrcLibMockedTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val calls = mutableListOf<String>()
    /** path ("get" / "search") → (code, body). */
    private var answers: Map<String, Pair<Int, String>> = emptyMap()

    private val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val req = chain.request()
        val path = req.url.pathSegments.last()
        calls += path + "?" + req.url.query
        val (code, body) = answers[path] ?: (404 to "{\"code\":404}")
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }).build()

    private val synced = "[00:01.00]first line\\n[00:05.00]second line"
    private fun record(dur: Double, syncedLyrics: String? = synced, plain: String? = "first line", instrumental: Boolean = false) =
        "{\"trackName\":\"Song\",\"artistName\":\"Artist\",\"albumName\":\"Album\",\"duration\":$dur," +
            "\"syncedLyrics\":${syncedLyrics?.let { "\"$it\"" } ?: "null"},\"plainLyrics\":${plain?.let { "\"$it\"" } ?: "null"},\"instrumental\":$instrumental}"

    private fun find(title: String = "Song", artist: String = "Artist", durMs: Long = 200_000) =
        LrcLib.find(ctx, client, title, artist, "Album", durMs)

    @Before fun clean() {
        // LrcLib is a process-wide object: drop the prefs it kept from the previous test's
        // Application, or that test's "no lyrics" mark leaks into this one (CI 44227662448).
        LrcLib.javaClass.getDeclaredField("prefs").apply { isAccessible = true }.set(LrcLib, null)
        LrcLib.init(ctx)
        ctx.getSharedPreferences("lyrics_online", Context.MODE_PRIVATE).edit().clear().commit()
        File(ctx.cacheDir, "lyrics_online").deleteRecursively()
        calls.clear()
    }

    @Test fun getHitIsReturnedAndCachedOnDisk() {
        answers = mapOf("get" to (200 to record(200.0)))
        val text = find()
        assertTrue(text!!.startsWith("[00:01.00]first line"))
        assertEquals(1, calls.size)
        assertTrue("query carries title, artist, album, length: ${calls[0]}", listOf("track_name=Song", "artist_name=Artist", "album_name=Album", "duration=200").all { it in calls[0] })
        assertEquals(text, find())
        assertEquals("second play must come from the disk cache", 1, calls.size)
    }

    @Test fun getMissFallsBackToSearchAndPicksTheSameLength() {
        answers = mapOf(
            "get" to (404 to "{}"),
            "search" to (200 to "[${record(260.0)},${record(201.0, syncedLyrics = null, plain = "plain only")},${record(199.0)}]"),
        )
        val text = find()
        assertEquals(listOf("get", "search"), calls.map { it.substringBefore('?') })
        assertTrue("synced within 2 s wins: $text", text!!.startsWith("[00:01.00]"))
    }

    @Test fun noLyricsIsRememberedForAWeek() {
        answers = mapOf("get" to (404 to "{}"), "search" to (200 to "[]"))
        assertNull(find())
        val n = calls.size
        assertNull(find())
        assertEquals("a known miss must not ask LRCLIB again", n, calls.size)
    }

    @Test fun serverErrorThrowsAndIsNotRememberedAsNoLyrics() {
        answers = mapOf("get" to (503 to "busy"))
        assertTrue(runCatching { find() }.isFailure)
        answers = mapOf("get" to (200 to record(200.0)))
        assertTrue("after the outage the song gets lyrics", find() != null)
    }

    @Test fun captivePortalPageIsAnErrorNotCached() {
        answers = mapOf("get" to (200 to "<html><body>Sign in to Wi-Fi</body></html>"))
        assertTrue(runCatching { find() }.isFailure)
        assertTrue("nothing cached from a portal page", File(ctx.cacheDir, "lyrics_online").listFiles().isNullOrEmpty())
        answers = mapOf("get" to (200 to record(200.0)))
        assertTrue(find() != null)
    }

    @Test fun instrumentalHasNoLyrics() {
        answers = mapOf("get" to (200 to record(200.0, syncedLyrics = null, plain = null, instrumental = true)))
        assertNull(find())
    }

    @Test fun unknownArtistOrBlankTitleNeverAsks() {
        assertNull(find(artist = "<unknown>"))
        assertNull(find(title = " "))
        assertEquals(0, calls.size)
    }

    @Test fun unknownLengthSkipsGetAndUsesSearch() {
        answers = mapOf("search" to (200 to "[${record(180.0)}]"))
        assertTrue(find(durMs = 0) != null)
        assertEquals(listOf("search"), calls.map { it.substringBefore('?') })
    }
}
