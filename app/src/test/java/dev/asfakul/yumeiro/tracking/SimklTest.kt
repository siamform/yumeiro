/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimklTest {
    @Test fun everyCallCarriesTheRequiredParams() {
        val u = Simkl.url("/sync/history", "cid", "1.2").toHttpUrl()
        assertEquals("cid", u.queryParameter("client_id")); assertEquals("yumeiro", u.queryParameter("app-name")); assertEquals("1.2", u.queryParameter("app-version"))
    }

    @Test fun pollStates() {
        assertEquals(Simkl.Poll.Pending, Simkl.classify(400, """{"error":"authorization_pending"}"""))
        assertEquals(Simkl.Poll.SlowDown, Simkl.classify(400, """{"error":"slow_down"}"""))
        assertTrue(Simkl.classify(200, """{"access_token":"x"}""") is Simkl.Poll.Done)
        assertTrue(Simkl.classify(400, """{"error":"expired_token"}""") is Simkl.Poll.Fail)
        assertTrue(Simkl.classify(500, "oops") is Simkl.Poll.Fail)
    }

    @Test fun readOnlyScopeIsDetected() {
        assertTrue(Simkl.canWrite(JSONObject().put("scope", "media:read media:write")))
        assertFalse(Simkl.canWrite(JSONObject().put("scope", "media:read")))
    }

    @Test fun episodeBody() {
        val b = Simkl.historyBody(Simkl.Match("Frieren", 2023, mal = 52991, imdb = "tt22248376"), 1, 5)
        val show = b.getJSONArray("shows").getJSONObject(0)
        assertEquals(52991, show.getJSONObject("ids").getInt("mal"))
        assertEquals(5, show.getJSONArray("seasons").getJSONObject(0).getJSONArray("episodes").getJSONObject(0).getInt("number"))
    }

    @Test fun movieBodyDropsBadImdb() {
        val m = Simkl.historyBody(Simkl.Match("Movie", null, imdb = "123"), null, null).getJSONArray("movies").getJSONObject(0)
        assertFalse(m.has("ids")); assertFalse(m.has("year"))
    }

    @Test fun addedCounts() {
        assertTrue(Simkl.added("""{"added":{"movies":0,"shows":1,"episodes":1},"not_found":{}}"""))
        assertFalse(Simkl.added("""{"added":{"movies":0,"shows":0},"not_found":{"shows":[{}]}}"""))
        assertFalse(Simkl.added("nope"))
    }

    @Test fun refreshWindow() {
        val now = 1_000_000_000_000L
        assertFalse(Simkl.needsRefresh(null, now)); assertFalse(Simkl.needsRefresh(now + 3 * 86_400_000L, now)); assertTrue(Simkl.needsRefresh(now + 1000, now))
    }
}
