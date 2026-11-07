/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.online

import android.app.Application
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.mtrevanced.stream.StreamFormat
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExtensionFormatsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        java.io.File(context.filesDir, "extensions").deleteRecursively()
        ExtensionManager.resetForTests(); ExtensionMarket.resetForTests()
        server = MockWebServer(); server.start()
    }
    @After fun tearDown() { server.close() }

    private val repo = ExtensionRepo("https://raw.githubusercontent.com/o/r/master/repo.json", "Test repo", pluginLists = listOf("x"))

    @Test fun parsesRepoAndResolvesRelativeLists() {
        val r = ExtensionFormats.parseRepo(
            "https://raw.githubusercontent.com/o/r/master/repo.json",
            """{"name":"My repo","description":"d","manifestVersion":1,"pluginLists":["builds/plugins.json","https://x.example/p.json","ftp://bad"]}""",
        )
        assertEquals("My repo", r.name)
        assertEquals(listOf("https://raw.githubusercontent.com/o/r/master/builds/plugins.json", "https://x.example/p.json"), r.pluginLists)
    }

    @Test fun rejectsNonRepositories() {
        listOf("<html></html>", """{"name":"x"}""", """{"pluginLists":[]}""").forEach {
            try { ExtensionFormats.parseRepo("https://a/repo.json", it); fail("accepted $it") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun parsesPluginListLeniently() {
        val list = ExtensionFormats.parsePlugins(
            "https://raw.githubusercontent.com/o/r/builds/plugins.json",
            """[
              {"internalName":"ArchiveProvider","name":"Archive","url":"Archive.cs3","version":7,"apiVersion":1,"status":3,
               "tvTypes":["Movie","Documentary"],"language":"en","fileSize":12345,"fileHash":"sha256-${"A".repeat(64)}","iconUrl":"https://i/%size%.png","authors":["a"]},
              {"internalName":"bad name!","url":"x.cs3"},
              {"internalName":"NoUrl"},
              {"internalName":"ArchiveProvider","url":"dupe.cs3"},
              "junk"
            ]""",
            repo,
        )
        assertEquals(1, list.size)
        val e = list[0]
        assertEquals("https://raw.githubusercontent.com/o/r/builds/Archive.cs3", e.url)
        assertEquals(7, e.version); assertEquals(3, e.status); assertEquals("Beta", e.statusLabel)
        assertEquals("a".repeat(64), e.sha256)
        assertEquals(12345L, e.fileSize)
        assertEquals(repo.url, e.repoUrl)
        assertFalse(e.isNsfw)
    }

    @Test fun normalizesGithubLinks() {
        assertEquals("https://raw.githubusercontent.com/o/r/HEAD/repo.json", normalizeRepoUrl("https://github.com/o/r"))
        assertEquals("https://raw.githubusercontent.com/o/r/HEAD/repo.json", normalizeRepoUrl("https://github.com/o/r.git/"))
        assertEquals("https://raw.githubusercontent.com/o/r/builds/repo.json", normalizeRepoUrl("https://github.com/o/r/tree/builds"))
        assertEquals("https://raw.githubusercontent.com/o/r/main/x/repo.json", normalizeRepoUrl("https://github.com/o/r/blob/main/x/repo.json"))
        assertEquals("https://a.example/repo.json", normalizeRepoUrl(" https://a.example/repo.json "))
        assertEquals(RemoteExtension.keyOf("https://github.com/o/r", "X"), RemoteExtension.keyOf("https://raw.githubusercontent.com/o/r/HEAD/repo.json", "X"))
    }

    @Test fun parsesMarketIndex() {
        val cat = ExtensionMarket.parse(
            """{"version":1,"generated":"2026-11-03T06:00:00Z",
              "repositories":[{"url":"https://r/repo.json","name":"R","stars":9,"count":2,"ok":true}],
              "extensions":[
                {"internalName":"A","name":"Alpha","url":"https://m/A.cs3","version":2,"repoUrl":"https://r/repo.json","sha256":"ABC","size":10,"tvTypes":["NSFW"]},
                {"internalName":"B","url":"http://insecure/B.cs3","repoUrl":"https://r/repo.json"},
                {"internalName":"C","url":"https://m/C.cs3"}
              ]}""",
        )
        assertEquals(1, cat.repos.size)
        assertEquals(1, cat.extensions.size)
        val a = cat.extensions[0]
        assertTrue(a.verified); assertEquals("R", a.repoName); assertEquals(9, a.stars); assertEquals("abc", a.sha256)
        assertTrue(a.isNsfw)
        assertTrue(cat.generated > 0)
    }

    @Test fun detectsUpdates() {
        val installed = InstalledExtension("A", "Alpha", 2, "https://r/repo.json", "R", "A.cs3", "https://m/A.cs3", "x".repeat(64))
        val same = RemoteExtension("A", "Alpha", "https://m/A.cs3", 2, sha256 = "x".repeat(64), repoUrl = "https://r/repo.json", repoName = "R")
        assertTrue(ExtensionManager.updatesForTests(listOf(installed), listOf(same)).isEmpty())
        assertEquals(1, ExtensionManager.updatesForTests(listOf(installed), listOf(same.copy(version = 3))).size)
        assertEquals(1, ExtensionManager.updatesForTests(listOf(installed), listOf(same.copy(sha256 = "y".repeat(64)))).size)
        assertTrue(ExtensionManager.updatesForTests(listOf(installed.copy(manual = true)), listOf(same.copy(version = 3))).isEmpty())
        assertTrue(ExtensionManager.updatesForTests(listOf(installed), listOf(same.copy(version = 1))).isEmpty())
    }

    @Test fun buildsPlayerRequestWithHeadersAndSubtitles() {
        val link = OnlineLink("Src 1080p", "Src", "https://cdn/x.m3u8", "https://site/", 1080, true, false,
            mapOf("User-Agent" to "UA", "Origin" to "https://site", "Referer" to "ignored"))
        val req = OnlinePlayback.request("Movie", link, listOf(
            OnlineSubtitle("English", "https://s/en.srt?x=1", emptyMap()),
            OnlineSubtitle("English", "https://s/en.srt?x=1", emptyMap()),
            OnlineSubtitle("Français", "https://s/fr.vtt", emptyMap()),
        ))
        assertEquals(StreamFormat.HLS, req.format)
        assertEquals("UA", req.userAgent)
        assertEquals("https://site/", req.referer)
        assertEquals(mapOf("Origin" to "https://site"), req.headers)
        assertEquals(2, req.subtitles.size)
        assertEquals(MimeTypes.APPLICATION_SUBRIP, req.subtitles[0].mimeType)
        assertEquals("en", req.subtitles[0].language)
        assertEquals(MimeTypes.TEXT_VTT, OnlinePlayback.subtitleMime("https://s/a.vtt"))
        assertEquals(MimeTypes.TEXT_SSA, OnlinePlayback.subtitleMime("https://s/a.ASS#t"))
        assertEquals("pt-br", OnlinePlayback.languageTag("pt_BR"))
        assertNull(OnlinePlayback.languageTag("  "))
    }

    @Test fun sortsLinksBestFirst() {
        fun l(name: String, q: Int, hls: Boolean = false, url: String = name) = OnlineLink(name, "s", url, null, q, hls, false, emptyMap())
        val sorted = OnlineRepository.sortLinks(listOf(l("b", 480), l("a", 1080), l("c", -1), l("d", 1080, hls = true), l("dup", 720, url = "a")))
        // Unknown quality ranks like 720p (above a known 480p).
        assertEquals(listOf("d", "a", "c", "b"), sorted.map { it.name })
    }

    @Test fun neverPrefersAudioOnlyFiles() {
        // Internet Archive returns MP4 and MP3 files with the same "unknown" quality (400); the
        // MP3 used to sort first by name and played as a black screen.
        fun l(name: String, url: String, q: Int = 400) = OnlineLink(name, "Internet Archive", url, null, q, false, false, emptyMap())
        val sorted = OnlineRepository.sortLinks(listOf(
            l("Talk (mp3)", "https://archive.org/download/x/talk.mp3"),
            l("Talk (mp3)", "https://archive.org/download/x/talk_64kb.mp3"),
            l("Talk (mp4)", "https://archive.org/download/x/talk.mp4"),
            l("Talk (ogv)", "https://archive.org/download/x/talk.ogv"),
        ))
        assertEquals("https://archive.org/download/x/talk.mp4", sorted.first().url)
        assertTrue(sorted.takeLast(2).all { it.audioOnly })
        assertFalse(sorted.first().audioOnly)
        assertEquals("Audio only", sorted.last().formatLabel)
        assertEquals("MP4", sorted.first().formatLabel)
    }

    @Test fun addRepositoryValidatesAndPersists() = runBlocking {
        ExtensionManager.start(context); ExtensionManager.ensureLoaded(context)
        server.enqueue(MockResponse.Builder().body("<html>nope</html>").build())
        try { ExtensionManager.addRepository(context, server.url("/bad.json").toString()); fail("accepted html") } catch (_: Exception) {}
        server.enqueue(MockResponse.Builder().body("""{"name":"Mock repo","pluginLists":["plugins.json"]}""").build())
        val r = ExtensionManager.addRepository(context, server.url("/repo.json").toString())
        assertEquals("Mock repo", r.name)
        assertEquals(listOf("Mock repo"), ExtensionManager.state.value.repos.map { it.name })
        ExtensionManager.removeRepository(context, r.url)
        assertTrue(ExtensionManager.state.value.repos.isEmpty())
    }

    @Test fun rejectsFilesThatAreNotExtensions() = runBlocking {
        ExtensionManager.start(context); ExtensionManager.ensureLoaded(context)
        server.enqueue(MockResponse.Builder().body("definitely not a zip").build())
        try { ExtensionManager.installFromUrl(context, server.url("/x.cs3").toString()); fail("installed junk") }
        catch (e: ExtensionManager.ExtensionException) { assertTrue(e.message!!.isNotBlank()) }
        assertTrue(ExtensionManager.state.value.installed.isEmpty())
    }
}
