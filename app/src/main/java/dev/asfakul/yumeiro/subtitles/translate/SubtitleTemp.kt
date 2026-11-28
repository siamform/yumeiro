/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.subtitles.translate

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.profiles.Profiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * B20 S6: the temporary files of subtitle translation, one folder per episode under
 * `cacheDir/sub-tl/<profile id>/<episode key>/` (the source file, the translated lines, the written tracks).
 * Folders go when the episode is finished and the player closes, when another episode starts, at app start when
 * older than 48 h and not the episode to resume, and the oldest go first once all of them pass 30 MB.
 */
object SubtitleTemp {
    private const val TAG = "SubtitleTemp"
    private const val ROOT = "sub-tl"
    private const val META = "meta.json"
    const val MAX_AGE_MS = 48L * 3_600_000L
    const val MAX_BYTES = 30L * 1024 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Episode keys finished while playing (deleted when the player closes). */
    private val finished = ConcurrentHashMap.newKeySet<String>()

    /** The key of one episode of one title from one source (no title text in the folder name). */
    fun keyFor(api: String, titleUrl: String, episodeData: String?): String =
        MessageDigest.getInstance("SHA-256").digest("$api|$titleUrl|${episodeData.orEmpty()}".toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    internal fun root(c: Context) = File(c.cacheDir, ROOT)
    private fun profileDir(c: Context) = File(root(c), Profiles.currentId(c))

    /** This episode's folder (made on demand), stamped with what it belongs to. */
    fun dir(c: Context, key: String, api: String, episodeData: String?): File = File(profileDir(c), key).apply {
        mkdirs()
        runCatching { File(this, META).writeText(JSONObject().put("api", api).put("data", episodeData.orEmpty()).put("at", System.currentTimeMillis()).toString()) }
    }

    /** Another episode starts: every other folder of this profile goes. */
    fun keepOnly(c: Context, key: String?) {
        val app = c.applicationContext
        val dir = profileDir(app)
        scope.launch { dir.listFiles()?.filter { it.isDirectory && it.name != key }?.forEach { delete(it) } }
    }

    /** The player reached the end of [key]'s episode. */
    fun markFinished(key: String) { finished += key }

    /** The player closed: a finished episode's folder goes. */
    fun onPlayerClosed(c: Context, key: String?) {
        if (key == null || !finished.remove(key)) return
        val f = File(profileDir(c.applicationContext), key)
        scope.launch { delete(f) }
    }

    /**
     * App start: folders older than 48 h go unless [isResumePoint] says the episode is the one to continue (this
     * profile only; other profiles' old folders always go), then the oldest until the total is under 30 MB.
     */
    fun sweep(c: Context, now: Long = System.currentTimeMillis(), isResumePoint: (api: String, data: String) -> Boolean) {
        val app = c.applicationContext
        val mine = Profiles.currentId(app)
        val all = root(app).listFiles()?.filter { it.isDirectory }.orEmpty().flatMap { p -> p.listFiles()?.filter { it.isDirectory }.orEmpty().map { p.name to it } }
        val kept = ArrayList<File>()
        for ((profile, f) in all) {
            val meta = runCatching { JSONObject(File(f, META).readText()) }.getOrNull()
            val at = meta?.optLong("at")?.takeIf { it > 0 } ?: f.lastModified()
            val old = now - at > MAX_AGE_MS
            val resume = profile == mine && meta != null && runCatching { isResumePoint(meta.optString("api"), meta.optString("data")) }.getOrDefault(false)
            if (old && !resume) delete(f) else kept += f
        }
        var total = kept.sumOf { size(it) }
        for (f in kept.sortedBy { it.lastModified() }) {
            if (total <= MAX_BYTES) break
            total -= size(f); delete(f)
        }
    }

    fun sweepAsync(c: Context, isResumePoint: (String, String) -> Boolean) { scope.launch { runCatching { sweep(c, isResumePoint = isResumePoint) }.onFailure { Log.w(TAG, "sweep: ${it.javaClass.simpleName}") } } }

    private fun size(f: File): Long = f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    private fun delete(f: File) { runCatching { f.deleteRecursively() } }
}
