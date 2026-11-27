/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
private val RX_SETTINGSCATALOG_1 = Regex("\\s+")

/**
 * The shape of Settings: a short home page of groups, each holding a few categories. A category
 * either opens its own page inside Settings ([route] == null) or jumps to an existing screen
 * ([route] is the key handled by `onOpenPage`). Plain data so ordering and search are unit-tested.
 */
object SettingsCatalog {

    /**
     * FINAL-47 (owner, 2026-11-20): one subject per group and per page. Profiles on Profiles,
     * download things on Downloads, video and music apart. Before, Profiles and Private downloads
     * were inside Privacy, video quality was inside "Audio quality & sound", subtitles and 18+
     * shared "Online videos", lyrics sat in Tracking and torrent streaming in Network.
     */
    enum class Group(val label: String) {
        YOU("You"),
        VIDEO("Video"),
        MUSIC("Music"),
        DOWNLOADS(tr(R.string.downloads_library, "Downloads & library")),
        SOURCES(tr(R.string.sources_accounts, "Sources & accounts")),
        SYSTEM("System"),
        ABOUT(tr(R.string.help_about, "Help & about")),
    }

    enum class Category(
        val group: Group,
        val title: String,
        val summary: String,
        /** Extra words people search for that aren't in the title or summary. */
        val keywords: List<String>,
        /** Key for an existing screen (see SettingsScreen's onOpenPage); null = page inside Settings. */
        val route: String? = null,
        /**
         * R17 (G9 S23): the names of the switches and actions on the page, so typing a setting's
         * own name finds its page. Before, 42 of 51 setting names found nothing (every word had to
         * be in the title, summary or keywords). `SettingsSearchCoverageTest` fails when a switch
         * is added without its name here.
         */
        val settings: List<String> = emptyList(),
    ) {
        PROFILES(Group.YOU, "Profiles", tr(R.string.whos_watching_own_history_and_lists, "Who's watching? Own history and lists per person, a PIN or fingerprint, 18+ per profile"),
            listOf("profile", "kids", "family", "child", "pin", "user", "switch", "people", "adult", "nsfw", "18"),
            settings = listOf("Add profile", tr(R.string.show_18_content_2, "Show 18+ content"), tr(R.string.keep_18_activity_private, "Keep 18+ activity private"))),
        STATS(Group.YOU, tr(R.string.your_statistics, "Your statistics"), tr(R.string.watch_and_listen_totals_streaks_and, "Watch and listen totals, streaks and the activity map"),
            listOf("stats", "streak", "activity", "totals", "milestones", "insights", "wrapped", "top artists"), route = "stats"),
        APPEARANCE(Group.YOU, tr(R.string.look_feel, "Look & feel"), tr(R.string.theme_accent_colour_time_of_day, "Theme, accent colour, time-of-day colours, pure black, backdrop"),
            listOf("theme", "dark", "light", "amoled", "colour", "color", "accent", "ambient", "appearance"),
            settings = listOf("Pure black (AMOLED)", tr(R.string.ambient_colour_from_artwork, "Ambient colour from artwork"), tr(R.string.compact_text_and_corners, "Compact text and corners"), tr(R.string.japanese_accents, "Japanese accents"), "Backdrop", tr(R.string.your_photo, "Your photo"), "Intensity")),
        NAVIGATION(Group.YOU, tr(R.string.navigation_bar, "Navigation bar"), tr(R.string.choose_the_tabs_and_add_up, "Choose the tabs and add up to two shortcuts"),
            listOf("bottom bar", "tabs", "taskbar", "nav", "shortcut", "menu", "dock"),
            settings = listOf("Reset to default")),

        PLAYER(Group.VIDEO, tr(R.string.video_player, "Video player"), tr(R.string.background_play_picture_in_picture_volume, "Background play, picture-in-picture, volume boost, compressor, Back button, info overlay"),
            listOf("video", "pip", "floating", "minimize", "background", "volume", "boost", "louder", "compressor", "drc", "night mode", "dynamic range", "dialogue", "stats for nerds", "controls", "buttons", "customize"),
            settings = listOf("Background play", "Picture-in-picture", tr(R.string.volume_boost_up_to_200, "Volume boost up to 200 %"), tr(R.string.dynamic_range_compressor, "Dynamic range compressor"), tr(R.string.back_button_while_a_video_plays, "Back button while a video plays"), tr(R.string.player_info_overlay, "Player info overlay"),
                "Player controls", "Buttons on the bar", "Previous and next video", "Rewind and forward 10 s", "Fullscreen button")),
        VIDEO_QUALITY(Group.VIDEO, tr(R.string.video_quality_playback, "Video quality & playback"), tr(R.string.default_quality_sub_or_dub_speed, "Default quality, Sub or Dub, speed, decoder, Anime4K, default player, torrents"),
            listOf("video", "resolution", "1080p", "720p", "dub", "sub", "renderer", "external player", "vlc", "mx player", "upscale", "torrent", "magnet", "denoise", "de-blur", "sharpen"),
            settings = listOf("Default quality", tr(R.string.default_audio, "Default audio"), tr(R.string.default_speed, "Default speed"), tr(R.string.resume_playback, "Resume playback"), tr(R.string.video_decoder, "Video decoder"), tr(R.string.video_renderer, "Video renderer"), tr(R.string.anime4k_enhancement, "Anime4K enhancement"), tr(R.string.anime4k_preset, "Anime4K preset"),
                "Default player", "Torrent streaming", "TorrServer")),
        SUBTITLES(Group.VIDEO, tr(R.string.subtitles_intros, "Subtitles & intros"), tr(R.string.captions_subtitle_style_and_search_skip, "Captions, subtitle style and search, skip intro and outro"),
            listOf("cc", "captions", "subs", "opensubtitles", "subdl", "subsource", "language", "aniskip", "introdb", "intro", "outro", "opening", "ending"),
            settings = listOf("Captions on by default", tr(R.string.subtitle_style, "Subtitle style"), tr(R.string.subtitle_search_language, "Subtitle search language"), tr(R.string.skip_intro_outro, "Skip intro & outro"), tr(R.string.skip_automatically, "Skip automatically"))),

        MUSIC(Group.MUSIC, tr(R.string.music_sound, "Music & sound"), tr(R.string.hi_res_lossless_or_data_saver, "Hi-Res, lossless or data saver for Wi-Fi and mobile, equalizer, lyrics, Last.fm"),
            listOf("audio", "sound", "eq", "bass", "3d", "virtualizer", "quality", "flac", "lossless", "hi-res", "hires", "dolby", "atmos",
                "kbps", "bitrate", "data saver", "320", "lyrics", "lrclib", "scrobble", "gapless"),
            settings = listOf("Audio quality", tr(R.string.equalizer_sound_effects, "Equalizer & sound effects"), tr(R.string.find_lyrics_online, "Find lyrics online"), tr(R.string.last_fm_scrobbling, "Last.fm scrobbling"))),

        DOWNLOADS(Group.DOWNLOADS, "Downloads", tr(R.string.quality_folders_clean_up_and_private, "Quality, folders, clean-up and Private downloads"),
            listOf("offline", "storage", "quality", "delete", "save", "1dm", "adm", "download manager", "external", "private", "vault", "hidden downloads"),
            settings = listOf("Ask before each download", tr(R.string.permanent_by_default, "Permanent by default"), tr(R.string.save_youtube_and_link_downloads_to, "Save YouTube and link downloads to phone"), tr(R.string.show_tips_on_the_downloads_page, "Show tips on the Downloads page"),
                tr(R.string.hide_the_private_entry, "Hide the Private entry"))),
        LIBRARY(Group.DOWNLOADS, tr(R.string.library_storage, "Library & storage"), tr(R.string.scan_this_device_for_music_and, "Scan this device for music and videos"),
            listOf("scan", "rescan", "media", "files", "folder", "local"),
            settings = listOf("Rescan this device")),

        SOURCES(Group.SOURCES, "Sources", tr(R.string.aniyomi_mihon_stremio_script_repos_vega, "Aniyomi, Mihon, Stremio, script repos (Vega, Nuvio, SkyStream) and home servers"),
            listOf("extensions", "repo", "repository", "addon", "jellyfin", "plex", "emby", "dlna", "smb", "webdav", "mangayomi",
                "script repos", "javascript", "cloudstream", "providers"), route = "sources"),
        SOURCE_STATUS(Group.SOURCES, tr(R.string.source_status, "Source status"), tr(R.string.see_which_installed_sources_respond_right, "See which installed sources respond right now"),
            listOf("health", "broken", "working", "check"), route = "health"),
        // F-02 split: Telegram and Websites are two pages, each with its own entry.
        TELEGRAM(Group.SOURCES, "Telegram", tr(R.string.public_telegram_channels_with_no_login, "Public Telegram channels with no login, TeraBox and DiskWala links in posts"),
            listOf("telegram", "t.me", "channel", "channels", "terabox", "diskwala"), route = "telegram"),
        WEBSITES(Group.SOURCES, "Websites", tr(R.string.saved_video_sites_the_ad_and, "Saved video sites, the ad and pop-up filter, and opening the real link behind short links"),
            listOf("websites", "sites", "browser", "web", "link", "ad block", "ads", "pop-up", "popup", "short link", "shortener"), route = "websites"),
        TRACKING(Group.SOURCES, "Tracking", "AniList, MyAnimeList, Kitsu, Trakt, Simkl and Last.fm sign-in",
            listOf("anilist", "mal", "myanimelist", "kitsu", "trakt", "simkl", "sync", "list", "progress", "account", "scrobble"), route = "tracking",
            settings = listOf("Scrobble what I play", "Last.fm", tr(R.string.tracking_v2, "Tracking V2"))),
        YOUTUBE(Group.SOURCES, tr(R.string.youtube_account, "YouTube account"), tr(R.string.optional_sign_in_for_blocked_or, "Optional sign-in for blocked or age-restricted videos"),
            listOf("google", "login", "sign in", "bot", "age", "cookies", "cookie", "paste", "token", "youtube music"), route = "youtube"),
        TOGETHER(Group.SOURCES, tr(R.string.watch_together, "Watch together"), tr(R.string.sync_playback_with_people_on_the, "Sync playback with people on the same Wi-Fi"),
            listOf("party", "sync", "friends", "share"), route = "together"),

        NOTIFICATIONS(Group.SYSTEM, "Notifications", tr(R.string.inbox_alert_types_on_time_push, "Inbox, alert types, on-time push alerts, chime and reminders"),
            listOf("alerts", "inbox", "sound", "chime", "episodes", "news", "push", "test", "unifiedpush", "ntfy", "reliable", "late"),
            settings = listOf("Show notifications", tr(R.string.new_episodes, "New episodes"), tr(R.string.new_extensions, "New extensions"), tr(R.string.new_stream_playlists, "New stream playlists"), tr(R.string.yumeiro_news, "Yumeiro news"), tr(R.string.service_alerts, "Service alerts"), "Suggestions", tr(R.string.yumeiro_chime, "Yumeiro chime"), tr(R.string.instant_alerts, "Instant alerts"), tr(R.string.in_app_reminders, "In-app reminders"), tr(R.string.send_a_test_notification, "Send a test notification"), tr(R.string.reliable_alerts_unifiedpush, "Reliable alerts (UnifiedPush)"))),
        BACKGROUND(Group.SYSTEM, tr(R.string.background_battery, "Background & battery"), tr(R.string.background_checks_wi_fi_only_battery, "Background checks, Wi-Fi only, battery optimisation"),
            listOf("battery", "doze", "worker", "auto start", "autostart", "killed", "optimisation", "optimization"),
            settings = listOf("Background checks", tr(R.string.wi_fi_only, "Wi-Fi only"))),
        NETWORK(Group.SYSTEM, "Network", tr(R.string.smart_dns_dns_fallback_proxy_and, "Smart DNS, DNS fallback, proxy and VPN"),
            listOf("dns", "doh", "vpn", "blocked", "proxy", "socks", "cloudflare", "quad9", "internet"),
            settings = listOf("Get around blocked sites automatically", tr(R.string.only_when_system_dns_fails, "Only when system DNS fails"), tr(R.string.automatic_dns_fallback, "Automatic DNS fallback"))),
        PRIVACY(Group.SYSTEM, tr(R.string.privacy_security, "Privacy & security"), tr(R.string.app_lock_incognito_hide_in_recents, "App lock, incognito, hide in recents, permissions and clearing history"),
            listOf("lock", "fingerprint", "biometric", "incognito", "history", "clear", "cache", "permissions", "recents"),
            settings = listOf("Incognito", tr(R.string.lock_yumeiro, "Lock Yumeiro"), tr(R.string.hide_content_in_recents, "Hide content in recents"), tr(R.string.clear_watch_history, "Clear watch history"), tr(R.string.clear_18_history, "Clear 18+ history"), tr(R.string.clear_listening_history, "Clear listening history"), tr(R.string.clear_search_history, "Clear search history"), tr(R.string.clear_notifications, "Clear notifications"), tr(R.string.clear_cache, "Clear cache"), tr(R.string.all_app_permissions, "All app permissions"))),
        BACKUP(Group.SYSTEM, tr(R.string.backup_restore, "Backup & restore"), tr(R.string.save_everything_to_a_file_you, "Save everything to a file you control"),
            listOf("export", "import", "restore", "transfer", "phone"), route = "backup",
            settings = listOf("Back up automatically", tr(R.string.save_loaded_pages_on_the_phone, "Save loaded pages on the phone"), tr(R.string.reset_settings, "Reset settings"), tr(R.string.erase_everything_2, "Erase everything"))),

        UPDATES(Group.ABOUT, tr(R.string.app_updates, "App updates"), tr(R.string.check_for_new_versions_and_see, "Check for new versions and see what changed"),
            listOf("update", "version", "release", "changelog", "beta")),
        SETUP(Group.ABOUT, tr(R.string.setup_guide, "Setup guide"), tr(R.string.explore_yumeiro_again_or_open_the, "Explore Yumeiro again, or open the quick-setup checklist"),
            listOf("onboarding", "tour", "explore", "welcome", "start", "checklist", "getting started"), route = "setup"),
        GUIDE(Group.ABOUT, tr(R.string.user_guide, "User guide"), tr(R.string.how_every_part_of_yumeiro_works, "How every part of Yumeiro works"),
            listOf("help", "how to", "tutorial", "faq", "manual"), route = "guide"),
        CRASHES(Group.ABOUT, tr(R.string.crash_reports, "Crash reports"), tr(R.string.saved_on_this_phone_read_copy, "Saved on this phone. Read, copy or share one with the developer"),
            listOf("crash", "log", "logs", "bug", "error", "freeze", "anr", "report", "diagnostics")),
        ABOUT(Group.ABOUT, tr(R.string.about_developer, "About & developer"), tr(R.string.version_source_code_the_developer_and, "Version, source code, the developer and support"),
            listOf("github", "license", "credits", "donate", "support", "social", "contact")),
    }

    fun byGroup(): List<Pair<Group, List<Category>>> =
        Group.entries.map { g -> g to Category.entries.filter { it.group == g } }.filter { it.second.isNotEmpty() }

    /**
     * Categories matching every word of [query] (title, summary or keywords; case-insensitive), best
     * matches first: title hits rank above summary hits, which rank above keyword-only hits.
     */
    fun search(query: String): List<Category> {
        val words = query.trim().lowercase().split(RX_SETTINGSCATALOG_1).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return Category.entries.mapNotNull { c ->
            val title = c.title.lowercase()
            val summary = c.summary.lowercase()
            val keys = (c.keywords + c.settings).joinToString(" ").lowercase()
            var score = 0
            for (w in words) {
                score += when {
                    title.contains(w) -> 3
                    summary.contains(w) -> 2
                    keys.contains(w) -> 1
                    else -> return@mapNotNull null
                }
            }
            c to score
        }.sortedWith(compareByDescending<Pair<Category, Int>> { it.second }.thenBy { it.first.ordinal }).map { it.first }
    }

    fun fromName(name: String?): Category? = Category.entries.firstOrNull { it.name == name }
}
