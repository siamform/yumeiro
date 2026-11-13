/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Applies a staged restore or reset at the very start of the next launch, before anything in the
 * app has read a setting or a file (called first thing in `MTApplication.onCreate`). Nothing in
 * memory can overwrite what was restored, which was the root cause of "restore did nothing".
 *
 * A broken plan is dropped after two tries so a bad backup can never trap the app in a crash loop.
 */
object PendingRestore {
    private const val TAG = "YuBackup"
    private const val PLAN = "plan.json"
    fun dir(context: Context) = File(context.filesDir, ".restore_pending")

    data class Plan(
        val sections: Set<Backup.Section> = emptySet(),
        val from: String = "",
        val createdAt: Long = 0,
        /** Extensions travel as a list: download them again after the restart. */
        val redownload: Boolean = false,
        /** Aniyomi / Mihon packages to download again (JSON array of {pkg, kind}). */
        val tachiyomi: String = "[]",
        /** Reset instead of restore: what to wipe (see [ResetCenter.Scope]). */
        val reset: Set<String> = emptySet(),
        val attempts: Int = 0,
    ) {
        fun toJson(): JSONObject = JSONObject().put("sections", JSONArray(sections.map { it.name })).put("from", from)
            .put("createdAt", createdAt).put("redownload", redownload).put("tachiyomi", tachiyomi)
            .put("reset", JSONArray(reset.toList())).put("attempts", attempts)

        companion object {
            fun from(o: JSONObject) = Plan(
                sections = o.optJSONArray("sections")?.let { a -> (0 until a.length()).mapNotNull { i -> runCatching { Backup.Section.valueOf(a.optString(i)) }.getOrNull() } }.orEmpty().toSet(),
                from = o.optString("from"), createdAt = o.optLong("createdAt"), redownload = o.optBoolean("redownload"),
                tachiyomi = o.optString("tachiyomi", "[]"),
                reset = o.optJSONArray("reset")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().toSet(),
                attempts = o.optInt("attempts"),
            )
        }
    }

    fun writePlan(context: Context, plan: Plan) {
        val d = dir(context).apply { mkdirs() }
        val tmp = File(d, "$PLAN.tmp")
        tmp.writeText(plan.toJson().toString())
        tmp.renameTo(File(d, PLAN))
    }

    fun hasPending(context: Context) = File(dir(context), PLAN).isFile

    /** Drops a staged restore the user cancelled (or that never got applied). */
    fun discard(context: Context) { dir(context).deleteRecursively() }

    /** Applies the waiting plan, if any. Never throws. Returns true when something was applied. */
    fun applyIfAny(context: Context): Boolean {
        val d = dir(context)
        val planFile = File(d, PLAN)
        if (!planFile.isFile) { if (d.exists()) d.deleteRecursively(); return false }
        return try {
            val plan = Plan.from(JSONObject(planFile.readText()))
            if (plan.attempts >= 2) { Log.w(TAG, "dropping a restore that failed twice"); d.deleteRecursively(); BackupMeta.recordFailure(context, "The last restore couldn't be applied and was skipped."); return false }
            planFile.writeText(plan.copy(attempts = plan.attempts + 1).toJson().toString())
            if (plan.reset.isNotEmpty()) {
                ResetCenter.applyNow(context, plan.reset)
                BackupMeta.recordReset(context, plan.reset)
            } else {
                val report = applyRestore(context, d, plan)
                BackupMeta.recordRestore(context, plan, report)
            }
            d.deleteRecursively()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "apply", t)
            false
        }
    }

    data class Report(val prefs: Int, val files: Int, val failed: Int)

    private fun applyRestore(context: Context, d: File, plan: Plan): Report {
        var prefs = 0; var files = 0; var failed = 0
        File(d, Backup.PREFS_JSON).takeIf { it.isFile }?.let { f ->
            val all = runCatching { JSONObject(f.readText()) }.getOrNull() ?: JSONObject()
            all.keys().forEach { name ->
                if (!Backup.prefIncluded(name)) return@forEach
                val ok = runCatching {
                    PrefCodec.apply(context.getSharedPreferences(name, Context.MODE_PRIVATE), PrefCodec.decode(all.getJSONObject(name)))
                }.getOrDefault(false)
                if (ok) prefs++ else failed++
            }
        }
        val src = File(d, "files")
        if (src.isDirectory) {
            // The extension list decides which plugin files are kept: restoring one without the
            // other would leave dangling entries, so a restored state.json replaces the old list.
            src.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(src).invariantSeparatorsPath
                val ok = runCatching {
                    val dest = File(context.filesDir, rel)
                    require(dest.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator))
                    dest.parentFile?.mkdirs()
                    if (dest.exists()) { dest.setWritable(true); dest.delete() }
                    if (!f.renameTo(dest)) f.copyTo(dest, overwrite = true)
                    // Installed Aniyomi / Mihon code must stay read-only (Android 14+ refuses writable dex).
                    if (rel.startsWith("tachiyomi/") && rel.endsWith(".ext")) dest.setReadOnly()
                }.isSuccess
                if (ok) files++ else failed++
            }
        }
        if (plan.redownload) BackupMeta.setPendingDownloads(context, plan.tachiyomi)
        return Report(prefs, files, failed)
    }
}

