/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.vault

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/**
 * F1 Vault storage. Each title is one .html file. The app's own copy (private storage) is what the Vault page
 * lists, so it never depends on storage permissions; a public copy is kept in `Download/Yumeiro/Vault` (Android 10+
 * through MediaStore, no permission; Android 9 and older with the storage permission) so the files can be opened
 * in any browser, copied to a computer and imported again after a reinstall ("Import" reads the data block back).
 */
object VaultStore {
    const val RELATIVE = "Download/Yumeiro/Vault"
    private const val MIME = "text/html"

    private val _entries = MutableStateFlow<List<VaultEntry>?>(null)
    /** Null until the first [refresh]. Newest first. */
    val entries: StateFlow<List<VaultEntry>?> = _entries

    private fun dir(c: Context) = File(c.filesDir, "vault").apply { mkdirs() }
    private fun fileOf(c: Context, e: VaultEntry) = File(dir(c), VaultLinks.fileName(e.title, e.id))
    private fun filesOf(c: Context, id: String) = dir(c).listFiles()?.filter { it.name.endsWith("-$id.html") }.orEmpty()

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    fun savedLabel(at: Long): String = "Saved " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))

    suspend fun refresh(context: Context): List<VaultEntry> = withContext(Dispatchers.IO) {
        val list = dir(context.applicationContext).listFiles()?.filter { it.name.endsWith(".html") }
            ?.mapNotNull { f -> runCatching { VaultHtml.parse(f.readText()) }.getOrNull() }
            .orEmpty().distinctBy { it.id }.sortedByDescending { it.savedAt }
        _entries.value = list
        list
    }

    fun get(id: String): VaultEntry? = _entries.value?.firstOrNull { it.id == id }

    /** Saves (or replaces, same id) [e]. Returns where the public copy went, or null if only the app copy was made. */
    suspend fun save(context: Context, e: VaultEntry): String? = withContext(Dispatchers.IO) {
        val c = context.applicationContext
        val html = VaultHtml.render(e, savedLabel(e.savedAt))
        filesOf(c, e.id).forEach { it.delete() }
        val f = fileOf(c, e)
        val tmp = File(f.parentFile, f.name + ".part")
        tmp.writeText(html)
        if (!tmp.renameTo(f)) { tmp.delete(); error("Couldn't save to the Vault") }
        val public = runCatching { writePublic(c, e, html) }.getOrNull()
        refresh(c)
        public
    }

    suspend fun rename(context: Context, id: String, title: String): Boolean {
        val e = get(id) ?: refresh(context).firstOrNull { it.id == id } ?: return false
        val t = title.trim().take(VaultHtml.MAX_TITLE).ifBlank { return false }
        save(context, e.copy(title = t))
        return true
    }

    suspend fun delete(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val c = context.applicationContext
        val gone = filesOf(c, id).map { it.delete() }.any { it }
        runCatching { deletePublic(c, id) }
        refresh(c)
        gone
    }

    /** Reads Vault files the user picked (after a reinstall, or from another phone). Returns how many were added. */
    suspend fun import(context: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val c = context.applicationContext
        var n = 0
        for (u in uris) {
            val text = runCatching { c.contentResolver.openInputStream(u)?.use { it.readBytes().take(2_000_000).toByteArray().decodeToString() } }.getOrNull() ?: continue
            val e = VaultHtml.parse(text) ?: continue
            filesOf(c, e.id).forEach { it.delete() }
            fileOf(c, e).writeText(VaultHtml.render(e, savedLabel(e.savedAt)))
            n++
        }
        refresh(c)
        n
    }

    /** Copy for the share sheet / an outside browser. */
    fun appFile(context: Context, id: String): File? = filesOf(context.applicationContext, id).firstOrNull()

    private fun legacyDir() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Yumeiro/Vault")
    private fun legacyAllowed(c: Context) = c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun writePublic(c: Context, e: VaultEntry, html: String): String? {
        deletePublic(c, e.id)
        val name = VaultLinks.fileName(e.title, e.id)
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$RELATIVE/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                c.contentResolver.openOutputStream(uri, "wt")?.use { it.write(html.toByteArray()) } ?: error("no stream")
                c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) { runCatching { c.contentResolver.delete(uri, null, null) }; return null }
            return "$RELATIVE/$name"
        }
        if (!legacyAllowed(c)) return null
        val d = legacyDir().apply { mkdirs() }
        File(d, name).writeText(html)
        return "$RELATIVE/$name"
    }

    private fun deletePublic(c: Context, id: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            // Only files this install wrote are visible here, which is exactly what we may replace.
            c.contentResolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("$RELATIVE%", "%-$id.html"))
        } else if (legacyAllowed(c)) {
            legacyDir().listFiles()?.filter { it.name.endsWith("-$id.html") }?.forEach { it.delete() }
        }
    }

    /** Builds a new entry from what the browser found. Pure except for the clock and id. */
    fun newEntry(title: String, pageUrl: String, sources: List<VaultSource>, now: Long = System.currentTimeMillis()) =
        VaultEntry(newId(), title.trim().take(VaultHtml.MAX_TITLE).ifBlank { VaultLinks.hostOf(pageUrl) ?: "Untitled" }, pageUrl, sources, now)
}
