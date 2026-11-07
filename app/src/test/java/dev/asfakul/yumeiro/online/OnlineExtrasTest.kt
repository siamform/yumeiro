/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.anime.AniSkip
import dev.asfakul.yumeiro.online.anime.AnimeIds
import dev.asfakul.yumeiro.online.download.HlsDownloader
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.subtitles.OpenSubtitles
import dev.asfakul.yumeiro.stream.StreamInterceptors
import dev.asfakul.yumeiro.ui.screens.online.heroPicks
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlineExtrasTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun hlsBestVariantPicksHighestBandwidth() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=9000000,BANDWIDTH=800000,RESOLUTION=640x360
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            hi/index.m3u8
        """.trimIndent()
        assertEquals("https://cdn.example/v/hi/index.m3u8", HlsDownloader.bestVariant(master, "https://cdn.example/v/master.m3u8"))
        assertNull(HlsDownloader.bestVariant("#EXTM3U\n#EXTINF:4,\na.ts", "https://x/y.m3u8"))
    }

    @Test fun hlsMediaPlaylistKeysAndSequence() {
        val media = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:7
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x0000000000000000000000000000000A
            #EXTINF:4,
            s1.m4s
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4,
            s2.m4s
        """.trimIndent()
        val segs = HlsDownloader.parseMedia(media, "https://cdn.example/v/index.m3u8")
        assertEquals(2, segs.size)
        assertEquals("https://cdn.example/v/s1.m4s", segs[0].url)
        assertEquals("https://cdn.example/v/key.bin", segs[0].keyUrl)
        assertEquals(10, segs[0].iv!![15].toInt())
        assertEquals(7L, segs[0].sequence)
        assertEquals("https://cdn.example/v/init.mp4", segs[0].initUrl)
        assertNull(segs[1].keyUrl)
        assertEquals(8L, segs[1].sequence)
    }

    @Test fun hlsAesDecryptRoundTrip() {
        val key = HlsDownloader.hex("000102030405060708090a0b0c0d0e0f")
        val iv = HlsDownloader.ivFor(258)
        assertEquals(1, iv[14].toInt()); assertEquals(2, iv[15].toInt())
        val plain = "hello segment data".toByteArray()
        val enc = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }.doFinal(plain)
        assertArrayEquals(plain, HlsDownloader.decrypt(enc, key, iv))
    }

    @Test fun aniSkipParse() {
        val json = """{"found":true,"results":[
            {"interval":{"startTime":1300.5,"endTime":1390},"skipType":"ed"},
            {"interval":{"startTime":60,"endTime":150},"skipType":"op"},
            {"interval":{"startTime":5,"endTime":5},"skipType":"recap"},
            {"interval":{"startTime":1,"endTime":2},"skipType":"weird"}]}"""
        val s = AniSkip.parse(json)
        assertEquals(2, s.size)
        assertEquals(AniSkip.Kind.INTRO, s[0].kind); assertEquals(60_000L, s[0].startMs)
        assertEquals(AniSkip.Kind.OUTRO, s[1].kind); assertEquals(1_300_500L, s[1].startMs)
        assertTrue(AniSkip.parse("""{"found":false,"results":[]}""").isEmpty())
    }

    @Test fun openSubtitlesParseSortsTrustedFirst() {
        val json = """{"data":[
            {"attributes":{"language":"en","release":"A","download_count":10,"from_trusted":false,"files":[{"file_id":1}]}},
            {"attributes":{"language":"en","release":"","download_count":5,"from_trusted":true,"files":[{"file_id":2,"file_name":"B.srt"}]}},
            {"attributes":{"language":"en","files":[]}}]}"""
        val r = OpenSubtitles.parse(json)
        assertEquals(listOf(2L, 1L), r.map { it.fileId })
        assertEquals("B.srt", r[0].release)
    }

    @Test fun bookmarksParseAndNewEpisodes() {
        val b = OnlineBookmarks.from(JSONObject("""{"api":"P","url":"u","name":"Show","status":"WATCHING","episodes":3}"""))!!
        assertEquals(OnlineBookmarks.Status.WATCHING, b.status)
        assertTrue(b.notify)
        assertNull(OnlineBookmarks.from(JSONObject("""{"url":"u"}""")))
        assertEquals(OnlineBookmarks.Status.PLAN, OnlineBookmarks.from(JSONObject("""{"api":"P","url":"u","status":"??"}"""))!!.status)

        OnlineBookmarks.reset()
        OnlineBookmarks.toggle(context, OnlineItem("Show", "u", "P", null), episodes = 3)
        assertEquals(2, OnlineBookmarks.checked(context, "P", "u", 5))
        assertEquals(0, OnlineBookmarks.checked(context, "P", "u", 5))
        assertEquals(2, OnlineBookmarks.items.value.single().newEpisodes)
        OnlineBookmarks.reset()
    }

    @Test fun providerOverrideNormalize() {
        assertEquals("https://example.org", ProviderOverrides.normalize(" example.org/ "))
        assertEquals("http://a.b/x", ProviderOverrides.normalize("http://a.b/x"))
        assertNull(ProviderOverrides.normalize("  "))
        assertNull(ProviderOverrides.normalize(null))
    }

    @Test fun animeTitleCleanup() {
        assertEquals("Frieren", AnimeIds.cleanTitle("Frieren (Dub)"))
        assertEquals("One Piece", AnimeIds.cleanTitle("One Piece [1080p]"))
        assertEquals("Naruto", AnimeIds.cleanTitle("Naruto Dubbed"))
    }

    @Test fun downloadFileNamesAreSafe() {
        assertEquals("a b c", OnlineDownloads.safeName("a/b:c"))
        assertEquals("video", OnlineDownloads.safeName("  "))
        assertTrue(OnlineDownloads.safeName("x".repeat(200)).length <= 80)
    }

    @Test fun heroPicksInterleavesSources() {
        fun item(n: String, api: String, poster: String? = "p") = OnlineItem(n, n, api, poster)
        val picks = heroPicks(listOf(listOf(item("a1", "A"), item("a2", "A")), listOf(item("b1", "B", null), item("b2", "B"))), max = 3)
        assertEquals(listOf("a1", "b2", "a2"), picks.map { it.name })
    }

    @Test fun streamInterceptorsWrapOnlyRegisteredUrls() {
        StreamInterceptors.clear()
        val base = OkHttpClient()
        assertSame(base, StreamInterceptors.clientFor("https://x/a.m3u8", base))
        StreamInterceptors.register("https://x/a.m3u8", listOf(Interceptor { it.proceed(it.request()) }))
        val c = StreamInterceptors.clientFor("https://x/a.m3u8", base)
        assertNotSame(base, c)
        assertEquals(base.interceptors.size + 1, c.interceptors.size)
        assertSame(c, StreamInterceptors.clientFor("https://x/a.m3u8", base))
        StreamInterceptors.register("https://x/a.m3u8", emptyList())
        assertSame(base, StreamInterceptors.clientFor("https://x/a.m3u8", base))
    }
}
