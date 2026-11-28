/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.asfakul.yumeiro.ui.image.VideoThumbnailFetcher

class MTApplication : Application(), ImageLoaderFactory {
    /**
     * ST-20: extensions keep the application context (`context.applicationContext`) for their
     * settings, so the guard sits here, where every lookup ends (the Activity hands its own lookups
     * here too, X2-3). Device run 43825449454 (api33-lowram): `DonationManager.recordShown` still
     * committed on main with the guard only on the Activity. Yumeiro's own files come back unwrapped.
     */
    override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences =
        dev.asfakul.yumeiro.online.ExtensionPrefs.wrapIfExtension(name, super.getSharedPreferences(name, mode), MTApplication::class.java.classLoader!!)

    override fun onCreate() {
        super.onCreate()
        // The helper process that restarts the app after a restore does nothing else.
        if (dev.asfakul.yumeiro.backup.Restart.isRestartProcess(this)) return
        // Round 12: a staged restore / reset is applied before anything reads a setting or file.
        dev.asfakul.yumeiro.backup.PendingRestore.applyIfAny(this)
        // FF-LOAD: flags read without a context load the saved file (not the defaults); no disk read here.
        FeatureFlags.attach(this)
        // G12 X11-2: the first network request loads the user's DNS settings (not the defaults).
        dev.asfakul.yumeiro.network.NetworkSettings.attach(this)
        // NBUG-13 (ADR-045): proxy settings load with the first request; WebView set-up only when it is on, at idle.
        dev.asfakul.yumeiro.network.AppProxy.attach(this)
        runCatching { dev.asfakul.yumeiro.network.AppProxy.applyWhenIdle(this) }
        // R15: load the settings files the first screen reads, off the main thread.
        dev.asfakul.yumeiro.startup.PrefsWarmup.start(this)
        // Round 2: crash reports are written to the phone (Settings → Crash reports) before anything else can fail.
        runCatching { dev.asfakul.yumeiro.diagnostics.CrashLog.install(this) }
        dev.asfakul.yumeiro.cache.DataCache.init(this)
        DeviceClass.init(this)
        // Must come first: a crashing extension must never take the whole app down.
        dev.asfakul.yumeiro.online.ExtensionGuard.install()
        // FINAL-17 (ADR-049): a full disk inside WorkManager's own threads no longer takes the app down.
        dev.asfakul.yumeiro.diagnostics.DiskFullGuard.install()
        dev.asfakul.yumeiro.security.Privacy.load(this)
        // Foreground/background tracking for seamless (Messenger-style) background updates.
        dev.asfakul.yumeiro.update.AppVisibility.install(this)
        // Round 2: thermal status + battery saver slow polling and extension work down.
        runCatching { dev.asfakul.yumeiro.player.HeatGuard.install(this) }
        // Per-host header profiles that unlocked refused (HTTP 403) streams before.
        runCatching { dev.asfakul.yumeiro.online.AccessLadder.attach(this) }
        // YouTube / YouTube Music (no login): NewPipe init + fresh-link refresher for 403/expiry.
        runCatching { dev.asfakul.yumeiro.youtube.YouTubePlayback.install(this) }
        // F-02 / F-03 (ADR-042): fresh-link refreshers for Telegram posts and TeraBox / DiskWala pages (no I/O here).
        runCatching { dev.asfakul.yumeiro.online.telegram.TelegramPlayback.install(this) }
        runCatching { dev.asfakul.yumeiro.stream.CloudRecapture.install(this) }
        // B-29: TeraBox's API reuses the sign-in the user made in the web view (read lazily, off the main thread).
        dev.asfakul.yumeiro.stream.TeraBoxApi.canPlayAv1 = {
            runCatching {
                android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS).codecInfos
                    .any { !it.isEncoder && it.supportedTypes.any { t -> t.equals("video/av01", true) } }
            }.getOrDefault(false)
        }
        dev.asfakul.yumeiro.stream.TeraBoxApi.cookieSource = { url -> runCatching { android.webkit.CookieManager.getInstance().getCookie(url) }.getOrNull() }
        // DW-BOT-1: DiskWala shares open through a Telegram downloader bot once an account is signed in (read when a link resolves, off the main thread).
        dev.asfakul.yumeiro.stream.StreamResolver.telegramSignedIn = {
            runCatching { dev.asfakul.yumeiro.FeatureFlags.on(this, dev.asfakul.yumeiro.FeatureFlags.Flag.TELEGRAM_LOGIN) && dev.asfakul.yumeiro.online.telegram.td.TgAccounts.signedInHint(this) }.getOrDefault(false)
        }
        runCatching { dev.asfakul.yumeiro.notify.AppNotifier.ensureChannels(this) }
        runCatching { dev.asfakul.yumeiro.network.Connectivity.start(this) }
        runCatching { dev.asfakul.yumeiro.player.AudioQuality.init(this) }
        runCatching { dev.asfakul.yumeiro.player.VideoPrefs.init(this) }
        runCatching { dev.asfakul.yumeiro.player.Anime4kV2.init(this) }
        runCatching { dev.asfakul.yumeiro.player.PictureLook.init(this) }
        runCatching { dev.asfakul.yumeiro.stream.TorrentSettings.init(this) }
        // R15: memory / thread breadcrumbs for crash reports, StrictMode in debug builds.
        runCatching { dev.asfakul.yumeiro.diagnostics.MemoryWatch.install(this) }
        runCatching { dev.asfakul.yumeiro.diagnostics.DevStrictMode.install() }
        // R15: everything below touches disk, Firebase or WorkManager and used to run on the main
        // thread before the first frame. None of it is needed to draw the first screen.
        Thread({
            val app = this
            // Precompile the big pattern tables (release tags, audio languages, source facts, Yumix
            // feed rules) here so the first list that shows a card never compiles them on the main thread.
            runCatching { dev.asfakul.yumeiro.text.RegexWarmup.run() }
            // B20 S6: translated-subtitle folders older than 48 h (unless it is the episode to resume) and over 30 MB.
            runCatching {
                dev.asfakul.yumeiro.online.OnlineHistory.ensureLoaded(app)
                dev.asfakul.yumeiro.subtitles.translate.SubtitleTemp.sweep(app) { api, data ->
                    dev.asfakul.yumeiro.online.OnlineHistory.progressOf(api, data)?.let { p -> p.durationMs <= 0 || p.positionMs < p.durationMs * 0.92 } ?: false
                }
            }
            // E4: measured extension speeds (prefs read, so here and not on main).
            runCatching { dev.asfakul.yumeiro.online.ExtensionSpeed.init(app) }
            // FIND-2: remembered title → source matches (prefs read).
            runCatching { dev.asfakul.yumeiro.sources.hub.SourceFinder2.init(app) }
            // TRACK-2: Tracking V2 switch and its queue file (disk read, so here and not on main).
            runCatching { dev.asfakul.yumeiro.tracking.v2.TrackV2.init(app) }
            // ADCHAIN-2: the page browser's ad-filter / short-link switches (prefs read, so here and not on main).
            runCatching { dev.asfakul.yumeiro.online.web.BrowserFilters.start(app) }
            // R16 (ST-7): build the source-menu icons (Material vectors, the Yumix logo) here, not in
            // the first composition on main. Icon getters cache what they build; a race only builds twice.
            runCatching {
                dev.asfakul.yumeiro.ui.screens.online.videoSourceOptions(0, 0, true)
                dev.asfakul.yumeiro.ui.screens.music.musicSourceOptions(0, true)
            }
            // Background work for online videos: new-episode checks and unfinished downloads.
            runCatching { dev.asfakul.yumeiro.online.NewEpisodesWorker.schedule(app) }
            // Feed (new extensions / playlists / news) and occasional suggestions (each switchable in Settings).
            runCatching { dev.asfakul.yumeiro.notify.DiscoveryWorker.schedule(app) }
            // Instant notices (FCM): set up Firebase from BuildConfig and sync registration/topics in the background.
            runCatching { dev.asfakul.yumeiro.notify.FcmPush.start(app) }
            runCatching { dev.asfakul.yumeiro.online.download.OnlineDownloads.resumeQueued(app) }
            runCatching {
                dev.asfakul.yumeiro.online.download.OnlineDownloads.cleanup(app)
                dev.asfakul.yumeiro.online.download.OnlineDownloads.scheduleCleanup(app)
            }
            runCatching { dev.asfakul.yumeiro.backup.AutoBackupWorker.schedule(app) }
            // Time limits for saved pages and website cookies.
            runCatching { dev.asfakul.yumeiro.cache.DataCache.runJanitor(app) }
            // B20: read the open profile's taste store here (library hooks call it on main), importing what the
            // profile already watched and read the first time.
            runCatching { dev.asfakul.yumeiro.intel.TasteImport.ensure(app) }
        }, "yu-startup").apply { priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runCatching { dev.asfakul.yumeiro.diagnostics.MemoryWatch.onTrim(this, level) }
        // A closed player kept for its short reopen grace goes at once when memory is tight.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) runCatching { dev.asfakul.yumeiro.player.PlaybackSession.releaseIfDetached() }
        // E4 part 2: background feed work steps back to one job for a minute when memory is short (FeedThrottle).
        // FE-T1: only when the trim is backed by a real shortage (FeedThrottle.confirmed).
        runCatching { dev.asfakul.yumeiro.online.FeedThrottle.onTrim(this, level) }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { dev.asfakul.yumeiro.diagnostics.MemoryWatch.onTrim(this, android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) }
        dev.asfakul.yumeiro.online.FeedThrottle.memoryLow()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        // R15: posters share the app's HTTP client (same DNS, pool and request cap) instead of Coil
        // building its own; at most a few bitmaps decode at once (each decode is a memory spike).
        // NBUG-14 (NB14-2): the same client with a named User-Agent (Wikimedia refuses okhttp's).
        .okHttpClient { dev.asfakul.yumeiro.ui.image.ImageHttp.client(this) }
        .fetcherDispatcher(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(if (DeviceClass.lowEnd) 4 else 8))
        .decoderDispatcher(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(if (DeviceClass.lowEnd) 2 else 3))
        .components {
            dev.asfakul.yumeiro.ui.image.ImageDecoders.factories().forEach { add(it) }
            add(VideoThumbnailFetcher.Keyer())
            add(VideoThumbnailFetcher.Factory(this@MTApplication))
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(if (DeviceClass.lowEnd && !Edition.isTv) 0.15 else Edition.imageMemoryShare).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(dev.asfakul.yumeiro.cache.ImageCacheSize.bytes(this)).build() }
        // TV: no crossfade (cheap TV GPUs drop frames while a rail scrolls) and 16-bit posters.
        // Round 13: the same on low-end phones (smooth scrolling over pretty fades).
        .crossfade(if (Edition.isTv || DeviceClass.lowEnd) 0 else 150)
        .allowRgb565(Edition.isTv || DeviceClass.lowEnd)
        .respectCacheHeaders(false)
        .build()
}
