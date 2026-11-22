/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.f05

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.UniversalAdd
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.sources.ecosystem.ParsedRepo
import dev.asfakul.yumeiro.sources.ecosystem.RepoFormats
import dev.asfakul.yumeiro.sources.ecosystem.SkyStreamAdapter
import dev.asfakul.yumeiro.sources.ecosystem.SkyStreamRepoParser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * F-05-S (ADR-058): SkyStream plugins. Shapes follow SkyStream's public developer guide
 * (akashdh11/skystream-tools DEVELOPER.md) and the plugin list of akashdh11/skystream-plugins
 * (fields only, no code). Before the fix there was no SkyStream adapter: a SkyStream repository
 * was recognised and refused, and nothing could read a .sky file.
 */
@RunWith(RobolectricTestRunner::class)
class SkyStreamAdapterTest {
    private fun json(s: String) = RepoFormats.json.parseToJsonElement(s)
    private fun obj(s: String) = json(s).jsonObject

    private fun sky(vararg entries: Pair<String, ByteArray>): File {
        val f = File.createTempFile("p", ".sky")
        ZipOutputStream(f.outputStream()).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return f
    }

    @Test fun theAdapterIsRegisteredBehindItsOwnFlagWhichIsOff() {
        assertTrue(SourceHub.Ecosystem.SKYSTREAM in EcosystemRegistry.adapters.keys)
        assertEquals(FeatureFlags.Flag.SCRIPTS_SKYSTREAM, SkyStreamAdapter.flag)
        assertFalse(FeatureFlags.Flag.SCRIPTS_SKYSTREAM.default)
        assertTrue("SkyStream repos stay refused while scripts_skystream is off", UniversalAdd.Kind.SKYSTREAM_REPO.notYet)
        assertTrue("a lone .sky file is recognised only", UniversalAdd.Kind.SKY_PLUGIN.notYet)
        assertEquals(setOf(SkyStreamRepoParser.SKY_FILE), SkyStreamAdapter.required(RepoFormats.parse("""[{"display_name":"X","value":"x","version":"1"}]""", "https://v.example/m.json")!!.plugins.single()))
    }

    @Test fun aSkyFileGivesItsScriptAndManifestOnly() {
        val f = sky("plugin.json" to """{"packageName":"com.x","name":"X","baseUrl":"https://x.example"}""".toByteArray(),
            "../../evil.js" to "boom".toByteArray(), "dir/plugin.js" to "function getHome(cb){cb({success:true,data:{}})}".toByteArray())
        val p = SkyStreamAdapter.readPackage(f)
        assertTrue(p.js.startsWith("function getHome"))
        assertEquals("https://x.example", (p.manifest["baseUrl"] as JsonPrimitive).content)
        assertFalse("nothing is unpacked next to the file", File(f.parentFile, "evil.js").exists())
    }

