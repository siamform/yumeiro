/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.profiles

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.InstalledExtension
import dev.asfakul.yumeiro.ui.theme.AccentTone
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
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
 * F-19-EXT: every profile saw, loaded and updated every installed extension and repository, so a kid's
 * profile got the parent's sources and the other way round. Now each profile has its own; the files are
 * shared and only deleted when no profile has them. A profile switch restarts the app, modelled here by
 * ExtensionManager.resetForTests() + ensureLoaded().
 * Old code: ProfileScope does not exist (compile failure); every profile sees everything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileExtensionsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()
    private val dir get() = File(ctx.filesDir, "extensions")
    private lateinit var server: MockWebServer

    private fun ext(name: String) = InstalledExtension(
        internalName = name, name = name, version = 1, repoUrl = "https://r.example/repo.json", repoName = "R",
        fileName = "$name.cs3", sourceUrl = "https://r.example/$name.cs3", sha256 = "0", enabled = false,
    )

    private fun writeState(vararg list: InstalledExtension) {
        dir.mkdirs()
        list.forEach { File(dir, "plugins").mkdirs(); File(File(dir, "plugins"), it.fileName).writeText("x") }
        File(dir, "state.json").writeText(JSONObject().put("version", 1).put("installed", JSONArray().apply { list.forEach { put(it.toJson()) } })
            .put("repos", JSONArray()).put("removedDefaults", JSONArray()).toString())
    }

    private fun restartAs(id: String) = runBlocking {
        Profiles.select(ctx, id)
        ExtensionManager.resetForTests(); ProfileScope.resetMemoryForTests()
        ExtensionManager.ensureLoaded(ctx)
    }
    private fun names() = ExtensionManager.state.value.installed.map { it.name }.sorted()
    private fun onDisk(): List<String> = JSONObject(File(dir, "state.json").readText()).getJSONArray("installed").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") } }.sorted()

    @Before fun setUp() {
        dir.deleteRecursively(); Profiles.resetForTests(ctx); ProfileScope.resetForTests(ctx); ExtensionManager.resetForTests()
        server = MockWebServer(); server.start()
    }
    @After fun tearDown() { server.close(); ExtensionManager.resetForTests(); ProfileScope.resetForTests(ctx); Profiles.resetForTests(ctx) }

    @Test fun tableRules() {
        val t = ProfileScope.Table().seeded(listOf("a"), listOf("0", "1"))
        assertTrue(t.visible("a", "0") && t.visible("a", "1"))
        assertTrue("no record = everyone (restored backup)", t.visible("restored", "1"))
        val c = t.claim("b", "1")
        assertFalse(c.visible("b", "0")); assertTrue(c.visible("b", "1"))
        val r = c.release("a", "1", listOf("0", "1"))
        assertFalse(r.visible("a", "1")); assertTrue(r.keptByOthers("a"))
        assertFalse(c.release("b", "1", listOf("0", "1")).keptByOthers("b"))
        assertTrue(c.dropProfile("1").orphan("b"))
        assertEquals(c, ProfileScope.Table.fromJson(c.toJson()))
        assertEquals("seeding runs once", t, t.seeded(listOf("x"), listOf("9")))
    }

    @Test fun legacyExtensionsStayForEveryoneAndRemovingInOneProfileKeepsThemForTheOther() {
        val kid = Profiles.add(ctx, "Kid", AccentTone.SAKURA)
        writeState(ext("Alpha"), ext("Beta"))
        restartAs(Profiles.DEFAULT)
        assertEquals(listOf("Alpha", "Beta"), names())
        restartAs(kid.id)
        assertEquals("profiles that existed at the update keep what was installed", listOf("Alpha", "Beta"), names())
        runBlocking { ExtensionManager.uninstall(ctx, ext("Beta").key) }
        assertEquals(listOf("Alpha"), names())
        assertEquals("the entry stays on disk for the parent", listOf("Alpha", "Beta"), onDisk())
        assertTrue("the file stays for the parent", File(File(dir, "plugins"), "Beta.cs3").isFile)
        restartAs(Profiles.DEFAULT)
        assertEquals(listOf("Alpha", "Beta"), names())
        runBlocking { ExtensionManager.uninstall(ctx, ext("Beta").key) }
        assertEquals(listOf("Alpha"), onDisk())
        assertFalse("nobody has Beta: file deleted", File(File(dir, "plugins"), "Beta.cs3").isFile)
    }

    @Test fun aNewProfileStartsEmptyAndItsRepositoryIsItsOwnAndGoesWithIt() {
        writeState(ext("Alpha"))
        restartAs(Profiles.DEFAULT)
        val guest = Profiles.add(ctx, "Guest", AccentTone.MATCHA)
        restartAs(guest.id)
        assertEquals("made after the update: none of the parent's extensions", emptyList<String>(), names())
        server.enqueue(MockResponse.Builder().body("""{"name":"Guest repo","pluginLists":["plugins.json"]}""").build())
        val url = server.url("/repo.json").toString()
        runBlocking { ExtensionManager.addRepository(ctx, url) }
        assertEquals(listOf("Guest repo"), ExtensionManager.state.value.repos.map { it.name })
        restartAs(Profiles.DEFAULT)
        assertEquals(listOf("Alpha"), names())
        assertTrue("the parent does not see the guest's repository", ExtensionManager.state.value.repos.isEmpty())
        assertTrue(Profiles.remove(ctx, guest.id))
        restartAs(Profiles.DEFAULT)
        assertTrue(ExtensionManager.state.value.repos.isEmpty())
        val disk = JSONObject(File(dir, "state.json").readText()).getJSONArray("repos")
        assertEquals("the removed profile's repository is gone from disk", 0, disk.length())
    }
}
