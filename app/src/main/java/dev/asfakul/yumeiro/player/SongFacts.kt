/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

/**
 * H-8 (owner item 8): Song info showed no duration (0:00) and no file size when the library row had 0 for
 * them (MediaStore leaves both 0 for some files and providers; online songs have no file). The best known
 * value wins: the library row, then the file itself, then (duration only) the player.
 */
object SongFacts {
    fun durationMs(library: Long, fromFile: Long?, fromPlayer: Long): Long? =
        library.takeIf { it > 0 } ?: fromFile?.takeIf { it > 0 } ?: fromPlayer.takeIf { it > 0 }

    fun sizeBytes(library: Long, fromFile: Long?): Long? = library.takeIf { it > 0 } ?: fromFile?.takeIf { it > 0 }

    /** True for songs played from the network (YouTube Music and links): no file, so no file size. */
    fun streamed(uri: String): Boolean = !uri.startsWith("content:") && !uri.startsWith("file:") && !uri.startsWith("/")

    /** The Size row's text: a size, "Streamed (no file)" or "Unknown". */
    fun sizeLabel(bytes: Long?, uri: String, format: (Long) -> String): String = when {
        bytes != null -> format(bytes)
        streamed(uri) -> "Streamed (no file)"
        else -> "Unknown"
    }
}
