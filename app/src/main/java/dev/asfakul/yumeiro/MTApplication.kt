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
    override fun onCreate() {
        super.onCreate()
        // Must come first: a crashing extension must never take the whole app down.
        dev.asfakul.yumeiro.online.ExtensionGuard.install()
        // Foreground/background tracking for seamless (Messenger-style) background updates.
        dev.asfakul.yumeiro.update.AppVisibility.install(this)
        // Per-host header profiles that unlocked refused (HTTP 403) streams before.
        runCatching { dev.asfakul.yumeiro.online.AccessLadder.attach(this) }
        // YouTube / YouTube Music (no login): NewPipe init + fresh-link refresher for 403/expiry.
        runCatching { dev.asfakul.yumeiro.youtube.YouTubePlayback.install(this) }
        // Background work for online videos: new-episode checks and unfinished downloads.
        runCatching { dev.asfakul.yumeiro.online.NewEpisodesWorker.schedule(this) }
        // Feed (new extensions / playlists / news) and occasional suggestions (each switchable in Settings).
        runCatching { dev.asfakul.yumeiro.notify.DiscoveryWorker.schedule(this) }
        runCatching { dev.asfakul.yumeiro.notify.AppNotifier.ensureChannels(this) }
        runCatching { dev.asfakul.yumeiro.network.Connectivity.start(this) }
        runCatching { dev.asfakul.yumeiro.online.download.OnlineDownloads.resumeQueued(this) }
        runCatching {
            dev.asfakul.yumeiro.online.download.OnlineDownloads.cleanup(this)
            dev.asfakul.yumeiro.online.download.OnlineDownloads.scheduleCleanup(this)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(VideoThumbnailFetcher.Keyer())
            add(VideoThumbnailFetcher.Factory(this@MTApplication))
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(64L * 1024 * 1024).build() }
        .crossfade(150)
        .respectCacheHeaders(false)
        .build()
}
