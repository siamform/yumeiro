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
    )

    fun start(context: Context) {
        val app = context.applicationContext
        Thread({
            for (name in FIRST_FRAME) {
                // contains() waits for the load, so the file is parsed here and not on the main thread.
                runCatching { app.getSharedPreferences(name, Context.MODE_PRIVATE).contains("") }
            }
            // Loading the FFmpeg native libraries reads the APK; do it here, not when the first player is built.
            runCatching { dev.asfakul.yumeiro.stream.YumeiroRenderersFactory.ffmpegAvailable }
        }, "yu-prefs").apply { priority = Thread.NORM_PRIORITY }.start()
    }
}
