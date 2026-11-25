/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.update.GitHubReleaseClient
import dev.asfakul.yumeiro.update.ReleaseInfo
import dev.asfakul.yumeiro.update.UpdateManager
import dev.asfakul.yumeiro.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP I part 5 (owner spec 2026-11-26-beta-channel.md): the update popup names the step it is really in and never
 * keeps a "Connecting GitHub" line while the APK downloads. Real everything: the real latest release of
 * siamform/yumeiro (public API), the real UpdateManager, Android's DownloadManager downloading the real APK, and the
 * real popup in the running app. Only the starting point is set by the test: the manager is put in "Available" with
 * that release (a debug build is never offered its own version), then the person taps Download.
 *  a) Every line the popup shows is read about every 150 ms until the download ends. No line names GitHub or
 *     "Connecting"; "Downloading update…" appears with real, rising byte counts; when the network can be cut on this
 *     emulator, "Waiting for a connection…" shows and the download goes on after the network comes back; the end is
 *     a check step and then a plain failure ("for a different app": the debug app is dev.asfakul.yumeiro.debug, the
 *     release APK is dev.asfakul.yumeiro, so the installer's own guard refuses it). That failure is the expected end.
 *  b) Cancel download in the middle: the system download is removed and the popup offers Download again.
 * Log tag YuGroupI; GROUPI_UPDATE_<x>_RESULT PASS lines are the evidence.
 */
