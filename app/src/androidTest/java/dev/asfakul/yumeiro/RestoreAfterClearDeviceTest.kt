/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.backup.PendingRestore
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionScanner
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.RemoteExtension
import dev.asfakul.yumeiro.sources.js.JsSources
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
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * G9 (NBUG-7): restore after the real "Clear all data". Three phases, run by scripts/device-check.sh:
 *
 * Phase a (after `pm clear`): installs from a local repository on a fixed port
 *  - A: the real Internet Archive CloudStream extension (downloaded once from its repository),
 *  - C: the same extension with a native-library entry added, so the safety check flags it; the
 *    user accepts the warning (the test answers Yes),
 *  - a Mangayomi JS repository and one JS source,
 * then makes a default backup (Extension files off, as the Backup page offers) in files/nbug7.zip.
 * The script copies it out, runs `pm clear` and puts it back.
 * Phase b: stages the restore with the default choice (everything but Extension files) and kills its
 * own process ("Process crashed" by design).
 * Phase c (a new process; the restore is applied at start): the repository answers 503 to the first
 * request for each extension (a repository or network that fails once). Expected within 4 minutes,
 * without restarting the app:
 *  - G9-2: A downloaded again and loaded (before: one try per process, then listed but missing),
 *  - G9-5: C downloaded again after asking the user (before: refused silently on every start),
 *  - G9-1: the JS repository and JS source listed again, and the source's code downloaded (before:
 *    both lists were left out of the default backup).
 * No network for the Internet Archive download in phase a → skipped, not passed.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RestoreAfterClearDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val phase: String? get() = InstrumentationRegistry.getArguments().getString("phase")
    private fun log(s: String) = Log.i("YuRestoreClear", "RAC $s")
    private var server: MockWebServer? = null
    private val firstRefused: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val served: MutableList<String> = Collections.synchronizedList(ArrayList())
    @Volatile private var answering = false
    private val asked: MutableList<String> = Collections.synchronizedList(ArrayList())

    private val zip get() = File(ctx.filesDir, "nbug7.zip")
    private fun base() = "http://127.0.0.1:$PORT"
    private fun remote(repo: String, name: String) = RemoteExtension(
        internalName = ExtensionManager.DEFAULT_INTERNAL_NAME, name = name,
        url = "${base()}/$repo/ia.cs3", version = 1, repoUrl = "${base()}/$repo/repo.json", repoName = "Yu restore $repo",
    )
    private val keyA get() = remote("a", NAME_A).key
    private val keyC get() = remote("c", NAME_C).key
    private val jsRepo get() = "${base()}/js/index.json"
    private val sections = Backup.Section.entries.toSet() - Backup.Section.EXTENSION_FILES

    /** The Internet Archive file, and the flagged copy (an extra `lib/…/libyu.so` entry). */
    private fun files(): Pair<ByteArray, ByteArray> {
        val ia = runCatching {
            val e = runBlocking { ExtensionSources.repoPlugins(ExtensionManager.DEFAULT_REPO).second }
                .first { it.internalName == ExtensionManager.DEFAULT_INTERNAL_NAME }
            NetworkClient.get(ctx).newCall(Request.Builder().url(e.url).build()).execute().use { it.body.bytes() }
        }.onFailure { log("Internet Archive download failed: $it") }.getOrDefault(ByteArray(0))
        if (ia.size < 1024) return ia to ia
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zo ->
            ZipInputStream(ByteArrayInputStream(ia)).use { zi ->
                while (true) {
                    val e = zi.nextEntry ?: break
                    zo.putNextEntry(ZipEntry(e.name)); zi.copyTo(zo); zo.closeEntry()
                }
            }
            zo.putNextEntry(ZipEntry("lib/arm64-v8a/libyu.so")); zo.write(ByteArray(64) { 0x7F }); zo.closeEntry()
        }
        return ia to out.toByteArray()
    }

    private fun startServer(ia: ByteArray, flagged: ByteArray, failFirst: Boolean) {
        val s = MockWebServer()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.url.encodedPath
                served += p
                if (failFirst && p.endsWith(".cs3") && firstRefused.add(p)) return MockResponse.Builder().code(503).body("busy").build()
                return when (p) {
                    "/a/ia.cs3" -> MockResponse.Builder().body(Buffer().write(ia)).build()
                    "/c/ia.cs3" -> MockResponse.Builder().body(Buffer().write(flagged)).build()
                    "/js/index.json" -> MockResponse.Builder().setHeader("Content-Type", "application/json").body(
                        """[{"name":"Yu JS","id":"$JS_ID","baseUrl":"https://example.org","lang":"en","typeSource":"single","iconUrl":"","isNsfw":false,"hasCloudflare":false,"sourceCodeUrl":"${base()}/js/yu.js","apiUrl":"","version":"0.0.1","isManga":false,"itemType":1,"isFullData":false,"appMinVerReq":"0.5.0","additionalParams":"","sourceCodeLanguage":1}]""",
                    ).build()
                    "/js/yu.js" -> MockResponse.Builder().body("const mangayomiSources = [];\nclass DefaultExtension extends MProvider { async search(q, p, f) { return { list: [], hasNextPage: false }; } }\n").build()
                    else -> MockResponse.Builder().code(404).body("not here").build()
                }
            }
        }
        s.start(PORT)
        server = s
    }

    /** Answers every safety question with Yes (the user accepting the warning), and records it. */
    private fun answerYes() {
        answering = true
        Thread {
            while (answering) {
                ExtensionReview.pending.value?.let { r -> asked += r.name; log("asked: ${r.name} level=${r.report.level}"); ExtensionReview.answer(true) }
                Thread.sleep(100)
            }
        }.apply { isDaemon = true }.start()
    }

    @After fun tearDown() {
        answering = false
        runCatching { server?.close() }
    }

    @Test fun a_installThenBackUp() {
        assumeTrue("phase a runs from scripts/device-check.sh", phase == "a")
        FirstRun.toTabs(rule)
        val (ia, flagged) = files()
        assumeTrue("Internet Archive extension could not be downloaded (no network)", ia.size > 1024)
        val level = ExtensionScanner.scan(flagged).level
        log("a files ia=${ia.size} flagged=${flagged.size} flaggedLevel=$level")
        assertTrue("fixture: the flagged copy must be DANGER, was $level", level == ExtensionScanner.Level.DANGER)
        startServer(ia, flagged, failFirst = false)
        answerYes()
        runBlocking {
            ExtensionManager.install(ctx, remote("a", NAME_A), ExtensionReview.Mode.AUTO)
            ExtensionManager.install(ctx, remote("c", NAME_C), ExtensionReview.Mode.ALWAYS)
            JsSources.addRepo(ctx, jsRepo).getOrThrow()
            val entry = JsSources.available(ctx).first { it.id == JS_ID }
            JsSources.install(ctx, entry).getOrThrow()
        }
        val s = ExtensionManager.state.value
        log("a installed A loaded=${s.info(keyA).loaded} C loaded=${s.info(keyC).loaded} asked=$asked " +
            "js repos=${JsSources.state.value.repos} js sources=${JsSources.state.value.installed.map { it.id }}")
        assertTrue("phase a: A not loaded ${s.info(keyA)}", s.info(keyA).loaded)
        assertTrue("phase a: C not loaded ${s.info(keyC)}", s.info(keyC).loaded)
        assertTrue("phase a: JS source not installed", JsSources.state.value.installed.any { it.id == JS_ID })
        val summary = zip.outputStream().use { runBlocking { Backup.export(ctx, it, sections, kind = "test") } }
        val names = ZipInputStream(zip.inputStream()).use { zi -> generateSequence { zi.nextEntry }.map { it.name }.toList() }
        log("a backup entries=${summary.entries} bytes=${zip.length()} js=${names.filter { "js/" in it }} " +
            "extensions=${names.filter { "extensions/" in it }}")
        log("a done")
    }

    @Test fun b_stageRestoreThenDie() {
        assumeTrue("phase b runs from scripts/device-check.sh", phase == "b")
        assertTrue("phase b: the backup was not put back after pm clear", zip.isFile && zip.length() > 0)
        runBlocking { ExtensionManager.ensureLoaded(ctx) }
        val left = ExtensionManager.state.value.installed.map { it.key }.filter { it == keyA || it == keyC }
        assertTrue("phase b: pm clear left extensions installed: $left", left.isEmpty())
        val staged = runBlocking { Backup.stage(ctx, { zip.inputStream() }, sections) }
        log("b staged=$staged pending=${PendingRestore.hasPending(ctx)}")
        assertTrue("phase b: nothing staged", PendingRestore.hasPending(ctx))
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun c_everySourceComesBack() {
        assumeTrue("phase c runs from scripts/device-check.sh", phase == "c")
        val (ia, flagged) = files()
        assumeTrue("Internet Archive extension could not be downloaded (no network)", ia.size > 1024)
        startServer(ia, flagged, failFirst = true)
        answerYes()
        val start = System.currentTimeMillis()
        FirstRun.toTabs(rule)
        fun ok(key: String): Boolean {
            val s = ExtensionManager.state.value
            return s.installed.any { it.key == key } && key !in ExtensionManager.missingFiles(ctx).map { it.key } && s.info(key).loaded
        }
        fun jsRepoBack() = jsRepo in JsSources.state.value.repos
        fun jsSourceBack() = JsSources.state.value.installed.any { it.id == JS_ID }
        fun jsCodeBack() = File(File(ctx.filesDir, "js"), "$JS_ID.js").isFile
        val end = start + 240_000
        while (System.currentTimeMillis() < end && !(ok(keyA) && ok(keyC) && jsRepoBack() && jsSourceBack() && jsCodeBack())) Thread.sleep(1_000)
        val s = ExtensionManager.state.value
        val secs = (System.currentTimeMillis() - start) / 1000
        log("c after ${secs}s listed=${s.installed.map { it.key }} missing=${ExtensionManager.missingFiles(ctx).map { it.key }} " +
            "A=${s.info(keyA)} C=${s.info(keyC)} asked=$asked served=${served.toList()}")
        log("c G9-2 ${if (ok(keyA)) "PASS" else "FAIL"} · G9-5 ${if (ok(keyC) && asked.any { it == NAME_C }) "PASS" else "FAIL"} · " +
            "G9-1 ${if (jsRepoBack() && jsSourceBack() && jsCodeBack()) "PASS" else "FAIL"} (repo=${jsRepoBack()} source=${jsSourceBack()} code=${jsCodeBack()})")
        try {
            assertTrue("G9-2: extension A still missing ${secs}s after the restore (repo refused the first request once): ${s.info(keyA)}", ok(keyA))
            assertTrue("G9-5: the extension the user had accepted was not offered again (asked=$asked): ${s.info(keyC)}", ok(keyC) && asked.any { it == NAME_C })
            assertTrue("G9-1: JS repository not restored: ${JsSources.state.value.repos}", jsRepoBack())
            assertTrue("G9-1: JS source not restored: ${JsSources.state.value.installed.map { it.id }}", jsSourceBack())
            assertTrue("G9-1: JS source's code not downloaded again", jsCodeBack())
            log("RESULT PASS")
        } finally {
            runBlocking {
                runCatching { ExtensionManager.uninstall(ctx, keyA) }
                runCatching { ExtensionManager.uninstall(ctx, keyC) }
            }
            runCatching { JsSources.uninstall(ctx, JS_ID) }
            runCatching { JsSources.removeRepo(ctx, jsRepo) }
            zip.delete()
        }
    }

    private companion object {
        const val PORT = 18765
        const val JS_ID = "9921"
        const val NAME_A = "Yu Restore Archive"
        const val NAME_C = "Yu Restore Flagged"
    }
}
