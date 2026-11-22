/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

/**
 * DL-ONE (owner 2026-11-22): every download, YouTube too, uses the one shared download popup (DownloadPrompt /
 * the OL-6 sheet). YouTube's own quality list is mapped onto the popup's choices: Quality chips are the video
 * heights; the "Audio track" chips are the audio-only downloads (Default = the video with sound, or for a song the
 * best audio). Pure, unit-tested.
 */
object YtOneSheet {
    /** Label of an audio-only choice in the popup's audio row. */
    fun audioLabel(c: YtDownloadOptions.Choice): String =
        "Audio only · " + c.label.removePrefix("Audio ").trim().ifEmpty { c.container }

    fun offer(choices: List<YtDownloadOptions.Choice>, music: Boolean): DownloadTracks.Offer {
        val heights = if (music) emptyList() else choices.filter { !it.audioOnly && it.height > 0 }.map { it.height }.distinct().sortedDescending()
        val audios = choices.filter { it.audioOnly && it.audioItag > 0 }.map(::audioLabel).distinct()
        return DownloadTracks.Offer(heights = heights, audios = audios)
    }

    /** The choice the popup's [pick] means; null when there is nothing to save. */
    fun choose(choices: List<YtDownloadOptions.Choice>, music: Boolean, pick: DownloadTracks.Pick): YtDownloadOptions.Choice? {
        pick.audio?.let { a -> choices.firstOrNull { it.audioOnly && audioLabel(it) == a }?.let { return it } }
        if (music) return YtDownloadOptions.bestAudio(choices) ?: choices.firstOrNull { it.audioOnly }
        val video = choices.filter { !it.audioOnly }
        if (video.isEmpty()) return YtDownloadOptions.bestAudio(choices)
        val max = pick.maxHeight
        if (max <= 0) return video.maxByOrNull { it.height }
        return video.filter { it.height in 1..max }.maxByOrNull { it.height } ?: video.minByOrNull { it.height }
    }
}
