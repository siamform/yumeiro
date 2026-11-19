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
import dev.asfakul.yumeiro.online.anime.MyAnimeList
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * IG-11: "never lowers progress" needs a successful read. When the progress read fails (429/5xx) nothing
 * may be written; before, the progress was taken as 0 and a rewatch of episode 3 set 12/12 back to 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackerProgressReadTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val tok = "g11-test-token-0123456789"

    @Before fun setUp() { OnlineSettings.resetForTests(); OnlineSettings.init(ctx) }

    @Test fun aniListWritesNothingAfterAFailedRead() {
        OnlineSettings.setSecret(ctx, "anilist_token", tok)
        val http = FakeHttp { s -> if (s.body.contains("mutation")) """{"data":{"SaveMediaListEntry":{"id":1}}}""" else 429 }
        runCatching { AniList.watched(http.client, 154587, 3, 12) }
        assertEquals("mutations sent after a failed read", 0, http.seen.count { it.body.contains("mutation") })
    }

    @Test fun myAnimeListWritesNothingAfterAFailedRead() {
        OnlineSettings.setSecret(ctx, "mal_token", tok)
        val http = FakeHttp { s -> if (s.method == "PATCH") """{"num_episodes_watched":3}""" else 500 }
        runCatching { MyAnimeList.watched(ctx, http.client, 52991, 3, 12) }
        assertEquals("PATCH sent after a failed read", 0, http.seen.count { it.method == "PATCH" })
    }
}
