/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-30. Before: a DiskWala share opened the web finder (auto), which waited on DiskWala's "Get the
 * app" page and never found a video. Fixture: the real temp_info answer of 2026-11-21.
 */
class DiskWalaHandoffTest {
    private val server = MockWebServer()
    private val link = "https://www.diskwala.com/app/6afdd044b61fd14efb3a5afd"

    @After fun tearDown() { DiskWalaApi.base = "https://udapi.diskwala.com/api/v1"; runCatching { server.close() } }

    private fun serve(body: String?) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.url.encodedPath == "/file/temp_info" && body != null) MockResponse.Builder().body(body).build()
                else MockResponse.Builder().code(404).build()
        }
        server.start()
        DiskWalaApi.base = server.url("/").toString().trimEnd('/')
    }

    @Test fun shareIdParsing() {
        assertEquals("6afdd044b61fd14efb3a5afd", DiskWalaApi.shareId(link))
        assertNull(DiskWalaApi.shareId("https://www.diskwala.com/"))
        assertEquals("1.0 GB", DiskWalaApi.sizeLabel(1_108_658_512L))
    }

    @Test fun knownShareIsHandedToTheDiskWalaApp() {
        serve("""{"fileInfo":{"_id":"6afdd044b61fd14efb3a5afd","extension":"mkv","name":"Movie.2026.mkv","size":1108658512,"type":"video/x-matroska"}}""")
        val r = StreamResolver.resolveBlocking(StreamRequest(link), "ua", OkHttpClient())
        assertTrue("got $r", r is Resolution.Handoff)
        r as Resolution.Handoff
        assertEquals("the DiskWala app", r.platform)
        assertEquals(link, r.url)
        assertTrue(r.reason, r.reason.startsWith("Movie.2026.mkv (1.0 GB)."))
    }

    @Test fun unknownShareSaysSo() {
        serve(null)
        val r = StreamResolver.resolveBlocking(StreamRequest(link), "ua", OkHttpClient())
        assertTrue("got $r", r is Resolution.Failed && r.title == "DiskWala file not found")
    }
}
