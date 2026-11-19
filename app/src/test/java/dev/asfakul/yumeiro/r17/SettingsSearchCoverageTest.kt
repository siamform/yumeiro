/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r17

import dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog
import dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R17 G9 S23 (ledger ST-3): typing a setting's own name in Settings search must open the page
 * that holds it. Before the fix 42 of 51 names found nothing.
 */
class SettingsSearchCoverageTest {

    /** Every switch and main action, mapped to the page it lives on (SettingsScreen.kt). */
    private val where: Map<String, Set<Category>> = buildMap {
        fun put(c: Category, vararg names: String) = names.forEach { put(it, setOf(c)) }
        put(Category.APPEARANCE, "Pure black (AMOLED)", "Ambient colour from artwork", "Japanese accents")
        put(Category.PLAYER, "Background play", "Picture-in-picture", "Captions on by default", "Volume boost up to 200 %")
        // The overlay switch is on the Player page and on the "Quality and audio" sheet.
        put("Player info overlay", setOf(Category.PLAYER, Category.AUDIO))
        put(Category.AUDIO, "Resume playback", "Anime4K enhancement", "Equalizer & sound effects", "Quality and audio")
        put(Category.ONLINE, "Show 18+ content", "Keep 18+ activity private", "Skip intro & outro (anime)", "Skip automatically")
        put(Category.DOWNLOADS, "Ask before each download", "Permanent by default", "Save YouTube and link downloads to phone", "Show tips on the Downloads page")
        put(Category.LIBRARY, "Rescan this device")
        put(Category.TRACKING, "Scrobble what I play", "Find lyrics online")
        put(Category.NOTIFICATIONS, "Show notifications", "New episodes", "New extensions", "New stream playlists",
            "Yumeiro news", "Service alerts", "Suggestions", "Yumeiro chime", "Instant alerts", "In-app reminders",
            "Send a test notification", "Reliable alerts (UnifiedPush)")
        put(Category.BACKGROUND, "Background checks", "Wi-Fi only")
        put(Category.NETWORK, "Get around blocked sites automatically", "Only when system DNS fails")
        put(Category.PRIVACY, "Incognito", "Lock Yumeiro", "Hide content in recents", "Clear watch history",
            "Clear 18+ history", "Clear listening history", "Clear search history", "Clear notifications",
            "Clear cache", "All app permissions")
        put(Category.BACKUP, "Back up automatically", "Save loaded pages on the phone", "Erase everything")
    }

    @Test fun `every setting name finds its own page`() {
        val misses = where.filter { (name, pages) -> SettingsCatalog.search(name).none { it in pages } }
        assertTrue("not found by their own name: ${misses.keys}", misses.isEmpty())
    }

    @Test fun `a setting name ranks its page near the top`() {
        // A full name should not be buried under pages that only share one common word.
        where.forEach { (name, pages) ->
            val r = SettingsCatalog.search(name)
            val at = r.indexOfFirst { it in pages }
            assertTrue("$name: page at $at in $r", at in 0..2)
        }
    }

    @Test fun `partial and case-insensitive names work`() {
        assertEquals(Category.AUDIO, SettingsCatalog.search("anime4k").first())
        assertTrue(Category.APPEARANCE in SettingsCatalog.search("JAPANESE"))
        assertTrue(Category.TRACKING in SettingsCatalog.search("scrobble"))
        assertTrue(Category.BACKUP in SettingsCatalog.search("back up auto"))
    }

    @Test fun `every literal switch in the code is in the table`() {
        // Guard so a new switch cannot be added without being searchable.
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val ui = File(root, "dev/asfakul/yumeiro/ui")
        val titles = ui.walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> TOGGLE.findAll(f.readText()).map { it.groupValues[1] } }
            .toSet()
        assertTrue("scan found too few switches (${titles.size}); pattern broken?", titles.size >= 35)
        val missing = titles - where.keys
        assertTrue("switches missing from search table: $missing", missing.isEmpty())
    }

    private companion object {
        val TOGGLE = Regex("""SettingsToggleRow\(\s*(?:title\s*=\s*)?"([^"$]+)"""")
    }
}
