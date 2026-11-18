/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.APIHolder
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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * G9-7 (NBUG-7, duplicate extensions across repositories): the same CloudStream extension (same
 * internal name, same file) installed from two repositories. Uninstalling one copy must leave the
 * other installed, with its file on disk and its providers still registered.
 *
 * Before: both entries were saved as one `internalName-sha12.cs3` file and providers were tracked by
 * file path, so the uninstall deleted the other copy's file and removed its providers.
 * Uses the real Internet Archive extension (downloaded once from its repository) served by a local
 * server under two repository paths. No network → skipped, not passed.
 */
@RunWith(AndroidJUnit4::class)
class ExtensionSharedFileDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(s: String) = Log.i("YuSharedFile", "SHARED_FILE $s")
    private lateinit var server: MockWebServer
    private var bytes = ByteArray(0)

    private fun remote(repo: String) = RemoteExtension(
        internalName = ExtensionManager.DEFAULT_INTERNAL_NAME, name = "Internet Archive ($repo)",
        url = server.url("/$repo/ia.cs3").toString(), version = 1,
        repoUrl = server.url("/$repo/repo.json").toString(), repoName = "Yu repo $repo",
    )

    @Before fun setUp() {
        bytes = runCatching {
            val ia = runBlocking { ExtensionSources.repoPlugins(ExtensionManager.DEFAULT_REPO).second }
                .first { it.internalName == ExtensionManager.DEFAULT_INTERNAL_NAME }
            NetworkClient.get(ctx).newCall(Request.Builder().url(ia.url).build()).execute().use { it.body.bytes() }
        }.onFailure { log("download failed: $it") }.getOrDefault(ByteArray(0))
        assumeTrue("Internet Archive extension could not be downloaded (no network)", bytes.size > 1024)
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
        runBlocking {
            for (r in listOf("a", "b")) runCatching { ExtensionManager.uninstall(ctx, remote(r).key) }
        }
        runCatching { server.close() }
    }

    private fun providersOf(file: File) = APIHolder.allProviders.toList().count { it.sourcePlugin == file.absolutePath }

    @Test fun uninstallingOneCopyKeepsTheOther(): Unit = runBlocking {
        val a = ExtensionManager.install(ctx, remote("a"), ExtensionReview.Mode.AUTO)
        val b = ExtensionManager.install(ctx, remote("b"), ExtensionReview.Mode.AUTO)
        val s = ExtensionManager.state.value
        log("installed a=${a.key} file=${a.fileName} loaded=${s.info(a.key).loaded} · b=${b.key} file=${b.fileName} loaded=${s.info(b.key).loaded}")
        assertTrue("copy a did not load: ${s.info(a.key)}", s.info(a.key).loaded)
        assertTrue("copy b did not load: ${s.info(b.key)}", s.info(b.key).loaded)

        ExtensionManager.uninstall(ctx, b.key)
        val fileA = ExtensionManager.pluginFile(ctx, ExtensionManager.state.value.installed.first { it.key == a.key })
        val missing = ExtensionManager.missingFiles(ctx).map { it.key }
        val providers = providersOf(fileA)
        log("after uninstall b: a file=${fileA.name} exists=${fileA.isFile} missing=$missing providers=$providers " +
            "a still listed=${ExtensionManager.state.value.installed.any { it.key == a.key }}")
        assertTrue("G9-7: uninstalling copy b deleted copy a's file ${fileA.name}", fileA.isFile)
        assertTrue("G9-7: copy a is listed as missing after uninstalling b: $missing", a.key !in missing)
        assertTrue("G9-7: copy a's providers were removed with copy b (0 registered from ${fileA.name})", providers > 0)
        log("RESULT PASS")
    }
}
