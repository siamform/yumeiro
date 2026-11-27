/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R

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
        // B13: a streamed song shows the size of the audio it streams (an estimate is marked as one).
        bytes != null && streamed(uri) -> if (estimated(uri)) "About ${format(bytes)} (streamed)" else "${format(bytes)} (streamed)"
        bytes != null -> format(bytes)
        streamed(uri) -> tr(R.string.streamed_no_file, "Streamed (no file)")
        else -> "Unknown"
    }

    private fun estimated(uri: String): Boolean {
        val id = dev.asfakul.yumeiro.youtube.YouTubeAudioDataSource.videoIdOf(runCatching { android.net.Uri.parse(uri) }.getOrNull()) ?: return false
        return dev.asfakul.yumeiro.youtube.YouTubeStreams.cachedFacts(id)?.exact == false
    }
}
