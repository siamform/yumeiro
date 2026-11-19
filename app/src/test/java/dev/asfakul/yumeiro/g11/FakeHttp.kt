/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g11

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** One request as the fake server saw it. */
data class Seen(val method: String, val url: String, val body: String)

/**
 * In-process fake for G11 tests: [answer] maps each request to an Int (status, empty body), a String
 * (200 JSON body), a Pair<Int, String> (status + body) or an IOException (offline). Every request is recorded.
 */
class FakeHttp(private val answer: (Seen) -> Any) {
    val seen = CopyOnWriteArrayList<Seen>()
    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val r = chain.request()
        val s = Seen(r.method, r.url.toString(), bodyOf(r))
        seen += s
        val json = "application/json".toMediaType()
        fun resp(code: Int, body: String) = Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody(json)).build()
        when (val a = answer(s)) {
            is IOException -> throw a
            is Int -> resp(a, "")
            is Pair<*, *> -> resp(a.first as Int, a.second as String)
            else -> resp(200, a as String)
        }
    }).build()

    private fun bodyOf(r: Request): String = r.body?.let { b -> okio.Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
}
