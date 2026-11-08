/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

/**
 * The shape of Settings: a short home page of groups, each holding a few categories. A category
 * either opens its own page inside Settings ([route] == null) or jumps to an existing screen
 * ([route] is the key handled by `onOpenPage`). Plain data so ordering and search are unit-tested.
 */
object SettingsCatalog {

    enum class Group(val label: String) {
        PERSONAL("Make it yours"),
        WATCH("Watch & listen"),
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
    ) {
        APPEARANCE(Group.PERSONAL, "Look & feel", "Theme, time-of-day colours, pure black, artwork glow",
            listOf("theme", "dark", "light", "amoled", "colour", "color", "ambient", "appearance")),

        PLAYER(Group.WATCH, "Player", "Background play, picture-in-picture, captions, Back button",
            listOf("video", "pip", "subtitles", "cc", "floating", "minimize", "background")),
        AUDIO(Group.WATCH, "Audio & equalizer", "Equalizer, bass boost, virtualizer, presets",
            listOf("sound", "eq", "bass", "music", "3d", "virtualizer")),
        ONLINE(Group.WATCH, "Online videos", "18+ content, skip intro, subtitle search",
            listOf("anime", "adult", "nsfw", "aniskip", "opensubtitles", "language", "intro", "outro")),
        DOWNLOADS(Group.WATCH, "Downloads", "Quality, folders and automatic clean-up",
            listOf("offline", "storage", "quality", "delete", "save")),
        LIBRARY(Group.WATCH, "Library & storage", "Scan this device for music and videos",
            listOf("scan", "rescan", "media", "files", "folder", "local")),

        SOURCES(Group.SOURCES, "Sources", "Aniyomi, Mihon, Stremio, JavaScript and home servers, with how-to guides",
            listOf("extensions", "repo", "repository", "addon", "jellyfin", "plex", "emby", "dlna", "smb", "webdav", "mangayomi"), route = "sources"),
        SOURCE_STATUS(Group.SOURCES, "Source status", "See which installed sources respond right now",
            listOf("health", "broken", "working", "check"), route = "health"),
        TRACKING(Group.SOURCES, "Tracking", "AniList, MyAnimeList, Kitsu and Trakt, with setup guides",
            listOf("anilist", "mal", "myanimelist", "kitsu", "trakt", "sync", "list", "progress", "account"), route = "tracking"),
        YOUTUBE(Group.SOURCES, "YouTube account", "Optional sign-in for blocked or age-restricted videos",
            listOf("google", "login", "sign in", "bot", "age", "cookies", "cookie", "paste", "token", "youtube music"), route = "youtube"),
        TOGETHER(Group.SOURCES, "Watch together", "Sync playback with people on the same Wi-Fi",
            listOf("party", "sync", "friends", "share"), route = "together"),

        NOTIFICATIONS(Group.SYSTEM, "Notifications", "Inbox, alert types, on-time push alerts, chime and reminders",
            listOf("alerts", "inbox", "sound", "chime", "episodes", "news", "push", "test", "unifiedpush", "ntfy", "reliable", "late")),
        BACKGROUND(Group.SYSTEM, "Background & battery", "Background checks, Wi-Fi only, battery optimisation",
            listOf("battery", "doze", "worker", "auto start", "autostart", "killed", "optimisation", "optimization")),
        NETWORK(Group.SYSTEM, "Network", "Smart DNS, automatic unblocking and VPN",
            listOf("dns", "doh", "vpn", "blocked", "proxy", "cloudflare", "internet")),
        PRIVACY(Group.SYSTEM, "Privacy & security", "App lock, incognito, permissions and clearing history",
            listOf("lock", "fingerprint", "biometric", "incognito", "history", "clear", "cache", "permissions", "recents")),
        BACKUP(Group.SYSTEM, "Backup & restore", "Save everything to a file you control",
            listOf("export", "import", "restore", "transfer", "phone"), route = "backup"),

        UPDATES(Group.ABOUT, "App updates", "Check for new versions from GitHub Releases",
            listOf("update", "version", "release", "changelog", "beta")),
        GUIDE(Group.ABOUT, "User guide", "How every part of Yumeiro works",
            listOf("help", "how to", "tutorial", "faq", "manual"), route = "guide"),
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
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return Category.entries.mapNotNull { c ->
            val title = c.title.lowercase()
            val summary = c.summary.lowercase()
            val keys = c.keywords.joinToString(" ").lowercase()
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
