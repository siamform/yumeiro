/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.RemoteExtension
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NBUG-16 NB16-4 ("after updating, syncing again flags it as needing an update again"): community and
 * discovered market entries point at the author's live file, which is often newer than the market's
 * listing (2026-11-19: StreamPlay listed 711, file 713). The installed version must be the file's own
 * (manifest), so a later listing of that same version is not an update.
 *
 * Uses the real Internet Archive extension (downloaded once from its repository) served by a local
 * server, listed one version below its manifest. No network → skipped, not passed.
 */
@RunWith(AndroidJUnit4::class)
class Nbug16ExtensionVersionDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(s: String) = Log.i("YuNb16", "NB16_4 $s")
    private lateinit var server: MockWebServer
    private var bytes = ByteArray(0)
    private var fileVersion = -1

    private fun remote(version: Int) = RemoteExtension(
        internalName = ExtensionManager.DEFAULT_INTERNAL_NAME, name = "Internet Archive (NB16)",
        url = server.url("/nb16/ia.cs3").toString(), version = version,
        repoUrl = server.url("/nb16/repo.json").toString(), repoName = "Yu NB16 repo", origin = "community",
    )

    private fun manifestVersion(b: ByteArray): Int {
        java.util.zip.ZipInputStream(b.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "manifest.json") return JSONObject(z.readBytes().decodeToString()).optInt("version", -1)
                e = z.nextEntry
            }
        }
        return -1
    }

    @Before fun setUp() {
        bytes = runCatching {
            val ia = runBlocking { ExtensionSources.repoPlugins(ExtensionManager.DEFAULT_REPO).second }
                .first { it.internalName == ExtensionManager.DEFAULT_INTERNAL_NAME }
            NetworkClient.get(ctx).newCall(Request.Builder().url(ia.url).build()).execute().use { it.body.bytes() }
        }.onFailure { log("download failed: $it") }.getOrDefault(ByteArray(0))
        assumeTrue("Internet Archive extension could not be downloaded (no network)", bytes.size > 1024)
        fileVersion = manifestVersion(bytes)
        assumeTrue("the Internet Archive file has no manifest version ($fileVersion)", fileVersion > 1)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.url.encodedPath.endsWith("/ia.cs3")) MockResponse.Builder().body(Buffer().write(bytes)).build()
                else MockResponse.Builder().code(404).build()
        }
        server.start()
    }

    @After fun tearDown() {
        if (!::server.isInitialized) return
        runBlocking { runCatching { ExtensionManager.uninstall(ctx, remote(1).key) } }
        runCatching { server.close() }
    }

    @Test fun installedVersionIsTheFilesOwn(): Unit = runBlocking {
        val listed = fileVersion - 1
        val ext = ExtensionManager.install(ctx, remote(listed), ExtensionReview.Mode.AUTO)
        val flagged = ExtensionManager.updatesFrom(listOf(remote(fileVersion))).filter { it.first.key == ext.key }
        log("file manifest=$fileVersion listed=$listed installed.version=${ext.version} loaded=${ExtensionManager.state.value.info(ext.key).loaded} flaggedAgainst=$fileVersion -> ${flagged.size}")
        assertTrue("extension did not load", ExtensionManager.state.value.info(ext.key).loaded)
        assertEquals("NB16-4: installed version is the listing's ($listed), not the file's own ($fileVersion)", fileVersion, ext.version)
        assertTrue("NB16-4: the same file is flagged as an update when the market lists $fileVersion", flagged.isEmpty())
        log("RESULT PASS")
    }
}
