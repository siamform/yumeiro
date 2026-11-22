/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.profiles

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.sources.js.JsSources
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions
import dev.asfakul.yumeiro.ui.theme.AccentTone
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * F-19-EXT-2: F-19-EXT made CloudStream extensions and repositories per profile, but Stremio add-ons,
 * JavaScript sources, script (Vega) plugins and Tachiyomi / Aniyomi extensions and repos were still
 * shared: every profile saw, loaded and could delete the others'. Same rules now (ProfileScope): what
 * was there at the update stays for every profile that existed then, a profile made later starts
 * empty, removing in one profile keeps the files for the others, and a removed profile's are deleted.
 * A profile switch restarts the app, modelled by each store's resetForTests() and a fresh load.
 * Old code: the stores share everything (ProfileScope.STREMIO and friends do not exist: compile failure).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileEcosystemsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer

    private fun clean() {
        listOf("stremio", "js", "scripts", "tachiyomi").forEach { File(ctx.filesDir, it).deleteRecursively() }
        StremioAddons.resetForTests(); JsSources.resetForTests(); EcosystemRegistry.resetForTests()
        ProfileScope.resetForTests(ctx); Profiles.resetForTests(ctx)
    }

    @Before fun setUp() {
        clean()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val id = request.url.encodedPath.trim('/').substringBefore('/')
                return MockResponse.Builder().body("""{"id":"$id","name":"Addon $id","version":"1.0.0","types":["movie"],"resources":["stream"],"catalogs":[]}""").build()
            }
        }
        server.start()
    }
    @After fun tearDown() { server.close(); clean() }

    private fun waitUntil(ms: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond() && System.currentTimeMillis() < end) Thread.sleep(20)
    }

    /** A profile switch: the app restarts, every store loads again. */
    private fun restartAs(id: String) {
        Profiles.select(ctx, id)
        StremioAddons.resetForTests(); JsSources.resetForTests(); EcosystemRegistry.resetForTests()
        ProfileScope.resetMemoryForTests()
    }

    // region Stremio
    private val addonsFile get() = File(File(ctx.filesDir, "stremio").apply { mkdirs() }, "addons.json")
    private fun savedAddons() = JSONArray(addonsFile.readText()).let { a -> List(a.length()) { a.getString(it) }.sorted() }
    private fun startStremio(expect: Int): List<String> {
        StremioAddons.start(ctx)
        waitUntil { StremioAddons.addons.value.size >= expect }
        Thread.sleep(300) // nothing else may arrive
        return StremioAddons.addons.value.map { it.manifestUrl }.sorted()
    }

    @Test fun stremioAddonsBelongToTheirProfile() {
        val kid = Profiles.add(ctx, "Kid", AccentTone.SAKURA)
        val a = server.url("/a/manifest.json").toString(); val b = server.url("/b/manifest.json").toString()
        addonsFile.writeText(JSONArray(listOf(a, b)).toString())
        restartAs(kid.id)
        assertEquals("profiles that existed at the update keep their add-ons", listOf(a, b), startStremio(2))
        StremioAddons.remove(ctx, b)
        assertEquals(listOf(a), StremioAddons.addons.value.map { it.manifestUrl })
        assertEquals("the parent still has b", listOf(a, b), savedAddons())
        restartAs(Profiles.DEFAULT)
        assertEquals(listOf(a, b), startStremio(2))
        val guest = Profiles.add(ctx, "Guest", AccentTone.MATCHA)
        restartAs(guest.id)
        assertEquals("a profile made later starts empty", emptyList<String>(), startStremio(0))
        val c = server.url("/c/manifest.json").toString()
        runBlocking { StremioAddons.add(ctx, c).getOrThrow() }
        restartAs(Profiles.DEFAULT)
        assertFalse("the parent does not get the guest's add-on", c in startStremio(2))
        assertTrue(Profiles.remove(ctx, guest.id))
        restartAs(Profiles.DEFAULT)
        startStremio(2)
        assertEquals("the removed profile's add-on is gone from disk", listOf(a, b), savedAddons())
    }
    // endregion

    // region JavaScript
    private val jsDir get() = File(ctx.filesDir, "js").apply { mkdirs() }
    private fun jsEntry(id: String) = """{"id":"$id","name":"S$id","sourceCodeLanguage":"1","itemType":"1","sourceCodeUrl":"https://x.example/$id.js"}"""
    private fun loadJs() { JsSources.start(ctx); runBlocking { JsSources.repairMissing(ctx) } }
    private fun jsIds() = JsSources.state.value.installed.map { it.id }.sorted()

    @Test fun javascriptSourcesAndReposBelongToTheirProfile() {
        val kid = Profiles.add(ctx, "Kid", AccentTone.SAKURA)
        File(jsDir, "repos.json").writeText("""["https://r.example/index.json"]""")
        File(jsDir, "sources.json").writeText("[${jsEntry("1")},${jsEntry("2")}]")
        listOf("1", "2").forEach { File(jsDir, "$it.js").writeText("class DefaultExtension {}") }
        restartAs(kid.id); loadJs()
        assertEquals(listOf("1", "2"), jsIds())
        assertEquals(listOf("https://r.example/index.json"), JsSources.state.value.repos)
        JsSources.uninstall(ctx, "2")
        JsSources.removeRepo(ctx, "https://r.example/index.json")
        assertEquals(listOf("1"), jsIds())
        assertTrue("the code stays for the parent", File(jsDir, "2.js").isFile)
        assertTrue("the entry stays on disk for the parent", File(jsDir, "sources.json").readText().contains("\"2\""))
        restartAs(Profiles.DEFAULT); loadJs()
        assertEquals(listOf("1", "2"), jsIds())
        assertEquals(listOf("https://r.example/index.json"), JsSources.state.value.repos)
        JsSources.uninstall(ctx, "2")
        assertFalse("nobody has 2: code deleted", File(jsDir, "2.js").isFile)
        val guest = Profiles.add(ctx, "Guest", AccentTone.MATCHA)
        restartAs(guest.id); loadJs()
        assertTrue("a profile made later starts empty", jsIds().isEmpty() && JsSources.state.value.repos.isEmpty())
    }
    // endregion

    // region Script plugins (Vega)
    private fun plugin(id: String) = EcosystemRegistry.Installed("http://127.0.0.1:1/r.json", SourceHub.Ecosystem.VEGA, id, "P$id", "1", null, emptyMap())
    private fun loadScripts(): List<String> = runBlocking { EcosystemRegistry.available(ctx); EcosystemRegistry.state.value.installed.map { it.id }.sorted() }

    @Test fun scriptPluginsBelongToTheirProfile() {
        val kid = Profiles.add(ctx, "Kid", AccentTone.SAKURA)
        val p1 = plugin("one"); val p2 = plugin("two")
        File(ctx.filesDir, "scripts").mkdirs()
        File(File(ctx.filesDir, "scripts"), "registry.json").writeText(EcosystemRegistry.encode(EcosystemRegistry.State(emptyList(), listOf(p1, p2))).toString())
        listOf(p1, p2).forEach { File(EcosystemRegistry.dirOf(ctx, it).apply { mkdirs() }, "index.js").writeText("x") }
        restartAs(kid.id)
        assertEquals(listOf("one", "two"), loadScripts())
        EcosystemRegistry.uninstall(ctx, p2.key)
        assertEquals(listOf("one"), EcosystemRegistry.state.value.installed.map { it.id })
        assertTrue("the code stays for the parent", EcosystemRegistry.dirOf(ctx, p2).isDirectory)
        restartAs(Profiles.DEFAULT)
        assertEquals(listOf("one", "two"), loadScripts())
        val guest = Profiles.add(ctx, "Guest", AccentTone.MATCHA)
        restartAs(guest.id)
        assertEquals("a profile made later starts empty", emptyList<String>(), loadScripts())
        restartAs(Profiles.DEFAULT); loadScripts()
        EcosystemRegistry.uninstall(ctx, p2.key)
        assertFalse("nobody has two: code deleted", EcosystemRegistry.dirOf(ctx, p2).isDirectory)
    }
    // endregion

    // region Tachiyomi / Aniyomi
    private val tachiDir get() = File(ctx.filesDir, "tachiyomi").apply { mkdirs() }
    private fun tachi(): Pair<List<String>, List<String>> = runBlocking {
        TachiyomiExtensions.reload(ctx)
        TachiyomiExtensions.state.value.let { s -> s.installed.map { it.pkg }.sorted() to s.repos.map { it.url }.sorted() }
    }

    @Test fun tachiyomiExtensionsAndReposBelongToTheirProfile() {
        val kid = Profiles.add(ctx, "Kid", AccentTone.SAKURA)
        File(tachiDir, "repos.json").writeText("""[{"url":"https://repo.example","kind":"ANIME","name":"R"}]""")
        val anime = File(tachiDir, "anime").apply { mkdirs() }
        listOf("pkg.one", "pkg.two").forEach { File(anime, "$it.ext").writeText("not an apk") }
        restartAs(kid.id)
        assertEquals(listOf("pkg.one", "pkg.two") to listOf("https://repo.example"), tachi())
        runBlocking { TachiyomiExtensions.uninstall(ctx, "pkg.two"); TachiyomiExtensions.removeRepo(ctx, "https://repo.example") }
        assertEquals(listOf("pkg.one") to emptyList<String>(), tachi())
        assertTrue("the file stays for the parent", File(anime, "pkg.two.ext").isFile)
        assertTrue("the repo stays on disk for the parent", File(tachiDir, "repos.json").readText().contains("https://repo.example"))
        restartAs(Profiles.DEFAULT)
        assertEquals(listOf("pkg.one", "pkg.two") to listOf("https://repo.example"), tachi())
        val guest = Profiles.add(ctx, "Guest", AccentTone.MATCHA)
        restartAs(guest.id)
        assertEquals("a profile made later starts empty", emptyList<String>() to emptyList<String>(), tachi())
        restartAs(Profiles.DEFAULT)
        runBlocking { TachiyomiExtensions.uninstall(ctx, "pkg.two") }
        assertFalse("nobody has pkg.two: file deleted", File(anime, "pkg.two.ext").isFile)
    }
    // endregion
}
