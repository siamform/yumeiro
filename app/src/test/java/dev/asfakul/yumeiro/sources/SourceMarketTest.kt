/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceMarketTest {
    private val sample = """
        {"version":1,"generated":"2026-11-10T03:30:30Z",
         "aniyomi":[
           {"kind":"aniyomi","url":"https://raw.githubusercontent.com/a/big/repo/index.min.json","github":"a/big","name":"big","owner":"a",
            "desc":"Big anime repo","stars":500,"updated":"2026-01-01T00:00:00Z","count":150,"nsfw":10,"langs":["en","ja"],"sample":["AnimeOne"],"score":120.5},
           {"kind":"aniyomi","url":"https://raw.githubusercontent.com/b/fresh/repo/index.min.json","github":"b/fresh","name":"fresh","owner":"b",
            "desc":"","stars":5,"updated":"2026-11-09T00:00:00Z","count":20,"nsfw":0,"langs":["bn"],"sample":["Bangla TV"],"score":60.0},
           {"kind":"aniyomi","url":"https://raw.githubusercontent.com/c/adult/repo/index.min.json","github":"c/adult","name":"adult","owner":"c",
            "stars":30,"updated":"2026-10-01T00:00:00Z","count":0,"nsfw":40,"score":70.0},
           {"kind":"aniyomi","url":"http://insecure/index.min.json","name":"bad"},
           "junk"
         ],
         "mihon":[],
         "stremio":[
           {"url":"https://x.example/manifest.json","name":"Zeta","types":["movie"],"resources":["stream"],"featured":false,"adult":false},
           {"url":"https://y.example/manifest.json","name":"Alpha","types":["series","anime"],"resources":["catalog"],"featured":true,"adult":false},
           {"url":"https://z.example/manifest.json","name":"Night","types":["movie"],"featured":false,"adult":true},
           {"url":"https://not-a-manifest.example/","name":"Broken"}
         ]}
    """.trimIndent()

    @Test fun parsesAndSkipsBadRows() {
        val c = SourceMarket.parse(sample)
        assertEquals(3, c.repos[SourceMarket.Kind.ANIYOMI]!!.size)
        assertTrue(c.repos[SourceMarket.Kind.MANGAYOMI]!!.isEmpty())
        assertEquals(3, c.addons.size)
        assertTrue(c.generated > 0)
        assertEquals(listOf("en", "ja"), c.repos[SourceMarket.Kind.ANIYOMI]!!.first().langs)
    }

    @Test fun sortsAndHidesAdultOnly() {
        val list = SourceMarket.parse(sample).repos[SourceMarket.Kind.ANIYOMI]!!
        assertEquals(listOf("big", "fresh"), SourceMarket.filterRepos(list, "", SourceMarket.Sort.TOP, adult = false).map { it.name })
        assertEquals(listOf("big", "adult", "fresh"), SourceMarket.filterRepos(list, "", SourceMarket.Sort.TOP, adult = true).map { it.name })
        assertEquals("fresh", SourceMarket.filterRepos(list, "", SourceMarket.Sort.RECENT, false).first().name)
        assertEquals("big", SourceMarket.filterRepos(list, "", SourceMarket.Sort.MOST, false).first().name)
        assertEquals(listOf("fresh"), SourceMarket.filterRepos(list, "bn", SourceMarket.Sort.TOP, false).map { it.name })
        assertEquals(listOf("fresh"), SourceMarket.filterRepos(list, "bangla", SourceMarket.Sort.TOP, false).map { it.name })
    }

    @Test fun addonsFeaturedFirstAndAdultHidden() {
        val a = SourceMarket.parse(sample).addons
        assertEquals(listOf("Alpha", "Zeta"), SourceMarket.filterAddons(a, "", SourceMarket.AddonSort.FEATURED, false).map { it.name })
        assertEquals(listOf("Alpha", "Night", "Zeta"), SourceMarket.filterAddons(a, "", SourceMarket.AddonSort.NAME, true).map { it.name })
        assertEquals(listOf("Alpha"), SourceMarket.filterAddons(a, "anime", SourceMarket.AddonSort.NAME, false).map { it.name })
    }

    @Test fun agoLabels() {
        val now = 1_000L * 86_400_000L
        assertEquals("today", SourceMarket.ago(now - 1000, now))
        assertEquals("5 days ago", SourceMarket.ago(now - 5 * 86_400_000L, now))
        assertEquals("2 mo ago", SourceMarket.ago(now - 65 * 86_400_000L, now))
        assertEquals("unknown", SourceMarket.ago(0, now))
    }
}