/** Device-only bookkeeping for backups (never itself backed up). */
object BackupMeta {
    const val PREFS = "backup_meta"
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordRestore(c: Context, plan: PendingRestore.Plan, r: PendingRestore.Report) {
        val text = buildString {
            append("Restored ${r.prefs} setting groups and ${r.files} files")
            if (plan.from.isNotBlank()) append(" from Yumeiro ${plan.from}")
            if (r.failed > 0) append(" · ${r.failed} couldn't be written")
            append('.')
            if (plan.redownload) append(" Extensions are downloading again from their sources.")
        }
        p(c).edit().putLong("last_restore_at", System.currentTimeMillis()).putString("last_restore_text", text).putBoolean("show_restore_note", true).commit()
    }

    fun recordReset(c: Context, scopes: Set<String>) {
        p(c).edit().putLong("last_reset_at", System.currentTimeMillis()).putString("last_restore_text", "Reset done: " + scopes.joinToString(", ") { ResetCenter.labelOf(it) } + ".")
            .putBoolean("show_restore_note", true).commit()
    }

    fun recordFailure(c: Context, text: String) { p(c).edit().putString("last_restore_text", text).putBoolean("show_restore_note", true).commit() }

    /** The note shown once after a restart ("Restored 14 setting groups..."), then cleared. */
    fun takeNote(c: Context): String? {
        val pr = p(c)
        if (!pr.getBoolean("show_restore_note", false)) return null
        pr.edit().putBoolean("show_restore_note", false).apply()
        return pr.getString("last_restore_text", null)
    }

    fun lastRestoreText(c: Context): String? = p(c).getString("last_restore_text", null)

    fun setPendingDownloads(c: Context, tachiyomi: String) { p(c).edit().putBoolean("pending_redownload", true).putString("pending_tachiyomi", tachiyomi).commit() }
    fun pendingDownloads(c: Context) = p(c).getBoolean("pending_redownload", false)
    fun pendingTachiyomi(c: Context): String = p(c).getString("pending_tachiyomi", "[]") ?: "[]"
    fun clearPendingDownloads(c: Context) { p(c).edit().putBoolean("pending_redownload", false).apply() }
    fun clearPendingTachiyomi(c: Context) { p(c).edit().remove("pending_tachiyomi").apply() }

    // Auto backup settings live here too: they describe this phone, not your data.
    fun autoEnabled(c: Context) = p(c).getBoolean("auto_enabled", true)
    fun setAutoEnabled(c: Context, on: Boolean) = p(c).edit().putBoolean("auto_enabled", on).apply()
    fun autoEveryDays(c: Context) = p(c).getInt("auto_days", 1).coerceIn(1, 30)
    fun setAutoEveryDays(c: Context, d: Int) = p(c).edit().putInt("auto_days", d.coerceIn(1, 30)).apply()
    fun autoKeep(c: Context) = p(c).getInt("auto_keep", 5).coerceIn(1, 30)
    fun setAutoKeep(c: Context, n: Int) = p(c).edit().putInt("auto_keep", n.coerceIn(1, 30)).apply()
    fun autoIncludeCode(c: Context) = p(c).getBoolean("auto_code", false)
    fun setAutoIncludeCode(c: Context, on: Boolean) = p(c).edit().putBoolean("auto_code", on).apply()
    fun lastAutoAt(c: Context) = p(c).getLong("last_auto_at", 0)
    fun lastAutoFingerprint(c: Context) = p(c).getString("last_auto_fp", null)
    fun recordAuto(c: Context, fp: String) = p(c).edit().putLong("last_auto_at", System.currentTimeMillis()).putString("last_auto_fp", fp).apply()
    fun lastBackupAt(c: Context) = p(c).getLong("last_backup_at", 0)
    fun recordBackup(c: Context) = p(c).edit().putLong("last_backup_at", System.currentTimeMillis()).apply()

    /** A folder the user picked (Storage Access Framework tree), or null for the default. */
    fun folder(c: Context): String? = p(c).getString("folder_uri", null)
    fun setFolder(c: Context, uri: String?) = p(c).edit().putString("folder_uri", uri).apply()
}
