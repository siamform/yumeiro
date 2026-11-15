/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.online.anime.AniSkip
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** A failed AniSkip call must not be cached as "no segments"; a real answer is. */
class AniSkipCacheTest {
    private val ok = """{"found":true,"results":[{"interval":{"startTime":60,"endTime":150},"skipType":"op"}]}"""

    private fun client(answers: MutableList<Any>, calls: IntArray) = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        calls[0]++
        when (val a = answers.removeAt(0)) {
            is IOException -> throw a
            is Int -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(a).message("x").body("".toResponseBody(null)).build()
            else -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((a as String).toResponseBody("application/json".toMediaType())).build()
        }
    }).build()

    @Test fun networkFailureIsRetriedAndSuccessIsCached() {
        val calls = IntArray(1)
        val c = client(mutableListOf(IOException("offline"), ok), calls)
        assertEquals(0, AniSkip.segments(c, 900001, 1).size)
        assertEquals(1, AniSkip.segments(c, 900001, 1).size)
        assertEquals(1, AniSkip.segments(c, 900001, 1).size) // cached: no third call
        assertEquals(2, calls[0])
    }

    @Test fun serverErrorIsRetriedButNotFoundIsCached() {
        val calls = IntArray(1)
        val c = client(mutableListOf(503, 404), calls)
        assertEquals(0, AniSkip.segments(c, 900002, 1).size)
        assertEquals(0, AniSkip.segments(c, 900002, 1).size)
        assertEquals(0, AniSkip.segments(c, 900002, 1).size)
        assertEquals(2, calls[0])
    }
}
