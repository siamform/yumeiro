/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Automatic backups to the phone's backup folder (on by default, daily). Skips the run when
 * nothing changed since the last one and keeps only the newest few, so it never fills storage.
 * Runs when the battery and storage aren't low; no network needed.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (!BackupMeta.autoEnabled(c)) return Result.success()
        return try {
            val sections = sections(c)
            val fp = fingerprint(c, sections)
            if (fp == BackupMeta.lastAutoFingerprint(c)) return Result.success()
            BackupStore.save(c, "auto", sections)
            BackupMeta.recordAuto(c, fp)
            BackupStore.prune(c, "auto", BackupMeta.autoKeep(c))
            Result.success()
        } catch (t: Throwable) {
            Log.w("YuBackup", "auto backup", t)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NAME = "yumeiro_auto_backup"

        fun sections(c: Context): Set<Backup.Section> = Backup.Section.entries.toSet() -
            if (BackupMeta.autoIncludeCode(c)) emptySet() else setOf(Backup.Section.EXTENSION_FILES)

        /** Changes when any backed-up file or setting changes (names, sizes, times). */
        internal fun fingerprint(c: Context, sections: Set<Backup.Section>): String {
            val md = MessageDigest.getInstance("SHA-256")
            Backup.collect(c, sections).forEach { (n, f) -> md.update("$n:${f.length()}:${f.lastModified()}\n".toByteArray()) }
            Backup.prefsDir(c).listFiles()?.filter { it.extension == "xml" && Backup.prefIncluded(it.nameWithoutExtension) }?.sortedBy { it.name }
                ?.forEach { md.update("${it.name}:${it.length()}:${it.lastModified()}\n".toByteArray()) }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        fun schedule(c: Context, replace: Boolean = false) {
            val wm = runCatching { WorkManager.getInstance(c) }.getOrNull() ?: return
            if (!BackupMeta.autoEnabled(c)) { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(BackupMeta.autoEveryDays(c).toLong(), TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                .setInitialDelay(2, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
