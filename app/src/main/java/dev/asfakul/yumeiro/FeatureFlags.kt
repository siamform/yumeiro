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
        /** F-15 (ADR-040): Live TV watch page shows Now / Next from the playlist's XMLTV guide (url-tvg). */
        LIVE_GUIDE("f15_live_guide", true),
        /** F-13 (ADR-040): save manga chapters on the phone for offline reading (chapter list button). */
        OFFLINE_MANGA("f13_offline_manga", true),
        /** PR-1 (ADR-041): profiles with their own data and lock (Settings → Profiles, "Who's watching?"). */
        PROFILES("f18_profiles", true),
        /** DL-H (ADR-041): hide downloads in the Private vault (Downloads → Private). */
        PRIVATE_DOWNLOADS("f18_private_downloads", true),
        /** F-02 (ADR-042): public Telegram channels and post links, read from Telegram's web preview (no login). */
        TELEGRAM_PUBLIC("telegram_public", true),
        /**
         * F-02 login (ADR-052): sign in to Telegram (TDLib), chats, search in a chat, play videos. Off until the
         * device tests pass; also hidden when the build has no Telegram api_id or no engine for the phone's ABI.
         */
        TELEGRAM_LOGIN("f02_telegram_login", false),
        /**
         * B-31 (ADR-053): built-in torrent engine (libtorrent): magnets, info-hashes and .torrent links play without
         * TorrServer. Off until the device tests pass; also hidden when no engine is pinned for the phone's ABI.
         */
        BUILTIN_TORRENT("b31_builtin_torrent", false),
        /** F-03 (ADR-042): an expired TeraBox / DiskWala stream is found again on its page and resumes at the same place. */
        CLOUD_CAPTURE_V2("cloud_capture_v2", true),
        /** F-11 (ADR-044): "Translate pages" in the manga reader (on-device OCR + translation). */
        MANGA_TRANSLATE("f11_manga_translate", true),
        /** NBUG-13 (ADR-045): Settings → Network → Proxy (HTTP / SOCKS5) for app requests and web pages. */
        APP_PROXY("nbug13_proxy", true),
        /** NBUG-6 (ADR-046): Settings → Network → "Automatic DNS fallback" (the user setting itself is Off by default). */
        DNS_FALLBACK("nbug6_dns_fallback", true),
        /** F-21: language chip + "N sources • ecosystem • repo" on market, installed and "Show videos from" rows. */
        EXTENSION_LABELS("f21_extension_labels", true),
        /** Vega step 4 (ADR-047): run Vega providers from a user-added repository (Sources → Script repos). */
        SCRIPTS_VEGA("scripts_vega", true),
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
