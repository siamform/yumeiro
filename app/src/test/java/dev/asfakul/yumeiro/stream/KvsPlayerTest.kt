/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NBUG-12: KVS player configs. The page below is cut down from the real Porn4fans title page
 * (2026-11-18, tokens shortened); the `function/0/` expectations were produced by yt-dlp's own
 * `_kvs_get_real_url` / `_kvs_get_license_token` (generic.py) run on the same inputs.
 */
class KvsPlayerTest {
    private val page = "https://www.example-kvs.com/video/12934/some-title/"
    private val html = """
        <script type="application/ld+json">{"contentUrl": "https://www.example-kvs.com/get_file/5/aaaa/12000/12934/12934_720p.mp4/"}</script>
        <script>
        var t5cab4e16df = {
            video_id: '12934', video_title: 'Some \'quoted\' title',
            license_code: '${'$'}546156418018655',
            rnd: '1795031175',
            video_url: 'https://www.example-kvs.com/get_file/5/d001/12000/12934/12934.mp4/?v-acctoken=NTIz',
            video_url_text: '480p',
            video_alt_url: 'https:\/\/www.example-kvs.com\/get_file\/5\/0195\/12000\/12934\/12934_720p.mp4\/?v-acctoken=ODk3',
            video_alt_url_text: '720p',
            video_alt_url2: 'https://www.example-kvs.com/login-required/',
            video_alt_url2_text: '1080p', video_alt_url2_redirect: '1',
            timeline_screens_url: 'https://www.example-kvs.com/contents/12934/timelines/{time}.jpg',
            preview_url: 'https://www.example-kvs.com/contents/12934/1.jpg'
        };
        kt_player('kt_player', 'https://www.example-kvs.com/player/kt_player.swf?v=2.7.5', '100%', '100%', t5cab4e16df);
        </script>
    """.trimIndent()

    @Test fun readsTheNamedConfigPastBracesInsideStrings() {
        val fv = KvsPlayer.flashvars(html)!!
        assertEquals("12934", fv["video_id"])
        assertEquals("Some 'quoted' title", fv["video_title"])
        assertEquals("https://www.example-kvs.com/contents/12934/1.jpg", fv["preview_url"]) // after "{time}"
    }

    @Test fun sourcesBestFirstSkippingSignInOnes() {
        val s = KvsPlayer.sources(html, page)
        assertEquals(listOf(
            "https://www.example-kvs.com/get_file/5/0195/12000/12934/12934_720p.mp4/?v-acctoken=ODk3",
            "https://www.example-kvs.com/get_file/5/d001/12000/12934/12934.mp4/?v-acctoken=NTIz",
        ), s.map { it.url })
        assertEquals(listOf(720, 480), s.map { it.height })
    }

    @Test fun pageExtractorPutsKvsSourcesFirst() {
        val a = WebPageExtractor.analyze(html, page)
        assertEquals("https://www.example-kvs.com/get_file/5/0195/12000/12934/12934_720p.mp4/?v-acctoken=ODk3", a.candidates.first().url)
        assertEquals("kvs", a.candidates.first().origin)
        assertTrue(a.candidates.none { it.url.contains("login-required") })
    }

    @Test fun licenseTokenMatchesYtDlp() {
        assertArrayEquals(intArrayOf(6, 5, 7, 2, 8, 0, 5, 9, 1, 6, 0, 1, 0, 4, 5, 3, 3, 4, 2, 9, 3, 1, 8, 5, 3, 0, 7, 9, 5, 2, 4, 5),
            KvsPlayer.licenseToken("$546156418018655"))
        assertArrayEquals(intArrayOf(3, 0, 9, 5, 5, 4, 0, 1, 8, 4, 5, 2, 5, 6, 3, 4, 6, 3, 4, 1, 0, 1, 8, 6, 4, 1, 9, 0, 3, 1, 2, 3),
            KvsPlayer.licenseToken("$521785631230418"))
        assertNull(KvsPlayer.licenseToken("abc"))
    }

    @Test fun descramblesFunctionZeroLikeYtDlp() {
        val u = "function/0/https://h.example/get_file/5/0123456789abcdefghijklmnopqrstuv/12000/12934/12934_720p.mp4/?v-acctoken=Q&x=1"
        assertEquals("https://h.example/get_file/5/idom0nc892us75lk6thgv3j4fpqreab1/12000/12934/12934_720p.mp4/?v-acctoken=Q&x=1",
            KvsPlayer.realUrl(u, "$546156418018655"))
        assertEquals("https://h.example/get_file/5/cpbn8t0g1jksu9d7r2ov6m4afqel5i3h/12000/12934/12934_720p.mp4/?v-acctoken=Q&x=1",
            KvsPlayer.realUrl(u, "$521785631230418"))
        assertEquals("http://127.0.0.1:8080/get_file/5/idom0nc892us75lk6thgv3j4fpqreab1XY/1000/1/1.mp4/?v-acctoken=YUNB12TOKEN",
            KvsPlayer.realUrl("function/0/http://127.0.0.1:8080/get_file/5/0123456789abcdefghijklmnopqrstuvXY/1000/1/1.mp4/?v-acctoken=YUNB12TOKEN", "$546156418018655"))
        assertNull("no licence → can't de-scramble", KvsPlayer.realUrl(u, null))
        assertEquals("plain URLs pass through", "https://h/x.mp4/", KvsPlayer.realUrl("https://h/x.mp4/", null))
    }

    @Test fun notAKvsPage() {
        assertNull(KvsPlayer.flashvars("<html><video src=\"a.mp4\"></video></html>"))
        assertTrue(KvsPlayer.sources("<script>var flashvars = { a: 'b' };</script>", page).isEmpty())
    }
}
