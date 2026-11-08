/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.sources.js.JsSources
import dev.asfakul.yumeiro.sources.js.JsUnpacker
import dev.asfakul.yumeiro.sources.servers.DlnaTree
import dev.asfakul.yumeiro.sources.servers.MediaTree
import dev.asfakul.yumeiro.sources.servers.WebDavTree
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions
import dev.asfakul.yumeiro.tracking.Trakt
import dev.asfakul.yumeiro.ui.screens.library.songsLabel
import dev.asfakul.yumeiro.watch.WatchTogether
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UniversalSourcesLogicTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun webdavParseDropsSelfAndReadsFiles() {
        val xml = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/dav/Shows/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
              <d:response><d:href>/dav/Shows/Season%201/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
              <d:response><d:href>/dav/Shows/ep%202.mkv</d:href><d:propstat><d:prop><d:getcontentlength>1234</d:getcontentlength><d:getcontenttype>video/x-matroska</d:getcontenttype><d:resourcetype/></d:prop></d:propstat></d:response>
            </d:multistatus>"""
        val list = WebDavTree.parse(xml, "https://nas.local/dav/Shows/")
        assertEquals(2, list.size)
        val dir = list.first { it.isDir }
        assertEquals("Season 1", dir.name)
        val file = list.first { !it.isDir }
        assertEquals("https://nas.local/dav/Shows/ep%202.mkv", file.path)
        assertEquals("ep 2.mkv", file.name)
        assertEquals(1234L, file.size)
    }

    @Test fun dlnaParseKeepsContainersAndVideoItemsOnly() {
        val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
            <container id="64$1"><dc:title>Movies</dc:title><upnp:class>object.container</upnp:class></container>
            <item id="64$2"><dc:title>Film</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:video/mp4:*" size="99">http://10.0.0.2:8200/MediaItems/2.mp4</res></item>
            <item id="64$3"><dc:title>Song</dc:title><upnp:class>object.item.audioItem</upnp:class><res protocolInfo="http-get:*:audio/mpeg:*">http://10.0.0.2:8200/MediaItems/3.mp3</res></item>
            </DIDL-Lite>"""
        val parsed = DlnaTree.parse(didl)
        assertEquals(2, parsed.size)
        assertTrue(parsed[0].first.isDir)
        assertEquals("Film", parsed[1].first.name)
        assertEquals("video/mp4", parsed[1].first.mime)
        assertEquals("http://10.0.0.2:8200/MediaItems/2.mp4", parsed[1].second)
        assertTrue(DlnaTree.parse("").isEmpty())
    }

    @Test fun dlnaControlUrlResolvesAgainstLocation() {
        val xml = """<root><device><serviceList>
            <service><serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType><controlURL>/cm</controlURL></service>
            <service><serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType><controlURL>/ctl/ContentDir</controlURL></service>
            </serviceList></device></root>"""
        assertEquals("http://10.0.0.2:8200/ctl/ContentDir", DlnaTree.controlFrom(xml, "http://10.0.0.2:8200/rootDesc.xml"))
        assertNull(DlnaTree.controlFrom("<root/>", "http://10.0.0.2/"))
    }

    @Test fun jsUnpackerRestoresWords() {
        val packed = "eval(function(p,a,c,k,e,d){return p}('0(\"1\")',10,2,'alert|hi'.split('|'),0,{}))"
        assertEquals("alert(\"hi\")", JsUnpacker.unpack(packed))
        assertEquals("plain", JsUnpacker.unpack("plain"))
    }

    @Test fun stremioNormalize() {
        assertEquals("https://v3-cinemeta.strem.io/manifest.json", StremioAddons.normalize("stremio://v3-cinemeta.strem.io/manifest.json"))
        assertEquals("https://a.example/manifest.json", StremioAddons.normalize("a.example/"))
        assertEquals("https://a.example/x/manifest.json", StremioAddons.normalize(" https://a.example/x/manifest.json "))
    }

    @Test fun tachiyomiRepoNormalize() {
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo", TachiyomiExtensions.normalizeRepo("https://github.com/keiyoushi/extensions"))
        assertEquals("https://raw.githubusercontent.com/o/r/main", TachiyomiExtensions.normalizeRepo("https://github.com/o/r/tree/main/"))
        assertEquals("https://x.dev/repo", TachiyomiExtensions.normalizeRepo("https://x.dev/repo/index.min.json"))
        assertEquals("https://x.dev/repo", TachiyomiExtensions.normalizeRepo("tachiyomi://add-repo?url=https://x.dev/repo/index.min.json"))
    }

    @Test fun jsRepoNormalize() {
        assertEquals("https://x.dev/index.json", JsSources.normalizeRepo("mangayomi://add-repo?url=https://x.dev/index.json"))
        assertEquals("https://x.dev/index.json", JsSources.normalizeRepo("x.dev/index.json"))
    }

    @Test fun naturalSortOrdersEpisodes() {
        val sorted = listOf("Episode 10", "Episode 2", "episode 1", "Extras").sortedWith(MediaTree.natural)
        assertEquals(listOf("episode 1", "Episode 2", "Episode 10", "Extras"), sorted)
    }

    @Test fun backupRejectsZipSlip() {
        val root = tmp.newFolder("staging")
        assertNotNull(Backup.safeTarget(root, "prefs/settings.xml"))
        assertNotNull(Backup.safeTarget(root, "files/history.json"))
        assertNull(Backup.safeTarget(root, "prefs/../../evil"))
        assertNull(Backup.safeTarget(root, "/etc/passwd"))
        assertNull(Backup.safeTarget(root, "lib/x.so"))
        assertNull(Backup.safeTarget(root, "files\\..\\x"))
    }

    @Test fun traktPicksExactTitleThenFirst() {
        val arr = JSONArray("""[{"show":{"title":"Other","ids":{"trakt":1}}},{"show":{"title":"Frieren: Beyond","ids":{"trakt":2}}}]""")
        assertEquals(2L, Trakt.pickId(arr, "show", "frieren beyond"))
        assertEquals(1L, Trakt.pickId(arr, "show", "nothing like it"))
        assertNull(Trakt.pickId(JSONArray(), "show", "x"))
    }

    @Test fun roomStateExtrapolatesWhilePlaying() {
        val s = WatchTogether.RoomState(positionMs = 10_000, playing = true, at = 1_000)
        assertEquals(15_000, s.positionAt(6_000))
        assertEquals(10_000, s.positionAt(500))
        assertEquals(10_000, s.copy(playing = false).positionAt(60_000))
        val round = WatchTogether.RoomState.from(s.copy(title = "Ep 1", seq = 3).toJson())
        assertEquals("Ep 1", round.title)
        assertEquals(3, round.seq)
        assertFalse(round.copy(playing = false).playing)
    }

    @Test fun songsLabelPlural() {
        assertEquals("1 song", songsLabel(1, 0))
        assertTrue(songsLabel(3, 125_000).startsWith("3 songs · "))
    }
}
