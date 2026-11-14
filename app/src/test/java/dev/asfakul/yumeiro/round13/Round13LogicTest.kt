package dev.asfakul.yumeiro.round13

import androidx.media3.common.Format
import dev.asfakul.yumeiro.DeviceClass
import dev.asfakul.yumeiro.online.AudioLanguages
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.player.Anime4kEffect
import dev.asfakul.yumeiro.player.QualityEngine
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.ui.components.HideOnScroll
import dev.asfakul.yumeiro.ui.screens.music.MusicMode
import dev.asfakul.yumeiro.ui.screens.online.filterByTitle
import dev.asfakul.yumeiro.ui.screens.video.codecName
import dev.asfakul.yumeiro.ui.screens.video.shortDecoder
import dev.asfakul.yumeiro.ui.screens.watch.WatchKind
import dev.asfakul.yumeiro.youtube.YtItem
import dev.asfakul.yumeiro.youtube.music.YmEndless
import dev.asfakul.yumeiro.youtube.music.YmItem
import dev.asfakul.yumeiro.youtube.music.YmRef
import dev.asfakul.yumeiro.youtube.music.YmShelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 13 (1.8.0): pure logic behind the player, sources, Yumix, Music and performance fixes. */
class Round13LogicTest {

    // ---- language pick only changes the audio track ------------------------------------------
    @Test fun audio_track_for_key() {
        val langs = listOf("en", "ja", null)
        assertEquals(1, AudioLanguages.trackFor(AudioLanguages.ORIG, langs))
        assertEquals(0, AudioLanguages.trackFor(AudioLanguages.DUB_OTHER, langs))
        assertEquals(0, AudioLanguages.trackFor("en", langs))
        assertEquals(-1, AudioLanguages.trackFor("fr", langs))
        assertEquals(-1, AudioLanguages.trackFor("", langs))
    }

    // ---- default quality pushes higher links back, never drops them --------------------------
    @Test fun sort_links_respects_default_quality() {
        fun l(name: String, q: Int) = OnlineLink(name, "s", "https://x/$name.m3u8", null, q, true, false, emptyMap())
        val all = listOf(l("a1080", 1080), l("b720", 720), l("c480", 480))
        assertEquals("a1080", OnlineRepository.sortLinks(all).first().name)
        val capped = OnlineRepository.sortLinks(all, maxHeight = 720)
        assertEquals("b720", capped.first().name)
        assertEquals(3, capped.size)
        assertEquals("a1080", capped.last().name)
    }

    // ---- quality engine ladder ---------------------------------------------------------------
    @Test fun quality_ladder_and_snap() {
        assertEquals(1080, QualityEngine.snap(1072))
        assertEquals(720, QualityEngine.snap(718))
        assertEquals(1000, QualityEngine.snap(1000))
        assertEquals(listOf(1080, 720, 360), QualityEngine.ladder(listOf(360, 1072, 720, 718, 50, 9000)))
    }

    // ---- player controls order ---------------------------------------------------------------
    @Test fun controls_order_and_move() {
        val all = VideoPrefs.Control.entries
        assertEquals(all, VideoPrefs.orderFrom(null))
        val saved = VideoPrefs.orderFrom(all.reversed().take(2).joinToString(",") { it.name } + ",Bogus")
        assertEquals(all.size, saved.size)
        assertEquals(all.last(), saved.first())
        val moved = VideoPrefs.move(all, all[1], -1)
        assertEquals(all[1], moved[0])
        assertEquals(all, VideoPrefs.move(all, all[0], -1))
    }

    @Test fun anime4k_tiers_grow() {
        val low = Anime4kEffect.weights(VideoPrefs.GpuTier.LOW)
        val mid = Anime4kEffect.weights(VideoPrefs.GpuTier.MID)
        val high = Anime4kEffect.weights(VideoPrefs.GpuTier.HIGH)
        assertTrue(low.sharpen < mid.sharpen && mid.sharpen < high.sharpen)
        assertTrue(high.diagonals && !mid.diagonals)
    }

    // ---- skeleton by type ----------------------------------------------------------------------
    @Test fun watch_kind_for_opening() {
        assertEquals(WatchKind.DIRECT, WatchKind.forOpening("Movie", "https://cdn/x/video.m3u8?t=1", null))
        assertEquals(WatchKind.DIRECT, WatchKind.forOpening("Live", "https://site/channel", null))
        assertEquals(WatchKind.MOVIE, WatchKind.forOpening("Movie", "https://site/movie/1", null))
        assertEquals(WatchKind.PAGE, WatchKind.forOpening("Movie", "https://site/movie/1", true))
        assertEquals(WatchKind.SERIES, WatchKind.forOpening("TvSeries", "https://site/show/1", true))
        assertEquals(WatchKind.DIRECT, WatchKind.forOpening(null, "https://site/x", false))
    }

