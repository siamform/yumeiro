/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context

/**
 * Kill switches for features added in the 2026-11-19 program (PROGRAM-2026-11-19.md, completion standard 3.3).
 * A feature that is not verified ships with its default off and stays out of the release notes' feature list.
 * Local prefs only (file `feature_flags`), no network, no UI except where a feature says so; a support build or
 * `adb shell` can flip one: `run-as dev.asfakul.yumeiro` and edit shared_prefs/feature_flags.xml.
 */
object FeatureFlags {
    enum class Flag(val key: String, val default: Boolean) {
        /** F-09: Watch later / Favorite sheet on posters (long press, TV Menu key) and poster marks. */
        POSTER_ACTIONS("f09_poster_actions", true),
        /** F-16: Yumix sections filled from release tags (Web series, Dual Audio, Sub, Dub) and banner tags. */
        YUMIX_TAG_SECTIONS("f16_tag_sections", true),
        /** NF-1: episode status line (Watched, N min left, Fresh). */
        EPISODE_STATUS("nf1_episode_status", true),
        /** F-08: Upcoming calendar entry on Yumix (Simkl public calendar). */
        UPCOMING("f08_upcoming", true),
        /** F-12: vertical Shorts player (swipe up / down) from the YouTube Shorts shelf; phones only. */
        SHORTS_PLAYER("f12_shorts_player", true),
        /** F-17: profile statistics (on-device activity map, streaks, totals, milestones). */
        PROFILE_STATS("f17_profile_stats", true),
        /** F-10: Kasumi glass theme in Appearance (off = it falls back to Tsuki Night). */
        GLASS_THEME("f10_glass_theme", true),
        /** TV-4 (ADR-036): Home's library search offers "Search online" (Yumix) for the committed words. */
        TV4_ONLINE_SEARCH("tv4_online_search", true),
        /** F-14 (ADR-040): a dead Live TV link tries the channel's other links before Auto-zap or the error card. */
        LIVE_FAILOVER("f14_live_failover", true),
    }

    @Volatile private var cache: Map<String, Boolean>? = null

    fun on(context: Context, flag: Flag): Boolean {
        val c = cache ?: load(context).also { cache = it }
        return c[flag.key] ?: flag.default
    }

    private fun load(context: Context): Map<String, Boolean> = runCatching {
        val p = context.applicationContext.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
        Flag.entries.associate { it.key to p.getBoolean(it.key, it.default) }
    }.getOrDefault(emptyMap())

    /** For tests and support: set a flag and drop the cache. */
    fun set(context: Context, flag: Flag, on: Boolean) {
        context.applicationContext.getSharedPreferences("feature_flags", Context.MODE_PRIVATE).edit().putBoolean(flag.key, on).apply()
        cache = null
    }
}
