/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.DownloadTracks
import dev.asfakul.yumeiro.online.download.DownloadTracks.Pick
import dev.asfakul.yumeiro.online.download.HlsDownloader
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.SubtitleSaver
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamSubtitle
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPromptHost
import dev.asfakul.yumeiro.ui.screens.downloads.StreamDownloadAsk
import dev.asfakul.yumeiro.ui.screens.downloads.subtitleSidecars
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** OL-5 (owner): the downloader offers quality, audio track, Sub / Dub and subtitles, and saved subtitles play offline. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class DownloadTracksTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private var server: MockWebServer? = null

    @After fun tearDown() { DownloadPrompt.dismiss(); runCatching { server?.close() } }

    private val master = """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",DEFAULT=YES,URI="audio/en.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Japanese, original",LANGUAGE="ja",URI="audio/ja.m3u8"
        #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",LANGUAGE="en",URI="subs/en.m3u8"
        #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Español",LANGUAGE="es-419",URI="subs/es.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",AUDIO="aud",SUBTITLES="subs"
        v/1080.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720,AUDIO="aud",SUBTITLES="subs"
        v/720.m3u8
    """.trimIndent()

    @Test fun ol5_readsQualityAudioAndSubtitlesFromTheMasterPlaylist() {
        val m = DownloadTracks.parseMaster(master, "https://cdn.example.com/show/master.m3u8")
        assertNotNull(m); m!!
        assertEquals(listOf(1080, 720), m.heights)
        assertEquals("quoted comma kept inside the name", listOf("English", "Japanese, original"), m.audios.map { it.label })
        assertEquals("https://cdn.example.com/show/audio/ja.m3u8", m.audios[1].uri)
        assertEquals("https://cdn.example.com/show/subs/es.m3u8", m.subtitles[1].uri)
        val o = DownloadTracks.offerOf(m)
        assertEquals(listOf(1080, 720), o.heights)
        assertEquals(listOf("English", "Japanese, original"), o.audios)
        assertEquals(listOf("English", "Español"), o.subtitles)
        assertTrue(o.hasChoices)
        assertNull("a media playlist has no choices", DownloadTracks.parseMaster("#EXTM3U\n#EXTINF:4,\na.ts", "https://x/a.m3u8"))
    }

    @Test fun ol5_picksTheAudioTrackAndSubtitlesTheUserChose() {
        val m = DownloadTracks.parseMaster(master, "https://cdn.example.com/show/master.m3u8")!!
        assertEquals("ja", DownloadTracks.pickAudio(m.audios, "aud", "Japanese, original")?.language)
        assertEquals("by language too", "ja", DownloadTracks.pickAudio(m.audios, "aud", "ja")?.language)
        assertEquals("no pick = DEFAULT=YES", "en", DownloadTracks.pickAudio(m.audios, "aud", null)?.language)
        assertEquals("unknown pick falls back to the default", "en", DownloadTracks.pickAudio(m.audios, "aud", "German")?.language)
        assertEquals(
            "the downloader joins the picked audio",
            "https://cdn.example.com/show/audio/ja.m3u8",
            HlsDownloader.audioRendition(master, "https://cdn.example.com/show/master.m3u8", "https://cdn.example.com/show/v/720.m3u8", "Japanese, original"),
        )
        assertEquals(emptyList<Any>(), DownloadTracks.pickSubtitles(m.subtitles, DownloadTracks.SUBS_NONE))
        assertEquals(2, DownloadTracks.pickSubtitles(m.subtitles, DownloadTracks.SUBS_ALL).size)
        assertEquals(listOf("Español"), DownloadTracks.pickSubtitles(m.subtitles, "es").map { it.label })
        val named = listOf("English" to "https://s/en.srt", "English (CC)" to "https://s/encc.srt", "French" to "https://s/fr.vtt")
        assertEquals(listOf("English (CC)" to "https://s/encc.srt"), DownloadTracks.pickNamed(named, "English (CC)"))
        assertEquals(listOf("French" to "https://s/fr.vtt"), DownloadTracks.pickNamed(named, "Fr"))
        assertEquals(3, DownloadTracks.pickNamed(named, DownloadTracks.SUBS_ALL).size)
    }

    @Test fun ol5_sidecarNamesAndJoinedWebVtt() {
        assertEquals("Ep 1.English CC.vtt", DownloadTracks.sidecarName("Ep 1.mp4", "English (CC)", "VTT"))
        val names = listOf("Ep 1.mp4", "Ep 1.English.vtt", "Ep 1.Español.srt", "Ep 10.English.vtt", "Ep 1.mp4.part", "Ep 1.jpg")
        assertEquals(listOf("Ep 1.English.vtt" to "English", "Ep 1.Español.srt" to "Español"), DownloadTracks.sidecarsOf("Ep 1.mp4", names))
        assertEquals("text/vtt", DownloadTracks.subtitleMime("a.vtt")); assertEquals("application/x-subrip", DownloadTracks.subtitleMime("a.srt"))
        assertEquals("srt", DownloadTracks.subtitleExt("https://s/sub?id=1", "1\n00:00:01,000 --> 00:00:02,000"))
        assertEquals("vtt", DownloadTracks.subtitleExt("https://s/sub", "\uFEFFWEBVTT\n"))
        val joined = DownloadTracks.joinVtt(listOf("WEBVTT\n\n00:00.000 --> 00:01.000\nHi", "WEBVTT\nX-TIMESTAMP-MAP=MPEGTS:0,LOCAL:00:00.000\n\n00:01.000 --> 00:02.000\nThere"))
        assertEquals(1, Regex("WEBVTT").findAll(joined).count())
        assertTrue(joined.contains("Hi") && joined.contains("There"))
    }

    @Test fun ol5_anyHeightTheStreamOffersIsKept() {
        assertEquals("540p from a real playlist", 540, DownloadSettings.heightForPick(540, setting = 1080))
        assertEquals(1440, DownloadSettings.heightForPick(1440, setting = 720))
        assertEquals("Best", 0, DownloadSettings.heightForPick(0, setting = 480))
        assertEquals("not picked = Settings", 1080, DownloadSettings.heightForPick(-1, setting = 1080))
        assertEquals("nonsense = Settings", 480, DownloadSettings.heightForPick(99_999, setting = 480))
    }

    @Test fun ol5_choicesAreSavedWithTheDownload() {
        val i = OnlineDownloads.Item(
            id = "x", title = "T", api = "Link", titleUrl = "", episodeLabel = null, posterUrl = null, posterHeaders = emptyMap(),
            url = "https://cdn/m.m3u8", isHls = true, headers = emptyMap(), fileName = "T.mp4",
            audioPref = "Japanese", subsPref = "English", subs = listOf("English" to "https://s/en.vtt"),
        )
        val back = OnlineDownloads.from(OnlineDownloads.json(i))!!
        assertEquals("Japanese", back.audioPref); assertEquals("English", back.subsPref); assertEquals(i.subs, back.subs)
    }

    @Test fun ol5_websiteOfferAddsThePagesSubtitles() {
        val req = StreamRequest(url = "https://cdn/m.mp4", title = "Clip", subtitles = listOf(StreamSubtitle("https://s/en.vtt", "English"), StreamSubtitle("https://s/x.srt", "", language = "fr")))
        val named = StreamDownloadAsk.namedSubs(req)
        assertEquals(listOf("English" to "https://s/en.vtt", "fr" to "https://s/x.srt"), named)
        assertEquals(listOf("English", "fr"), StreamDownloadAsk.offerFor(null, named)?.subtitles)
        assertNull("nothing to choose = the plain popup", StreamDownloadAsk.offerFor(null, emptyList()))
    }

    @Test fun ol5_popupStartsWithTheQualityAudioAndSubtitlesPicked() {
        DownloadSettings.setAskFirst(ctx, true)
        rule.setContent { MTPlayerTheme { DownloadPromptHost() } }
        rule.waitForIdle()
        var got: Pick? = null
        var plain = false
        rule.runOnIdle {
            DownloadPrompt.ask(ctx, DownloadPrompt.Request(
                type = DownloadPrompt.Type.LINK, title = "Show", what = "this video", count = 1, folder = "", quality = "HLS", defaultKeep = false,
                offer = DownloadTracks.Offer(heights = listOf(1080, 720), audios = listOf("English", "Japanese"), subtitles = listOf("English", "Español")),
                startWith = { _, p -> got = p; null },
            ) { plain = true; null })
        }
        rule.onNodeWithTag("download_track_quality_2").performScrollTo().performClick() // 720p (0 = Best)
        rule.onNodeWithTag("download_track_audio_2").performScrollTo().performClick()   // Japanese (0 = Default)
        rule.onNodeWithTag("download_track_subs_2").performScrollTo().performClick()    // Español (0 = None)
        rule.onNodeWithTag("download_confirm_start").performClick()
        rule.waitForIdle()
        assertEquals(Pick(maxHeight = 720, audio = "Japanese", subtitles = "Español"), got)
        assertTrue("the plain start must not run", !plain)
    }

    @Test fun ol5_episodePopupOffersSaveAllSubtitles() {
        DownloadSettings.setAskFirst(ctx, true)
        rule.setContent { MTPlayerTheme { DownloadPromptHost() } }
        rule.waitForIdle()
        var got: Pick? = null
        rule.runOnIdle {
            DownloadPrompt.ask(ctx, DownloadPrompt.Request(
                type = DownloadPrompt.Type.EPISODES, title = "Show", what = "3 episodes", count = 3, folder = "Show", quality = "Best", defaultKeep = false,
                startAt = { _, _ -> null }, offer = DownloadTracks.Offer(subtitlesLater = true), startWith = { _, p -> got = p; null },
            ) { null })
        }
        rule.onNodeWithText("Save all the source has").assertIsDisplayed()
        rule.onNodeWithTag("download_track_subs_1").performScrollTo().performClick()
        rule.onNodeWithTag("download_confirm_start").performClick()
        rule.waitForIdle()
        assertEquals(DownloadTracks.SUBS_ALL, got?.subtitles)
    }

    @Test fun ol5_savesPickedSubtitlesNextToTheVideoAndThePlayerFindsThem() = runBlocking {
        val s = MockWebServer().also { server = it }
        val seen = mutableListOf<String>()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(seen) { seen += request.url.encodedPath }
                val body = when (request.url.encodedPath) {
                    "/show/master.m3u8" -> master
                    "/show/subs/en.m3u8" -> "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nen1.vtt\n#EXTINF:6,\nen2.vtt\n#EXT-X-ENDLIST"
                    "/show/subs/en1.vtt" -> "WEBVTT\n\n00:00.000 --> 00:01.000\nOne"
                    "/show/subs/en2.vtt" -> "WEBVTT\n\n00:06.000 --> 00:07.000\nTwo"
                    "/page/fr.srt" -> "1\n00:00:01,000 --> 00:00:02,000\nBonjour\n"
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().body(body).build()
            }
        }
        s.start()
        val dir = Files.createTempDirectory("ol5").toFile()
        val video = File(dir, "Ep 1.mp4").apply { writeText("video") }
        val item = OnlineDownloads.Item(
            id = "x", title = "T", api = "Link", titleUrl = "", episodeLabel = null, posterUrl = null, posterHeaders = emptyMap(),
            url = s.url("/show/master.m3u8").toString(), isHls = true, headers = emptyMap(), fileName = video.name,
            subsPref = DownloadTracks.SUBS_ALL, subs = listOf("French" to s.url("/page/fr.srt").toString()),
        )
        val files = SubtitleSaver.save(OkHttpClient(), item, video)
        // Español's playlist is missing (404): skipped, the rest still saved.
        assertEquals(listOf("Ep 1.English.vtt", "Ep 1.French.srt"), files.map { it.name }.sorted())
        val en = File(dir, "Ep 1.English.vtt").readText()
        assertTrue(en.contains("One") && en.contains("Two"))
        assertTrue(File(dir, "Ep 1.French.srt").readText().contains("Bonjour"))
        assertTrue("a missing subtitle is skipped", !File(dir, "Ep 1.Español.vtt").exists())
        val player = subtitleSidecars(video)
        assertEquals(listOf("English", "French"), player.map { it.label }.sorted())
        assertTrue(player.all { it.url.startsWith("file:") })
        assertEquals("application/x-subrip", player.first { it.label == "French" }.mimeType)

        val none = SubtitleSaver.save(OkHttpClient(), item.copy(subsPref = DownloadTracks.SUBS_NONE), File(dir, "Ep 2.mp4"))
        assertTrue("None = nothing fetched or saved", none.isEmpty())
        dir.deleteRecursively(); Unit
    }
}
