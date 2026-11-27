/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groupp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.backup.PrefSections
import dev.asfakul.yumeiro.online.telegram.td.TgChat
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.sources.novel.NovelImageHeaders
import dev.asfakul.yumeiro.ui.screens.sites.TelegramChatFilter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Group P (beta chapter) rules that need no device: B7 cover headers, B8 chat filter, B9 history keys, B19 scoped stores. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupPTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun b7_pluginImageHeadersAreUsed() {
        val webnovel = JSONObject("""{"site":"https://www.webnovel.com","imageRequestInit":{"headers":{"referrer":"https://www.webnovel.com"}}}""")
        assertEquals(mapOf("Referer" to "https://www.webnovel.com"), NovelImageHeaders.fromInfo(webnovel))
        val mtl = JSONObject("""{"imageRequestInit":{"headers":{"Alt-Used":"www.mtlnovels.com","Referer":"https://www.mtlnovels.com/"}}}""")
        assertEquals("www.mtlnovels.com", NovelImageHeaders.fromInfo(mtl)["Alt-Used"])
        assertEquals(emptyMap<String, String>(), NovelImageHeaders.fromInfo(JSONObject("""{"site":"https://x.example"}""")))
        // No headers from the plugin: the site goes as Referer, with a browser user agent.
        val plain = NovelImageHeaders.forCovers("https://novel.example", emptyMap(), userAgent = "UA")
        assertEquals("https://novel.example/", plain["Referer"]); assertEquals("UA", plain["User-Agent"])
        // The plugin's own Referer wins.
        assertEquals("https://cdn.example/", NovelImageHeaders.forCovers("https://novel.example", mapOf("Referer" to "https://cdn.example/"), "UA")["Referer"])
    }

    @Test fun b7_emptyListSaysWhy() {
        val s = dev.asfakul.yumeiro.sources.novel.NovelSiteCheck
        assertEquals("survey-twist.com", s.awayHost("https://www.mtlnovels.com/novel-list/?pg=1", "http://survey-twist.com/", "https://www.mtlnovels.com/"))
        assertEquals(null, s.awayHost("https://www.site.com/a", "https://site.com/b", "https://www.site.com/"))
        assertEquals(null, s.awayHost("https://api.site.com/a", "https://cdn.api.site.com/b", "https://site.com/"))
        assertTrue(s.isSiteCheck(s.emptyReason("Scribble Hub", challenged = true, away = null)))
        assertTrue(s.emptyReason("MTL Novel", false, "survey-twist.com")!!.contains("another site"))
        assertEquals(null, s.emptyReason("X", false, null))
        assertEquals("survey-twist.com", s.cleartextHost("CLEARTEXT communication to survey-twist.com not permitted by network security policy"))
        assertEquals(null, s.cleartextHost("timeout"))
        val blocked = s.emptyReason("Scribble Hub", false, null, blocked = true)!!
        assertTrue("firewall" in blocked); assertTrue(!s.isSiteCheck(blocked))
    }

    @Test fun b7_interstitialAndParkedDomain() {
        val s = dev.asfakul.yumeiro.sources.novel.NovelSiteCheck
        // MTL Novel 2026-11-27: the list answers with a "Loading..." page that moves on by script.
        val loading = "<html><head><title>Loading...</title></head><body><script type='text/javascript'>window.location.replace('https://www.mtlnovels.com/novel-list/?ch=1&js=abc&pg=1');</script></body></html>"
        assertEquals("https://www.mtlnovels.com/novel-list/?ch=1&js=abc&pg=1", s.interstitialTarget(loading, "https://www.mtlnovels.com/novel-list/?pg=1"))
        assertEquals("https://a.example/next", s.interstitialTarget("<meta http-equiv=\"refresh\" content=\"0;url=/next\">", "https://a.example/list"))
        // A real page with a script redirect somewhere in a big body is never followed.
        assertEquals(null, s.interstitialTarget("x".repeat(5_000) + "location.href='https://ads.example/'", "https://a.example/"))
        assertEquals(null, s.interstitialTarget("<html><body>Novels</body></html>", "https://a.example/"))
        // Then it lands on a parked plain-http sub-domain: that is "moved", not "listed no novels".
        assertEquals("ww19.mtlnovels.com", s.awayHost("https://www.mtlnovels.com/novel-list/?pg=1", "http://ww19.mtlnovels.com/", "https://www.mtlnovels.com/"))
        // www and https sub-domains of the same site still count as the same site.
        assertEquals(null, s.awayHost("https://www.mtlnovels.com/a", "https://mtlnovels.com/b", "https://www.mtlnovels.com/"))
        assertEquals(null, s.awayHost("https://site.example/a", "https://cdn.site.example/b", "https://site.example/"))
    }

    @Test fun b8_chatFilter() {
        val chats = listOf(TgChat(1, "Anime News", TgChat.Kind.CHANNEL, 0), TgChat(2, "Movie Club", TgChat.Kind.GROUP, 0), TgChat(3, "Sam", TgChat.Kind.PRIVATE, 0))
        assertEquals(listOf(1L), TelegramChatFilter.apply(chats, " anime ", TelegramChatFilter.Kind.ALL).map { it.id })
        assertEquals(listOf(2L), TelegramChatFilter.apply(chats, "", TelegramChatFilter.Kind.GROUPS).map { it.id })
        assertEquals(emptyList<Long>(), TelegramChatFilter.apply(chats, "anime", TelegramChatFilter.Kind.CHATS).map { it.id })
        assertEquals(listOf(TelegramChatFilter.Kind.ALL, TelegramChatFilter.Kind.CHANNELS), TelegramChatFilter.kinds(chats.take(1)))
    }

    @Test fun b9_historyKeys() {
        assertTrue(PrefSections.isHistory("episode_seen", "any"))
        assertTrue(PrefSections.isHistory("online@p2", "online_search_history"))
        assertFalse("how results are shown is a setting", PrefSections.isHistory("online", "search_view"))
        assertTrue(PrefSections.isHistory("mt_streams", "history")); assertTrue(PrefSections.isHistory("mt_streams", "live_history"))
        assertTrue(PrefSections.isHistory("live_tv", "country_recent")); assertTrue(PrefSections.isHistory("yu_sites_recent", "visits"))
    }

    @Test fun b19_storesAreProfileScoped() {
        Profiles.resetForTests(ctx)
        val p = Profiles.add(ctx, "Two", dev.asfakul.yumeiro.ui.theme.AccentTone.values().first())
        Profiles.select(ctx, p.id)
        listOf("online", "search_recent", "yu_sites", "yu_sites_recent", "site_logins", "mt_streams", "live_tv", "episode_seen").forEach { n ->
            assertTrue("$n is kept per profile (and goes with it)", n in Profiles.SCOPED_PREFS)
            assertEquals("$n is the profile's own", "$n@p${p.id}", Profiles.prefsName(ctx, n))
        }
        Profiles.select(ctx, Profiles.DEFAULT)
        assertEquals("online", Profiles.prefsName(ctx, "online"))
        Profiles.resetForTests(ctx)
    }
}
