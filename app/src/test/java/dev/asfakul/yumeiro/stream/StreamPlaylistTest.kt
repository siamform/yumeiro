/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreamPlaylistTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun reset() {
        java.io.File(context.filesDir, "streams").deleteRecursively()
        PlaylistStore.resetForTests()
    }

    @Test fun parsesRealWorldQuirks() {
        // No #EXTM3U header, CRLF endings, BOM, repeated headers, junk lines, options before #EXTINF,
        // commas inside quoted attributes and in titles.
        val text = "\uFEFF#EXTINF:-1 tvg-logo=\"https://x/f_png,w_300/a.png\" group-title=\"BD\",[BD] CNN, Live\r\n" +
            "https://a.example/cnn.m3u8\r\n" +
            "#EXTM3U url-tvg=\"https://epg/x.xml\"\r\n" +
            "#EXTVLCOPT:http-user-agent=Toffee (Linux;Android 14)\r\n" +
            "#EXTINF:-1 group-title=\"BD\",TLC\r\n" +
            "https://a.example/tlc.m3u8\r\n" +
            "404: Not Found\r\n" +
            "#EXTINF:-1,Broken\r\n" +
            "hls\r\n" +
            "#EXTINF:-1,Piped\n" +
            "https://b.example/p.m3u8|Referer=https%3A%2F%2Fsite%2F&X-Token=abc\n" +
            "#EXTINF:-1,Headers\n#EXTHTTP:{\"cookie\":\"k=v\",\"User-Agent\":\"UA1\"}\n" +
            "#KODIPROP:inputstream.adaptive.license_type=clearkey\n#KODIPROP:inputstream.adaptive.license_key=aa:bb\n" +
            "https://c.example/d.mpd\n" +
            "javascript://nope\n"
        val list = M3uParser.parse(text)
        assertEquals(listOf("[BD] CNN, Live", "TLC", "Piped", "Headers"), list.map { it.title })
        assertEquals("https://x/f_png,w_300/a.png", list[0].logoUrl)
        assertEquals("BD", list[0].group)
        assertNull(list[0].userAgent)
        assertEquals("Toffee (Linux;Android 14)", list[1].userAgent)
        assertEquals("https://site/", list[2].referer)
        assertEquals(mapOf("X-Token" to "abc"), list[2].headers)
        assertEquals("k=v", list[3].cookie)
        assertEquals("UA1", list[3].userAgent)
        assertEquals(DrmScheme.CLEARKEY, list[3].drmScheme)
        assertEquals("aa:bb", list[3].clearKeys)
    }

    @Test fun parserNeverThrowsOnGarbage() {
        val garbage = (0 until 2000).joinToString("\n") { i ->
            when (i % 6) { 0 -> "#EXTINF:-1 tvg-name=\"unterminated"; 1 -> "|||"; 2 -> "http://x/%zz|A=%zz"; 3 -> "#EXTHTTP:{bad"; 4 -> "#KODIPROP:"; else -> "\u0000" }
        }
        M3uParser.parse(garbage) // must not throw
    }

    @Test fun exportRoundTripKeepsEverything() {
        val items = listOf(
            StreamRequest(url = "https://a/1.m3u8", title = "One, \"quoted\"", logoUrl = "https://l/1.png", group = "News",
                userAgent = "UA", referer = "https://r/", origin = "https://o", cookie = "c=1", headers = mapOf("X-A" to "b")),
            StreamRequest(url = "https://a/2.mpd", title = "Two", format = StreamFormat.DASH, drmScheme = DrmScheme.WIDEVINE, drmLicenseUrl = "https://lic/"),
            StreamRequest(url = "rtsp://cam/3", title = "Cam"),
        )
        val back = M3uParser.parse(M3uWriter.write(items, "Mine"))
        assertEquals(3, back.size)
        assertEquals("One, 'quoted'", back[0].title.replace('"', '\''))
        assertEquals(items[0].copy(title = back[0].title), back[0])
        assertEquals(items[1], back[1])
        assertEquals(items[2], back[2])
    }

    @Test fun onlyMrgifyBdixIsSeededAndCanBeDisabledDeletedAndRestored() = runBlocking {
        PlaylistStore.init(context)
        val all = PlaylistStore.playlists.value
        assertEquals(listOf("mrgify_bdix"), all.map { it.id })
        assertTrue(all.single().isDefault && all.single().enabled && all.single().fallbackUrl!!.startsWith("https://raw.githubusercontent.com/abusaeeidx/"))

        PlaylistStore.setEnabled(context, "mrgify_bdix", false)
        PlaylistStore.resetForTests(); PlaylistStore.init(context) // reload from disk
        assertFalse(PlaylistStore.get("mrgify_bdix")!!.enabled)
        PlaylistStore.delete(context, "mrgify_bdix")
        PlaylistStore.resetForTests(); PlaylistStore.init(context)
        assertTrue(PlaylistStore.playlists.value.isEmpty())

        assertEquals(1, PlaylistStore.restoreDefaults(context))
        assertEquals(listOf("mrgify_bdix"), PlaylistStore.playlists.value.map { it.id })
        assertTrue(PlaylistStore.playlists.value.all { it.enabled })
    }

    @Test fun upgradeRemovesFormerDefaultsButKeepsEditedOnesAndUserLists() = runBlocking {
        val dir = java.io.File(context.filesDir, "streams").apply { mkdirs() }
        val old = org.json.JSONArray()
        for (id in DefaultPlaylists.retired + "mrgify_bdix") {
            old.put(org.json.JSONObject().put("id", id).put("name", id).put("url", "https://m/$id.m3u").put("default", true).put("edited", id == "yupptv"))
            java.io.File(dir, "ch_$id.m3u").writeText("#EXTM3U\n#EXTINF:-1,A\nhttps://a/1.m3u8\n")
        }
        old.put(org.json.JSONObject().put("id", "l_mine").put("name", "Mine").put("default", false))
        java.io.File(dir, "playlists.json").writeText(org.json.JSONObject().put("version", 1).put("playlists", old)
            .put("deletedDefaults", org.json.JSONArray(listOf("crichd"))).toString())

        PlaylistStore.init(context)
        val ids = PlaylistStore.playlists.value.map { it.id }.toSet()
        assertEquals(setOf("mrgify_bdix", "yupptv", "l_mine"), ids)
        assertFalse("edited former default becomes a normal playlist", PlaylistStore.get("yupptv")!!.isDefault)
        assertFalse(java.io.File(dir, "ch_zilla_combined.m3u").exists())
        assertTrue(java.io.File(dir, "ch_yupptv.m3u").exists())
        // Migration is persisted (runs once).
        PlaylistStore.resetForTests(); PlaylistStore.init(context)
        assertEquals(ids, PlaylistStore.playlists.value.map { it.id }.toSet())
        assertEquals(2, org.json.JSONObject(java.io.File(dir, "playlists.json").readText()).getInt("version"))
    }

    @Test fun marketFieldsSurviveJsonRoundTrip() {
        val p = StreamPlaylist(id = "m_x", name = "X", url = "https://m/x.m3u", marketId = "x", marketSha = "abc", autoUpdate = false, repo = "o/r")
        assertEquals(p, StreamPlaylist.fromJson(p.toJson()))
        // Old JSON without the new keys: auto-update defaults to on.
        assertTrue(StreamPlaylist.fromJson(org.json.JSONObject().put("id", "a").put("name", "A")).autoUpdate)
    }

    @Test fun localPlaylistCrudAndPersistence() = runBlocking {
        PlaylistStore.init(context)
        val p = PlaylistStore.createLocal(context, "Favourites")
        PlaylistStore.addChannels(context, p.id, listOf(StreamRequest("https://a/1.m3u8", "One"), StreamRequest("https://a/2.m3u8", "Two")))
        PlaylistStore.addChannels(context, p.id, listOf(StreamRequest("https://a/1.m3u8", "One"))) // duplicate ignored
        PlaylistStore.updateChannel(context, p.id, 1, StreamRequest("https://a/2b.m3u8", "Two B", group = "G"))
        PlaylistStore.update(context, p.id, "Faves", null)
        PlaylistStore.resetForTests(); PlaylistStore.init(context)
        val chans = PlaylistStore.channels(context, p.id)
        assertEquals(listOf("One", "Two B"), chans.map { it.title })
        assertEquals("G", chans[1].group)
        assertEquals("Faves", PlaylistStore.get(p.id)!!.name)
        assertEquals(2, PlaylistStore.get(p.id)!!.channelCount)
        PlaylistStore.removeChannel(context, p.id, 0)
        assertEquals(listOf("Two B"), PlaylistStore.channels(context, p.id).map { it.title })
        PlaylistStore.delete(context, p.id)
        assertNull(PlaylistStore.get(p.id))
        assertEquals(1, PlaylistStore.playlists.value.size) // deleting a user playlist doesn't touch the default
    }
}
