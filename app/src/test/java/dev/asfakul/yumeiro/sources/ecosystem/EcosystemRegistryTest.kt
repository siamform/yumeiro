/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.ecosystem

import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.UniversalAdd
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vega step 4 (ADR-047): one registry, one parser per format, one adapter per ecosystem. Fixtures
 * follow the shapes read at Zenda-Cross/vega-providers 24054d4, tapframe/NuvioStreaming e022cfd,
 * tapframe/nuvio-providers cb4bf14, akashdh11/skystream-plugins and sky-universe a0dc58f, and
 * CloudStream's RepositoryManager (fields only, no code).
 */
class EcosystemRegistryTest {
    private val vega = """[{"display_name":"VMovies","value":"vega","version":"2.52","icon":"","type":"global","disabled":false,"hasSettings":true},
        {"display_name":"Old","value":"old","version":"1.0","type":"india","disabled":true}]"""
    private val nuvioObject = """{"name":"Community","version":"1.0.0","scrapers":[{"id":"showbox","name":"ShowBox","version":"1.2","filename":"providers/showbox.js","supportedTypes":["movie","tv"],"enabled":true}]}"""
    private val nuvioArray = """[{"id":"vidsrc","name":"VidSrc","version":"0.3","filename":"providers/vidsrc.js","supportedTypes":["movie"]}]"""
    private val skyRepo = """{"name":"SkyStream Plugins","packageName":"com.akash.plugins","manifestVersion":1,"pluginLists":["dist/plugins.json"]}"""
    private val skyMega = """{"name":"Sky Universe","id":"sky.universe","manifestVersion":1,"repos":["https://raw.githubusercontent.com/a/b/main/repo.json"]}"""
    private val csRepo = """{"name":"CS repo","description":"x","manifestVersion":1,"pluginLists":["https://raw.githubusercontent.com/c/d/builds/plugins.json"]}"""
    private val mangayomi = """[{"name":"M","id":1,"baseUrl":"https://m.example","sourceCodeUrl":"https://m.example/a.js","itemType":0}]"""

    @Test fun eachFormatHasExactlyOneParserAndEcosystem() {
        val url = "https://raw.githubusercontent.com/Zenda-Cross/vega-providers/main/manifest.json"
        val v = RepoFormats.parse(vega, url)!!
        assertEquals(SourceHub.Ecosystem.VEGA, v.ecosystem)
        assertEquals(listOf("vega", "old"), v.plugins.map { it.id })
        assertTrue("disabled flag kept", v.plugins[1].disabled)
        assertEquals("https://raw.githubusercontent.com/Zenda-Cross/vega-providers/main/dist/vega/posts.js", v.plugins[0].files["posts.js"])

        val n1 = RepoFormats.parse(nuvioObject, "https://n.example/repo/manifest.json")!!
        assertEquals(SourceHub.Ecosystem.NUVIO, n1.ecosystem)
        assertEquals("https://n.example/repo/providers/showbox.js", n1.plugins.single().files["scraper.js"])
        val n2 = RepoFormats.parse(nuvioArray, "https://n.example/manifest.json")!!
        assertEquals("both Nuvio manifest shapes parse", SourceHub.Ecosystem.NUVIO, n2.ecosystem)
        assertEquals("vidsrc", n2.plugins.single().id)

        val s = RepoFormats.parse(skyRepo, "https://s.example/repo.json")!!
        assertEquals(SourceHub.Ecosystem.SKYSTREAM, s.ecosystem)
        assertEquals(listOf("https://s.example/dist/plugins.json"), s.children)
        assertEquals(SourceHub.Ecosystem.SKYSTREAM, RepoFormats.parse(skyMega, "https://s.example/mega_repo.json")!!.ecosystem)

        val cs = RepoFormats.parse(csRepo, "https://c.example/repo.json")!!
        assertEquals("CloudStream is detected and left to its own loader", SourceHub.Ecosystem.CLOUDSTREAM, cs.ecosystem)
        assertTrue(cs.plugins.isEmpty())

        assertNull("Mangayomi index is not a script-ecosystem repo", RepoFormats.parse(mangayomi, "https://m.example/index.json"))
        assertNull(RepoFormats.parse("<html>", "https://x.example/"))
    }

    @Test fun parsersAgreeWithUniversalAdd() {
        fun sniff(t: String) = UniversalAdd.sniff("https://x.example/manifest.json", t.toByteArray())?.name
        assertEquals("VEGA_REPO", sniff(vega))
        assertEquals("NUVIO_REPO", sniff(nuvioObject))
        assertEquals("SKYSTREAM_REPO", sniff(skyRepo))
        assertEquals("CS_REPO", sniff(csRepo))
    }

    @Test fun vegaRunsAndTheOthersAreRecognisedOnly() {
        // F-05-N (ADR-057): Nuvio has an adapter now. F-05-N-ON: scripts_nuvio is on by default, so Nuvio repos are added.
        // F-05-S (ADR-058): SkyStream has an adapter too; F-05-S-ON: scripts_skystream is on, so its repos are added.
        // A lone .sky file is still recognised only (it has no repository to update from).
        assertEquals(setOf(SourceHub.Ecosystem.VEGA, SourceHub.Ecosystem.NUVIO, SourceHub.Ecosystem.SKYSTREAM), EcosystemRegistry.adapters.keys)
        assertFalse("Vega repos are added now", UniversalAdd.Kind.VEGA_REPO.notYet)
        assertFalse("Nuvio repos are added now (scripts_nuvio on)", UniversalAdd.Kind.NUVIO_REPO.notYet)
        assertFalse("SkyStream repos are added now (scripts_skystream on)", UniversalAdd.Kind.SKYSTREAM_REPO.notYet)
        assertTrue(UniversalAdd.Kind.SKY_PLUGIN.notYet)
        assertFalse("the CloudStream loader is not part of the registry", SourceHub.Ecosystem.CLOUDSTREAM in EcosystemRegistry.known)
    }

