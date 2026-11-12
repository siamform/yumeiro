/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.youtube.YtItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Download this song" from anywhere in Music (Online rows, Mix rows and search, the Now Playing
 * options): one request at a time, shown by a single host sheet in the app shell. The sheet is the
 * existing YouTube quality picker opened on its Audio only tab; the file is fetched by the
 * download worker (NewPipe Extractor streams) and lands in Downloads and, if enabled, the phone's
 * Music/Yumeiro folder.
 */
object SongDownloads {
    private val _request = MutableStateFlow<YtItem?>(null)
    val request: StateFlow<YtItem?> = _request.asStateFlow()

    fun ask(item: YtItem) { _request.value = item.copy(music = true) }
    fun ask(track: AudioTrack) { fromTrack(track)?.let(::ask) }
    fun dismiss() { _request.value = null }

    /** A YouTube Music track of the music player → the item the download sheet needs; null for device songs. */
    fun fromTrack(track: AudioTrack): YtItem? {
        if (!track.id.startsWith("yt:")) return null
        val id = track.id.removePrefix("yt:").takeIf { it.isNotBlank() } ?: return null
        return YtItem(
            id = id,
            url = track.folderPath.takeIf { it.startsWith("http") } ?: "https://music.youtube.com/watch?v=$id",
            title = track.title,
            channel = track.artist,
            thumbnail = track.albumArtUri,
            durationSec = (track.durationMs / 1000L).coerceAtLeast(0L),
            music = true,
        )
    }
}
