/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.js

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

/**
 * SRC-2: a Mangayomi script asks JsBridge for any URL. It must never reach the user's own
 * network (router, NAS, TorrServer on 127.0.0.1), whatever the name, redirect or DNS answer.
 * MockWebServer listens on loopback, so every request it receives is a request that got through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JsBridgePrivateNetworkTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.close() }

    private fun bridge(client: OkHttpClient = OkHttpClient()) = JsBridge(context, client, JsPreferences(context, "ssrf-test"))

    private fun get(b: JsBridge, url: String): JsonObject {
        val raw = b.handle("http_get", "[null,{},\"$url\",{}]").toString()
        return JsJson.json.parseToJsonElement(raw) as JsonObject
    }

    @Test fun loopbackAddressIsRefused() {
        server.enqueue(MockResponse.Builder().body("router admin page").build())
        val r = get(bridge(), server.url("/admin").toString())
        assertNotEquals("loopback answered the script: $r", 200, r["statusCode"]?.jsonPrimitive?.content?.toIntOrNull())
        assertEquals("", (r["body"] as? JsonPrimitive)?.content.orEmpty())
        assertEquals("request reached the local server", 0, server.requestCount)
    }

    @Test fun publicNameResolvingToPrivateAddressIsRefused() {
        // DNS rebinding: a public-looking name that answers with a private address.
        val rebinding = OkHttpClient.Builder().dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (hostname == "cdn.public-example.com") listOf(InetAddress.getByName(server.hostName)) else Dns.SYSTEM.lookup(hostname)
        }).build()
        server.enqueue(MockResponse.Builder().body("nas files").build())
        val r = get(bridge(rebinding), "http://cdn.public-example.com:${server.port}/files")
        assertNotEquals("rebinding answered the script: $r", 200, r["statusCode"]?.jsonPrimitive?.content?.toIntOrNull())
        assertEquals("request reached the local server", 0, server.requestCount)
    }

    @Test fun localhostNameIsRefused() {
        server.enqueue(MockResponse.Builder().body("torrserver").build())
        val r = get(bridge(), "http://localhost:${server.port}/echo")
        assertNotEquals("localhost answered the script: $r", 200, r["statusCode"]?.jsonPrimitive?.content?.toIntOrNull())
        assertEquals("request reached the local server", 0, server.requestCount)
    }
}
