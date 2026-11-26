/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

/**
 * R17 (G9 S24/S25): which backup section each shared-preference value belongs to. Backup, restore
 * and the resets all read this one table.
 *
 * Before, Backup kept a short "library prefs" list and ResetCenter its own "keep" list. Neither
 * knew that playlists, favourite songs, favourite videos and device resume points live in
 * `mt_player_music_prefs` / `mt_player_video_prefs`, so "Reset settings" (which says the library
 * stays) erased them, a Library-only restore left them out, and a Settings-only restore replaced
 * them. Saved sites, listening time, Jump back in for songs, the unsent Last.fm queue and online
 * search history had the same problem.
 */
internal object PrefSections {
    /** Whole files that hold the user's library or history. */
    val libraryFiles = setOf(
        "play_counts", "search_recent", "yt_history", "manga_reader", "reminders",
        "mt_player_music_prefs", // favourites, playlists, recently played, play counts
        "online",                // online search history
        "yu_last_played",        // Jump back in: the song and YouTube video played last
        "yumeiro_listening",     // total listening time
        "yu_sites",              // saved websites
        "scrobble",              // Last.fm queue of plays not sent yet (and its on/off)
        "activity_log",          // F-17: per-day activity for the profile statistics
        "profiles",              // PR-1: the profiles themselves (names, colours, PIN hashes)
    )

    /** PR-1: "yt_history@p2" is profile 2's copy of "yt_history" and is sorted the same way. */
    private fun base(file: String) = file.substringBefore("@p")

    /** Files that go with the sources (the extension list, servers, overrides). */
    val sourceFiles = setOf("tachiyomi_bridge", "provider_overrides", "pinned_extensions", "home_servers")
    private const val JS_SOURCE_PREFIX = "js_source_"

    /** Library keys inside files that also hold settings. */
    private fun mixedLibraryKey(file: String, key: String): Boolean = when (file) {
        "mt_player_video_prefs" -> key == "favorite_video_ids" || key.startsWith("pos_") || key.startsWith("watched_")
        else -> false
    }

    fun sectionOf(file: String, key: String): Backup.Section = sectionOfBase(base(file), key)

    private fun sectionOfBase(file: String, key: String): Backup.Section = when {
        file in libraryFiles || mixedLibraryKey(file, key) -> Backup.Section.LIBRARY
        file in sourceFiles || file.startsWith(JS_SOURCE_PREFIX) || file.startsWith("script_kv_") -> Backup.Section.SOURCES // B16: script plugins' own storage
        else -> Backup.Section.SETTINGS
    }

    /** History a "Clear watch and search history" reset removes, as file → key test. */
    fun isHistory(file: String, key: String): Boolean = when (base(file)) {
        "play_counts", "search_recent", "yt_history", "online", "yu_last_played", "activity_log" -> true
        "mt_player_video_prefs" -> key.startsWith("pos_") || key.startsWith("watched_")
        "mt_player_music_prefs" -> key == "recently_played_ids" || key == "play_counts_json"
        else -> false
    }
}
