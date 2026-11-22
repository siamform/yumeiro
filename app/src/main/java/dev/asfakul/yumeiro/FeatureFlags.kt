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
        /** F-12: YouTube's own Short mark (vertical, up to 3 min) also opens the Shorts player; landscape clips there are fitted, not cropped. Also gates vertical mode and the Shorts chrome auto-hide. On since F-12-FLAG (Verified: device 44916676685 / 44916693769, CI 44918012619). */
        SHORTS_VERTICAL("f12_vertical_shorts", true),
        /** F12-VIEW: the Shorts pager keeps one video view under the pages (moved with the page on screen) instead of inflating one per page. */
        SHORTS_ONE_VIEW("f12_one_view", true),
        /** F12-HIST: YouTube history writes (first read from disk, JSON encode) run on a background lane, not on the main thread. */
        HISTORY_OFF_MAIN("f12_history_off_main", true),
        /** F-12 (ADR-056): a 9:16 video in the normal player switches to vertical mode (full screen, cropped to fill, swipe up / down for the queue). Acts only while f12_vertical_shorts is on. */
        VERTICAL_MODE("f12_vertical_mode", true),
        /** F-12 (ADR-056): the Shorts player's top bar and title hide after 3.5 s of play; a tap brings them back. Acts only while f12_vertical_shorts is on. */
        SHORTS_AUTOHIDE("f12_shorts_autohide", true),
        /** NBUG-13-PORT: a proxy that can't be reached is reported as unreachable even when its port or address holds the digits 407 (was "wants a login"). */
        PROXY_REACH_FIRST("nbug13_reach_first", true),
        /** OL-8: the website helper banner hides by itself 3 s after it shows (tap still hides it at once). */
        PAGE_HINT_AUTOHIDE("ol8_hint_autohide", true),
        /** OL-2: the website "Detected streams" panel has Rescan, Collapse (down arrow) and Close; a closed panel leaves a small "Streams" pill. */
        STREAM_PANEL_CONTROLS("ol2_stream_panel_controls", true),
        /** OL-3: the website stream panel lists only the video playing now (cleared when the page switches video or page) and shows its title. */
        STREAM_CURRENT_ONLY("ol3_stream_current_only", true),
        /** OL-1: a website stays open in the background (playing its video, Downloads, Minimize, Back) and comes back as it was. */
        WEBSITE_TAB("ol1_website_tab", true),
        /** OL-4: website, link and Telegram downloads show the same Offline / Permanent download popup as every other download. */
        STREAM_DOWNLOAD_PROMPT("ol4_stream_download_prompt", true),
        /** OL-7: player top bar: Quality / Audio / More sit flush at the trailing edge, the title takes the rest (no gap left of the buttons). */
        PLAYER_TOPBAR_ALIGN("ol7_player_topbar_align", true),
        /** OL-9: Home Spaces "See more" also has Telegram, Profiles, Your stats, Airing soon, Reading and Inbox. */
        HOME_SPACES_MORE("ol9_home_spaces_more", true),
        /** OL-10: Settings -> Navigation bar can also pin Profiles, Your statistics, Airing soon, Reading and Tracking. */
        NAV_MORE_DESTINATIONS("ol10_nav_more_destinations", true),
        /** OL-11: episode download sheet also offers "This episode only" and an "Episode range" (from N to M). */
        EPISODE_RANGE_DOWNLOAD("ol11_episode_range_download", true),
        /** OL-12: Online Music playlist / album pages have a "Download playlist" button (all songs, one batch, live progress). */
        PLAYLIST_DOWNLOAD("ol12_playlist_download", true),
        /** OL-5: download popup offers quality / audio track / subtitles when the source has them; picked subtitles are saved next to the file and load in the player. */
        DOWNLOAD_TRACKS("ol5_download_tracks", true),
        /** OL-6: redesigned download popup (bottom sheet on phones, dialog on TV / wide screens). */
        DOWNLOAD_SHEET("ol6_download_sheet", true),
        /** OL-7-EDGE: player top bar uses the 4 dp Material app-bar side padding, so More ends at the edge like other apps. */
        PLAYER_TOPBAR_EDGE("ol7_player_topbar_edge", true),
        /** OL-13: About shows every official Yumeiro account (Discord, Facebook, Instagram, X, TikTok, Reddit) and the developer's Facebook / Instagram / Telegram. */
        SOCIAL_LINKS("ol13_social_links", true),
        /** PIN-1-DUP: at most 2 installed copies of one extension name (from different repositories); a third install is refused with the names of the two copies. */
        DUPLICATE_CAP("pin1_duplicate_cap", true),
        /** F-19-EXT: CloudStream extensions and repositories belong to the profile that added them (files stay shared); others neither see nor load them. */
        PROFILE_EXTENSIONS("f19_profile_extensions", true),
        /** GATE-1-VIDEOS-LOWRAM: a tab page opened after start composes its content one frame after the page (the jump frame stays short; phones only). */
        TAB_NEXT_FRAME("gate1_tab_next_frame", true),
        /** GATE-1-RENDER: a short while after start, a tiny canvas under the app draws the first screens' primitives one group per frame, so their GPU programs compile before the first tap. */
        SHADER_PREWARM("gate1_shader_prewarm", true),
        /** GATE-1-RENDER-2: the Yumix feed opens with its sections added one per frame, top to bottom (a list reopened scrolled down shows all at once). */
        FEED_STAGED_REVEAL("gate1_feed_staged_reveal", true),
        /** GATE-1-FEED-LATE: sections that appear after the opening reveal (data arriving under the skeleton) join one per frame too. */
        FEED_STAGED_LATE("gate1_feed_staged_late", true),
        /** GATE-1-RENDER-4: posters load through AsyncImage in the same composition (no SubcomposeAsyncImage per poster). */
        POSTER_PLAIN_IMAGE("gate1_poster_plain_image", true),
        /** GATE-1-RENDER-5: extension work runs on 8 own threads at THREAD_PRIORITY_BACKGROUND (not normal-priority IO threads). */
        EXTENSION_BG_PRIORITY("gate1_extension_bg_priority", true),
        /** GATE-1-MUSIC-START: the music player (ExoPlayer) is built on the first music command, not right after start. */
        MUSIC_PLAYER_ON_USE("gate1_music_player_on_use", true),
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
        /** F-22: Upcoming date headers ("Today • Sat, Nov 21"), air times, date-only days kept, category kept per profile. */
        UPCOMING_SECTIONS("f22_upcoming_sections", true),
        /** E4: Yumix loads extension homes fastest-first (measured on this phone); failing ones are quarantined last. */
        E4_SPEED_ORDER("e4_speed_order", true),
        /** FE-2 (docs/FEED-ENGINE-2.1.md): Yumix queue by priority; the extension picked in "Show videos from" jumps it. */
        FE_PRIORITY_GATE("fe2_priority_gate", true),
        /** B31-SEEK3: the built-in torrent engine downloads only the playback window (piece priorities). */
        B31_STREAM_WINDOW("b31_stream_window", true),
        /** B31-SEEK5: until the piece the player waits for has bytes, only that piece downloads (window and tail after). */
        B31_TARGET_FIRST("b31_target_first", true),
        /** FE-3: an extension shows on Yumix as soon as its first home row answers (the rest fill in). */
        FE3_FIRST_ROW("fe3_first_row", true),
        /** FE-4: Yumix rebuilds its mix at most once per 300 ms frame (first update at once), not on every row answer. */
        FE4_FEED_FRAMES("fe4_feed_frames", true),
        /** FE-4b: only the picked, pinned and fastest 24 (12 on 2 GB phones) extensions fetch live; the rest show saved pages and join on scroll. */
        FE4_WORKING_SET("fe4_working_set", true),
        /** E4 part 2: background feed work runs 1 at a time when hot or short of memory, one less when warm (FeedThrottle). */
        E4_THROTTLE("e4_throttle", true),
        /** FE-S3: a site that failed a search in the last 2 minutes answers with that failure at once (Retry asks again). */
        FE_S3_FAIL_MEMORY("fe_s3_fail_memory", true),
        /** FE-S4: a site's search timeout follows its measured speed (8..25 s); Retry and unknown sites get 25 s. */
        FE_S4_ADAPTIVE_TIMEOUT("fe_s4_adaptive_timeout", true),
        /** FE-P: while a video or song plays, Yumix background fetches run one at a time (picked / on screen unchanged). */
        FE_PAUSE_WHILE_PLAYING("fe_pause_while_playing", true),
        /** FE-2c: the shared extension-call limit (6) serves search, scroll pages and picked work before background rows. */
        FE_CALL_LANES("fe2c_call_lanes", true),
        /** FE-5: on Wi-Fi with a healthy battery, the working set's home pages are refreshed every 6 h (reserve cache). */
        FE5_FEED_WARM("fe5_feed_warm", true),
        /** Vega step 4 (ADR-047): run Vega providers from a user-added repository (Sources → Script repos). */
        SCRIPTS_VEGA("scripts_vega", true),
        /**
         * F-05-N (ADR-057): Nuvio scrapers from a user-added repository; their streams join a title's
         * sources. On since F-05-N-ON: NuvioProviderDeviceTest 4/4 on api36 + api33-lowram (device 44985807462).
         */
        SCRIPTS_NUVIO("scripts_nuvio", true),
        /** F-05-N (ADR-057): stream-only add-ons (Nuvio now) are asked for extra links when a title plays. On with SCRIPTS_NUVIO (F-05-N-ON). */
        STREAM_ADDONS("f05_stream_addons", true),
        /**
         * F-05-STREMIO: Stremio add-ons with no catalog (stream-only) give links to any title with an IMDb id,
         * from any source, through the F-05-N add-on seam. Before, they were asked only for Stremio catalog titles.
         */
        F05_STREMIO_STREAMS("f05_stremio_streams", true),
        /** F-22-C: Upcoming Series by country (TVmaze schedule) with a genre filter and badges. */
        F22_COUNTRY_GENRE("f22_country_genre", true),
        /**
         * F-05-S (ADR-058): SkyStream plugins (.sky) from a user-added repository run as normal sources. On since
         * F-05-S-ON: SkyStreamProviderDeviceTest 3/3 on api36 + api33-lowram (device 44992834769).
         */
        SCRIPTS_SKYSTREAM("scripts_skystream", true),
        /** F-05-N-ERR: a script error is reported as "Error: message" (then the stack), not a bare stack line. */
        SCRIPT_ERROR_MESSAGE("f05_script_error_message", true),
        /** NBUG10-IME: an extension's own window (e.g. a Cloudflare check) that opens while the user types is held behind a card instead of taking the keyboard. */
        EXT_HOLD_WHILE_TYPING("ext_hold_while_typing", true),
    }

    @Volatile private var cache: Map<String, Boolean>? = null

    /**
     * FF-LOAD: the application context, kept so [isOn] can load the flag file itself. Before this, [isOn]
     * returned the built-in default until some other code had called [on] with a context, so a flag
     * switched off in feature_flags.xml could still run (48 [isOn] calls, among them all five GATE-1 fixes).
     */
    @Volatile private var app: Context? = null

    /** Called first thing in MTApplication.onCreate: stores the context, reads nothing. */
    fun attach(context: Context) {
        app = context.applicationContext
    }

    /** Loads the flag file now (PrefsWarmup calls this on its background thread at start). */
    fun warm(context: Context) {
        if (cache == null) cache = load(context)
    }

    fun on(context: Context, flag: Flag): Boolean {
        val c = cache ?: load(context).also { cache = it }
        return c[flag.key] ?: flag.default
    }

    /** Without a context: the saved value (loaded on first use once [attach] ran), else the default. */
    fun isOn(flag: Flag): Boolean {
        cache?.let { return it[flag.key] ?: flag.default }
        val c = app ?: return flag.default
        return on(c, flag)
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
