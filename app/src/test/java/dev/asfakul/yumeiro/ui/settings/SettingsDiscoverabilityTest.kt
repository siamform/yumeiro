/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.settings

import dev.asfakul.yumeiro.notify.WhatsNew
import dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog
import dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FINAL-47: the owner could not find Profiles, Telegram or Statistics in 1.9.0. They were in
 * the build but Profiles sat three levels down (Privacy & security → App lock → scroll),
 * Telegram had no entry at all (only typing @name into the Web sites box) and Statistics was
 * one card inside Library. Settings search found none of them.
 */
class SettingsDiscoverabilityTest {

    @Test fun `profiles has its own settings page and search finds it`() {
        assertEquals(Category.PROFILES, SettingsCatalog.search("profile").first())
        assertEquals(Category.PROFILES, SettingsCatalog.search("who's watching").first())
        assertTrue(Category.PROFILES in SettingsCatalog.search("kids"))
        assertEquals(SettingsCatalog.Group.YOU, Category.PROFILES.group)
    }

    @Test fun `statistics opens the stats screen`() {
        assertEquals(Category.STATS, SettingsCatalog.search("statistics").first())
        assertEquals(Category.STATS, SettingsCatalog.search("stats").first())
        assertEquals("stats", Category.STATS.route)
    }

    // F-02 split (2.1.0): Telegram and Websites are separate pages with their own entries.
    @Test fun `telegram opens the telegram page and websites the websites page`() {
        assertEquals(Category.TELEGRAM, SettingsCatalog.search("telegram").first())
        assertTrue(Category.TELEGRAM in SettingsCatalog.search("terabox"))
        assertEquals("telegram", Category.TELEGRAM.route)
        assertEquals(Category.WEBSITES, SettingsCatalog.search("websites").first())
        assertEquals("websites", Category.WEBSITES.route)
    }

    @Test fun `script repos and vega find sources`() {
        assertTrue(Category.SOURCES in SettingsCatalog.search("vega"))
        assertTrue(Category.SOURCES in SettingsCatalog.search("script repos"))
        assertTrue(Category.SOURCES in SettingsCatalog.search("nuvio"))
    }

    @Test fun `each subject has its own page`() {
        // Owner, 2026-11-20: profile things on Profiles, download things on Downloads, video and music apart.
        assertTrue("Hide the Private entry" in Category.DOWNLOADS.settings)
        assertTrue("Show 18+ content" in Category.PROFILES.settings)
        assertFalse(Category.PRIVACY.settings.any { "Private entry" in it || "18+ content" in it })
        assertEquals(SettingsCatalog.Group.VIDEO, Category.PLAYER.group)
        assertEquals(SettingsCatalog.Group.VIDEO, Category.VIDEO_QUALITY.group)
        assertEquals(SettingsCatalog.Group.VIDEO, Category.SUBTITLES.group)
        assertEquals(SettingsCatalog.Group.MUSIC, Category.MUSIC.group)
        assertTrue("Anime4K enhancement" in Category.VIDEO_QUALITY.settings)
        assertFalse(Category.MUSIC.settings.any { "Anime4K" in it || "Default quality" in it })
        assertTrue("Find lyrics online" in Category.MUSIC.settings)
        assertTrue("Torrent streaming" in Category.VIDEO_QUALITY.settings)
        assertEquals(Category.SUBTITLES, SettingsCatalog.search("subtitles").first())
        assertEquals(Category.VIDEO_QUALITY, SettingsCatalog.search("video quality").first())
        assertEquals(Category.MUSIC, SettingsCatalog.search("lyrics").first())
    }

    @Test fun `every setting name is on exactly one page`() {
        val seen = Category.entries.flatMap { c -> c.settings.map { it to c } }.groupBy({ it.first }, { it.second })
        val twice = seen.filterValues { it.size > 1 }
        assertTrue("on more than one page: $twice", twice.isEmpty())
    }

    @Test fun `whats new note goes to updaters only`() {
        assertFalse(WhatsNew.shouldPost(firstInstall = 1000L, lastUpdate = 1000L, version = "1.9.0"))
        assertTrue(WhatsNew.shouldPost(firstInstall = 1000L, lastUpdate = 5000L, version = "1.9.0"))
        assertTrue(WhatsNew.shouldPost(firstInstall = 1000L, lastUpdate = 5000L, version = "2.0.0"))
        assertFalse(WhatsNew.shouldPost(firstInstall = 1000L, lastUpdate = 1000L, version = "2.0.0"))
        assertFalse(WhatsNew.shouldPost(firstInstall = 1000L, lastUpdate = 5000L, version = "2.1.0"))
    }

    @Test fun `whats new names only real settings pages`() {
        // Every "Settings → X" in the note must be a category title, so the note can't drift.
        val titles = Category.entries.map { it.title }.toSet()
        val named = Regex("""Settings → ([^.,;(]+?)(?=[.,;(]| then| or |$)""").findAll(WhatsNew.BODY).map { it.groupValues[1].trim() }.toList()
        assertTrue("note names no settings page", named.size >= 4)
        named.forEach { assertTrue("'$it' is not a Settings page", it in titles) }
        listOf("Profiles", "Telegram", "Statistics", "Private downloads", "Script repos", "Subtitles", "Video quality", "lyrics").forEach {
            assertTrue("note does not mention $it", WhatsNew.BODY.contains(it, ignoreCase = true))
        }
    }
}
