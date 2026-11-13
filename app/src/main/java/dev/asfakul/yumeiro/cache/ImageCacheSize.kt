/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cache

import android.content.Context
import android.os.StatFs

/**
 * Poster cache size: 2 % of free space, between 64 MB and 512 MB. Posters were capped at 64 MB
 * and a few busy Yumix sessions pushed out yesterday's images, so they downloaded again.
 */
object ImageCacheSize {
    fun bytes(c: Context): Long = runCatching { pick(StatFs(c.cacheDir.path).availableBytes) }.getOrDefault(MIN)
    internal fun pick(free: Long): Long = (free / 50).coerceIn(MIN, MAX)
    private const val MIN = 64L * 1024 * 1024
    private const val MAX = 512L * 1024 * 1024
}
