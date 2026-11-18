/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import dev.asfakul.yumeiro.online.download.DashGrab
import dev.asfakul.yumeiro.online.download.ForceGrab
import dev.asfakul.yumeiro.online.download.ForceHeaders
import dev.asfakul.yumeiro.online.download.Segmented
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** R15, Phase 5: forced downloading (multi-connection chunks, header escalation, DASH plans). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForceGrabTest {

    @Test fun chunksCoverTheFileExactlyOnce() {
        val total = 10L * 1024 * 1024 + 7
        val c = ForceGrab.chunks(total, 4L * 1024 * 1024)
        assertEquals(3, c.size)
        assertEquals(0L, c.first().first)
        assertEquals(total - 1, c.last().last)
        assertEquals(total, c.sumOf { it.last - it.first + 1 })
        c.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
        assertTrue(ForceGrab.chunks(0).isEmpty())
        assertEquals(listOf(0L..0L), ForceGrab.chunks(1))
    }

    @Test fun contentRangeTotal() {
        assertEquals(12345L, ForceGrab.totalFromContentRange("bytes 0-0/12345"))
        assertNull(ForceGrab.totalFromContentRange("bytes 0-0/*"))
        assertNull(ForceGrab.totalFromContentRange(null))
    }

    @Test fun connectionsAreFewerOnLowEndPhones() {
        assertEquals(2, ForceGrab.connections(lowEnd = true))
        assertEquals(4, ForceGrab.connections(lowEnd = false))
    }

    @Test fun headersEscalate() {
        val base = mapOf("Referer" to "https://site.example:8443/watch/1", "User-Agent" to "ExoPlayer")
        assertEquals(base, ForceHeaders.forAttempt(base, 0, "a=b"))
        val one = ForceHeaders.forAttempt(base, 1, "a=b")
        assertEquals(ForceHeaders.BROWSER_UA, one["User-Agent"])
        assertEquals("https://site.example:8443", one["Origin"])
        assertEquals("a=b", one["Cookie"])
        assertEquals("video", one["Sec-Fetch-Dest"])
        assertEquals(base["Referer"], one["Referer"])
        val two = ForceHeaders.forAttempt(base, 2, null)
        assertFalse(two.containsKey("Referer")); assertFalse(two.containsKey("Origin")); assertFalse(two.containsKey("Cookie"))
        assertEquals(ForceHeaders.BROWSER_UA, two["User-Agent"])
        // A cookie the extension already set is kept, never overwritten.
        assertEquals("mine=1", ForceHeaders.forAttempt(base + ("Cookie" to "mine=1"), 1, "a=b")["Cookie"])
    }

    @Test fun finishedChunkFileIsRead() {
        val f = File.createTempFile("yumeiro", ".chunks"); f.writeText("0\n3\n\nx\n2\n")
        assertEquals(setOf(0, 2, 3), ForceGrab.readDone(f)); f.delete()
        assertTrue(ForceGrab.readDone(File(f.path + ".none")).isEmpty())
    }

    private fun mpd(dynamic: Boolean = false, drmVideo: Boolean = false, kidVideo: Boolean = false) = """<?xml version="1.0"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" xmlns:cenc="urn:mpeg:cenc:2013" type="${if (dynamic) "dynamic" else "static"}" ${if (dynamic) "availabilityStartTime=\"2026-01-01T00:00:00Z\"" else "mediaPresentationDuration=\"PT20S\""} minBufferTime="PT2S" profiles="urn:mpeg:dash:profile:isoff-live:2011">
 <Period id="0" start="PT0S">
  <AdaptationSet contentType="video" mimeType="video/mp4" segmentAlignment="true">
   ${if (kidVideo) "<ContentProtection schemeIdUri=\"urn:mpeg:dash:mp4protection:2011\" value=\"cenc\" xmlns:cenc=\"urn:mpeg:cenc:2013\" cenc:default_KID=\"10000000-1000-1000-1000-100000000001\"/>" else ""}
   ${if (drmVideo) "<ContentProtection schemeIdUri=\"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\"><cenc:pssh>AAAAAA==</cenc:pssh></ContentProtection>" else ""}
   <SegmentTemplate timescale="1000" duration="4000" startNumber="1" initialization="v/${'$'}RepresentationID${'$'}/init.mp4" media="v/${'$'}RepresentationID${'$'}/${'$'}Number${'$'}.m4s"/>
   <Representation id="360" codecs="avc1.4d401e" width="640" height="360" bandwidth="800000"/>
   <Representation id="720" codecs="avc1.4d401f" width="1280" height="720" bandwidth="2500000"/>
   <Representation id="1080" codecs="avc1.640028" width="1920" height="1080" bandwidth="5000000"/>
  </AdaptationSet>
  <AdaptationSet contentType="audio" mimeType="audio/mp4" lang="en">
   <SegmentTemplate timescale="1000" duration="4000" startNumber="1" initialization="a/${'$'}RepresentationID${'$'}/init.mp4" media="a/${'$'}RepresentationID${'$'}/${'$'}Number${'$'}.m4s"/>
   <Representation id="a64" codecs="mp4a.40.2" audioSamplingRate="44100" bandwidth="64000"/>
   <Representation id="a128" codecs="mp4a.40.2" audioSamplingRate="44100" bandwidth="128000"/>
  </AdaptationSet>
 </Period>
</MPD>"""

    @Test fun dashPlanPicksBestTrackUnderTheCapAndListsEverySegment() {
        val p = DashGrab.plan(mpd(), "https://cdn.example/show/ep1/manifest.mpd", 720)
        val v = p.video!!; val a = p.audio!!
        assertEquals(720, v.height)
        assertEquals("https://cdn.example/show/ep1/v/720/init.mp4", v.init!!.url)
        assertEquals(5, v.parts.size) // 20 s / 4 s
        assertEquals("https://cdn.example/show/ep1/v/720/1.m4s", v.parts.first().url)
        assertEquals("https://cdn.example/show/ep1/v/720/5.m4s", v.parts.last().url)
        assertEquals(128000, a.bitrate)
        assertEquals("https://cdn.example/show/ep1/a/a128/init.mp4", a.init!!.url)
        assertFalse(p.webm)
        // No cap → the best; a cap below every track → the smallest one.
        assertEquals(1080, DashGrab.plan(mpd(), "https://cdn.example/m.mpd", 0).video!!.height)
        assertEquals(360, DashGrab.plan(mpd(), "https://cdn.example/m.mpd", 144).video!!.height)
    }

    @Test fun dashLiveIsRefused() {
        try { DashGrab.plan(mpd(dynamic = true), "https://cdn.example/live.mpd", 720); fail() }
        catch (e: IOException) { assertTrue(e.message!!, e.message!!.contains("Live")) }
    }

    @Test fun dashKeyIdProtectedVideoIsNeverSaved() {
        val p = DashGrab.plan(mpd(kidVideo = true), "https://cdn.example/m.mpd", 720)
        assertNull(p.video); assertEquals(128000, p.audio!!.bitrate)
        assertEquals(setOf(0), DashGrab.protectedSets(mpd(kidVideo = true)))
        assertTrue(DashGrab.protectedSets(mpd()).isEmpty())
    }

    @Test fun dashDrmVideoIsNeverSaved() {
        // The DRM video set is skipped; only the clear audio could be saved.
        val p = DashGrab.plan(mpd(drmVideo = true), "https://cdn.example/m.mpd", 720)
        assertNull(p.video)
        assertEquals(128000, p.audio!!.bitrate)
    }

    // ---- Live: real hosts, real bytes (run with -Pyumeiro.live=true) ----

    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private fun md5(f: File) = MessageDigest.getInstance("MD5").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @Test fun liveSegmentedDownloadEqualsSingleConnection() = runBlocking {
        assumeTrue(System.getProperty("yumeiro.live") == "true")
        val url = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4"
        val dir = File("build/force-live").apply { mkdirs() }
        val multi = File(dir, "multi.mp4").apply { delete() }; File(multi.path + ".chunks").delete()
        val single = File(dir, "single.mp4").apply { delete() }
        val lastDone = java.util.concurrent.atomic.AtomicLong()
        val n1 = Segmented(client, 4, segmentFrom = 256 * 1024, chunk = 256 * 1024).download(url, emptyMap(), multi) { d, _ -> lastDone.accumulateAndGet(d) { a, b -> maxOf(a, b) } }
        val n2 = Segmented(client, 1).download(url, emptyMap(), single) { _, _ -> }
        assertEquals(n2, n1); assertEquals(n1, multi.length()); assertEquals(md5(single), md5(multi))
        assertEquals(n1, lastDone.get())
        // G8-3 (ADR-020): the record stays until the worker renames the file (OnlineDownloads.removeSideFiles).
        assertTrue("resume record kept for the worker", File(multi.path + ".chunks").exists())
        File(dir, "report.txt").writeText("segmented bytes=$n1 md5=${md5(multi)} single md5=${md5(single)}\n")
    }

    @Test fun liveSegmentedResumesFromTheChunkFile() = runBlocking {
        assumeTrue(System.getProperty("yumeiro.live") == "true")
        val url = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4"
        val dir = File("build/force-live").apply { mkdirs() }
        val ref = File(dir, "ref.mp4").apply { delete() }
        Segmented(client, 1).download(url, emptyMap(), ref) { _, _ -> }
        val out = File(dir, "resume.mp4").apply { delete() }
        val chunk = 256L * 1024
        // Pretend chunk 0 and 2 were finished before the app died; their bytes are in place.
        val bytes = ref.readBytes()
        java.io.RandomAccessFile(out, "rw").use { raf ->
            raf.setLength(bytes.size.toLong())
            for (i in listOf(0, 2)) { raf.seek(i * chunk); raf.write(bytes, (i * chunk).toInt(), chunk.toInt()) }
        }
        File(out.path + ".chunks").writeText("0\n2\n")
        val n = Segmented(client, 4, segmentFrom = chunk, chunk = chunk).download(url, emptyMap(), out) { _, _ -> }
        assertEquals(bytes.size.toLong(), n); assertEquals(md5(ref), md5(out))
    }

    @Test fun liveRealManifestGivesOneContainerAndTheMainLanguage() {
        assumeTrue(System.getProperty("yumeiro.live") == "true")
        // Shaka's angel-one: mp4 + webm video, AAC + Opus audio in 7 languages, SegmentBase files.
        val url = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd"
        val mpd = client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { it.body.string() }
        val p = DashGrab.plan(mpd, url, 720)
        assertEquals("video/avc", p.video!!.mime); assertEquals(576, p.video!!.height)
        assertEquals("audio/mp4a-latm", p.audio!!.mime)
        assertFalse(p.webm)
        assertEquals(1, p.video!!.parts.size)
        assertTrue(p.video!!.parts[0].url.startsWith("https://storage.googleapis.com/shaka-demo-assets/angel-one/"))
        val low = DashGrab.plan(mpd, url, 360)
        assertEquals(360, low.video!!.height)
        File("build/force-live").apply { mkdirs() }.resolve("dash-plan.txt").writeText("angel-one cap720 → ${p.video} / ${p.audio}\n")
    }
}