    // ---- player info overlay text --------------------------------------------------------------
    @Test fun codec_and_decoder_names() {
        assertEquals("H.264 (avc1.64001F)", codecName(Format.Builder().setSampleMimeType("video/avc").setCodecs("avc1.64001F").build()))
        assertEquals("E-AC-3", codecName(Format.Builder().setSampleMimeType("audio/eac3").build()))
        assertEquals("c2.android.avc (software)", shortDecoder("c2.android.avc.decoder"))
        assertEquals("c2.qti.avc (hardware)", shortDecoder("c2.qti.avc.decoder"))
    }

    // ---- Yumix section search ------------------------------------------------------------------
    @Test fun section_search_matches_every_word_accent_blind() {
        val titles = listOf("One Piece", "Pokémon Horizons", "Piece of Cake", "Naruto")
        assertEquals(listOf("Pokémon Horizons"), filterByTitle(titles, "pokemon") { it })
        assertEquals(listOf("Piece of Cake", "One Piece"), filterByTitle(titles, "piece") { it })
        assertEquals(listOf("One Piece"), filterByTitle(titles, "one piece") { it })
        assertEquals(titles, filterByTitle(titles, "  ") { it })
    }

    // ---- hide on scroll ------------------------------------------------------------------------
    @Test fun extension_bar_hides_down_and_returns_on_small_scroll_up() {
        val h = HideOnScroll(hideAfterPx = 48f, showAfterPx = 12f)
        h.onDelta(-30f); assertTrue(h.visible)
        h.onDelta(-30f); assertFalse(h.visible)
        h.onDelta(8f); assertFalse(h.visible)
        h.onDelta(8f); assertTrue(h.visible)
    }

    // ---- music mode follows the connection -----------------------------------------------------
    @Test fun music_mode_follows_connection() {
        assertEquals(MusicMode.ONLINE, MusicMode.defaultFor(true))
        assertEquals(MusicMode.DEVICE, MusicMode.defaultFor(false))
        assertEquals(MusicMode.ONLINE, MusicMode.follow(MusicMode.DEVICE, online = true, picked = false))
        assertEquals(MusicMode.DEVICE, MusicMode.follow(MusicMode.ONLINE, online = false, picked = true))
        assertEquals(MusicMode.DEVICE, MusicMode.follow(MusicMode.DEVICE, online = true, picked = true))
        assertEquals(MusicMode.MIX, MusicMode.follow(MusicMode.MIX, online = false, picked = true))
    }

    // ---- endless music feed --------------------------------------------------------------------
    @Test fun endless_feed_seeds_vary_by_artist_and_skip_shown_songs() {
        fun yt(id: String, artist: String) = YtItem(id = id, url = "https://youtu.be/$id", title = "T$id", channel = "$artist - Topic", music = true)
        val history = listOf(yt("aaaaaaaaaa1", "A"), yt("aaaaaaaaaa2", "A"), yt("bbbbbbbbbb1", "B"))
        var used = emptySet<String>()
        val s1 = YmEndless.nextSeed(history, emptyList(), used)!!
        assertEquals("aaaaaaaaaa1", s1.id)
        used = YmEndless.use(used, s1)
        val s2 = YmEndless.nextSeed(history, emptyList(), used)!!
        assertEquals("bbbbbbbbbb1", s2.id) // a different artist before the same one again
        val shelfSongs = listOf(YmItem.Song("ccccccccccc", "Shown", listOf(YmRef("C"))))
        val shown = YmEndless.shownIds(listOf(YmShelf("Quick picks", null, shelfSongs, YmShelf.Style.LIST)))
        val related = listOf(yt("ccccccccccc", "C"), yt("ddddddddddd", "D"), yt("eeeeeeeeeee", "E"), yt("fffffffffff", "F"), yt(s1.id, "A"))
        val shelf = YmEndless.shelfFor(s1, related, shown)!!
        assertEquals("Similar to ${s1.title}", shelf.title)
        assertEquals(listOf("ddddddddddd", "eeeeeeeeeee", "fffffffffff"), shelf.items.map { (it as YmItem.Song).videoId })
        assertNull(YmEndless.shelfFor(s1, related.take(2), shown))
    }

    // ---- notifications are paced ---------------------------------------------------------------
    @Test fun notify_pacing_rules() {
        val pacer = dev.asfakul.yumeiro.notify.NotifyPacer
        assertTrue(pacer.canShowNow(1_000_000L, 0L))
        assertFalse(pacer.canShowNow(1_000_000L, 1_000_000L - 60_000L))
        assertTrue(pacer.canShowNow(1_000_000L + pacer.GAP_MS, 1_000_000L))
        assertEquals(1_000_000L + pacer.GAP_MS, pacer.nextAt(1_000_000L))
    }

    @Test fun low_end_devices() {
        assertTrue(DeviceClass.isLowEnd(true, 512, 8_000_000_000L, 8))
        assertTrue(DeviceClass.isLowEnd(false, 128, 8_000_000_000L, 8))
        assertTrue(DeviceClass.isLowEnd(false, 512, 2_000_000_000L, 8))
        assertFalse(DeviceClass.isLowEnd(false, 512, 8_000_000_000L, 8))
    }
}
