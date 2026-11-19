/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g11

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.EpisodeStills
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * IG-13: a 429/5xx from ani.zip or TVmaze is a failed lookup (IG-5: the next visit tries again), not
 * "nothing found". Before, the title stayed marked as tried until the app restarted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EpisodeStillsRateLimitTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val field = NetworkClient::class.java.getDeclaredField("client").apply { isAccessible = true }

    @After fun tearDown() { field.set(NetworkClient, null) }

    private fun ep(n: Int) = OnlineEpisode(data = "e$n", name = null, season = 1, episode = n, posterUrl = null, description = null, runtimeMin = null)

    private fun waitFor(what: () -> Boolean) { val until = System.currentTimeMillis() + 5_000; while (!what() && System.currentTimeMillis() < until) Thread.sleep(20) }

    @Test fun rateLimitedLookupIsTriedAgain() {
        val http = FakeHttp { 429 }
        NetworkClient.get(ctx)
        field.set(NetworkClient, http.client as OkHttpClient)
        val d = OnlineDetail(
            api = "g11", url = "ig13-a", name = "Frieren", kind = OnlineKind.ANIME, type = "Anime", posterUrl = null, backgroundUrl = null,
            posterHeaders = emptyMap(), plot = null, year = 2023, tags = emptyList(), durationMin = null, score = null, contentRating = null,
            playData = null, episodes = mapOf("" to listOf(ep(1), ep(2), ep(3))), seasonNames = emptyMap(), recommendations = emptyList(),
            aniListId = 154587,
        )
        EpisodeStills.ensure(ctx, d)
        waitFor { http.seen.size >= 1 }
        Thread.sleep(300) // let the first lookup finish and settle its mark
        EpisodeStills.ensure(ctx, d) // the user opens the title again
        waitFor { http.seen.size >= 2 }
        assertEquals("ani.zip asked again after a 429", 2, http.seen.count { it.url.contains("ani.zip") })
    }
}