    @Test fun eachEcosystemPublishesUnderItsOwnBridge() {
        val keys = EcosystemRegistry.known.map(EcosystemRegistry::bridgeKey)
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.none { it == "js" || it == "stremio" || it == "cs" })
    }

    @Test fun hostileIdsCannotLeaveThePluginFolder() {
        assertFalse(RepoFormats.safeId("../../etc/passwd").contains('/'))
        assertEquals("plugin", RepoFormats.safeId(".."))
        val p = RepoFormats.parse("""[{"display_name":"X","value":"../../x","version":"1"}]""", "https://v.example/manifest.json")!!.plugins.single()
        assertFalse(p.id.contains('/'))
        assertTrue(p.files.values.all { it.startsWith("https://v.example/dist/") })
    }

    @Test fun relativeLinksResolveLikeABrowser() {
        assertEquals("https://h.example/a/b.js", RepoFormats.resolve("https://h.example/a/manifest.json", "./b.js"))
        assertEquals("https://h.example/b.js", RepoFormats.resolve("https://h.example/a/manifest.json", "/b.js"))
        assertEquals("https://o.example/c.js", RepoFormats.resolve("https://h.example/a/manifest.json", "https://o.example/c.js"))
        assertEquals("https://h.example/a/", RepoFormats.baseOf("https://h.example/a/manifest.json?x=1#y"))
    }

    @Test fun storeRoundTripsAndSkipsUnknownEcosystems() {
        val s = EcosystemRegistry.State(
            listOf(EcosystemRegistry.Repo("https://v.example/manifest.json", SourceHub.Ecosystem.VEGA, "Vega providers", 5)),
            listOf(EcosystemRegistry.Installed("https://v.example/manifest.json", SourceHub.Ecosystem.VEGA, "vega", "VMovies", "2.52", null,
                mapOf("posts.js" to "ab"), JsonObject(mapOf("catalog" to JsonPrimitive("x"))))),
        )
        val back = EcosystemRegistry.decode(EcosystemRegistry.encode(s))
        assertEquals(s, back)
        val bad = EcosystemRegistry.decode(RepoFormats.json.parseToJsonElement("""{"repos":[{"url":"u","ecosystem":"FUTURE"}],"installed":[{}]}""") as JsonObject)
        assertTrue(bad.repos.isEmpty() && bad.installed.isEmpty())
    }

    @Test fun keysSeparateRepositoriesAndEcosystems() {
        fun i(repo: String, eco: SourceHub.Ecosystem) = EcosystemRegistry.Installed(repo, eco, "vega", "V", "1", null, emptyMap())
        val a = i("https://a.example/manifest.json", SourceHub.Ecosystem.VEGA)
        val b = i("https://b.example/manifest.json", SourceHub.Ecosystem.VEGA)
        val c = i("https://a.example/manifest.json", SourceHub.Ecosystem.NUVIO)
        assertEquals(3, setOf(a.key, b.key, c.key).size)
        assertFalse(a.storeName.contains(':'))
    }

    @Test fun drmStreamsAreDroppedAndQualityIsRead() {
        val clear = RepoFormats.json.parseToJsonElement("""{"server":"S","link":"https://cdn.example/a.m3u8","type":"m3u8","quality":"1080"}""") as JsonObject
        val drm = RepoFormats.json.parseToJsonElement("""{"server":"S","link":"https://cdn.example/a.mpd","type":"mpd","drm":{"type":"clearkey","clearKeys":{"a":"b"}}}""") as JsonObject
        val nolink = RepoFormats.json.parseToJsonElement("""{"server":"S"}""") as JsonObject
        assertTrue(VegaAdapter.playable(clear))
        assertFalse("provider-supplied DRM keys are never used", VegaAdapter.playable(drm))
        assertFalse(VegaAdapter.playable(nolink))
        assertEquals(1080, VegaAdapter.qualityOf("1080"))
        assertEquals(2160, VegaAdapter.qualityOf(null, "Server 4K"))
        assertEquals(0, VegaAdapter.qualityOf("auto"))
        assertNotNull(RepoFormats.json.parseToJsonElement(VegaAdapter.target("/x", "series")))
    }

    @Test fun runtimeShimIsSelfContained() {
        val js = listOf(java.io.File("src/main/assets/js/provider_runtime.js"), java.io.File("app/src/main/assets/js/provider_runtime.js")).first { it.isFile }.readText()
        for (name in listOf("G.fetch", "G.axios", "G.cheerio", "G.URL", "G.AbortController", "G.require", "G.__invoke", "openWebView")) assertTrue("missing $name", js.contains(name))
        // The shim must not open pages by itself or bundle third-party code.
        assertFalse(js.contains("eval("))
        assertTrue(js.length < 60_000)
    }
}
