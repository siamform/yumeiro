/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g11

import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.anime.AnimeIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/**
 * IG-9: a failed anime id lookup (AniList 429/5xx, offline) must throw, so AnimeSync's
 * "id lookup failed: retry this tracker later" branch runs. Only "no such anime" is null.
 */
class AnimeIdsLookupFailureTest {
    private fun detail(url: String, aniListId: Int? = null) = OnlineDetail(
        api = "g11", url = url, name = "Frieren", kind = OnlineKind.ANIME, type = "Anime", posterUrl = null, backgroundUrl = null,
        posterHeaders = emptyMap(), plot = null, year = 2023, tags = emptyList(), durationMin = null, score = null, contentRating = null,
        playData = null, episodes = mapOf("" to listOf(OnlineEpisode(data = "e1", name = null, season = 1, episode = 1, posterUrl = null, description = null, runtimeMin = null))),
        seasonNames = emptyMap(), recommendations = emptyList(), aniListId = aniListId,
    )

    @Test fun rateLimitedSearchThrows() {
        val notFound = FakeHttp { """{"data":{"Page":{"media":[]}}}""" }
        assertNull("no such anime is still null", AnimeIds.of(notFound.client, detail("ig9-a0")))
        val limited = FakeHttp { 429 }
        assertThrows(Exception::class.java) { AnimeIds.of(limited.client, detail("ig9-a1")) }
    }

    @Test fun offlineSearchThrows() {
        val offline = FakeHttp { IOException("offline") }
        assertThrows(Exception::class.java) { AnimeIds.of(offline.client, detail("ig9-b1")) }
    }

    @Test fun failedMalLookupForKnownAniListIdThrows() {
        // The extension gave the AniList id only; MAL and Kitsu need idMal from AniList.
        val down = FakeHttp { 503 }
        assertThrows(Exception::class.java) { AnimeIds.of(down.client, detail("ig9-c1", aniListId = 154587)) }
        val ok = FakeHttp { """{"data":{"Media":{"id":154587,"idMal":52991,"episodes":28}}}""" }
        assertEquals(52991, AnimeIds.of(ok.client, detail("ig9-c2", aniListId = 154587))?.mal)
    }
}
