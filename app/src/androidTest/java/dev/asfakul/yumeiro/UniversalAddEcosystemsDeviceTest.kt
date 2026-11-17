/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.UniversalAdd
import dev.asfakul.yumeiro.sources.js.JsSources
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G2 E10 (re-audit): "Add anything" against a repository of each ecosystem, on the device's real
 * network stack (OkHttp, storage, the source hub), not only the pure detection unit tests.
 *
 * A local server publishes one repository per format, the way the real ones do:
 *  a Aniyomi: `repo.json` + `index.min.json` with an `.animeextension.` package, added through the
 *    `aniyomi://add-repo?url=` link a website button opens → Aniyomi repository, its extension listed;
 *  b Mihon: the same format with a manga package, pasted as a plain folder link → Mihon repository;
 *  c Stremio: `manifest.json` with a searchable catalog → add-on added, published as a video source,
 *    its search asks `/catalog/movie/yu/search=…` and its streams come from `/stream/…`;
 *  d Mangayomi JavaScript: `index.json` → repository added, the source listed and installed;
 *  e a link with nothing at it → the readable "couldn't find" message, nothing saved.
 * Everything added is removed again.
 */
@RunWith(AndroidJUnit4::class)
class UniversalAddEcosystemsDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuAddTest", s)
    private lateinit var server: MockWebServer
    private val paths = java.util.Collections.synchronizedList(ArrayList<String>())
    private fun base() = "http://${server.hostName}:${server.port}"

    private fun json(body: String) = MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body(body).build()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                paths += p
                return when (request.url.encodedPath) {
                    "/aniyomi/repo.json" -> json("""{"meta":{"name":"Yu Anime Repo","shortName":"yuani","website":"","signingKeyFingerprint":"00"}}""")
                    "/aniyomi/index.min.json" -> json("""[{"name":"Aniyomi: Yu Anime","pkg":"eu.kanade.tachiyomi.animeextension.en.yutest","apk":"yu.apk","lang":"en","code":1,"version":"14.1","nsfw":0,"sources":[{"name":"Yu Anime","lang":"en","id":"1","baseUrl":"https://example.org"}]}]""")
                    "/mihon/repo.json" -> json("""{"meta":{"name":"Yu Manga Repo","shortName":"yuman","website":"","signingKeyFingerprint":"00"}}""")
                    "/mihon/index.min.json" -> json("""[{"name":"Tachiyomi: Yu Manga","pkg":"eu.kanade.tachiyomi.extension.en.yutest","apk":"yu.apk","lang":"en","code":1,"version":"1.4.1","nsfw":0,"sources":[{"name":"Yu Manga","lang":"en","id":"2","baseUrl":"https://example.org"}]}]""")
                    "/stremio/manifest.json" -> json("""{"id":"org.yumeiro.test","version":"1.0.0","name":"Yu Test Addon","description":"test","resources":["catalog","stream"],"types":["movie"],"idPrefixes":["yu"],"catalogs":[{"type":"movie","id":"yu","name":"Yu","extra":[{"name":"search","isRequired":false}]}]}""")
                    "/stremio/catalog/movie/yu/search=night.json" -> json("""{"metas":[{"id":"yu1","type":"movie","name":"Yu Nightfall","poster":""}]}""")
                    "/stremio/stream/movie/yu1.json" -> json("""{"streams":[{"url":"${base()}/media/yu1.mp4","title":"720p"}]}""")
                    "/js/index.json" -> json("""[{"name":"Yu JS","id":"9911","baseUrl":"https://example.org","lang":"en","typeSource":"single","iconUrl":"","isNsfw":false,"hasCloudflare":false,"sourceCodeUrl":"${base()}/js/yu.js","apiUrl":"","version":"0.0.1","isManga":false,"itemType":1,"isFullData":false,"appMinVerReq":"0.5.0","additionalParams":"","sourceCodeLanguage":1}]""")
                    "/js/yu.js" -> MockResponse.Builder().code(200).body("const mangayomiSources = [];\nclass DefaultExtension extends MProvider { async search(q, p, f) { return { list: [], hasNextPage: false }; } }\n").build()
                    else -> MockResponse.Builder().code(404).body("not here").build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() {
        runCatching { server.close() }
    }

    @Test fun eachEcosystemAddsFromItsOwnRepositoryFormat() {
        val added = ArrayList<() -> Unit>()
        try {
            // a Aniyomi, through the deep link a website's "Add to Aniyomi" button opens.
            val a = runBlocking { UniversalAdd.add(ctx, "aniyomi://add-repo?url=${base()}/aniyomi/index.min.json") }.getOrThrow()
            added += { runBlocking { TachiyomiExtensions.removeRepo(ctx, "${base()}/aniyomi") } }
            val animeRepos = TachiyomiExtensions.state.value.repos.filter { it.url == "${base()}/aniyomi" }
            val anime = runBlocking { TachiyomiExtensions.available(ctx, TachiyomiExtensions.Kind.ANIME) }.map { it.entry.pkg }
            log("ADD_A kind=${a.kind} msg='${a.message}' repos=${animeRepos.map { it.name + ":" + it.kind }} available=$anime")
            assertEquals(UniversalAdd.Kind.ANIYOMI_REPO, a.kind)
            assertTrue("Aniyomi repo saved as anime: $animeRepos", animeRepos.size == 1 && animeRepos[0].kind == TachiyomiExtensions.Kind.ANIME)
            assertTrue("Aniyomi extension listed: $anime", "eu.kanade.tachiyomi.animeextension.en.yutest" in anime)

            // b Mihon, pasted as the repository folder (no file name).
            val b = runBlocking { UniversalAdd.add(ctx, "${base()}/mihon") }.getOrThrow()
            added += { runBlocking { TachiyomiExtensions.removeRepo(ctx, "${base()}/mihon") } }
            val manga = runBlocking { TachiyomiExtensions.available(ctx, TachiyomiExtensions.Kind.MANGA) }.map { it.entry.pkg }
            log("ADD_B kind=${b.kind} msg='${b.message}' available=$manga")
            assertEquals(UniversalAdd.Kind.MIHON_REPO, b.kind)
            assertTrue("Mihon extension listed: $manga", "eu.kanade.tachiyomi.extension.en.yutest" in manga)

            // c Stremio: the folder link finds manifest.json; search and streams go to the add-on.
            val c = runBlocking { UniversalAdd.add(ctx, "${base()}/stremio") }.getOrThrow()
            added += { StremioAddons.remove(ctx, "${base()}/stremio/manifest.json") }
            assertEquals(UniversalAdd.Kind.STREMIO, c.kind)
            val api = SourceHub.providers().firstOrNull { it.name.startsWith("Yu Test Addon") }
            log("ADD_C msg='${c.message}' provider=${api?.name} all=${SourceHub.providers().map { it.name }}")
            assertTrue("Stremio add-on not published as a video source", api != null)
            val hits = runBlocking { api!!.search("night") }.orEmpty()
            log("ADD_C search: ${hits.map { it.name + " → " + it.url }}")
            assertTrue("Stremio search found nothing: $hits", hits.any { it.name == "Yu Nightfall" })
            val links = ArrayList<String>()
            val found = runBlocking { api!!.loadLinks(hits.first { it.name == "Yu Nightfall" }.url, false, {}) { links += it.url } }
            log("ADD_C links found=$found $links")
            assertTrue("Stremio stream not offered: $links", links.any { it.endsWith("/media/yu1.mp4") })

            // d Mangayomi JavaScript repository: listed, then installed (code saved, engine reset).
            val d = runBlocking { UniversalAdd.add(ctx, "${base()}/js/index.json") }.getOrThrow()
            added += { JsSources.removeRepo(ctx, "${base()}/js/index.json") }
            assertEquals(UniversalAdd.Kind.JS_REPO, d.kind)
            val js = runBlocking { JsSources.available(ctx) }
            log("ADD_D msg='${d.message}' available=${js.map { it.id + ":" + it.name }}")
            val entry = js.firstOrNull { it.id == "9911" }
            assertTrue("JS source not listed: ${js.map { it.name }}", entry != null)
            runBlocking { JsSources.install(ctx, entry!!) }.getOrThrow()
            added += { JsSources.uninstall(ctx, "9911") }
            assertTrue("JS source not installed", JsSources.state.value.installed.any { it.id == "9911" })

            // e Nothing at the link: a readable message, nothing saved.
            val reposBefore = TachiyomiExtensions.state.value.repos.size
            val e = runBlocking { UniversalAdd.add(ctx, "${base()}/nothing") }
            log("ADD_E failure='${e.exceptionOrNull()?.message}'")
            assertTrue("empty link was accepted: $e", e.isFailure)
            assertTrue("message: ${e.exceptionOrNull()?.message}", e.exceptionOrNull()?.message?.contains("couldn't find") == true)
            assertEquals("a failed add saved a repository", reposBefore, TachiyomiExtensions.state.value.repos.size)
            log("ADD_REQUESTS ${paths.joinToString(" ")}")
            log("ADD_RESULT PASS")
        } finally {
            added.asReversed().forEach { runCatching { it() } }
        }
    }
}