    @Test fun aSkyFileWithoutScriptOrTooBigIsRefused() {
        try { SkyStreamAdapter.readPackage(sky("plugin.json" to "{}".toByteArray())); fail() } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("plugin.js")) }
        val big = ByteArray((SkyStreamAdapter.MAX_ENTRY_BYTES + 1).toInt()) { 'a'.code.toByte() }
        try { SkyStreamAdapter.readPackage(sky("plugin.js" to big)); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("too big")) }
        val many = (0..SkyStreamAdapter.MAX_ENTRIES).map { "f$it.txt" to ByteArray(1) }.toTypedArray()
        try { SkyStreamAdapter.readPackage(sky(*many)); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("too many")) }
    }

    @Test fun theManifestFallsBackToTheFirstMirror() {
        val m = SkyStreamAdapter.manifestOf(obj("""{"name":"X","domains":[{"name":"Main","url":"https://m1.example"},{"url":"https://m2.example"}]}"""))
        assertEquals("https://m1.example", (m["baseUrl"] as JsonPrimitive).content)
        val kept = obj("""{"baseUrl":"https://b.example","domains":[{"url":"https://m1.example"}]}""")
        assertEquals(kept, SkyStreamAdapter.manifestOf(kept))
    }

    @Test fun homeRowsPutTrendingFirstAndDropEmptyOrBrokenItems() {
        val rows = SkyStreamAdapter.homeOf(json("""{"Latest":[{"title":"A","url":"https://x/a","type":"movie"},{"title":"no url"}],
            "Empty":[],"Trending":[{"title":"T","url":"https://x/t","type":"series","posterUrl":"https://x/p.jpg"},{"title":"T","url":"https://x/t"}]}"""))
        assertEquals(listOf("Trending", "Latest"), rows.map { it.first })
        assertEquals(1, rows[0].second.size)
        assertEquals(TvType.TvSeries, rows[0].second[0].type)
        assertEquals("https://x/p.jpg", rows[0].second[0].poster)
        assertEquals(listOf("A"), rows[1].second.map { it.title })
        assertTrue(SkyStreamAdapter.homeOf(json("[]")).isEmpty())
    }

    @Test fun typesMapToCloudStreamTypes() {
        assertEquals(TvType.Movie, SkyStreamAdapter.typeOf("movie"))
        assertEquals(TvType.TvSeries, SkyStreamAdapter.typeOf("series"))
        assertEquals(TvType.Anime, SkyStreamAdapter.typeOf("anime"))
        assertEquals(TvType.Live, SkyStreamAdapter.typeOf("livestream"))
        assertEquals(TvType.Movie, SkyStreamAdapter.typeOf(null))
    }

    @Test fun streamsKeepHeadersQualityAndSubtitlesAndSkipWhatCantPlay() {
        val ok = SkyStreamAdapter.streamOf(obj("""{"url":"https://cdn.example/v/master.m3u8?t=1","source":"Server 1","quality":"1080",
            "headers":{"Referer":"https://site.example/"},"subtitles":[{"url":"https://cdn.example/en.vtt","label":"English","lang":"en"}]}"""), "Src")
        ok as SkyStreamAdapter.Stream.Ok
        assertEquals(ExtractorLinkType.M3U8, ok.type)
        assertEquals(1080, ok.quality)
        assertEquals("Server 1 · 1080p", ok.label)
        assertEquals("https://site.example/", ok.headers["Referer"])
        assertEquals(listOf("English" to "https://cdn.example/en.vtt"), ok.subtitles)
        fun skip(s: String) = (SkyStreamAdapter.streamOf(obj(s), "Src") as SkyStreamAdapter.Stream.Skip).reason
        assertEquals("DRM", skip("""{"url":"https://x/a.mpd","drmKid":"k","drmKey":"v"}"""))
        assertEquals("DRM", skip("""{"url":"https://x/a.mpd","licenseUrl":"https://x/l"}"""))
        assertEquals("Torrent", skip("""{"url":"magnet:?xt=urn:btih:abc"}"""))
        assertEquals("Torrent", skip("""{"url":"https://x/a.torrent"}"""))
        assertEquals("Generated playlist", skip("""{"url":"magic_m3u8:#EXTM3U"}"""))
        assertEquals("Unsupported link", skip("""{"url":"file:///sdcard/a.mp4"}"""))
        assertEquals("No link", skip("""{"source":"x"}"""))
    }

    @Test fun aMagicProxyLinkIsUnwrappedToItsRealAddress() {
        val b64 = okio.ByteString.Companion.run { "https://cdn.example/a.mp4".encodeUtf8().base64() }
        val s = SkyStreamAdapter.streamOf(obj("""{"url":"${SkyStreamAdapter.MAGIC_PROXY}$b64","quality":"720p"}"""), "Src") as SkyStreamAdapter.Stream.Ok
        assertEquals("https://cdn.example/a.mp4", s.url)
        assertEquals(ExtractorLinkType.VIDEO, s.type)
        assertEquals(720, s.quality)
        val bad = SkyStreamAdapter.streamOf(obj("""{"url":"${SkyStreamAdapter.MAGIC_PROXY}${"file:///etc/passwd".let { okio.ByteString.Companion.run { it.encodeUtf8().base64() } }}"}"""), "Src")
        assertEquals("a proxy can't smuggle a local file", "Unsupported link", (bad as SkyStreamAdapter.Stream.Skip).reason)
    }

    @Test fun pluginListsAreParsedAndMegaReposExpandOneLevel() {
        val list = """[{"packageName":"com.a.one","name":"One","version":3,"url":"one.sky","languages":["en","hi"],"categories":["Movie"]},
            {"packageName":"com.a.two","name":"Two","version":"1.0","url":"https://cdn.example/two.sky","disabled":true}]"""
        assertTrue(SkyStreamRepoParser.isPluginList(json(list)))
        val ps = SkyStreamRepoParser.parsePluginList(json(list), "https://r.example/dist/plugins.json")
        assertEquals("3", ps[0].version)
        assertEquals("en", ps[0].lang)
        assertEquals("https://r.example/dist/one.sky", ps[0].files[SkyStreamRepoParser.SKY_FILE])
        assertTrue(ps[1].disabled)
        val reads = ArrayList<String>()
        val pages = mapOf(
            "https://m.example/a.json" to """{"name":"A","packageName":"a","pluginLists":["https://r.example/dist/plugins.json"]}""",
            "https://r.example/dist/plugins.json" to list,
            "https://m.example/b.json" to """{"name":"B","packageName":"b","repos":["https://m.example/deeper.json"]}""",
            "https://m.example/deeper.json" to """{"name":"C","packageName":"c","pluginLists":["https://never.example/p.json"]}""",
        )
        val mega = ParsedRepo(SourceHub.Ecosystem.SKYSTREAM, "skystream-repo", "Mega", emptyList(),
            listOf("https://m.example/a.json", "https://r.example/dist/plugins.json", "https://m.example/b.json", "https://broken.example/x.json"))
        val out = EcosystemRegistry.expandSky(mega) { u -> reads += u; pages[u] ?: error("404") }
        assertEquals(listOf("com.a.one", "com.a.two"), out.plugins.map { it.id }.map { it })
        assertFalse("only one level of child repos is followed", "https://never.example/p.json" in reads)
        assertEquals("each list is read once", reads.size, reads.toSet().size)
    }

    @Test fun aLoadTargetCarriesTheUrlAndInstantStreams() {
        val t = obj(SkyStreamAdapter.target("https://x/e1", json("""[{"url":"https://x/a.m3u8"}]""")))
        assertEquals("https://x/e1", (t["u"] as JsonPrimitive).content)
        assertEquals(1, (t["s"] as kotlinx.serialization.json.JsonArray).size)
        assertFalse(obj(SkyStreamAdapter.target("https://x/e1", null)).containsKey("s"))
        assertTrue(JsonObject(emptyMap()).isEmpty())
    }
}
