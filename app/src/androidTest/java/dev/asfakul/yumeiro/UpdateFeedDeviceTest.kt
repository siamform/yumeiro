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
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRuntime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * FEED-ENGINE FE-Q1 / FE-Q2 (owner 2026-11-21: "the feed takes ~10 min after install or update; check whether
 * extensions get reinstalled on update"). Three phases, each its own process (scripts/device-check.sh):
 *  a. install real market extensions and save every extension's home (the state a user has before an update);
 *  b. new process, same app: time to the first usable extension, all loaded, and the first saved home row;
 *  c. after `adb install -r` of the same app (a real in-place package update: new code path, data kept):
 *     the same timings, and the extension files must be the very same files (no download, no rewrite).
 * Numbers go to logcat as `UPDFEED <what> <ms>`; the script also prints `dumpsys package dexopt` and the
 * extension folder before and after, so a recompile of the extension code (FE-Q2) shows in the evidence.
 */
@RunWith(AndroidJUnit4::class)
class UpdateFeedDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(what: String, v: Any, extra: String = "") = Log.i("UPDFEED", "UPDFEED $what $v $extra".trim())
    private val manifest get() = File(ctx.filesDir, "updfeed-manifest.txt")

    /** name|size|lastModified of every file the extension engine keeps (code files and their compiled folders). */
    private fun files(): List<String> = File(ctx.filesDir, "extensions").walkTopDown().filter { it.isFile }
        .map { "${it.relativeTo(ctx.filesDir)}|${it.length()}|${it.lastModified()}" }.sorted().toList()

    @Test fun a_installAndSaveHomes() = runBlocking<Unit> {
        withTimeout(400_000) {
            ExtensionManager.ensureLoaded(ctx)
            val items = ExtensionSources.all(ctx, force = true).items.filter {
                it.primary && !it.isNsfw && !it.isDown && it.apiVersion <= OnlineRuntime.API_VERSION
            }
            var n = ExtensionManager.state.value.installed.count { it.enabled }
            for (c in items) {
                if (n >= WANT) break
                if (ExtensionManager.state.value.installed.any { it.internalName == c.internalName }) continue
                runCatching { ExtensionManager.install(ctx, c, ExtensionReview.Mode.AUTO) }
                    .onSuccess { n++ }.onFailure { log("install_failed", c.internalName, it.message.orEmpty()) }
            }
        }
        val apis = homeApis()
        assertTrue("need at least 3 extensions with a home, have ${apis.size}", apis.size >= 3)
        val saved = coroutineScope {
            apis.map { api -> async { withTimeoutOrNull(60_000) { runCatching { OnlineRepository.home(api, force = true) }.getOrNull() }.orEmpty().isNotEmpty() } }.awaitAll()
        }.count { it }
        log("a_homes_saved", saved, "apis=${apis.size}")
        assertTrue("no extension home could be saved", saved > 0)
        val f = files()
        manifest.writeText(f.joinToString("\n"))
        log("a_files", f.size)
    }

    @Test fun b_relaunchBeforeUpdate() = measure("b")

    @Test fun c_afterUpdate() {
        val before = manifest.readLines().filter { it.isNotBlank() }
        val codeBefore = before.filter { !it.contains("/oat/") }
        measure("c")
        val after = files()
        val codeAfter = after.filter { !it.contains("/oat/") }
        log("c_files", after.size, "before=${before.size}")
        // FE-Q2 evidence: compiled-code files that changed, appeared or went away across the update.
        val oatB = before.filter { it.contains("/oat/") }.toSet(); val oatA = after.filter { it.contains("/oat/") }.toSet()
        val oatChanged = (oatB - oatA) + (oatA - oatB)
        log("c_oat_changed", oatChanged.size, oatChanged.take(6).joinToString(";"))
        // FE-Q1: the update must not download or rewrite any extension code file.
        assertEquals("extension code files changed across the update (reinstalled?)", codeBefore, codeAfter)
    }

    private fun measure(phase: String) = runBlocking<Unit> {
        val t0 = System.currentTimeMillis()
        val loading = async { ExtensionManager.ensureLoaded(ctx) }
        withTimeout(120_000) { ExtensionManager.state.first { it.usable && ExtensionManager.providers().isNotEmpty() } }
        log("${phase}_ext_first_usable", System.currentTimeMillis() - t0)
        withTimeout(240_000) { loading.await() }
        val loaded = ExtensionManager.state.value.runtime.values.count { it.loaded }
        log("${phase}_ext_all_loaded", System.currentTimeMillis() - t0, "extensions=$loaded providers=${ExtensionManager.providers().size}")
        assertTrue("no extension loaded", loaded > 0)
        val apis = homeApis()
        val h0 = System.currentTimeMillis()
        val rows = coroutineScope { apis.map { api -> async { OnlineRepository.feedHome(api).orEmpty().isNotEmpty() } }.awaitAll() }
        val ms = System.currentTimeMillis() - h0
        log("${phase}_saved_homes", ms, "with_rows=${rows.count { it }} apis=${apis.size}")
        // FE-1 promise kept across a relaunch and an update: saved homes are there and come back at once.
        assertTrue("no saved home after relaunch ($phase)", rows.any { it })
        assertTrue("saved homes took $ms ms ($phase)", ms <= SAVED_HOME_MS)
    }

    private fun homeApis() = ExtensionManager.providers().filter { runCatching { it.hasMainPage }.getOrDefault(false) }

    private companion object {
        const val WANT = 12
        /** Same 1 s promise as FE-1 (device: home_first_row_cached 1 ms on 44884969625). */
        const val SAVED_HOME_MS = 1_000L
    }
}
