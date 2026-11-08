/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.settings

import dev.asfakul.yumeiro.online.anime.AniList
import dev.asfakul.yumeiro.online.anime.MyAnimeList
import dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog
import dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category
import dev.asfakul.yumeiro.ui.screens.settings.SetupGuides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {

    @Test fun `every group has categories and every category is listed once`() {
        val grouped = SettingsCatalog.byGroup()
        assertEquals(SettingsCatalog.Group.entries.size, grouped.size)
        val listed = grouped.flatMap { it.second }
        assertEquals(Category.entries.toList(), listed.sortedBy { it.ordinal })
        assertEquals(listed.size, listed.toSet().size)
    }

    @Test fun `routes are the ones the app knows how to open`() {
        val known = setOf("sources", "health", "tracking", "youtube", "together", "backup", "guide")
        Category.entries.mapNotNull { it.route }.forEach { assertTrue("unknown route $it", it in known) }
    }

    @Test fun `title hits rank above keyword hits`() {
        val r = SettingsCatalog.search("tracking")
        assertEquals(Category.TRACKING, r.first())
    }

    @Test fun `keyword finds the right page`() {
        assertEquals(Category.TRACKING, SettingsCatalog.search("anilist").first())
        assertEquals(Category.YOUTUBE, SettingsCatalog.search("google login").first())
        assertEquals(Category.NETWORK, SettingsCatalog.search("DNS").first())
        assertEquals(Category.SOURCES, SettingsCatalog.search("jellyfin").first())
    }

    @Test fun `all words must match and blank finds nothing`() {
        assertTrue(SettingsCatalog.search("anilist equalizer").isEmpty())
        assertTrue(SettingsCatalog.search("   ").isEmpty())
        assertTrue(SettingsCatalog.search("zzzqqq").isEmpty())
    }

    @Test fun `fromName round trips`() {
        Category.entries.forEach { assertEquals(it, SettingsCatalog.fromName(it.name)) }
        assertEquals(null, SettingsCatalog.fromName("nope"))
        assertEquals(null, SettingsCatalog.fromName(null))
    }
}

class SetupGuidesTest {

    @Test fun `ids are unique and every guide has steps`() {
        val ids = SetupGuides.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        SetupGuides.all.forEach { assertTrue("${it.id} has no steps", it.steps.isNotEmpty()) }
    }

    @Test fun `every link is https`() {
        SetupGuides.all.flatMap { g -> g.steps.mapNotNull { it.link } }.forEach { assertTrue(it, it.startsWith("https://")) }
    }

    @Test fun `redirect values users copy match what the app expects`() {
        val copies = SetupGuides.all.flatMap { g -> g.steps.mapNotNull { it.copy } }
        assertTrue(AniList.REDIRECT in SetupGuides.anilist.steps.mapNotNull { it.copy })
        assertTrue(MyAnimeList.REDIRECT in SetupGuides.myAnimeList.steps.mapNotNull { it.copy })
        assertTrue(SetupGuides.TRAKT_REDIRECT in SetupGuides.trakt.steps.mapNotNull { it.copy })
        assertTrue(copies.isNotEmpty())
    }
}
