/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.asfakul.mtrevanced.ui.image.VideoThumbnailFetcher

class MTApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Must come first: a crashing extension must never take the whole app down.
        dev.asfakul.mtrevanced.online.ExtensionGuard.install()
        // Foreground/background tracking for seamless (Messenger-style) background updates.
        dev.asfakul.mtrevanced.update.AppVisibility.install(this)
        // Per-host header profiles that unlocked refused (HTTP 403) streams before.
        runCatching { dev.asfakul.mtrevanced.online.AccessLadder.attach(this) }
        // YouTube / YouTube Music (no login): NewPipe init + fresh-link refresher for 403/expiry.
        runCatching { dev.asfakul.mtrevanced.youtube.YouTubePlayback.install(this) }
        // Background work for online videos: new-episode checks and unfinished downloads.
        runCatching { dev.asfakul.mtrevanced.online.NewEpisodesWorker.schedule(this) }
        // Feed (new extensions / playlists / news) and occasional suggestions (each switchable in Settings).
        runCatching { dev.asfakul.mtrevanced.notify.DiscoveryWorker.schedule(this) }
        runCatching { dev.asfakul.mtrevanced.notify.AppNotifier.ensureChannels(this) }
        runCatching { dev.asfakul.mtrevanced.network.Connectivity.start(this) }
        runCatching { dev.asfakul.mtrevanced.online.download.OnlineDownloads.resumeQueued(this) }
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
