/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g11

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.anime.AniList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * IG-12: AniList implicit-grant tokens can't be refreshed. When AniList rejects the token (401) the account
 * is signed out, so Settings asks to sign in again instead of showing "Signed in" while nothing syncs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AniListExpiredTokenTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { OnlineSettings.resetForTests(); OnlineSettings.init(ctx) }

    @Test fun rejectedTokenSignsOut() {
        OnlineSettings.setSecret(ctx, "anilist_token", "g11-expired-token-0123456789")
        OnlineSettings.setSecret(ctx, "anilist_user", "someone")
        val http = FakeHttp { 401 to """{"errors":[{"message":"Invalid token","status":401}],"data":null}""" }
        runCatching { AniList.watched(http.client, 154587, 2, 12) }
        assertFalse("still signed in after a 401", AniList.signedIn)
        assertNull(OnlineSettings.secret("anilist_user"))
    }
}
