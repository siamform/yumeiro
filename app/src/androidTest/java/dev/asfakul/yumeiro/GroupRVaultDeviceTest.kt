/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen
import dev.asfakul.yumeiro.ui.screens.sites.WebsiteTab
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * GROUP R (F1 Yumeiro Vault) on a real WebView with a local server as the website. Only real taps on the UI, and
 * the files the app wrote are read back from storage (this test uses no Vault code, so it also ran before the
 * feature existed: fail-before on `ab/groupr-before`).
 *  a  The browser's page menu has "Save to Vault"; saving a title page keeps its player iframe, its server button,
 *     its direct video file and the stream its player loaded; it drops navigation, social and asset links. One
 *     .html file is written to the app's Vault and a public copy to Download/Yumeiro/Vault; the file forbids
 *     scripts (CSP) and has no cookie.
 *  b  The Vault page (Websites header): the title is listed; opening it lists the sources; tapping the video file
 *     plays it in Yumeiro's player (with the page as Referer); Rename renames the file; Delete (confirmed) empties
 *     the Vault, removes the public copy and shows the empty state.
 * Log tag YuGroupR, result lines GROUPR_<case>.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupRVaultDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private fun log(s: String) = Log.i("YuGroupR", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun count(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun hasText(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, what: () -> Boolean) = runCatching { compose.waitUntil(ms) { what() }; true }.getOrDefault(false)
    private fun click(tag: String) { compose.onNodeWithTag(tag, true).performClick(); compose.waitForIdle() }
    private val tv get() = Edition.isTv
    private val local get() = server.url("/").toString().trimEnd('/')
    private val other get() = local.replace("localhost", "127.0.0.1")
    private val vaultDir get() = File(ctx.filesDir, "vault")
    private fun vaultFiles() = vaultDir.listFiles()?.filter { it.name.endsWith(".html") }.orEmpty()

    private val titlePage get() = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Yu Vault Movie (2026)</title></head>
<body style="margin:0;background:#111;color:#eee;font:16px sans-serif">
<nav><a href="/">Home</a> <a href="/genre/action">Action</a> <a href="/privacy">Privacy</a> <a href="https://www.facebook.com/yumeirotest">Facebook</a></nav>
<h1>Yu Vault Movie</h1>
<iframe id="player" src="$other/embed/abc123" title="Server 1" width="320" height="180"></iframe>
<ul><li data-embed="$other/e/xyz789">Server 2</li></ul>
<a href="$other/files/yu-vault-movie.mp4">Download 1080p</a>
<a href="/static/app.css">css</a>
<script>fetch('/hls/master.m3u8').catch(function(){});</script>
</body></html>"""

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                fun page(b: String) = MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(b).build()
                return when {
                    path == "/movie" -> page(titlePage)
                    path.startsWith("/embed/") || path.startsWith("/e/") -> page("<!doctype html><title>Player</title><video></video>")
                    path == "/hls/master.m3u8" -> MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl")
                        .body("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow.m3u8\n").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        if (Build.VERSION.SDK_INT < 29) FirstRun.shell("pm grant ${ctx.packageName} android.permission.WRITE_EXTERNAL_STORAGE")
        clearVault()
        inst.runOnMainSync {
            WebsiteTab.closeAll(); SavedSites.init(ctx)
            SavedSites.sites.value.forEach { SavedSites.remove(ctx, it.url) }
        }
        IncomingMedia.consume()
    }

    @After fun tearDown() {
        inst.runOnMainSync { WebsiteTab.closeAll(); SavedSites.sites.value.forEach { SavedSites.remove(ctx, it.url) } }
        clearVault()
        IncomingMedia.consume()
        runCatching { server.close() }
    }

    private fun clearVault() {
        vaultFiles().forEach { it.delete() }
        publicNames().forEach { n -> deletePublic(n) }
    }

    /** The Vault's public copies this install can see (Download/Yumeiro/Vault). */
    private fun publicNames(): List<String> = if (Build.VERSION.SDK_INT >= 29) {
        val out = ArrayList<String>()
        ctx.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("Download/Yumeiro/Vault%"), null)?.use { c -> while (c.moveToNext()) out += c.getString(0) }
        out
    } else {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Yumeiro/Vault").listFiles()?.map { it.name }.orEmpty()
    }

    private fun deletePublic(name: String) {
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf("Download/Yumeiro/Vault%", name))
        else File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Yumeiro/Vault/$name").delete()
    }

    private fun show() {
        compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.WEBSITE) } } }
        compose.waitForIdle()
    }

    /** Opens the title page in the browser and saves it to the Vault through the page menu. Returns the file. */
    private fun saveTitlePage(): File {
        inst.runOnMainSync { SavedSites.add(ctx, "$local/movie", "YuVaultSite") }
        show()
        assertTrue("no site row", waitFor(15_000) { hasText("YuVaultSite") })
        compose.onAllNodesWithText("YuVaultSite", substring = false, useUnmergedTree = true).onFirst().performClick()
        assertTrue("the website page did not open", waitFor(10_000) { has("browser_bar") })
        // The page loaded and its player asked for its stream.
        assertTrue("page never loaded", waitFor(20_000) { WebsiteTab.tab.value?.keep?.title?.contains("Yu Vault Movie") == true || hasText("Yu Vault Movie") })
        Thread.sleep(2_500)
        click("browser_menu")
        assertTrue("the page menu has no Save to Vault", waitFor(5_000) { has("browser_save_vault") })
        click("browser_save_vault")
        assertTrue("the save dialog did not open", waitFor(10_000) { has("vault_save_dialog") })
        val picks = (0 until 20).count { has("vault_pick_$it") }
        log("picks=$picks")
        assertTrue("expected at least 3 sources (player, server 2, file), got $picks", picks >= 3)
        FirstRun.shot("groupr_save_dialog")
        click("vault_save_confirm")
        assertTrue("nothing was written to the Vault", waitFor(10_000) { vaultFiles().size == 1 })
        assertTrue("the save note did not show", waitFor(5_000) { has("browser_note") })
        return vaultFiles().single()
    }

    @Test fun a_saveFromBrowserWritesTheFile() {
        val f = saveTitlePage()
        val html = f.readText()
        log("file=${f.name} bytes=${html.length}")
        assertTrue("title missing", html.contains("Yu Vault Movie"))
        assertTrue("player iframe missing", html.contains("$other/embed/abc123"))
        assertTrue("server button missing", html.contains("$other/e/xyz789"))
        assertTrue("video file missing", html.contains("$other/files/yu-vault-movie.mp4"))
        assertFalse("social link kept", html.contains("facebook.com"))
        assertFalse("navigation link kept", html.contains("/genre/action") || html.contains("/privacy"))
        assertFalse("asset kept", html.contains("app.css"))
        assertTrue("no CSP that forbids scripts", html.contains("default-src 'none'"))
        assertFalse("a cookie was stored", html.contains("cookie", ignoreCase = true))
        assertFalse("a live script tag", Regex("<script(?![^>]*application/json)", RegexOption.IGNORE_CASE).containsMatchIn(html))
        val pub = publicNames()
        log("public=$pub")
        assertTrue("no public copy in Download/Yumeiro/Vault: $pub", pub.contains(f.name))
        log("GROUPR_A PASS tv=$tv api=${Build.VERSION.SDK_INT}")
    }

    @Test fun b_vaultPageOpensPlaysRenamesDeletes() {
        val f = saveTitlePage()
        inst.runOnMainSync { WebsiteTab.closeAll() }
        assertTrue("did not return to the list", waitFor(10_000) { !has("browser_bar") })
        click("site_vault")
        assertTrue("the Vault page did not open", waitFor(10_000) { has("vault_screen") })
        assertTrue("the saved title is not listed", waitFor(10_000) { has("vault_entry") && hasText("Yu Vault Movie") })
        FirstRun.shot("groupr_vault_list")
        click("vault_entry")
        assertTrue("the title's sources did not open", waitFor(10_000) { has("vault_sources") && count("vault_source") >= 3 })
        FirstRun.shot("groupr_vault_sources")
        IncomingMedia.consume()
        compose.onAllNodesWithText("Download 1080p", substring = true, useUnmergedTree = true).onFirst().performClick()
        assertTrue("the video file did not open in the player", waitFor(10_000) { IncomingMedia.pending.value?.uri?.contains("yu-vault-movie.mp4") == true })
        IncomingMedia.consume()
        // Rename.
        click("vault_rename")
        assertTrue(waitFor(5_000) { has("vault_rename_field") })
        compose.onNodeWithTag("vault_rename_field", true).performTextClearance()
        compose.onNodeWithTag("vault_rename_field", true).performTextInput("Yu Renamed Title")
        click("vault_rename_confirm")
        assertTrue("rename did not rename the file", waitFor(10_000) { vaultFiles().singleOrNull()?.name?.startsWith("yu-renamed-title-") == true })
        assertFalse("the old file stayed", f.exists())
        assertTrue("the public copy was not renamed: ${publicNames()}", waitFor(5_000) { publicNames().any { it.startsWith("yu-renamed-title-") } && publicNames().none { it == f.name } })
        // Delete.
        click("vault_delete")
        click("vault_delete_confirm")
        assertTrue("delete did not empty the Vault", waitFor(10_000) { vaultFiles().isEmpty() && has("vault_empty") })
        assertTrue("the public copy stayed: ${publicNames()}", waitFor(5_000) { publicNames().isEmpty() })
        FirstRun.shot("groupr_vault_empty")
        log("GROUPR_B PASS tv=$tv api=${Build.VERSION.SDK_INT}")
    }
}
