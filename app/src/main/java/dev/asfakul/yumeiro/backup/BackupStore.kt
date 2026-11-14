/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Where backups live on the phone. By default `Download/Yumeiro/Backups` in the phone's own
 * storage (no permission needed on Android 10+), so they survive uninstalling the app and can be
 * copied to a computer or another phone. The user can pick any other folder instead (SD card,
 * a synced Drive folder) through the system folder picker.
 *
 * After a reinstall Android hides files the old install wrote, so the Backup page asks once for
 * access to the folder ("Find my backups") and lists them again.
 */
object BackupStore {
    const val RELATIVE = "Download/Yumeiro/Backups"
    private const val MIME = "application/zip"

    enum class Where { FOLDER, DOWNLOADS, LEGACY, APP }

    data class Item(val uri: Uri, val name: String, val size: Long, val modified: Long, val where: Where) {
        /** "auto", "safety" or "manual", read from the name the app gave it. */
        val kind: String get() = when {
            name.startsWith("yumeiro-auto-") -> "auto"
            name.startsWith("yumeiro-before-") -> "safety"
            else -> "manual"
        }
    }

    fun isBackupName(n: String) = n.endsWith(".zip", true) && n.startsWith("yumeiro", true)

    private fun legacyDir(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Yumeiro/Backups")
    private fun appDir(c: Context): File? = c.getExternalFilesDir("Backups")

    fun legacyNeedsPermission(c: Context): Boolean = Build.VERSION.SDK_INT < 29 &&
        c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    fun folderUri(c: Context): Uri? = BackupMeta.folder(c)?.let(Uri::parse)?.takeIf { u ->
        c.contentResolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission }
    }

    /** Human description of where the next backup goes. */
    fun locationLabel(c: Context): String = when {
        folderUri(c) != null -> "Your chosen folder: " + (folderUri(c)?.let { DocumentsContract.getTreeDocumentId(it).substringAfter(':').ifBlank { "storage root" } } ?: "")
        Build.VERSION.SDK_INT >= 29 || !legacyNeedsPermission(c) -> "Phone storage › Download › Yumeiro › Backups"
        else -> "App storage (removed if you uninstall). Allow storage access to keep backups in Downloads"
    }

