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
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.backup.PendingRestore
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
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
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

/**
 * B16 on a real device: a Vega plugin (served by a local repository) is installed, backed up, removed
 * as on a new phone, and restored through the same export / stage / apply steps Settings uses.
 * With extension files the plugin comes back from the backup; without them it is downloaded again.
 * Before the fix the backup had no `scripts/` entries at all, so the plugin was simply gone.
 */
@RunWith(AndroidJUnit4::class)
class GroupMBackupScriptsDeviceTest {
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var server: MockWebServer
    private val codeHits = AtomicInteger()
    private lateinit var url: String
    private val sources = setOf(Backup.Section.SOURCES, Backup.Section.EXTENSION_FILES)

    private fun log(m: String) = Log.i("YuGroupM", m)

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path == "/vega/manifest.json" -> MockResponse.Builder().code(200).setHeader("Content-Type", "application/json")
                        .body("""[{"display_name":"Backup Probe","value":"yubackupprobe","version":"1.0.0","type":"global","disabled":false}]""").build()
                    path.startsWith("/vega/dist/yubackupprobe/") && path.endsWith(".js") -> {
                        codeHits.incrementAndGet()
                        MockResponse.Builder().code(200).body("exports.probe = '" + path.substringAfterLast('/') + "';").build()
                    }
                    else -> MockResponse.Builder().code(404).body("not here").build()
                }
            }
        }
        server.start()
        url = "http://${server.hostName}:${server.port}/vega/manifest.json"
    }

    @After fun tearDown() {
        runCatching { EcosystemRegistry.state.value.installed.filter { it.repoUrl == url }.forEach { EcosystemRegistry.uninstall(ctx, it.key) } }
        runCatching { EcosystemRegistry.removeRepo(ctx, url) }
        runCatching { server.close() }
    }

    private fun install(): EcosystemRegistry.Installed = runBlocking {
        val added = EcosystemRegistry.addRepo(ctx, url)
        assertTrue("repo added: ${added.exceptionOrNull()?.message}", added.isSuccess)
        val (repo, entry) = EcosystemRegistry.available(ctx).first { it.first.url == url }
        val p = EcosystemRegistry.install(ctx, repo, entry).getOrThrow()
        ctx.getSharedPreferences("script_kv_${p.storeName}", Context.MODE_PRIVATE).edit().putString("probe", "kept").commit()
        p
    }

    /** Backs up [sections], removes the plugin and repo as on a new phone, then restores. Returns the zip's entry names. */
    private fun roundTrip(p: EcosystemRegistry.Installed, sections: Set<Backup.Section>): List<String> = runBlocking {
        val zip = File(ctx.cacheDir, "yu-groupm-scripts.zip")
        try {
            zip.outputStream().use { Backup.export(ctx, it, sections, kind = "test") }
            val names = ZipInputStream(zip.inputStream()).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
            EcosystemRegistry.uninstall(ctx, p.key)
            EcosystemRegistry.removeRepo(ctx, url)
            assertTrue("gone before restore", EcosystemRegistry.state.value.installed.none { it.key == p.key })
            assertTrue("files gone before restore", !EcosystemRegistry.dirOf(ctx, p).exists())
            val staged = Backup.stage(ctx, { zip.inputStream() }, sections)
            val applied = PendingRestore.applyIfAny(ctx)
            log("GM_CASE B16 staged=$staged applied=$applied entries=${names.count { it.startsWith("files/scripts/") }}")
            // The app restarts after a restore; the registry then reads the restored list from disk.
            EcosystemRegistry.resetForTests()
            names
        } finally { zip.delete() }
    }

    /** What the app does at start: load the list, then (B16) fetch plugins whose files are gone. */
    private fun restart(p: EcosystemRegistry.Installed): EcosystemRegistry.Installed? {
        EcosystemRegistry.start(ctx)
        val end = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < end) {
            val back = EcosystemRegistry.state.value.installed.firstOrNull { it.key == p.key }
            if (back != null && EcosystemRegistry.intact(ctx, back)) return back
            Thread.sleep(250)
        }
        return EcosystemRegistry.state.value.installed.firstOrNull { it.key == p.key }
    }

    @Test fun pluginComesBackFromTheBackup() = runBlocking<Unit> {
        val p = install()
        val names = roundTrip(p, sources)
        assertTrue("registry in backup: ${names.filter { "scripts" in it }}", "files/scripts/registry.json" in names)
        assertTrue("plugin code in backup", names.any { it.startsWith("files/scripts/vega/") && it.endsWith("/catalog.js") })
        val hitsBefore = codeHits.get()
        val back = restart(p)
        val line = "GM_CASE B16 withFiles back=${back != null} intact=${back?.let { EcosystemRegistry.intact(ctx, it) }} code=${codeHits.get() - hitsBefore} " +
            "kv=${ctx.getSharedPreferences("script_kv_${p.storeName}", Context.MODE_PRIVATE).getString("probe", null)}"
        log(line)
        assertTrue(line, back != null && EcosystemRegistry.intact(ctx, back))
        assertEquals("$line: no download needed", hitsBefore, codeHits.get())
        assertTrue(line, EcosystemRegistry.state.value.repos.any { it.url == url })
        assertEquals(line, "kept", ctx.getSharedPreferences("script_kv_${p.storeName}", Context.MODE_PRIVATE).getString("probe", null))
        log("GM_RESULT B16 withFiles PASS")
    }

    @Test fun pluginIsDownloadedAgainWithoutExtensionFiles() = runBlocking<Unit> {
        val p = install()
        val names = roundTrip(p, setOf(Backup.Section.SOURCES))
        assertTrue("registry in backup", "files/scripts/registry.json" in names)
        assertTrue("no code in a backup without extension files", names.none { it.startsWith("files/scripts/vega/") })
        val hitsBefore = codeHits.get()
        val back = restart(p)
        val line = "GM_CASE B16 listOnly back=${back != null} intact=${back?.let { EcosystemRegistry.intact(ctx, it) }} code=${codeHits.get() - hitsBefore}"
        log(line)
        assertTrue("$line: downloaded again", codeHits.get() > hitsBefore)
        assertTrue(line, back != null && EcosystemRegistry.intact(ctx, back))
        log("GM_RESULT B16 listOnly PASS")
    }
}
