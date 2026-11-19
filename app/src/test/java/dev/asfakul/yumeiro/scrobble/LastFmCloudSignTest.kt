/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.scrobble

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * SEC-LFM (ADR-048): Yumeiro's built-in Last.fm key is signed by Yumeiro Cloud, so the APK carries no shared
 * secret. The phone sends only the call's parameters; every refusal keeps the plays (transient), so the
 * scrobble queue tries again on its next flush instead of dropping them or looping.
 */
class LastFmCloudSignTest {
    private val seen = mutableListOf<Request>()
    private var bodies = mutableListOf<String>()
    private fun client(code: Int, body: String) = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val r = chain.request(); seen += r; bodies += Buffer().also { r.body?.writeTo(it) }.readUtf8()
        Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }).build()
    private val params = mapOf("method" to "track.scrobble", "api_key" to "k", "sk" to "SK", "artist[0]" to "Björk", "track[0]" to "Jóga")

    @Test fun theWorkerSignsAndThePhoneSendsOnlyTheParameters() {
        val sig = LastFm.cloudSig(client(200, """{"api_sig":"0886adad51e1f072f531d9f437427cd8"}"""), params, "api.yumeiro.click")
        assertEquals("0886adad51e1f072f531d9f437427cd8", sig)
        assertEquals("https://api.yumeiro.click/v1/lastfm/sign", seen.single().url.toString())
        val sent = JSONObject(bodies.single()).getJSONObject("params")
        assertEquals(params.keys, sent.keys().asSequence().toSet())
        assertTrue("no secret or signature leaves the phone", !sent.has("api_sig") && "secret" !in bodies.single())
    }

    private fun refused(code: Int, body: String): LastFm.ApiException {
        try { LastFm.cloudSig(client(code, body), params, "api.yumeiro.click"); fail("no exception for $code") } catch (e: LastFm.ApiException) { return e }
        error("unreachable")
    }

    @Test fun everyRefusalKeepsThePlaysForTheNextFlush() {
        assertEquals(29, refused(429, """{"error":"slow_down"}""").code)
        for ((code, body) in listOf(503 to """{"error":"lastfm_off"}""", 500 to "{}", 502 to "<html>Bad Gateway</html>", 403 to """{"error":"wrong_key"}""", 200 to """{"api_sig":"short"}""")) {
            val e = refused(code, body)
            assertTrue("$code is transient", e.transient)
            assertTrue("$code says what happened: ${e.message}", e.message!!.isNotBlank())
        }
    }

    @Test fun noCloudHostMeansTheUserNeedsTheirOwnKey() {
        try { LastFm.cloudSig(client(200, "{}"), params, ""); fail() } catch (e: LastFm.ApiException) {
            assertTrue(e.message!!.contains("own API key")); assertTrue(seen.isEmpty())
        }
    }
}
