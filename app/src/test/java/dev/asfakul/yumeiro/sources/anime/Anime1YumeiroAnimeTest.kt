/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.anime

import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.OnlinePlayback
import dev.asfakul.yumeiro.youtube.YouTube
import dev.asfakul.yumeiro.youtube.YtItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ANIME-1 (item 20): the legal free-anime part of the built-in "Yumeiro" source. Playlist titles are the real ones
 * read from the channels' Playlists tabs on 2026-11-24 (Muse Asia, Ani-One Asia, GUNDAM CHANNEL, RetroCrush).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Anime1YumeiroAnimeTest {
    private val gundam = "UCejtUitnpnf8Be-v5NuDSLw"
    private val cards = listOf(
        YouTube.YtPlaylistCard("https://www.youtube.com/playlist?list=PLJV1h9xQ4Hx-SEEDDESTINYEN", "【EN Dub ver】Mobile Suit Gundam SEED DESTINY", "https://i.ytimg.com/a.jpg", 50, "GUNDAM CHANNEL INTL"),
        YouTube.YtPlaylistCard("https://www.youtube.com/playlist?list=PLJV1h9xQ4Hx-YZAKGP2026xx", "【Yzak Jule】SEEDGP2026", null, 6, "GUNDAM CHANNEL INTL"),
        YouTube.YtPlaylistCard("https://www.youtube.com/playlist?list=PLJV1h9xQ4Hx-ONEVIDEOxxxx", "Single PV", null, 1, "GUNDAM CHANNEL INTL"),
    )
    private fun v(id: String, title: String, sec: Long, live: Boolean = false) =
        YtItem(id = id, url = "https://www.youtube.com/watch?v=$id", title = title, channel = "GUNDAM CHANNEL INTL", durationSec = sec, live = live)
    private val videos = listOf(
        v("aaaaaaaaaa1", "Mobile Suit Gundam SEED DESTINY Episode 1", 24 * 60),
        v("aaaaaaaaaa2", "SEED DESTINY Trailer", 90),
        v("aaaaaaaaaa3", "Mobile Suit Gundam SEED DESTINY Episode 2", 24 * 60),
        v("aaaaaaaaaa4", "Watch party", 3 * 60 * 60, live = true),
    )
    private val asked = mutableListOf<String>()
    private val api = YumeiroAnimeProvider(
        playlists = { url -> asked += url; if (gundam in url) cards else emptyList() },
        episodes = { url -> asked += url; "Mobile Suit Gundam SEED DESTINY" to videos },
    )

    @Test fun onByDefaultAndOnlyOfficialChannels() {
        assertTrue(FeatureFlags.Flag.OFFICIAL_ANIME.default)
        assertEquals(6, YumeiroAnimeProvider.CHANNELS.size)
        assertTrue(YumeiroAnimeProvider.CHANNELS.all { it.id.startsWith("UC") && it.id.length == 24 })
    }

    @Test fun shelfShowsSeriesNotClipsOrSingleVideos() = runBlocking {
        val home = api.getMainPage(1, MainPageRequest("Gundam", gundam, false))
        val list = home.items.first().list
        assertEquals(listOf("Mobile Suit Gundam SEED DESTINY"), list.map { it.name })
        assertEquals("anime:pl:PLJV1h9xQ4Hx-SEEDDESTINYEN", list.first().url)
        assertTrue(asked.first().endsWith("/channel/$gundam"))
    }

    @Test fun seriesHasOnlyFullEpisodesInOrder() = runBlocking {
        api.getMainPage(1, MainPageRequest("Gundam", gundam, false))
        val load = api.load("anime:pl:PLJV1h9xQ4Hx-SEEDDESTINYEN") as TvSeriesLoadResponse
        assertEquals("Mobile Suit Gundam SEED DESTINY", load.name)
        assertEquals(listOf("anime:v:aaaaaaaaaa1", "anime:v:aaaaaaaaaa3"), load.episodes.map { it.data })
        assertEquals(listOf(1, 2), load.episodes.map { it.episode })
        assertEquals("https://www.youtube.com/playlist?list=PLJV1h9xQ4Hx-SEEDDESTINYEN", asked.last())
    }

    @Test fun episodePlaysAsTheYouTubeVideo() = runBlocking {
        val links = mutableListOf<ExtractorLink>()
        assertTrue(api.loadLinks("https://www.youtube.com/anime:v:aaaaaaaaaa1", false, {}, { links += it }))
        assertEquals("https://www.youtube.com/watch?v=aaaaaaaaaa1", links.single().url)
        assertFalse(api.loadLinks("anime:v:bad", false, {}, { links += it }))
    }

    @Test fun searchMatchesSeriesTitles() = runBlocking {
        assertEquals(listOf("Mobile Suit Gundam SEED DESTINY"), api.search("seed destiny", 1).items.map { it.name })
        assertTrue(api.search("one piece", 1).items.isEmpty())
    }

    @Test fun titleAndFilterRules() {
        assertEquals("AOASHI", YumeiroAnimeProvider.cleanTitle("AOASHI｜青之蘆葦【Ani-One Asia】"))
        assertEquals("[2026 Oct] Firefly Wedding [English Sub]", YumeiroAnimeProvider.cleanTitle("[2026 Oct] Firefly Wedding [English Sub]"))
        assertFalse(YumeiroAnimeProvider.looksLikeSeries("RetroCrush Chat", 20))
        assertFalse(YumeiroAnimeProvider.looksLikeSeries("Sonic Boom | Best Moments", 20))
        assertFalse(YumeiroAnimeProvider.looksLikeSeries("The RetroCrush Podcast", 20))
        assertTrue(YumeiroAnimeProvider.looksLikeSeries("Future Boy Conan", 26))
        assertTrue(YumeiroAnimeProvider.looksLikeSeries("Ranma 1/2", -1))
        assertNull(YumeiroAnimeProvider.playlistOf("drama:123"))
        assertNull(YumeiroAnimeProvider.videoOf("anime:v:../../x"))
    }

    @Test fun youTubePagesAreResolvedNotPlayedAsFiles() {
        assertTrue(OnlinePlayback.isYouTubePage("https://www.youtube.com/watch?v=aaaaaaaaaa1"))
        assertFalse(OnlinePlayback.isYouTubePage("https://rr1---sn-x.googlevideo.com/videoplayback?id=1"))
    }
}
