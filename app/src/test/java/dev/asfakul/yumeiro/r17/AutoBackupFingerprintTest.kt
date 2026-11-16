/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r17

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.backup.AutoBackupWorker
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.backup.BackupMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G9 S25 re-check of the automatic backup (AutoBackupWorker): it skips a run only when nothing the
 * backup carries changed, its own bookkeeping never counts as a change, and extension code is left
 * out unless the user asked for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupFingerprintTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun p(n: String) = ctx.getSharedPreferences(n, Context.MODE_PRIVATE)
    private fun fp() = AutoBackupWorker.fingerprint(ctx, AutoBackupWorker.sections(ctx))

    @Test fun `unchanged data gives the same fingerprint`() {
        p("mt_player_music_prefs").edit().putString("playlists", "[\"a\"]").commit()
        assertEquals(fp(), fp())
    }

    @Test fun `a library change is noticed`() {
        p("mt_player_music_prefs").edit().putString("playlists", "[\"a\"]").commit()
        val before = fp()
        Thread.sleep(1_100) // file times have 1 s resolution on some file systems
        p("mt_player_music_prefs").edit().putString("playlists", "[\"a\",\"b\"]").commit()
        assertNotEquals(before, fp())
    }

    @Test fun `the backup's own bookkeeping and device-only prefs do not count`() {
        p("mt_player_music_prefs").edit().putString("playlists", "[\"a\"]").commit()
        val before = fp()
        Thread.sleep(1_100)
        BackupMeta.recordAuto(ctx, "x")
        p(BackupMeta.PREFS).edit().putLong("last_auto_at", 42L).commit()
        p("online_secrets").edit().putString("k", "v").commit()
        assertEquals(before, fp())
    }

    @Test fun `extension code only when asked`() {
        p(BackupMeta.PREFS).edit().putBoolean("auto_code", false).commit()
        assertFalse(Backup.Section.EXTENSION_FILES in AutoBackupWorker.sections(ctx))
        p(BackupMeta.PREFS).edit().putBoolean("auto_code", true).commit()
        assertTrue(Backup.Section.EXTENSION_FILES in AutoBackupWorker.sections(ctx))
        assertTrue(Backup.Section.LIBRARY in AutoBackupWorker.sections(ctx))
        p(BackupMeta.PREFS).edit().remove("auto_code").commit()
    }
}
