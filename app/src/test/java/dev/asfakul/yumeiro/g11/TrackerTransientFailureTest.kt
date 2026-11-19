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
import dev.asfakul.yumeiro.tracking.Kitsu
import dev.asfakul.yumeiro.tracking.Simkl
import dev.asfakul.yumeiro.tracking.Trakt
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * IG-10: a 429/5xx (or a network error) on a tracker write is a failure, not "done": the call throws so
 * AnimeSync gives the tracker its key back and retries it (IG-2). Before, every tracker returned false.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackerTransientFailureTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val tok = "g11-test-token-0123456789"

    @Before fun setUp() { OnlineSettings.resetForTests(); OnlineSettings.init(ctx) }

    @Test fun aniListMutation503Throws() {
        OnlineSettings.setSecret(ctx, "anilist_token", tok)
        val http = FakeHttp { s ->
            if (s.body.contains("mutation")) 503
            else """{"data":{"Media":{"episodes":12,"mediaListEntry":{"progress":1,"status":"CURRENT"}}}}"""
        }
        assertThrows(Exception::class.java) { AniList.watched(http.client, 154587, 2, 12) }
    }

    @Test fun myAnimeListPatch503AndOfflineThrow() {
        OnlineSettings.setSecret(ctx, "mal_token", tok)
        val read = """{"num_episodes":12,"my_list_status":{"num_episodes_watched":1}}"""
        val down = FakeHttp { s -> if (s.method == "PATCH") 503 else read }
        assertThrows(Exception::class.java) { MyAnimeList.watched(ctx, down.client, 52991, 2, 12) }
        val offline = FakeHttp { s -> if (s.method == "PATCH") IOException("offline") else read }
        assertThrows(Exception::class.java) { MyAnimeList.watched(ctx, offline.client, 52991, 2, 12) }
    }

    @Test fun kitsuWrite429Throws() {
        OnlineSettings.setSecret(ctx, "kitsu_token", tok)
        OnlineSettings.setSecret(ctx, "kitsu_uid", "9")
        val http = FakeHttp { s ->
            when {
                s.method == "POST" -> 429
                s.url.contains("mappings") -> """{"included":[{"id":"42"}]}"""
                else -> """{"data":[]}"""
            }
        }
        assertThrows(Exception::class.java) { Kitsu.watched(ctx, http.client, 52991, 2, 12) }
    }

    @Test fun traktSearchAndHistory5xxThrow() {
        OnlineSettings.setSecret(ctx, "trakt_token", tok)
        val searchDown = FakeHttp { 502 }
        assertThrows(Exception::class.java) { Trakt.watched(ctx, searchDown.client, "Frieren", 2023, 1, 2) }
        val historyDown = FakeHttp { s ->
            if (s.url.contains("/search/")) """[{"show":{"title":"Frieren","ids":{"trakt":7}}}]""" else 503
        }
        assertThrows(Exception::class.java) { Trakt.watched(ctx, historyDown.client, "Frieren", 2023, 1, 2) }
    }

    @Test fun simklHistory503Throws() {
        OnlineSettings.setSecret(ctx, "simkl_token", tok)
        val http = FakeHttp { 503 }
        assertThrows(Exception::class.java) { Simkl.watched(ctx, http.client, Simkl.Match("Frieren", 2023, mal = 52991), 1, 2) }
    }
}