@RunWith(AndroidJUnit4::class)
class GroupIUpdateDownloadDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupI", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private lateinit var mgr: UpdateManager

    @Before fun setUp() {
        runCatching { InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        network(true)
        FirstRun.toTabs(rule)
        mgr = UpdateManager.get(ctx)
    }

    @After fun tearDown() {
        network(true)
        runCatching { rule.runOnUiThread { mgr.cancelDownload(); mgr.dismissDialog() } }
    }

    private fun network(on: Boolean) {
        val v = if (on) "enable" else "disable"
        FirstRun.shell("svc wifi $v"); FirstRun.shell("svc data $v")
    }
    private fun online(): Boolean = (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo?.isConnected == true

    /** Every text inside the update popup, as the person sees it. */
    private fun popupLines(): List<String> = runCatching {
        rule.onAllNodes(hasAnyAncestor(hasTestTag("update_dialog")), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() }
    }.getOrDefault(emptyList())

    @Suppress("UNCHECKED_CAST")
    private fun offer(release: ReleaseInfo) {
        val f = UpdateManager::class.java.getDeclaredField("_state").apply { isAccessible = true }
        rule.runOnUiThread {
            (f.get(mgr) as MutableStateFlow<UpdateState>).value = UpdateState.Available(release)
            mgr.showDialog()
        }
    }

    /**
     * The real latest release through the app's own client. Runs 45717326746 / 45717330319: the shared CI address had
     * used up GitHub's anonymous API allowance (HTTP 403, "Too many update checks"), which says nothing about the
     * download popup. Then the same real release is read from the github.com pages (not the API): tag from the
     * /releases/latest redirect, the real file, its real size and its SHA-256 from SHA256SUMS.txt. The download still
     * goes through the app's own resolve + DownloadManager path (API first, browser link when the API says no).
     */
    private fun latest(): ReleaseInfo {
        val tv = mgr.wantsTvEdition()
        val viaApi = runCatching { runBlocking { GitHubReleaseClient(wantTv = { tv }).fetchLatestRelease() } }
        viaApi.getOrNull()?.let { return it }
        val err = viaApi.exceptionOrNull()!!
        if (err.message?.startsWith("Too many update checks") != true) throw err
        log("GitHub API rate-limited on this runner; reading the same latest release from github.com pages")
        fun open(url: String, head: Boolean, follow: Boolean) = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            instanceFollowRedirects = follow; connectTimeout = 20_000; readTimeout = 30_000
            if (head) requestMethod = "HEAD"; setRequestProperty("User-Agent", "Yumeiro-device-test")
        }
        val base = "https://github.com/siamform/yumeiro/releases"
        val loc = open("$base/latest", head = true, follow = false).let { c -> try { c.responseCode; c.getHeaderField("Location") } finally { c.disconnect() } }
        val tag = loc?.substringAfterLast("/tag/")?.takeIf { it.startsWith("v") } ?: throw AssertionError("no latest tag from $loc", err)
        val v = tag.removePrefix("v")
        val name = if (tv) "Yumeiro-TV-v$v.tv.apk" else "release-Yumeiro-v$v.apk"
        val url = "$base/download/$tag/$name"
        val sums = open("$base/download/$tag/SHA256SUMS.txt", head = false, follow = true).let { c -> try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() } }
        val sha = sums.lines().map { it.trim().split(Regex("\\s+")) }.firstOrNull { it.size == 2 && it[1].removePrefix("*") == name }?.get(0)
            ?: throw AssertionError("$name not in SHA256SUMS.txt of $tag", err)
        val size = open(url, head = true, follow = true).let { c -> try { c.responseCode; c.contentLengthLong } finally { c.disconnect() } }
        assertTrue("real size of $name ($size)", size > 1_000_000)
        return ReleaseInfo(
            tagName = tag, versionName = v, versionCode = null, title = "Yumeiro $tag", notes = "", publishedAt = "",
            htmlUrl = "$base/tag/$tag",
            asset = dev.asfakul.yumeiro.update.ApkAsset(id = 0, name = name, sizeBytes = size, apiUrl = "", browserUrl = url, sha256 = sha.lowercase(), checksumsUrl = null),
            source = dev.asfakul.yumeiro.update.UpdateSource.PUBLIC,
        )
    }

    private fun startFromThePopup(release: ReleaseInfo) {
        offer(release)
        rule.waitUntil(20_000) { has("update_download") }
        FirstRun.shot("groupi_update_available")
        rule.onAllNodesWithTag("update_download", true)[0].performClick()
    }

    @Test fun a_popupNamesTheRealStepWithRealProgress() {
        val release = latest()
        log("release ${release.tagName} asset=${release.asset.name} size=${release.asset.sizeBytes}")
        startFromThePopup(release)
        val seen = LinkedHashSet<String>()
        val bad = LinkedHashSet<String>()
        val bytes = ArrayList<Long>()
        var cut = false; var cutDone = false; var cutWorked = false; var sawWaiting = false; var resumedAfter = false
        var end: UpdateState? = null
        val until = System.currentTimeMillis() + 420_000
        while (System.currentTimeMillis() < until) {
            val s = mgr.state.value
            val lines = popupLines()
            lines.filter { "github" in it.lowercase() || "connecting" in it.lowercase() }.forEach { bad += it }
            if (s is UpdateState.Downloading) {
                lines.firstOrNull()?.let { if (seen.add(it)) log("title '$it' at ${s.downloadedBytes}/${s.totalBytes} stage=${s.stage}") }
                if (s.downloadedBytes > 0 && (bytes.isEmpty() || bytes.last() != s.downloadedBytes)) bytes += s.downloadedBytes
                if (lines.any { it == "Waiting for a connection…" }) { if (!sawWaiting) FirstRun.shot("groupi_update_waiting"); sawWaiting = true }
                if (sawWaiting && !cut && s.stage.name == "DOWNLOADING" && lines.firstOrNull() == "Downloading update…") resumedAfter = true
                if (!cut && !cutDone && s.totalBytes > 0 && s.downloadedBytes > s.totalBytes / 5 && s.downloadedBytes < s.totalBytes / 2) {
                    FirstRun.shot("groupi_update_downloading")
                    network(false); cut = true
                    cutWorked = runCatching { rule.waitUntil(10_000) { !online() }; true }.getOrDefault(false)
                    log("network cut at ${s.downloadedBytes} worked=$cutWorked")
                    if (!cutWorked) { network(true); cut = false; bytes.clear(); bytes += s.downloadedBytes }
                    else {
                        // One cut per run: api33-lowram run 45717326746 cut again on every loop pass after the network came
                        // back (the byte count was still between 20 % and 50 %), so the download never had a chance to go on.
                        cutDone = true
                        val waited = runCatching { rule.waitUntil(120_000) { popupLines().any { it == "Waiting for a connection…" } || mgr.state.value is UpdateState.Failed }; true }.getOrDefault(false)
                        log("after cut: waited=$waited lines=${popupLines()} state=${mgr.state.value::class.simpleName}")
                        popupLines().filter { "github" in it.lowercase() || "connecting" in it.lowercase() }.forEach { bad += it }
                        if (popupLines().any { it == "Waiting for a connection…" }) { sawWaiting = true; FirstRun.shot("groupi_update_waiting") }
                        network(true); cut = false
                        continue
                    }
                }
            } else if (s is UpdateState.Failed || s is UpdateState.ReadyToInstall) {
                rule.waitForIdle(); popupLines().let { l -> log("end ${s::class.simpleName}: $l"); l.filter { "github" in it.lowercase() || "connecting" in it.lowercase() }.forEach { bad += it } }
                FirstRun.shot("groupi_update_end")
                end = s; break
            }
            Thread.sleep(150)
        }
        log("titles=$seen bytes=${bytes.size} first=${bytes.firstOrNull()} last=${bytes.lastOrNull()} cutWorked=$cutWorked waiting=$sawWaiting resumed=$resumedAfter end=$end bad=$bad")
        assertTrue("the popup named the hosting service or a connection step: $bad", bad.isEmpty())
        assertTrue("'Downloading update…' was shown: $seen", "Downloading update…" in seen)
        assertTrue("only real step titles: $seen", seen.all { it in STEPS })
        assertTrue("real progress: the byte count rose several times ($bytes)", bytes.size >= 3 && bytes.zipWithNext().all { (a, b) -> b > a || cutWorked })
        if (cutWorked) {
            assertTrue("network cut: 'Waiting for a connection…' was shown", sawWaiting)
            assertTrue("network back: the download went on", resumedAfter || end != null && end !is UpdateState.Failed || (end as? UpdateState.Failed)?.message?.contains("different app") == true)
        } else log("this emulator's network could not be cut with svc; the waiting step is covered by UpdateStatusTextTest and MANUAL_TESTS.md")
        val f = end as? UpdateState.Failed
        assertTrue("the download ended (state=$end)", end != null)
        assertTrue("the end is the installer's own guard, in plain words: ${f?.message}", f?.message?.contains("different app") == true)
        log("GROUPI_UPDATE_A_RESULT PASS titles=$seen samples=${bytes.size} cut=$cutWorked")
    }

    @Test fun b_cancelInTheMiddleOffersDownloadAgain() {
        val release = latest()
        startFromThePopup(release)
        val moving = runCatching { rule.waitUntil(180_000) { (mgr.state.value as? UpdateState.Downloading)?.downloadedBytes ?: 0L > 0L }; true }.getOrDefault(false)
        assertTrue("the download started (${mgr.state.value})", moving)
        rule.waitUntil(10_000) { has("update_cancel") }
        rule.onAllNodesWithTag("update_cancel", true)[0].performClick()
        val back = runCatching { rule.waitUntil(15_000) { mgr.state.value is UpdateState.Available && has("update_download") }; true }.getOrDefault(false)
        FirstRun.shot("groupi_update_cancelled")
        assertTrue("after Cancel the popup offers Download again (${mgr.state.value})", back)
        val dm = ctx.getSystemService(android.app.DownloadManager::class.java)
        var running = 0
        dm.query(android.app.DownloadManager.Query().setFilterByStatus(android.app.DownloadManager.STATUS_RUNNING or android.app.DownloadManager.STATUS_PENDING or android.app.DownloadManager.STATUS_PAUSED))?.use { running = it.count }
        assertTrue("no system download left running ($running)", running == 0)
        log("GROUPI_UPDATE_B_RESULT PASS")
    }

    companion object {
        val STEPS = setOf("Preparing your update…", "Starting download…", "Waiting for a connection…", "Downloading update…", "Checking the download…")
    }
}