    fun rememberFolder(c: Context, tree: Uri) {
        runCatching { c.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        BackupMeta.setFolder(c, tree.toString())
    }

    fun forgetFolder(c: Context) {
        folderUri(c)?.let { u -> runCatching { c.contentResolver.releasePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        BackupMeta.setFolder(c, null)
    }

    /** Initial location for the folder picker: Download/Yumeiro (Android 8+ honours it). */
    fun pickerStart(): Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download/Yumeiro")

    /** Makes a backup and saves it in the backup folder. */
    suspend fun save(context: Context, kind: String, sections: Set<Backup.Section>): Item = withContext(Dispatchers.IO) {
        val c = context.applicationContext
        val name = Backup.suggestedName(kind)
        val item = when {
            folderUri(c) != null -> saveToTree(c, folderUri(c)!!, name, sections, kind)
            Build.VERSION.SDK_INT >= 29 -> saveToDownloads(c, name, sections, kind)
            !legacyNeedsPermission(c) -> saveToFile(c, File(legacyDir().apply { mkdirs() }, name), sections, kind, Where.LEGACY)
            else -> saveToFile(c, File(appDir(c)?.apply { mkdirs() } ?: error("No storage available"), name), sections, kind, Where.APP)
        }
        if (kind != "auto") BackupMeta.recordBackup(c)
        item
    }

    private suspend fun saveToTree(c: Context, tree: Uri, name: String, sections: Set<Backup.Section>, kind: String): Item {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(c.contentResolver, parent, MIME, name) ?: error("Couldn't create a file in that folder")
        try {
            val out = c.contentResolver.openOutputStream(doc, "wt") ?: error("Couldn't write to that folder")
            val s = Backup.export(c, out, sections, kind)
            return Item(doc, name, s.bytes, System.currentTimeMillis(), Where.FOLDER)
        } catch (t: Throwable) { runCatching { DocumentsContract.deleteDocument(c.contentResolver, doc) }; throw t }
    }

    @android.annotation.TargetApi(29)
    private suspend fun saveToDownloads(c: Context, name: String, sections: Set<Backup.Section>, kind: String): Item {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$RELATIVE/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Couldn't save to Downloads")
        try {
            val out = c.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't save to Downloads")
            val s = Backup.export(c, out, sections, kind)
            c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return Item(uri, name, s.bytes, System.currentTimeMillis(), Where.DOWNLOADS)
        } catch (t: Throwable) { runCatching { c.contentResolver.delete(uri, null, null) }; throw t }
    }

    private suspend fun saveToFile(c: Context, f: File, sections: Set<Backup.Section>, kind: String, where: Where): Item {
        val tmp = File(f.parentFile, f.name + ".part")
        try {
            val s = Backup.export(c, tmp.outputStream(), sections, kind)
            if (!tmp.renameTo(f)) error("Couldn't save the backup")
            return Item(Uri.fromFile(f), f.name, s.bytes, f.lastModified(), where)
        } finally { tmp.delete() }
    }

    /** Every backup we can see, newest first. */
    suspend fun list(context: Context): List<Item> = withContext(Dispatchers.IO) {
        val c = context.applicationContext
        val out = ArrayList<Item>()
        folderUri(c)?.let { out += runCatching { listTree(c, it) }.getOrDefault(emptyList()) }
        if (Build.VERSION.SDK_INT >= 29) out += runCatching { listDownloads(c) }.getOrDefault(emptyList())
        if (Build.VERSION.SDK_INT < 29 && !legacyNeedsPermission(c)) out += listDir(legacyDir(), Where.LEGACY)
        appDir(c)?.let { out += listDir(it, Where.APP) }
        out.distinctBy { it.name to it.size }.sortedByDescending { it.modified }
    }

    private fun listDir(d: File, where: Where) = d.listFiles()?.filter { it.isFile && isBackupName(it.name) }
        ?.map { Item(Uri.fromFile(it), it.name, it.length(), it.lastModified(), where) }.orEmpty()

    private fun listTree(c: Context, tree: Uri): List<Item> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val out = ArrayList<Item>()
        c.contentResolver.query(children, cols, null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val n = cur.getString(1) ?: continue
                if (!isBackupName(n)) continue
                out += Item(DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0)), n, cur.getLong(2), cur.getLong(3), Where.FOLDER)
            }
        }
        return out
    }

    @android.annotation.TargetApi(29)
    private fun listDownloads(c: Context): List<Item> {
        val cols = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED)
        val out = ArrayList<Item>()
        c.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cols,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0", arrayOf("$RELATIVE%"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cur ->
            while (cur.moveToNext()) {
                val n = cur.getString(1) ?: continue
                if (!isBackupName(n)) continue
                out += Item(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cur.getLong(0)), n, cur.getLong(2), cur.getLong(3) * 1000, Where.DOWNLOADS)
            }
        }
        return out
    }

    fun open(context: Context, item: Item): InputStream? = open(context, item.uri)
    fun open(context: Context, uri: Uri): InputStream? =
        if (uri.scheme == "file") uri.path?.let { File(it) }?.takeIf { it.isFile }?.inputStream() else context.contentResolver.openInputStream(uri)

    fun delete(context: Context, item: Item): Boolean = runCatching {
        when (item.where) {
            Where.FOLDER -> DocumentsContract.deleteDocument(context.contentResolver, item.uri)
            Where.DOWNLOADS -> context.contentResolver.delete(item.uri, null, null) > 0
            else -> item.uri.path?.let { File(it).delete() } ?: false
        }
    }.getOrDefault(false)

    /** Keeps the newest [keep] backups of [kind]; never touches the other kinds. */
    suspend fun prune(context: Context, kind: String, keep: Int) = withContext(Dispatchers.IO) {
        pruneList(list(context), kind, keep).forEach { delete(context, it) }
    }

    /** The backups [prune] would delete. Pure. */
    internal fun pruneList(all: List<Item>, kind: String, keep: Int): List<Item> =
        all.filter { it.kind == kind }.sortedByDescending { it.modified }.drop(keep.coerceAtLeast(1))
}
