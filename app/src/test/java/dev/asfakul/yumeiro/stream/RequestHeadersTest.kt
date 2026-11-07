/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.app.Application
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The headers an extension gives must reach the host for the video, its subtitles and its audio. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RequestHeadersTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.close() }

    private fun fetch(factory: RequestAwareHttpFactory, url: String) {
        server.enqueue(MockResponse.Builder().body("ok").build())
        val ds = factory.createDataSource()
        ds.open(DataSpec(Uri.parse(url)))
        ds.close()
    }

    @Test fun videoSubtitleAndAudio_eachGetTheirOwnHeaders() {
        val video = server.url("/v.mp4").toString()
        val sub = server.url("/en.vtt").toString()
        val audio = server.url("/a.m4a").toString()
        val req = StreamRequest(
            url = video, userAgent = "MT-Test", referer = "https://video.example/", headers = mapOf("X-Token" to "abc"),
            subtitles = listOf(StreamSubtitle(sub, "English", headers = mapOf("Referer" to "https://subs.example/"))),
            audioTracks = listOf(StreamAudio(audio, headers = mapOf("Referer" to "https://audio.example/"))),
        )
        val factory = RequestAwareHttpFactory(context, "default-ua") { req }

        fetch(factory, video)
        server.takeRequest().let {
            assertEquals("https://video.example/", it.headers["Referer"])
            assertEquals("abc", it.headers["X-Token"])
            assertEquals("MT-Test", it.headers["User-Agent"])
        }
        fetch(factory, sub)
        server.takeRequest().let {
            assertEquals("https://subs.example/", it.headers["Referer"])
            assertEquals("abc", it.headers["X-Token"]) // the video's other headers still apply
        }
        fetch(factory, audio)
        assertEquals("https://audio.example/", server.takeRequest().headers["Referer"])
    }
}
