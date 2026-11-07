/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tokens must never be persisted in plaintext (Robolectric has no AndroidKeyStore, so this covers the fail-closed path). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlineSecretsTest {
    @Test
    fun `legacy plaintext tokens are migrated off disk and stay readable`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        OnlineSettings.resetForTests()
        val legacy = ctx.getSharedPreferences("online_settings", Context.MODE_PRIVATE)
        legacy.edit().putString("secret:anilist_token", "tok-123").putString("os_key", "os-abc").commit()

        OnlineSettings.init(ctx)
        OnlineSettings.setSecret(ctx, "mal_token", "mal-xyz")

        assertEquals("tok-123", OnlineSettings.secret("anilist_token"))
        assertEquals("mal-xyz", OnlineSettings.secret("mal_token"))
        assertEquals("os-abc", OnlineSettings.openSubtitlesKey.value)
        assertFalse(legacy.contains("secret:anilist_token"))
        assertFalse(legacy.contains("os_key"))
        val onDisk = ctx.getSharedPreferences("online_secrets", Context.MODE_PRIVATE).all.values.map { it.toString() }
        listOf("tok-123", "mal-xyz", "os-abc").forEach { s -> assertFalse(onDisk.any { it.contains(s) }) }

        OnlineSettings.setSecret(ctx, "mal_token", null)
        assertNull(OnlineSettings.secret("mal_token"))
    }
}
