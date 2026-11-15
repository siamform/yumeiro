package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.backup.PendingRestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipInputStream

/**
 * R16 (G9): backup → change → restore on a real device, through the same export / stage / apply
 * steps the Settings screen uses (apply normally runs at the next start; here it runs in place).
 * Also checks that device-bound secrets (online_secrets) never go into the backup file.
 */
@RunWith(AndroidJUnit4::class)
class BackupDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Test fun settingsRoundTripAndSecretsStayOut() = runBlocking<Unit> {
        val probe = ctx.getSharedPreferences("yu_backup_probe", Context.MODE_PRIVATE)
        val secrets = ctx.getSharedPreferences("online_secrets", Context.MODE_PRIVATE)
        val marker = "yu-secret-marker-" + System.nanoTime()
        probe.edit().putString("k", "before").putInt("n", 7).commit()
        secrets.edit().putString("yu_probe", marker).commit()
        val file = File(ctx.cacheDir, "yu-backup-test.zip")
        try {
            file.outputStream().use { Backup.export(ctx, it, setOf(Backup.Section.SETTINGS), kind = "test") }
            var leaked = false; var entries = 0
            ZipInputStream(file.inputStream()).use { z ->
                generateSequence { z.nextEntry }.forEach { _ -> entries++; if (marker in z.readBytes().decodeToString()) leaked = true }
            }
            probe.edit().putString("k", "after").remove("n").commit()
            val staged = Backup.stage(ctx, { file.inputStream() }, setOf(Backup.Section.SETTINGS))
            val applied = PendingRestore.applyIfAny(ctx)
            val line = "BK_A entries=$entries leaked=$leaked staged=$staged applied=$applied k=${probe.getString("k", null)} n=${probe.getInt("n", -1)}"
            Log.i("YuBackupTest", line)
            assertFalse(line, leaked)
            assertTrue(line, staged > 0 && applied)
            assertEquals(line, "before", probe.getString("k", null))
            assertEquals(line, 7, probe.getInt("n", -1))
            assertFalse(line, PendingRestore.hasPending(ctx))
            Log.i("YuBackupTest", "BK_A_RESULT PASS")
        } finally {
            file.delete()
            probe.edit().clear().commit()
            secrets.edit().remove("yu_probe").commit()
        }
    }
}
