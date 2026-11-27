/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.diagnostics

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * R15, Phase 5: every crash report is copied as a plain `.txt` file to
 * `Download/Yumeiro/Crash reports/` as soon as it is written (crash handler) or imported (freezes,
 * native crashes and memory kills found on the next start). The user can attach the file to a
 * message without opening Settings first. Reports are already scrubbed by [CrashLog.scrub].
 *
 * Android 10+: MediaStore Downloads (no storage permission). Android 9 and older TV boxes: the
 * app's own external folder (`Android/data/<package>/files/Crash reports`).
 */
object CrashExport {
    const val FOLDER = "Yumeiro/Crash reports"
    private const val PREFS = "yumeiro_crash_export"

    /** Exports [report] once. Safe to call from the crash handler (no coroutines, no locks held). */
    fun export(context: Context, report: File): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(report.name, false)) return true
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "yumeiro-" + report.name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
                    // R15 (device run 43629395323): without the pending step MediaStore never
                    // rescanned the entry, so Downloads listed the report as an empty 0-byte file.
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val cr = context.contentResolver
                val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                val written = runCatching {
                    cr.openOutputStream(uri, "w")?.use { out -> report.inputStream().use { it.copyTo(out) } } ?: -1L
                }.getOrDefault(-1L)
                if (written < 0L) {
                    runCatching { cr.delete(uri, null, null) } // never leave an empty entry behind
                    return@runCatching false
                }
                cr.update(uri, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                    put(MediaStore.Downloads.SIZE, written)
                }, null, null)
                true
            } else {
                val dir = File(context.getExternalFilesDir(null) ?: return@runCatching false, tr(R.string.crash_reports, "Crash reports")).apply { mkdirs() }
                report.copyTo(File(dir, "yumeiro-" + report.name), overwrite = true)
                true
            }
        }.getOrDefault(false)
        // commit(): the process may be about to die.
        if (ok) prefs.edit().putBoolean(report.name, true).commit()
        return ok
    }

    /** On start: anything not exported yet (a crash that killed the process before the copy finished). */
    fun exportPending(context: Context) {
        val dir = File(context.filesDir, "crash")
        (dir.listFiles { f -> f.name.endsWith(".txt") } ?: return)
            .sortedBy { it.lastModified() }
            .forEach { export(context, it) }
    }
}
