/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PIN-1-DUP: one extension name could be installed from any number of repositories (every market and
 * mirror repo ships HiAnime), so the feed, search and filter sheet filled with copies. At most 2 copies;
 * the third install is refused before anything is downloaded, and the message names both copies.
 * Old code: duplicateCapMessage / MAX_SAME_NAME do not exist (compile failure) and the third copy installs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DuplicateCapTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer

    private fun ext(name: String, repo: String, internal: String = name) = InstalledExtension(
        internalName = internal, name = name, version = 1, repoUrl = "https://$repo.example/repo.json", repoName = repo,
        fileName = "$repo-$internal.cs3", sourceUrl = "https://$repo.example/$internal.cs3", sha256 = "0", enabled = false,
    )

    @Before fun setUp() {
        java.io.File(context.filesDir, "extensions").deleteRecursively()
        ExtensionManager.resetForTests()
        server = MockWebServer(); server.start()
    }
    @After fun tearDown() { server.close(); ExtensionManager.resetForTests() }

    @Test fun aThirdCopyIsRefusedAndTheMessageNamesTheTwo() {
        val two = listOf(ext("HiAnime", "Phisher"), ext("HiAnime", "Kraptor"))
        val third = RemoteExtension.keyOf("https://Mega.example/repo.json", "HiAnime")
        assertEquals(2, ExtensionManager.MAX_SAME_NAME)
        assertEquals(
            "HiAnime is already installed 2 times (from Phisher and Kraptor). Remove one first",
            ExtensionManager.duplicateCapMessage(two, "HiAnime", third),
        )
        // same name as the user reads it: case and spaces do not make a new extension
        assertTrue(ExtensionManager.duplicateCapMessage(two, " hianime ", third) != null)
    }

    @Test fun oneCopyUpdatesAndOtherNamesAreAllowed() {
        val one = listOf(ext("HiAnime", "Phisher"))
        assertNull(ExtensionManager.duplicateCapMessage(one, "HiAnime", RemoteExtension.keyOf("https://Kraptor.example/repo.json", "HiAnime")))
        val two = one + ext("HiAnime", "Kraptor")
        // updating or reinstalling a copy that is there is never blocked
        assertNull(ExtensionManager.duplicateCapMessage(two, "HiAnime", two[1].key))
        assertNull(ExtensionManager.duplicateCapMessage(two, "Gogo", RemoteExtension.keyOf("https://Mega.example/repo.json", "Gogo")))
    }

    @Test fun installRefusesTheThirdCopyBeforeDownloading() = runBlocking {
        val dir = java.io.File(context.filesDir, "extensions").apply { mkdirs() }
        val two = listOf(ext("HiAnime", "Phisher"), ext("HiAnime", "Kraptor"))
        java.io.File(dir, "state.json").writeText(
            JSONObject().put("version", 1).put("installed", JSONArray().apply { two.forEach { put(it.toJson()) } })
                .put("repos", JSONArray()).put("removedDefaults", JSONArray()).toString(),
        )
        ExtensionManager.ensureLoaded(context)
        assertEquals(2, ExtensionManager.state.value.installed.size)
        server.enqueue(MockResponse.Builder().body("not reached").build())
        val remote = RemoteExtension(
            internalName = "HiAnime", name = "HiAnime", url = server.url("/HiAnime.cs3").toString(), version = 3,
            repoUrl = "https://Mega.example/repo.json", repoName = "Mega",
        )
        try { ExtensionManager.install(context, remote); fail("a third copy installed") }
        catch (e: ExtensionManager.ExtensionException) {
            assertEquals("HiAnime is already installed 2 times (from Phisher and Kraptor). Remove one first", e.message)
        }
        assertEquals(0, server.requestCount)
        assertEquals(2, ExtensionManager.state.value.installed.size)
    }
}
