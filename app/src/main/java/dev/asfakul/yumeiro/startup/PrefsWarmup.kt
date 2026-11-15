package dev.asfakul.yumeiro.startup

import android.content.Context

/**
 * R15 audit: the first frame reads the theme, music library, equalizer and scrobbler settings on the
 * main thread. The first `getSharedPreferences` call for a file checks the disk and every read waits
 * for the XML to be parsed, which StrictMode flagged at startup (and which costs tens of milliseconds
 * on a slow eMMC phone). Opening those files on a background thread right after a staged restore has
 * been applied means the main thread later gets the cached, already-loaded instance.
 *
 * Must run after [dev.asfakul.yumeiro.backup.PendingRestore.applyIfAny]: a restore replaces these files
 * and the framework caches the instance for the life of the process.
 */
object PrefsWarmup {
    /** Files read while the first screen is drawn. */
    val FIRST_FRAME = listOf(
        "yumeiro_tv_mode",        // TvMode, read in MainActivity.onCreate (R16 stall, run 43671209244)
        "play_counts",            // PlayCounts (Home "Most played")
        "mt_appearance",          // ThemeSettings
        "mt_player_video_prefs",  // OfflineMediaRepository: favourites, hidden folders
        "mt_player_music_prefs",  // OfflineMediaRepository: favourites, playlists, history
        "mt_player_eq_prefs",     // EqualizerManager
        "scrobble",               // Scrobbler
        "online_settings",
        "online",
        "pinned_extensions",
        "download_settings",
        "provider_overrides",
        // Second StrictMode pass (device run 43629395323):
        "mt_streams",             // StreamRepository
        "mt_updater",             // UpdatePreferences
        "yu_opening_kinds",       // OpeningKinds
        "watch_prefs",            // AudioLanguages
        "mt_subtitle_style",      // SubtitleStyleSettings
        "mt_player",              // PlayerSettings
        "yu_last_played",         // LastPlayed
        "yumeiro_listening",      // ListeningTime
        "browser_check",          // BrowserCheck cached UA
        // R16 third StrictMode pass (device run 43683520404, api36), still read on main at start:
        "yu_nav",                 // NavPrefs
        "setup_guide",            // SetupGuide.shouldAutoShow
        "home_layout",            // HomeLayout.load
        "yt_topics",              // YtTopics.load
        "search_recent",          // RecentSearches
        "backup_meta",            // BackupMeta.takeNote
        "notify_settings",        // NotifySettings
        "permission_center",      // PermissionCenter
        "push_wake",              // PushWake
        "background_work",        // BackgroundHealth
        "yu_sort",                // MainViewModel sort orders (R16 G5)
        "discovery_state",
        "reliability_guide",      // ReliabilityGuide
        "reminders",              // Reminders
    )

    fun start(context: Context) {
        val app = context.applicationContext
        Thread({
            // filesDir is cached by the context after the first call (a stat/mkdir on disk).
            runCatching { app.filesDir }
            // getSharedPreferences() starts each file's load on its own thread, so asking for all
            // of them first loads them side by side; the main thread then finds most ready instead
            // of waiting for a file far down a one-by-one list (R16: 3rd entry still read on main).
            val all = FIRST_FRAME.mapNotNull { runCatching { app.getSharedPreferences(it, Context.MODE_PRIVATE) }.getOrNull() }
            // contains() waits for the load, so any parse cost lands here, not on the main thread.
            for (p in all) runCatching { p.contains("") }
            // The inbox JSON file is read when Home draws its bell badge.
            runCatching { dev.asfakul.yumeiro.notify.Inbox.ensureLoaded(app) }
            // R16 (stress 43687396407 StrictMode): the page backdrop checks its photo file, Home
            // reads the bookmarks and downloads JSON files; all three ran on main at first draw.
            runCatching { dev.asfakul.yumeiro.ui.theme.BackdropSettings.init(app) }
            runCatching { dev.asfakul.yumeiro.online.OnlineBookmarks.ensureLoaded(app) }
            runCatching { dev.asfakul.yumeiro.online.download.OnlineDownloads.ensureLoaded(app) }
            // G12 X2-2: Home's setup checklist asks for the media and notification permission;
            // the first binder call in a process is uncached, so make it here, not in composition.
            runCatching { dev.asfakul.yumeiro.ui.screens.extras.PermissionCenter.readSetupPerms(app) }
            // Loading the FFmpeg native libraries reads the APK; do it here, not when the first player is built.
            runCatching { dev.asfakul.yumeiro.stream.YumeiroRenderersFactory.ffmpegAvailable }
        }, "yu-prefs").apply { priority = Thread.NORM_PRIORITY }.start()
    }
}
