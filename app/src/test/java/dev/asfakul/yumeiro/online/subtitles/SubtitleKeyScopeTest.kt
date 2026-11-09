/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.subtitles

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The user's subtitle API key must never be resent to another host or over plain HTTP by a redirect. */
class SubtitleKeyScopeTest {
    private lateinit var site: MockWebServer
    private lateinit var other: MockWebServer

    @Before fun setUp() {
        site = MockWebServer(); site.start()
        other = MockWebServer(); other.start()
    }

    @After fun tearDown() {
        site.close(); other.close()
    }

    @Test fun keyAllowedOnlyOnHttpsSiteAndSubdomains() {
        assertTrue(SubtitleNet.keyAllowed("https://api.subsource.net/api/v1/subtitles".toHttpUrl(), "subsource.net"))
        assertTrue(SubtitleNet.keyAllowed("https://subsource.net/x".toHttpUrl(), "subsource.net"))
        assertTrue(SubtitleNet.keyAllowed("https://vip-api.opensubtitles.com/api/v1/download".toHttpUrl(), "opensubtitles.com"))
        assertFalse(SubtitleNet.keyAllowed("http://api.subsource.net/api/v1".toHttpUrl(), "subsource.net")) // downgrade
        assertFalse(SubtitleNet.keyAllowed("https://cdn.example.com/file.zip".toHttpUrl(), "subsource.net"))
        assertFalse(SubtitleNet.keyAllowed("https://evilsubsource.net/".toHttpUrl(), "subsource.net")) // suffix trick
        assertFalse(SubtitleNet.keyAllowed("https://subsource.net.evil.com/".toHttpUrl(), "subsource.net"))
    }

    @Test fun keyHeaderDroppedWhenRedirectLeavesTheSite() {
        // Two MockWebServers addressed by different host names: "localhost" plays the site, 127.0.0.1 the CDN.
        val cdn = other.url("/file.zip").newBuilder().host("127.0.0.1").build()
        site.enqueue(MockResponse.Builder().code(302).addHeader("Location", cdn.toString()).build())
        other.enqueue(MockResponse.Builder().body("zip").build())
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(SubtitleNet.KeyScope("X-API-Key") { it.host == "localhost" })
            .build()
        val start = site.url("/download").newBuilder().host("localhost").build()
        client.newCall(Request.Builder().url(start).header("X-API-Key", "user-key").header("Accept", "application/json").build())
            .execute().use { assertEquals("zip", it.body.string()) }

        assertEquals("user-key", site.takeRequest().headers["X-API-Key"]) // the site itself gets the key
        val hop = other.takeRequest()
        assertNull(hop.headers["X-API-Key"]) // the other host does not
        assertEquals("application/json", hop.headers["Accept"]) // other headers are untouched
    }

    @Test fun keyHeaderKeptOnSameSiteRedirect() {
        site.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/v2/download").build())
        site.enqueue(MockResponse.Builder().body("ok").build())
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(SubtitleNet.KeyScope("Api-Key") { it.host == site.hostName })
            .build()
        client.newCall(Request.Builder().url(site.url("/download")).header("Api-Key", "k").build()).execute().use { it.body.string() }
        assertEquals("k", site.takeRequest().headers["Api-Key"])
        assertEquals("k", site.takeRequest().headers["Api-Key"])
    }
}
