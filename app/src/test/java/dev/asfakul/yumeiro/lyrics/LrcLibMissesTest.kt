/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.lyrics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LrcLibMissesTest {
    @Test fun expiredNoLyricsMarksAreDropped() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        LrcLib.init(ctx)
        val now = 100L * 86_400_000
        val p = ctx.getSharedPreferences("lyrics_online", Context.MODE_PRIVATE)
        p.edit().clear().apply {
            repeat(310) { putLong("miss:old$it", now - 8L * 86_400_000) }
            repeat(5) { putLong("miss:new$it", now - 86_400_000) }
            putBoolean("enabled", true)
        }.commit()
        LrcLib.pruneMisses(now)
        val left = p.all.keys
        assertEquals(6, left.size) // 5 fresh marks + the setting
        assertEquals(5, left.count { it.startsWith("miss:new") })
    }
}
