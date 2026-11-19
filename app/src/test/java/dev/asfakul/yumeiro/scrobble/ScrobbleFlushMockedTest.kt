/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.scrobble

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineSettings
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * G6 Last.fm with the server mocked (no account, no network): the offline queue's rules — oldest
 * first in batches of 50, nothing lost offline, a transient error keeps the rest without resending
 * what was accepted, a revoked session signs out and keeps the queue, a hard error drops only its
 * batch, listens older than 14 days are dropped unsent, an HTML gateway page counts as transient,
 * and nothing is sent while scrobbling is off. Real-account scrobbling stays OMT-10.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScrobbleFlushMockedTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    /** Each call: the batch's "track[i]" values in order. */
    private val sent = mutableListOf<List<String>>()
    /** Answer for the n-th call (0-based); default ok. */
    private var answer: (Int) -> Pair<Int, String>? = { 200 to "{\"scrobbles\":{}}" }

    private val mock = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val req = chain.request()
        // SEC-LFM (ADR-048): the built-in key is signed by Yumeiro Cloud first; that call is not a scrobble batch.
        if (req.url.encodedPath == "/v1/lastfm/sign") return@Interceptor Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("ok")
            .body("{\"api_sig\":\"0123456789abcdef0123456789abcdef\"}".toResponseBody("application/json".toMediaType())).build()
        val form = Buffer().also { req.body?.writeTo(it) }.readUtf8()
        val fields = form.split('&').map { java.net.URLDecoder.decode(it, "UTF-8") }
        val tracks = fields.filter { it.startsWith("track[") }.sortedBy { it.substringAfter('[').substringBefore(']').toInt() }.map { it.substringAfter('=') }
        val n = sent.size
        sent += tracks
        val a = answer(n) ?: throw IOException("offline")
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(a.first).message("x")
            .body(a.second.toResponseBody("application/json".toMediaType())).build()
    }).build()

    private fun setField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private val now get() = System.currentTimeMillis() / 1000
    private fun queue(n: Int, startedAt: (Int) -> Long = { now - 3_600 + it }) {
        val a = JSONArray(); repeat(n) { a.put(Scrobbler.encode(ScrobblePlay("Artist", "T$it", "", 200, startedAt(it)))) }
        ctx.getSharedPreferences("scrobble", Context.MODE_PRIVATE).edit().clear().putString("queue", a.toString()).commit()
    }
    private fun left(): List<String> = Scrobbler.decodeQueue(ctx.getSharedPreferences("scrobble", Context.MODE_PRIVATE).getString("queue", "[]")).map { it.track }
    private fun flush() = runBlocking { Scrobbler.flush(ctx) }

    @Before fun setUp() {
        setField(Scrobbler, "prefs", null)
        OnlineSettings.resetForTests()
        OnlineSettings.setSecret(ctx, "lastfm_session", "sk-test")
        setField(NetworkClient, "client", mock)
        Scrobbler.setEnabled(ctx, true)
    }

    @After fun tearDown() {
        setField(NetworkClient, "client", null)
        OnlineSettings.setSecret(ctx, "lastfm_session", null)
    }

    @Test fun sendsOldestFirstInBatchesOf50AndEmptiesTheQueue() {
        queue(120)
        flush()
        assertEquals(listOf(50, 50, 20), sent.map { it.size })
        assertEquals((0 until 120).map { "T$it" }, sent.flatten())
        assertTrue(left().isEmpty())
        assertEquals(0, Scrobbler.pending.value)
        flush()
        assertEquals("an empty queue sends nothing again (no duplicates)", 3, sent.size)
    }

    @Test fun offlineKeepsEveryListen() {
        queue(120); answer = { null }
        flush()
        assertEquals(120, left().size)
        assertEquals(120, Scrobbler.pending.value)
    }

    @Test fun transientErrorKeepsTheRestWithoutResendingAcceptedOnes() {
        queue(120); answer = { n -> if (n == 1) 200 to "{\"error\":11,\"message\":\"Service Offline\"}" else 200 to "{}" }
        flush()
        assertEquals((50 until 120).map { "T$it" }, left())
        answer = { 200 to "{}" }
        flush()
        val later = sent.drop(2).flatten()
        assertEquals("retry starts after the accepted batch", (50 until 120).map { "T$it" }, later)
        assertTrue(left().isEmpty())
    }

    @Test fun revokedSessionSignsOutAndKeepsTheQueue() {
        queue(10); answer = { 200 to "{\"error\":9,\"message\":\"Invalid session key\"}" }
        flush()
        assertNull(LastFm.session())
        assertEquals(10, left().size)
    }

    @Test fun hardErrorDropsOnlyThatBatch() {
        queue(60); answer = { n -> if (n == 0) 200 to "{\"error\":6,\"message\":\"Invalid parameters\"}" else 200 to "{}" }
        flush()
        assertEquals(listOf(50, 10), sent.map { it.size })
        assertTrue(left().isEmpty())
    }

    @Test fun listensOlderThan14DaysAreDroppedUnsent() {
        queue(4) { i -> if (i < 2) now - 15 * 86_400 else now - 60 }
        flush()
        assertEquals(listOf(listOf("T2", "T3")), sent)
        assertTrue(left().isEmpty())
    }

    @Test fun htmlGatewayPageIsTransient() {
        queue(5); answer = { 502 to "<html>Bad Gateway</html>" }
        flush()
        assertEquals(5, left().size)
    }

    @Test fun nothingIsSentWhileScrobblingIsOff() {
        queue(5); Scrobbler.setEnabled(ctx, false)
        flush()
        assertTrue(sent.isEmpty())
        assertEquals(5, left().size)
        Scrobbler.setEnabled(ctx, true)
    }
}
