/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.youtube.music.YmPage
import dev.asfakul.yumeiro.youtube.music.YmShelf
import dev.asfakul.yumeiro.youtube.music.YtMusicApi
import dev.asfakul.yumeiro.ui.screens.youtube.ymAccountKey
import dev.asfakul.yumeiro.ui.screens.youtube.ymHomeStale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G13 M2-1: YouTube Music pages are cached for ten minutes. A change of account (sign in, sign
 * out, account mode) must drop them and the visitor id, or a signed-out phone keeps showing the
 * previous account's own Home.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YtAccountMusicCacheTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun seed() = YtMusicApi.putForTests("b:FEmusic_home:null", YmPage(shelves = listOf(YmShelf(title = "Your mix", items = emptyList(), style = YmShelf.Style.LIST))), "visitor-of-account")

    @Test fun signOutDropsTheCachedHome() {
        seed()
        assertEquals(1, YtMusicApi.cachedCount())
        YtAccount.signOut(ctx)
        assertEquals(0, YtMusicApi.cachedCount())
        assertNull(YtMusicApi.visitorForTests())
    }

    @Test fun changingTheAccountModeDropsTheCachedHome() {
        seed()
        YtAccount.setMode(ctx, YtAccount.Mode.ALWAYS)
        assertEquals(0, YtMusicApi.cachedCount())
        assertNull(YtMusicApi.visitorForTests())
        YtAccount.setMode(ctx, YtAccount.Mode.WHEN_BLOCKED)
    }

    @Test fun aHomeLoadedUnderAnotherAccountIsReloaded() {
        val signedIn = ymAccountKey(YtAccount.State(signedIn = true, mode = YtAccount.Mode.ALWAYS))
        val signedOut = ymAccountKey(YtAccount.State(signedIn = false, mode = YtAccount.Mode.ALWAYS))
        val blocked = ymAccountKey(YtAccount.State(signedIn = true, mode = YtAccount.Mode.WHEN_BLOCKED))
        assertTrue(ymHomeStale(signedIn, signedOut))
        assertTrue(ymHomeStale(signedIn, blocked))
        assertFalse(ymHomeStale(signedIn, signedIn))
        assertFalse(ymHomeStale(null, signedOut)) // nothing loaded yet: the normal first load runs
        // "since" (sign-in time) is not part of the key: re-reading the same account keeps the page.
        assertEquals(signedIn, ymAccountKey(YtAccount.State(signedIn = true, since = 42L, mode = YtAccount.Mode.ALWAYS)))
    }
}
