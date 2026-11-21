/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

/**
 * OL-12: "Download playlist" on an Online Music playlist / album page. Every song is queued as
 * best audio (M4A when offered, picked when that song starts, so long queues never hold stale
 * links), songs already saved or downloading are skipped (Metrolist #4261 re-downloaded them),
 * and the header shows live progress for the whole batch.
 */
object PlaylistDownload {
    data class Track(val id: String, val title: String, val channel: String?, val thumbnail: String?)

    data class Progress(val total: Int, val done: Int, val active: Int, val failed: Int, val fraction: Float) {
        val started: Boolean get() = done + active + failed > 0
        val allDone: Boolean get() = total > 0 && done == total
    }

    /** Audio items of [items] for song [id] (any quality). */
    private fun audioOf(items: List<OnlineDownloads.Item>, id: String) =
        items.filter { it.ytId == id && it.kind == OnlineDownloads.Kind.YT_AUDIO }

    /** Songs of [tracks] still to queue: distinct, not saved and not already downloading. */
    fun toQueue(tracks: List<Track>, items: List<OnlineDownloads.Item>): List<Track> =
        tracks.distinctBy { it.id }.filter { t ->
            audioOf(items, t.id).none { it.status == OnlineDownloads.Status.DONE || it.active }
        }

    /** Progress of the batch [ids] (a song counts once; a finished copy wins over a failed one). */
    fun progress(ids: List<String>, items: List<OnlineDownloads.Item>): Progress {
        val songs = ids.distinct()
        var done = 0; var active = 0; var failed = 0; var sum = 0f
        for (id in songs) {
            val mine = audioOf(items, id)
            when {
                mine.any { it.status == OnlineDownloads.Status.DONE } -> { done++; sum += 1f }
                mine.any { it.active } -> { active++; sum += mine.filter { it.active }.maxOf { it.fraction } }
                mine.any { it.status == OnlineDownloads.Status.FAILED } -> failed++
            }
        }
        return Progress(songs.size, done, active, failed, if (songs.isEmpty()) 0f else sum / songs.size)
    }

    /** Short status line under the header buttons. */
    fun label(p: Progress): String = when {
        p.total == 0 -> ""
        p.allDone -> "All ${p.total} songs downloaded"
        p.active > 0 -> "Downloading · ${p.done} of ${p.total} done" + if (p.failed > 0) " · ${p.failed} failed" else ""
        p.failed > 0 -> "${p.done} of ${p.total} downloaded · ${p.failed} failed (tap to retry)"
        p.done > 0 -> "${p.done} of ${p.total} downloaded"
        else -> ""
    }
}
