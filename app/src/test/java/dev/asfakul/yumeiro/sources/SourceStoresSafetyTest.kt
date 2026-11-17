/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.data.SafeJsonFile
import dev.asfakul.yumeiro.sources.js.JsSources
import dev.asfakul.yumeiro.sources.servers.HomeServers
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * N4-1 / N5-1 regression: the user's saved home servers, Stremio add-ons, JavaScript sources and
 * Tachiyomi repos must survive a damaged file, a change made before the start-up read finished, and
 * an offline start. Every case here lost data on the old code (read → empty list → save wipes).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SourceStoresSafetyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        listOf("servers", "stremio", "js", "tachiyomi").forEach { File(context.filesDir, it).deleteRecursively() }
        HomeServers.resetForTests(); StremioAddons.resetForTests(); JsSources.resetForTests()
        server = MockWebServer(); server.start()
    }
    @After fun tearDown() { server.close() }

    private fun waitUntil(ms: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond() && System.currentTimeMillis() < end) Thread.sleep(20)
    }

    private fun manifest(id: String) = """{"id":"$id","name":"Addon $id","version":"1.0.0","types":["movie"],"resources":["stream"],"catalogs":[]}"""

    // N4-1
    @Test fun homeServersDamagedFileIsKeptNotWiped() {
        val f = File(File(context.filesDir, "servers").apply { mkdirs() }, "servers.json")
        val damaged = """[{"id":"a","kind":"DLNA","name":"Living room","url":"http://192.168.1.5""" // cut mid-write
        f.writeText(damaged)
        HomeServers.start(context)
        HomeServers.remove(context, "nothing")
        assertEquals("damaged copy kept for recovery", damaged, SafeJsonFile.badOf(f).takeIf { it.isFile }?.readText())
    }

    @Test fun homeServersRecoveredFromLeftoverTmp() {
        val f = File(File(context.filesDir, "servers").apply { mkdirs() }, "servers.json")
        SafeJsonFile.tmpOf(f).writeText("""[{"id":"a","kind":"DLNA","name":"Living room","url":"http://192.168.1.5:8200/desc.xml"}]""")
        HomeServers.start(context)
        assertEquals(listOf("a"), HomeServers.servers.value.map { it.id })
    }

    // N5-1 Stremio: an add-on that cannot be reached at start must stay saved after another change.
    @Test fun stremioOfflineAddonSurvivesRemoveOfAnother() {
        server.enqueue(MockResponse.Builder().body(manifest("live")).build())
        val live = server.url("/live/manifest.json").toString()
        val dead = "http://127.0.0.1:1/dead/manifest.json" // connection refused = offline
        val f = File(File(context.filesDir, "stremio").apply { mkdirs() }, "addons.json")
        f.writeText(JSONArray(listOf(dead, live)).toString())
        StremioAddons.start(context)
        waitUntil { StremioAddons.addons.value.any { it.manifestUrl == live } }
        assertEquals(listOf(live), StremioAddons.addons.value.map { it.manifestUrl })
        StremioAddons.remove(context, live)
        val saved = JSONArray(f.readText()).let { a -> List(a.length()) { a.getString(it) } }
        assertEquals("offline add-on must not be deleted", listOf(dead), saved)
    }

    @Test fun stremioDamagedFileKeptOnAdd() = runBlocking {
        val f = File(File(context.filesDir, "stremio").apply { mkdirs() }, "addons.json")
        f.writeText("""["https://a.example/manifest.json","https://b.ex""")
        server.enqueue(MockResponse.Builder().body(manifest("n")).build())
        StremioAddons.add(context, server.url("/n/manifest.json").toString()).getOrThrow()
        assertTrue("damaged copy kept", SafeJsonFile.badOf(f).isFile)
    }

    // N5-1 JS: one damaged file must not empty the other list; a change before start must keep data.
    @Test fun jsDamagedSourcesFileDoesNotEmptyRepos() {
        val d = File(context.filesDir, "js").apply { mkdirs() }
        File(d, "repos.json").writeText("""["https://repo.example/index.json"]""")
        File(d, "sources.json").writeText("""[{"id":"1","name":"cut""")
        JsSources.start(context)
        waitUntil { JsSources.state.value.repos.isNotEmpty() }
        assertEquals(listOf("https://repo.example/index.json"), JsSources.state.value.repos)
    }

    @Test fun jsChangeBeforeStartKeepsSavedLists() {
        val d = File(context.filesDir, "js").apply { mkdirs() }
        File(d, "repos.json").writeText("""["https://repo.example/index.json"]""")
        File(d, "sources.json").writeText("""[{"id":"s1","name":"S1","sourceCodeLanguage":"1","itemType":"1","sourceCodeUrl":"https://x/s1.js"}]""")
        JsSources.removeRepo(context, "https://other.example/index.json")
        assertEquals("""["https://repo.example/index.json"]""", File(d, "repos.json").readText())
        assertTrue(File(d, "sources.json").readText().contains("\"s1\""))
    }

    // N5-1 Tachiyomi: a change before the start-up reload must build on the saved repos.
    @Test fun tachiyomiRemoveBeforeReloadKeepsSavedRepos() = runBlocking {
        val f = File(File(context.filesDir, "tachiyomi").apply { mkdirs() }, "repos.json")
        val text = """[{"url":"https://repo.example","kind":"ANIME","name":"R"}]"""
        f.writeText(text)
        TachiyomiExtensions.removeRepo(context, "https://other.example")
        assertTrue("saved repo kept: ${f.readText()}", f.readText().contains("https://repo.example"))
    }

    @Test fun tachiyomiDamagedReposKept() = runBlocking {
        val f = File(File(context.filesDir, "tachiyomi").apply { mkdirs() }, "repos.json")
        f.writeText("""[{"url":"https://repo.exa""")
        TachiyomiExtensions.removeRepo(context, "https://other.example")
        assertTrue("damaged copy kept", SafeJsonFile.badOf(f).isFile)
    }
}
