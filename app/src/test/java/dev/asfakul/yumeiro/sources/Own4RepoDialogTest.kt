/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * OWN-4 (owner list, 2.1.0): the "Add repository" dialogs in Extensions and the Extension market called only the
 * CloudStream repository parser, so a link the universal add detects (here a Stremio add-on manifest, which needs
 * no extra flags or files) failed with "not a repository". It must be detected and added there too, and a
 * CloudStream repository must still come back as a repository.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Own4RepoDialogTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        File(context.filesDir, "stremio").deleteRecursively()
        StremioAddons.resetForTests()
        server = MockWebServer(); server.start()
    }
    @After fun tearDown() { server.close() }

    @Test fun aNonCloudStreamLinkIsDetectedAndAdded() = runBlocking {
        val manifest = """{"id":"own4.addon","name":"Own4 Addon","version":"1.0.0","types":["movie"],"resources":["stream"],"catalogs":[]}"""
        repeat(6) { server.enqueue(MockResponse.Builder().code(200).body(manifest).build()) }
        val url = server.url("/manifest.json").toString()
        val result = UniversalAdd.addFromRepoDialog(context, url)
        assertTrue("the dialog added it: ${result.exceptionOrNull()}", result.isSuccess)
        val (repo, other) = result.getOrThrow()
        assertNull("not a CloudStream repository", repo)
        assertEquals(UniversalAdd.Kind.STREMIO, other?.kind)
    }

    @Test fun repoOrAnythingKeepsTheCloudStreamResultFirst() = runBlocking {
        var anythingCalled = false
        val r = UniversalAdd.repoOrAnything({ "cs-repo" }, { anythingCalled = true; Result.failure(IllegalStateException()) })
        assertEquals("cs-repo" to null, r.getOrThrow())
        assertEquals(false, anythingCalled)
        val fail = UniversalAdd.repoOrAnything<String>({ error("not a repo") }, { Result.failure(IllegalStateException("detected nothing")) })
        assertEquals("detected nothing", fail.exceptionOrNull()?.message)
    }
}
