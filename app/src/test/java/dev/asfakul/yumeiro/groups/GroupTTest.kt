/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import dev.asfakul.yumeiro.stream.SearchLink
import dev.asfakul.yumeiro.subtitles.SubtitleCharset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.util.Locale

/** F3 (Group T): old code-page subtitles and links typed into search. */
class GroupTTest {
    private fun srt(t: String) = "1\n00:00:01,000 --> 00:00:03,000\n$t\n\n2\n00:00:04,000 --> 00:00:06,000\n$t\n"

    private fun check(cs: String, text: String, hint: String?) {
        val bytes = srt(text).toByteArray(Charset.forName(cs))
        val r = SubtitleCharset.detect(bytes, hint)
        assertTrue("$cs hint=$hint read as ${r.charset}: ${r.text.lines()[2]}", r.text.contains(text))
    }

    @Test fun t_nonLatinPagesNeedNoHint() {
        val keep = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            check("windows-1251", "Привет! Как у тебя дела сегодня? Мы скоро уезжаем, собирай вещи.", null)
            check("windows-1253", "Γεια σου! Πώς είσαι σήμερα; Φεύγουμε σύντομα, μάζεψε τα πράγματά σου.", null)
            check("windows-1255", "שלום! מה שלומך היום? אנחנו עוזבים בקרוב, תארוז את הדברים שלך.", null)
            check("windows-1256", "مرحبا! كيف حالك اليوم؟ سنغادر قريبا، اجمع أغراضك.", null)
            check("x-windows-874", "สวัสดี! วันนี้คุณเป็นอย่างไรบ้าง เราจะออกเดินทางเร็วๆ นี้", null)
            check("windows-1250", "Cześć! Jak się dziś czujesz? Wkrótce wyjeżdżamy, spakuj swoje rzeczy. Źle, łódź żółta.", null)
            check("windows-1250", "Příliš žluťoučký kůň úpěl ďábelské ódy. Brzy odjíždíme, sbal si věci.", null)
            check("windows-1254", "Merhaba! Bugün nasılsın? Yakında gidiyoruz, eşyalarını topla. Işık, güneş, çiçek.", null)
        } finally { Locale.setDefault(keep) }
    }

    @Test fun t_westernTextStaysWindows1252() {
        check("windows-1252", "Salut ! Comment ça va aujourd'hui ? Nous partons bientôt, prépare tes affaires.", "fr")
        check("windows-1252", "¡Hola! ¿Cómo estás hoy? Nos vamos pronto, recoge tus cosas, niño.", "en")
        check("windows-1252", "Hallo! Wie geht es dir heute? Schöne Grüße. Straße, Mädchen, über.", "en")
    }

    @Test fun t_hintsAndCjk() {
        check("windows-1250", "Cześć! Jak się dziś czujesz?", "pl")
        check("windows-1254", "Bugün nasılsın? Eşyalarını topla.", "tr")
        check("windows-1250", "Jó napot! Hogy vagy? Köszönöm, jól vagyok. Őszintén szólva, tűz.", "hu")
        check("windows-1257", "Labas! Kaip sekasi? Ačiū, gerai. Šiandien gražu, žmonės čia.", "lt")
        check("windows-1250", "Bună ziua! Ce mai faceţi? Mulţumesc frumos, şi eu sunt bine. Ţara mea.", "en")
        check("Shift_JIS", "こんにちは、元気ですか？今日はいい天気ですね。", "ja")
        check("GBK", "你好，今天天气很好。我们很快就要走了。", "zh")
        check("EUC-KR", "안녕하세요, 오늘 날씨가 좋네요. 곧 떠나요.", "ko")
    }

    @Test fun t_unicodeIsKept() {
        val text = "こんにちは — Привет — مرحبا — café"
        assertEquals("UTF-8", SubtitleCharset.detect(srt(text).toByteArray()).charset)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + srt(text).toByteArray()
        assertTrue(SubtitleCharset.decode(bom).startsWith("1\n"))
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + srt(text).toByteArray(Charsets.UTF_16LE)
        assertTrue(SubtitleCharset.decode(le).contains(text))
        assertEquals("", SubtitleCharset.decode(ByteArray(0)))
        assertEquals("plain ascii", SubtitleCharset.decode("plain ascii".toByteArray()))
    }

    @Test fun t_searchLinkParse() {
        assertEquals("https://cdn.example.com/a/b.m3u8", SearchLink.parse("  https://cdn.example.com/a/b.m3u8 "))
        assertEquals("http://localhost:8080/v.mp4", SearchLink.parse("http://localhost:8080/v.mp4"))
        assertEquals("rtsp://10.0.0.5/live", SearchLink.parse("rtsp://10.0.0.5/live"))
        val m = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=x"
        assertEquals(m, SearchLink.parse(m))
        assertTrue(SearchLink.parse("0123456789ABCDEF0123456789ABCDEF01234567")!!.startsWith("magnet:?xt=urn:btih:0123456789abcdef"))
        assertNull(SearchLink.parse("one piece"))
        assertNull(SearchLink.parse("https://"))
        assertNull(SearchLink.parse("http://nohost/x"))
        assertNull(SearchLink.parse("attackontitanfinalseasonpartthree")) // 33 letters, not a hash
        assertNull(SearchLink.parse("abcdefghijklmnopqrstuvwxyzabcdef")) // 32 base32 letters but no digit
        assertNull(SearchLink.parse("https://example.com/watch me"))
    }
}
