/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.fixUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GROUP-13 N4-2 (unit): bridge ids survive CloudStream's fixUrl() (library 4.8.0, the one the app
 * ships), which every search result and episode goes through by default.
 */
class BridgeIdsTest {
    private fun api(base: String) = object : MainAPI() { override var mainUrl = base; override var name = "t" }

    private fun roundTrip(base: String, id: String) {
        val fixed = api(base).fixUrl(id)
        assertEquals("unfix(fixUrl(\"$id\")) on $base (fixed = $fixed)", id, BridgeIds.unfix(base, fixed))
        assertEquals("an id that was never fixed stays as it is", id, BridgeIds.unfix(base, id))
    }

    @Test fun stremioPlexJellyfinTreeIdsRoundTrip() {
        roundTrip("http://10.0.0.2:50525/stremio", "movie/yu1")
        roundTrip("http://10.0.0.2:50525/stremio", "series/tt0944947:1:2")
        roundTrip("http://plex:32400", "plex:12") // a server called "plex" broke substringAfter("plex:")
        roundTrip("http://plex:32400", "rk:12")
        roundTrip("http://jf.lan:8096", "item:ab12")
        roundTrip("http://nas.lan", "d:/Films")
        roundTrip("smb://nas/share", "Films/a.mkv")
        roundTrip("http://dlna.lan:8200/desc.xml", "64\$1\$2")
        roundTrip("http://h.lan/", "rk:1") // mainUrl with a trailing slash: fixUrl gives "http://h.lan//rk:1"
    }

    @Test fun onlyAWholePrefixIsRemoved() {
        assertEquals("http://srv:8096/x", BridgeIds.unfix("http://srv:809", "http://srv:8096/x"))
        assertEquals("http://other/x", BridgeIds.unfix("http://srv", "http://other/x"))
        assertEquals("x", BridgeIds.unfix("", "x"))
    }
}
