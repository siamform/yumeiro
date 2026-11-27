/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.subtitles.SubtitleArchive
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.SubtitleKind
import dev.asfakul.yumeiro.stream.SubtitleSniffer
import dev.asfakul.yumeiro.ui.screens.online.OnlineSearchScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.nio.charset.Charset

/**
 * GROUP T (F3, ideas from the VyseTV audit) on a real device with the platform's own charsets. Uses only APIs that
 * existed before the group (SubtitleArchive.decode(bytes), SubtitleSniffer.prepare(…4 args), the search screen's
 * input tag), so it also ran on the old code: fail-before on `ab/groupt-before`.
 *  a  Subtitle files in old Windows code pages (Russian 1251, Greek 1253, Hebrew 1255, Arabic 1256, Thai 874,
 *     Polish 1250) read as real text, both by the subtitle downloader and by the player's parser path; UTF-8 and
 *     French Windows-1252 stay as they were.
 *  b  A direct video link pasted into Search online shows "Play this link" (no source search) and plays it.
 * Log tag YuGroupT, result lines GROUPT_<case>.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupTDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var server: MockWebServer
    private fun log(s: String) = Log.i("YuGroupT", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, what: () -> Boolean) = runCatching { compose.waitUntil(ms) { what() }; true }.getOrDefault(false)

    private val samples = listOf(
        Triple("ru", "windows-1251", "Привет! Как у тебя дела сегодня?\nМы скоро уезжаем, собирай вещи."),
        Triple("el", "windows-1253", "Γεια σου! Πώς είσαι σήμερα;\nΦεύγουμε σύντομα, μάζεψε τα πράγματά σου."),
        Triple("he", "windows-1255", "שלום! מה שלומך היום?\nאנחנו עוזבים בקרוב, תארוז את הדברים שלך."),
        Triple("ar", "windows-1256", "مرحبا! كيف حالك اليوم؟\nسنغادر قريبا، اجمع أغراضك."),
        Triple("th", "x-windows-874", "สวัสดี! วันนี้คุณเป็นอย่างไรบ้าง\nเราจะออกเดินทางเร็วๆ นี้ เก็บของของคุณ"),
        Triple("pl", "windows-1250", "Cześć! Jak się dziś czujesz?\nWkrótce wyjeżdżamy, spakuj swoje rzeczy. Źle, łódź żółta."),
        Triple("fr", "windows-1252", "Salut ! Comment ça va aujourd'hui ?\nNous partons bientôt, prépare tes affaires."),
        Triple("utf8", "UTF-8", "こんにちは — Привет — مرحبا — café"),
    )

    private fun srt(text: String) = "1\n00:00:01,000 --> 00:00:03,000\n$text\n\n"
    private fun charset(name: String): Charset = runCatching { Charset.forName(name) }.getOrElse { Charset.forName("windows-874") }

    @Before fun setUp() {
        server = MockWebServer()
        // ftyp + a tiny moov: enough for the resolver to see a real MP4 (also by content, not only the header).
        val mp4 = byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0, 0, 2, 0,
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '1'.code.toByte()) +
            ByteArray(4096)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/media/yu-group-t.mp4" -> MockResponse.Builder().addHeader("Content-Type", "video/mp4")
                    .addHeader("Accept-Ranges", "bytes").body(Buffer().write(mp4)).build()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        IncomingMedia.consume()
    }

    @After fun tearDown() {
        IncomingMedia.consume()
        runCatching { server.close() }
    }

    @Test fun a_oldCodePageSubtitlesReadAsRealText() {
        val bad = ArrayList<String>()
        for ((lang, cs, text) in samples) {
            val bytes = srt(text).toByteArray(charset(cs))
            val viaDownloader = SubtitleArchive.decode(bytes)
            val viaPlayer = String(SubtitleSniffer.prepare(bytes, 0, bytes.size, SubtitleKind.SUBRIP), Charsets.UTF_8)
            val ok1 = viaDownloader.contains(text); val ok2 = viaPlayer.contains(text)
            log("GROUPT_a $lang $cs downloader=${if (ok1) "OK" else "BAD"} player=${if (ok2) "OK" else "BAD"} got=${viaPlayer.lines().getOrNull(2)}")
            if (!ok1) bad += "$lang/downloader"
            if (!ok2) bad += "$lang/player"
        }
        log("GROUPT_a_RESULT ${if (bad.isEmpty()) "PASS" else "FAIL $bad"}")
        assertTrue("Mojibake for: $bad", bad.isEmpty())
    }

    @Test fun b_linkPastedIntoSearchPlays() {
        val url = server.url("/media/yu-group-t.mp4").toString()
        compose.setContent { CompositionLocalProvider(LocalTvMode provides Edition.isTv) { MTPlayerTheme { OnlineSearchScreen(onBack = {}, onOpenTitle = {}, onOpenExtensions = {}) } } }
        compose.waitForIdle()
        compose.onNodeWithTag("online_search_input", true).performTextInput(url)
        compose.waitForIdle()
        val card = waitFor(5_000) { has("online_search_play_link") }
        log("GROUPT_b card=$card")
        FirstRun.shot("groupt_search_link")
        assertTrue("No 'Play this link' card for a pasted link", card)
        compose.onNodeWithTag("online_search_play_link", true).performClick()
        val played = waitFor(20_000) { IncomingMedia.pending.value != null }
        val item = IncomingMedia.pending.value
        log("GROUPT_b played=$played uri=${item?.uri}")
        val same = item?.uri?.contains("/media/yu-group-t.mp4") == true
        log("GROUPT_b_RESULT ${if (played && same) "PASS" else "FAIL"}")
        assertTrue("The link was not sent to the player", played)
        assertTrue("Played ${item?.uri}, not the pasted link", same)
    }
}
