/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.asfakul.yumeiro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local backup and restore: one .zip with settings, history, My List, playlists, sources,
 * repositories, home-server entries and manga progress. Written wherever the user picks (Drive,
 * SD card, Downloads) through the system file picker, so no Yumeiro server is involved.
 *
 * Never included: the updater state, encrypted account tokens (they are bound to this device's
 * Keystore and would not decrypt elsewhere) and downloaded videos.
 */
object Backup {
    const val FORMAT = 1
    private const val MANIFEST = "yumeiro-backup.json"

    /** Shared preferences that must stay on this device. */
    private val excludedPrefs = setOf("mt_updater", "online_secrets", "privacy", "WebViewChromiumPrefs")
    /** Top-level files that are device-specific. */
    private val excludedFiles = setOf("online_downloads.json")
    /** Folders under filesDir that hold user data (always small). */
    private val dataDirs = listOf("streams", "servers", "manga", "stremio", "js")
    /** Folders that hold extension code; optional because they can be large. */
    private val codeDirs = listOf("extensions", "tachiyomi")

    data class Summary(val entries: Int, val bytes: Long, val createdAt: Long, val appVersion: String)

    suspend fun export(context: Context, target: Uri, includeExtensions: Boolean): Summary = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        var count = 0
        var bytes = 0L
        val now = System.currentTimeMillis()
        val out = app.contentResolver.openOutputStream(target, "wt") ?: error("Couldn't write to that location")
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, file: File) {
                if (!file.isFile) return
                zip.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                count++; bytes += file.length()
            }
            val manifest = JSONObject().put("format", FORMAT).put("app", BuildConfig.APPLICATION_ID)
                .put("version", BuildConfig.VERSION_NAME).put("createdAt", now).put("extensions", includeExtensions)
            zip.putNextEntry(ZipEntry(MANIFEST)); zip.write(manifest.toString(2).toByteArray()); zip.closeEntry()

            prefsDir(app).listFiles()?.filter { it.extension == "xml" && it.nameWithoutExtension !in excludedPrefs }?.forEach { put("prefs/${it.name}", it) }
            app.filesDir.listFiles()?.filter { it.isFile && it.extension == "json" && it.name !in excludedFiles }?.forEach { put("files/${it.name}", it) }
            val dirs = dataDirs + if (includeExtensions) codeDirs else emptyList()
            dirs.forEach { d ->
                val root = File(app.filesDir, d)
                root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f -> put("files/" + f.relativeTo(app.filesDir).invariantSeparatorsPath, f) }
            }
        }
        Summary(count, bytes, now, BuildConfig.VERSION_NAME)
    }

    /** Restore limits: a hostile "backup" must not fill the phone (zip bomb). */
    const val MAX_RESTORE_BYTES = 768L * 1024 * 1024
    const val MAX_RESTORE_ENTRIES = 50_000
    /** Paths under files/ that hold runnable code (extensions, Aniyomi/Mihon dex, JS providers). */
    private val codePrefixes = (codeDirs + "js").map { "files/$it/" }
    private fun readCapped(input: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (out.size() < max) { val n = input.read(buf, 0, minOf(buf.size, max - out.size())); if (n < 0) break; out.write(buf, 0, n) }
        return out.toByteArray()
    }
    internal fun isCode(name: String) = codePrefixes.any { name.startsWith(it) }

    /**
     * Reads the manifest so the UI can show what's inside before restoring. `hasCode` is worked out
     * from the real entries (the manifest is just text and could lie).
     */
    suspend fun inspect(context: Context, source: Uri): JSONObject = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(source) ?: error("Couldn't open that file")
        var manifest: JSONObject? = null
        var hasCode = false
        var entries = 0
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e ->
                if (++entries > MAX_RESTORE_ENTRIES) error("This backup has too many files")
                if (e.name == MANIFEST) manifest = JSONObject(readCapped(zip, 256 * 1024).decodeToString())
                else if (isCode(e.name)) hasCode = true
            }
        }
        (manifest ?: error("This isn't a Yumeiro backup")).put("hasCode", hasCode)
    }

    /**
     * Restores a backup. Existing data with the same names is replaced; anything else is kept.
     * Extension code is only restored when [includeCode] is set (the user ticked it), because a
     * backup someone else shared could carry code. The app restarts afterwards.
     */
    suspend fun restore(context: Context, source: Uri, includeCode: Boolean = false): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val manifest = inspect(app, source)
        require(manifest.optInt("format") in 1..FORMAT) { "This backup was made by a newer Yumeiro. Update the app first." }
        val staging = File(app.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var count = 0
        var total = 0L
        val input = app.contentResolver.openInputStream(source) ?: error("Couldn't open that file")
        try {
            ZipInputStream(input.buffered()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { e ->
                    if (e.isDirectory || e.name == MANIFEST) return@forEach
                    if (!includeCode && isCode(e.name)) return@forEach
                    val target = safeTarget(staging, e.name) ?: return@forEach
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buf); if (n < 0) break
                            total += n
                            if (total > MAX_RESTORE_BYTES) error("This backup is too large to restore")
                            out.write(buf, 0, n)
                        }
                    }
                    count++
                }
            }
        } catch (t: Throwable) { staging.deleteRecursively(); throw t }
        // Move staged files into place only after the whole archive was read successfully.
        File(staging, "prefs").listFiles()?.forEach { f ->
            if (f.nameWithoutExtension in excludedPrefs) return@forEach
            f.copyTo(File(prefsDir(app), f.name), overwrite = true)
        }
        File(staging, "files").takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile }?.forEach { f ->
            val rel = f.relativeTo(File(staging, "files")).invariantSeparatorsPath
            val dest = File(app.filesDir, rel)
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.setWritable(true)
            f.copyTo(dest, overwrite = true)
            // Installed Aniyomi / Mihon code must stay read-only (Android 14+ refuses writable dex).
            if (rel.startsWith("tachiyomi/") && rel.endsWith(".ext")) dest.setReadOnly()
        }
        staging.deleteRecursively()
        count
    }

    /** Rejects zip-slip paths and anything outside prefs/ and files/. */
    internal fun safeTarget(root: File, name: String): File? {
        if (!(name.startsWith("prefs/") || name.startsWith("files/"))) return null
        // files/: only the top-level JSON stores and the known data / code folders, never anything else.
        if (name.startsWith("files/")) {
            val rel = name.removePrefix("files/")
            val top = rel.substringBefore('/')
            val ok = if ('/' !in rel) rel.endsWith(".json") && rel !in excludedFiles else top in dataDirs || top in codeDirs
            if (!ok) return null
        } else if (name.removePrefix("prefs/").let { '/' in it || !it.endsWith(".xml") }) return null
        if (name.contains("..") || name.contains('\\') || name.startsWith("/")) return null
        val f = File(root, name)
        return f.takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
    }

    private fun prefsDir(context: Context) = File(context.applicationInfo.dataDir, "shared_prefs")

    /** Relaunches the app so every in-memory cache reloads from the restored files. */
    fun restartApp(context: Context) {
        val app = context.applicationContext
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) app.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }

    fun suggestedName(): String = "yumeiro-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".zip"
}
