/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

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
        DOWNLOADS("Downloads & library"),
        SOURCES("Sources & accounts"),
        SYSTEM("System"),
        ABOUT("Help & about"),
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
        PROFILES(Group.YOU, "Profiles", "Who's watching? Own history and lists per person, a PIN or fingerprint, 18+ per profile",
            listOf("profile", "kids", "family", "child", "pin", "user", "switch", "people", "adult", "nsfw", "18"),
            settings = listOf("Add profile", "Show 18+ content", "Keep 18+ activity private")),
        STATS(Group.YOU, "Your statistics", "Watch and listen totals, streaks and the activity map",
            listOf("stats", "streak", "activity", "totals", "milestones", "insights", "wrapped", "top artists"), route = "stats"),
        APPEARANCE(Group.YOU, "Look & feel", "Theme, accent colour, time-of-day colours, pure black, backdrop",
            listOf("theme", "dark", "light", "amoled", "colour", "color", "accent", "ambient", "appearance"),
            settings = listOf("Pure black (AMOLED)", "Ambient colour from artwork", "Japanese accents", "Backdrop", "Your photo", "Intensity")),
        NAVIGATION(Group.YOU, "Navigation bar", "Choose the tabs and add up to two shortcuts",
            listOf("bottom bar", "tabs", "taskbar", "nav", "shortcut", "menu", "dock"),
            settings = listOf("Reset to default")),

        PLAYER(Group.VIDEO, "Video player", "Background play, picture-in-picture, volume boost, Back button, info overlay",
            listOf("video", "pip", "floating", "minimize", "background", "volume", "boost", "louder", "stats for nerds", "controls", "buttons", "customize"),
            settings = listOf("Background play", "Picture-in-picture", "Volume boost up to 200 %", "Back button while a video plays", "Player info overlay",
                "Player controls", "Buttons on the bar", "Previous and next video", "Rewind and forward 10 s", "Fullscreen button")),
        VIDEO_QUALITY(Group.VIDEO, "Video quality & playback", "Default quality, Sub or Dub, speed, decoder, Anime4K, default player, torrents",
            listOf("video", "resolution", "1080p", "720p", "dub", "sub", "renderer", "external player", "vlc", "mx player", "upscale", "torrent", "magnet"),
            settings = listOf("Default quality", "Default audio", "Default speed", "Resume playback", "Video decoder", "Video renderer", "Anime4K enhancement",
                "Default player", "Torrent streaming", "TorrServer")),
        SUBTITLES(Group.VIDEO, "Subtitles & intros", "Captions, subtitle style and search, skip intro and outro",
            listOf("cc", "captions", "subs", "opensubtitles", "subdl", "subsource", "language", "aniskip", "introdb", "intro", "outro", "opening", "ending"),
            settings = listOf("Captions on by default", "Subtitle style", "Subtitle search language", "Skip intro & outro", "Skip automatically")),

        MUSIC(Group.MUSIC, "Music & sound", "Hi-Res, lossless or data saver for Wi-Fi and mobile, equalizer, lyrics, Last.fm",
            listOf("audio", "sound", "eq", "bass", "3d", "virtualizer", "quality", "flac", "lossless", "hi-res", "hires", "dolby", "atmos",
                "kbps", "bitrate", "data saver", "320", "lyrics", "lrclib", "scrobble", "gapless"),
            settings = listOf("Audio quality", "Equalizer & sound effects", "Find lyrics online", "Last.fm scrobbling")),

        DOWNLOADS(Group.DOWNLOADS, "Downloads", "Quality, folders, clean-up and Private downloads",
            listOf("offline", "storage", "quality", "delete", "save", "1dm", "adm", "download manager", "external", "private", "vault", "hidden downloads"),
            settings = listOf("Ask before each download", "Permanent by default", "Save YouTube and link downloads to phone", "Show tips on the Downloads page",
                "Hide the Private entry")),
        LIBRARY(Group.DOWNLOADS, "Library & storage", "Scan this device for music and videos",
            listOf("scan", "rescan", "media", "files", "folder", "local"),
            settings = listOf("Rescan this device")),

        SOURCES(Group.SOURCES, "Sources", "Aniyomi, Mihon, Stremio, script repos (Vega, Nuvio, SkyStream) and home servers",
            listOf("extensions", "repo", "repository", "addon", "jellyfin", "plex", "emby", "dlna", "smb", "webdav", "mangayomi",
                "script repos", "javascript", "cloudstream", "providers"), route = "sources"),
        SOURCE_STATUS(Group.SOURCES, "Source status", "See which installed sources respond right now",
            listOf("health", "broken", "working", "check"), route = "health"),
        // F-02 split: Telegram and Websites are two pages, each with its own entry.
        TELEGRAM(Group.SOURCES, "Telegram", "Public Telegram channels with no login, TeraBox and DiskWala links in posts",
            listOf("telegram", "t.me", "channel", "channels", "terabox", "diskwala"), route = "telegram"),
        WEBSITES(Group.SOURCES, "Websites", "Saved video sites, the ad and pop-up filter, and opening the real link behind short links",
            listOf("websites", "sites", "browser", "web", "link", "ad block", "ads", "pop-up", "popup", "short link", "shortener"), route = "websites"),
        TRACKING(Group.SOURCES, "Tracking", "AniList, MyAnimeList, Kitsu, Trakt, Simkl and Last.fm sign-in",
            listOf("anilist", "mal", "myanimelist", "kitsu", "trakt", "simkl", "sync", "list", "progress", "account", "scrobble"), route = "tracking",
            settings = listOf("Scrobble what I play", "Last.fm")),
        YOUTUBE(Group.SOURCES, "YouTube account", "Optional sign-in for blocked or age-restricted videos",
            listOf("google", "login", "sign in", "bot", "age", "cookies", "cookie", "paste", "token", "youtube music"), route = "youtube"),
        TOGETHER(Group.SOURCES, "Watch together", "Sync playback with people on the same Wi-Fi",
            listOf("party", "sync", "friends", "share"), route = "together"),

        NOTIFICATIONS(Group.SYSTEM, "Notifications", "Inbox, alert types, on-time push alerts, chime and reminders",
            listOf("alerts", "inbox", "sound", "chime", "episodes", "news", "push", "test", "unifiedpush", "ntfy", "reliable", "late"),
            settings = listOf("Show notifications", "New episodes", "New extensions", "New stream playlists", "Yumeiro news", "Service alerts", "Suggestions", "Yumeiro chime", "Instant alerts", "In-app reminders", "Send a test notification", "Reliable alerts (UnifiedPush)")),
        BACKGROUND(Group.SYSTEM, "Background & battery", "Background checks, Wi-Fi only, battery optimisation",
            listOf("battery", "doze", "worker", "auto start", "autostart", "killed", "optimisation", "optimization"),
            settings = listOf("Background checks", "Wi-Fi only")),
        NETWORK(Group.SYSTEM, "Network", "Smart DNS, DNS fallback, proxy and VPN",
            listOf("dns", "doh", "vpn", "blocked", "proxy", "socks", "cloudflare", "quad9", "internet"),
            settings = listOf("Get around blocked sites automatically", "Only when system DNS fails", "Automatic DNS fallback")),
        PRIVACY(Group.SYSTEM, "Privacy & security", "App lock, incognito, hide in recents, permissions and clearing history",
            listOf("lock", "fingerprint", "biometric", "incognito", "history", "clear", "cache", "permissions", "recents"),
            settings = listOf("Incognito", "Lock Yumeiro", "Hide content in recents", "Clear watch history", "Clear 18+ history", "Clear listening history", "Clear search history", "Clear notifications", "Clear cache", "All app permissions")),
        BACKUP(Group.SYSTEM, "Backup & restore", "Save everything to a file you control",
            listOf("export", "import", "restore", "transfer", "phone"), route = "backup",
            settings = listOf("Back up automatically", "Save loaded pages on the phone", "Reset settings", "Erase everything")),

        UPDATES(Group.ABOUT, "App updates", "Check for new versions and see what changed",
            listOf("update", "version", "release", "changelog", "beta")),
        SETUP(Group.ABOUT, "Setup guide", "Explore Yumeiro again, or open the quick-setup checklist",
            listOf("onboarding", "tour", "explore", "welcome", "start", "checklist", "getting started"), route = "setup"),
        GUIDE(Group.ABOUT, "User guide", "How every part of Yumeiro works",
            listOf("help", "how to", "tutorial", "faq", "manual"), route = "guide"),
        CRASHES(Group.ABOUT, "Crash reports", "Saved on this phone. Read, copy or share one with the developer",
            listOf("crash", "log", "logs", "bug", "error", "freeze", "anr", "report", "diagnostics")),
        ABOUT(Group.ABOUT, "About & developer", "Version, source code, the developer and support",
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
